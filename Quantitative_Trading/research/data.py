from __future__ import annotations

import sqlite3
from pathlib import Path

import baostock as bs
import pandas as pd


def load_calendar(database: Path) -> list[str]:
    with sqlite3.connect(database) as conn:
        rows = conn.execute(
            "SELECT date FROM trading_calendar WHERE is_trading_day = 1 ORDER BY date"
        ).fetchall()
    return [row[0] for row in rows]


def load_membership_intervals(database: Path) -> pd.DataFrame:
    with sqlite3.connect(database) as conn:
        return pd.read_sql_query(
            "SELECT stock_code, valid_from, valid_to FROM universe_membership "
            "WHERE index_code = '000300' ORDER BY stock_code, valid_from",
            conn,
            dtype={"stock_code": "string"},
        )


def load_names(database: Path) -> dict[str, str]:
    with sqlite3.connect(database) as conn:
        return dict(conn.execute("SELECT stock_code, name FROM security"))


def lookback_start(calendar: list[str], start_date: str, trading_days: int) -> str:
    eligible = [day for day in calendar if day < start_date]
    if not eligible:
        return calendar[0]
    index = max(0, len(eligible) - trading_days)
    return eligible[index]


def load_factor_panel(database: Path, start_date: str, end_date: str) -> pd.DataFrame:
    query = """
        SELECT
            a.date,
            a.stock_code,
            a.close_adj,
            r.close_raw,
            r.amount_cny,
            r.trade_status,
            s.is_st
        FROM daily_price_adjusted AS a
        JOIN daily_price_raw AS r
          ON r.date = a.date AND r.stock_code = a.stock_code
        JOIN daily_security_status AS s
          ON s.date = a.date AND s.stock_code = a.stock_code
        WHERE a.date >= ? AND a.date <= ?
        ORDER BY a.stock_code, a.date
    """
    with sqlite3.connect(database) as conn:
        return pd.read_sql_query(
            query,
            conn,
            params=(start_date, end_date),
            dtype={"stock_code": "string"},
        )


def load_execution_prices(
    database: Path,
    stock_codes: list[str],
    start_date: str,
    end_date: str,
) -> pd.DataFrame:
    if not stock_codes:
        return pd.DataFrame()
    placeholders = ",".join("?" for _ in stock_codes)
    query = f"""
        SELECT
            r.date,
            r.stock_code,
            r.open_raw,
            r.high_raw,
            r.low_raw,
            r.close_raw,
            r.volume_shares,
            r.amount_cny,
            r.trade_status,
            s.is_st,
            (
                SELECT previous.close_raw
                FROM daily_price_raw AS previous
                WHERE previous.stock_code = r.stock_code
                  AND previous.date < r.date
                ORDER BY previous.date DESC
                LIMIT 1
            ) AS prev_close_raw
        FROM daily_price_raw AS r
        JOIN daily_security_status AS s
          ON s.date = r.date AND s.stock_code = r.stock_code
        WHERE r.date >= ? AND r.date <= ?
          AND r.stock_code IN ({placeholders})
        ORDER BY r.stock_code, r.date
    """
    params = [start_date, end_date, *stock_codes]
    with sqlite3.connect(database) as conn:
        frame = pd.read_sql_query(
            query,
            conn,
            params=params,
            dtype={"stock_code": "string"},
        )
    return frame


def load_security_transition_schedule(
    database: Path,
    stock_codes: list[str],
    start_date: str,
    end_date: str,
) -> pd.DataFrame:
    columns = [
        "date", "source_stock_code", "target_stock_code", "record_date",
        "exchange_ratio", "cash_per_source_share", "event_type",
        "official_fractional_rule", "simulation_fractional_rule",
        "verification_status",
    ]
    if not stock_codes:
        return pd.DataFrame(columns=columns)
    placeholders = ",".join("?" for _ in stock_codes)
    with sqlite3.connect(database) as conn:
        available = {
            row[0]
            for row in conn.execute(
                "SELECT name FROM sqlite_master WHERE type = 'table'"
            )
        }
        if "security_transitions" not in available:
            return pd.DataFrame(columns=columns)
        frame = pd.read_sql_query(
            f"""
            SELECT
                event_date AS date,
                source_stock_code,
                target_stock_code,
                record_date,
                exchange_ratio,
                cash_per_source_share,
                event_type,
                official_fractional_rule,
                simulation_fractional_rule,
                verification_status
            FROM security_transitions
            WHERE event_date BETWEEN ? AND ?
              AND source_stock_code IN ({placeholders})
            ORDER BY event_date, source_stock_code
            """,
            conn,
            params=[start_date, end_date, *stock_codes],
            dtype={"source_stock_code": "string", "target_stock_code": "string"},
        )
    unverified = frame[~frame["verification_status"].eq("official_sse_verified")]
    if not unverified.empty:
        raise RuntimeError(
            "unverified security transitions block formal backtests: "
            f"{unverified[['date', 'source_stock_code']].to_dict('records')[:10]}"
        )
    if frame.duplicated(["date", "source_stock_code"]).any():
        raise ValueError("duplicate cross-security settlement events")
    return frame[columns]


def load_corporate_action_schedule(
    database: Path,
    stock_codes: list[str],
    start_date: str,
    end_date: str,
) -> pd.DataFrame:
    columns = [
        "date", "stock_code", "cash_dividend_gross_per_share",
        "share_multiplier", "method", "validation_status",
    ]
    if not stock_codes:
        return pd.DataFrame(columns=columns)
    placeholders = ",".join("?" for _ in stock_codes)
    with sqlite3.connect(database) as conn:
        available = {
            row[0]
            for row in conn.execute(
                "SELECT name FROM sqlite_master WHERE type IN ('table', 'view')"
            )
        }
        required = {"corporate_action_daily", "adjustment_factor_events"}
        missing = required - available
        if missing:
            raise RuntimeError(
                "corporate-action ledger is missing; run build_corporate_actions.py first: "
                f"{sorted(missing)}"
            )
        mismatch_count = conn.execute(
            "SELECT COUNT(*) FROM adjustment_factor_events "
            "WHERE ex_date BETWEEN ? AND ? AND validation_status = 'mismatch'",
            (start_date, end_date),
        ).fetchone()[0]
        if mismatch_count:
            raise RuntimeError(
                f"corporate-action ledger has {mismatch_count} blocker mismatches in the requested range"
            )
        params = [start_date, end_date, *stock_codes]
        explicit = pd.read_sql_query(
            f"""
            SELECT
                ex_date AS date,
                stock_code,
                cash_dividend_gross_per_share,
                share_multiplier,
                'explicit' AS method,
                COALESCE(
                    (SELECT validation_status FROM adjustment_factor_events AS f
                     WHERE f.stock_code = a.stock_code AND f.ex_date = a.ex_date),
                    'action_only'
                ) AS validation_status
            FROM corporate_action_daily AS a
            WHERE ex_date BETWEEN ? AND ?
              AND stock_code IN ({placeholders})
              AND (cash_dividend_gross_per_share <> 0 OR share_multiplier <> 1)
            """,
            conn,
            params=params,
            dtype={"stock_code": "string"},
        )
        fallback = pd.read_sql_query(
            f"""
            SELECT
                ex_date AS date,
                stock_code,
                0.0 AS cash_dividend_gross_per_share,
                factor_ratio AS share_multiplier,
                'factor_fallback' AS method,
                validation_status
            FROM adjustment_factor_events
            WHERE ex_date BETWEEN ? AND ?
              AND stock_code IN ({placeholders})
              AND validation_status = 'factor_only'
            """,
            conn,
            params=params,
            dtype={"stock_code": "string"},
        )
    populated = [frame for frame in [explicit, fallback] if not frame.empty]
    schedule = pd.concat(populated, ignore_index=True) if populated else pd.DataFrame(columns=columns)
    if schedule.empty:
        return pd.DataFrame(columns=columns)
    if schedule.duplicated(["date", "stock_code"]).any():
        duplicates = schedule.loc[
            schedule.duplicated(["date", "stock_code"], keep=False),
            ["date", "stock_code"],
        ].drop_duplicates().to_dict("records")
        raise ValueError(f"duplicate corporate-action treatments: {duplicates[:10]}")
    return schedule[columns].sort_values(["date", "stock_code"]).reset_index(drop=True)


def fetch_csi300_benchmark(start_date: str, end_date: str, output: Path) -> pd.DataFrame:
    login = bs.login()
    if login.error_code != "0":
        raise RuntimeError(f"Baostock login failed: {login.error_msg}")
    try:
        result = bs.query_history_k_data_plus(
            "sh.000300",
            "date,open,high,low,close,volume,amount",
            start_date=start_date,
            end_date=end_date,
            frequency="d",
            adjustflag="3",
        )
        if result.error_code != "0":
            raise RuntimeError(f"Baostock benchmark query failed: {result.error_msg}")
        rows = []
        while result.next():
            rows.append(result.get_row_data())
    finally:
        bs.logout()
    columns = ["date", "open", "high", "low", "close", "volume", "amount"]
    frame = pd.DataFrame(rows, columns=columns)
    for column in columns[1:]:
        frame[column] = pd.to_numeric(frame[column], errors="coerce")
    if frame.empty or frame["date"].duplicated().any():
        raise ValueError("invalid CSI 300 benchmark response")
    output.parent.mkdir(parents=True, exist_ok=True)
    frame.to_csv(output, index=False, encoding="utf-8-sig")
    return frame


def load_benchmark(
    path: Path,
    start_date: str,
    end_date: str,
    allow_price_index_download: bool = True,
) -> pd.DataFrame:
    if not path.exists():
        if not allow_price_index_download:
            raise FileNotFoundError(
                f"external total-return benchmark file is required: {path}"
            )
        return fetch_csi300_benchmark(start_date, end_date, path)
    frame = pd.read_csv(path, dtype={"date": "string"})
    missing_columns = {"date", "close"} - set(frame.columns)
    if missing_columns:
        raise ValueError(f"benchmark file missing columns: {sorted(missing_columns)}")
    if frame.empty or frame["date"].min() > start_date or frame["date"].max() < end_date:
        if not allow_price_index_download:
            raise ValueError(
                f"external total-return benchmark does not cover {start_date} to {end_date}: {path}"
            )
        return fetch_csi300_benchmark(start_date, end_date, path)
    for column in ["open", "high", "low", "close", "volume", "amount"]:
        if column in frame.columns:
            frame[column] = pd.to_numeric(frame[column], errors="coerce")
    return frame[(frame["date"] >= start_date) & (frame["date"] <= end_date)].copy()
