"""Build and audit a corporate-action ledger for the CSI 300 research database.

CNINFO implementation announcements provide the economic terms of dividends
and bonus/share-transfer events.  The locally frozen Baostock raw and forward-
adjusted prices provide an independent adjustment-factor series.  Keeping both
lets the research engine book explicit actions where possible and identify
events that require a factor-based fallback.
"""
from __future__ import annotations

import argparse
from contextlib import nullcontext
import hashlib
import json
import multiprocessing as mp
import queue
import sqlite3
import time
from datetime import datetime
from pathlib import Path

import akshare as ak
import numpy as np
import pandas as pd


SOURCE = "cninfo_via_akshare"
FALLBACK_SOURCE = "baostock_dividend_fallback"
FACTOR_SOURCE = "derived_from_baostock_raw_and_qfq"

ACTION_COLUMNS = {
    "实施方案公告日期": "announcement_date",
    "分红类型": "action_type",
    "送股比例": "stock_dividend_per_10",
    "转增比例": "transfer_per_10",
    "派息比例": "cash_dividend_gross_per_10",
    "股权登记日": "record_date",
    "除权日": "ex_date",
    "派息日": "payment_date",
    "股份到账日": "shares_credit_date",
    "实施方案分红说明": "description",
    "报告时间": "report_period",
}

SCHEMA = """
CREATE TABLE IF NOT EXISTS corporate_actions (
    stock_code TEXT NOT NULL,
    ex_date TEXT NOT NULL,
    action_sequence INTEGER NOT NULL,
    announcement_date TEXT,
    action_type TEXT,
    record_date TEXT,
    payment_date TEXT,
    shares_credit_date TEXT,
    report_period TEXT,
    cash_dividend_gross_per_10 REAL NOT NULL DEFAULT 0,
    stock_dividend_per_10 REAL NOT NULL DEFAULT 0,
    transfer_per_10 REAL NOT NULL DEFAULT 0,
    description TEXT,
    source TEXT NOT NULL,
    downloaded_at TEXT NOT NULL,
    PRIMARY KEY (stock_code, ex_date, action_sequence)
);
CREATE TABLE IF NOT EXISTS corporate_action_download_status (
    stock_code TEXT PRIMARY KEY,
    status TEXT NOT NULL,
    row_count INTEGER NOT NULL,
    downloaded_at TEXT NOT NULL,
    error_message TEXT
);
CREATE TABLE IF NOT EXISTS adjustment_factor_events (
    stock_code TEXT NOT NULL,
    ex_date TEXT NOT NULL,
    previous_trade_date TEXT NOT NULL,
    previous_fore_adjust_factor REAL NOT NULL,
    fore_adjust_factor REAL NOT NULL,
    factor_ratio REAL NOT NULL,
    previous_close_raw REAL NOT NULL,
    close_raw REAL NOT NULL,
    previous_close_adj REAL NOT NULL,
    close_adj REAL NOT NULL,
    adjusted_return REAL NOT NULL,
    action_count INTEGER NOT NULL,
    cash_dividend_gross_per_share REAL,
    share_multiplier REAL,
    ledger_gross_return REAL,
    return_residual REAL,
    validation_status TEXT NOT NULL,
    source TEXT NOT NULL,
    PRIMARY KEY (stock_code, ex_date)
);
CREATE INDEX IF NOT EXISTS idx_corporate_actions_date
    ON corporate_actions(ex_date, stock_code);
CREATE INDEX IF NOT EXISTS idx_adjustment_events_date
    ON adjustment_factor_events(ex_date, stock_code);
DROP VIEW IF EXISTS corporate_action_daily;
CREATE VIEW corporate_action_daily AS
SELECT
    stock_code,
    ex_date,
    SUM(CASE WHEN action_type LIKE '%重整%' THEN 0 ELSE 1 END) AS action_count,
    SUM(cash_dividend_gross_per_10) / 10.0 AS cash_dividend_gross_per_share,
    1.0 + SUM(
        CASE WHEN action_type LIKE '%重整%' THEN 0
             ELSE stock_dividend_per_10 + transfer_per_10 END
    ) / 10.0 AS share_multiplier,
    GROUP_CONCAT(action_type, ' + ') AS action_types,
    GROUP_CONCAT(description, ' | ') AS descriptions,
    MIN(record_date) AS record_date,
    MAX(payment_date) AS payment_date,
    MAX(shares_credit_date) AS shares_credit_date,
    source
FROM corporate_actions
GROUP BY stock_code, ex_date, source;
"""


def clean_date(series: pd.Series) -> pd.Series:
    return pd.to_datetime(series, errors="coerce").dt.strftime("%Y-%m-%d")


def clean_number(series: pd.Series) -> pd.Series:
    return pd.to_numeric(series, errors="coerce").fillna(0.0)


def normalize_actions(stock_code: str, raw: pd.DataFrame, downloaded_at: str) -> pd.DataFrame:
    columns = [
        "stock_code", "ex_date", "action_sequence", "announcement_date",
        "action_type", "record_date", "payment_date", "shares_credit_date",
        "report_period", "cash_dividend_gross_per_10", "stock_dividend_per_10",
        "transfer_per_10", "description", "source", "downloaded_at",
    ]
    if raw.empty:
        return pd.DataFrame(columns=columns)
    missing = set(ACTION_COLUMNS) - set(raw.columns)
    if missing:
        raise ValueError(f"CNINFO response missing columns: {sorted(missing)}")
    frame = raw.rename(columns=ACTION_COLUMNS)[list(ACTION_COLUMNS.values())].copy()
    for name in ["announcement_date", "record_date", "ex_date", "payment_date", "shares_credit_date"]:
        frame[name] = clean_date(frame[name])
    for name in ["cash_dividend_gross_per_10", "stock_dividend_per_10", "transfer_per_10"]:
        frame[name] = clean_number(frame[name])
    frame = frame[frame["ex_date"].notna()].copy()
    frame["action_type"] = frame["action_type"].fillna("").astype(str).str.strip()
    frame["report_period"] = frame["report_period"].fillna("").astype(str).str.strip()
    frame["description"] = frame["description"].fillna("").astype(str).str.strip()
    frame = frame.drop_duplicates().sort_values(
        ["ex_date", "announcement_date", "action_type", "report_period", "description"],
        na_position="last",
    )
    frame["action_sequence"] = frame.groupby("ex_date").cumcount() + 1
    frame.insert(0, "stock_code", stock_code)
    frame["source"] = SOURCE
    frame["downloaded_at"] = downloaded_at
    return frame[columns]


def ensure_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(SCHEMA)


def stored_success_codes(conn: sqlite3.Connection) -> set[str]:
    return {
        row[0]
        for row in conn.execute(
            "SELECT stock_code FROM corporate_action_download_status "
            "WHERE status IN ('success', 'empty', 'success_fallback', 'empty_fallback')"
        )
    }


def _fetch_actions_worker(stock_code: str, output_queue) -> None:
    try:
        output_queue.put(("ok", ak.stock_dividend_cninfo(symbol=stock_code)))
    except Exception as exc:
        output_queue.put(("error", f"{type(exc).__name__}: {exc}"))


def fetch_actions_once(stock_code: str, timeout_seconds: float) -> pd.DataFrame:
    context = mp.get_context("spawn")
    output_queue = context.Queue(maxsize=1)
    process = context.Process(target=_fetch_actions_worker, args=(stock_code, output_queue))
    process.start()
    try:
        status, payload = output_queue.get(timeout=timeout_seconds)
    except queue.Empty:
        process.terminate()
        process.join(timeout=5)
        if process.is_alive():
            process.kill()
            process.join(timeout=5)
        raise TimeoutError(f"CNINFO request exceeded {timeout_seconds:g} seconds")
    finally:
        output_queue.close()
    process.join(timeout=5)
    if process.is_alive():
        process.terminate()
        process.join(timeout=5)
    if status == "error":
        raise RuntimeError(payload)
    return payload


def fetch_actions(
    stock_code: str,
    retries: int = 3,
    timeout_seconds: float = 20.0,
) -> pd.DataFrame:
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            return fetch_actions_once(stock_code, timeout_seconds)
        except Exception as exc:  # network/provider failures are retried and audited
            last_error = exc
            if attempt + 1 < retries:
                time.sleep(2 ** attempt)
    raise RuntimeError(f"CNINFO download failed after {retries} attempts: {last_error}")


def download_all_actions(
    conn: sqlite3.Connection,
    stock_codes: list[str],
    refresh: bool = False,
    retries: int = 3,
    timeout_seconds: float = 20.0,
) -> dict:
    completed = set() if refresh else stored_success_codes(conn)
    stats = {"requested": len(stock_codes), "skipped_cached": 0, "success": 0, "empty": 0, "failed": 0}
    for index, stock_code in enumerate(stock_codes, start=1):
        if stock_code in completed:
            stats["skipped_cached"] += 1
            continue
        downloaded_at = datetime.now().isoformat(timespec="seconds")
        try:
            frame = normalize_actions(
                stock_code,
                fetch_actions(stock_code, retries, timeout_seconds),
                downloaded_at,
            )
            with conn:
                conn.execute("DELETE FROM corporate_actions WHERE stock_code = ?", (stock_code,))
                if not frame.empty:
                    conn.executemany(
                        "INSERT INTO corporate_actions VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        frame.itertuples(index=False, name=None),
                    )
                status = "empty" if frame.empty else "success"
                conn.execute(
                    "INSERT OR REPLACE INTO corporate_action_download_status VALUES (?, ?, ?, ?, NULL)",
                    (stock_code, status, len(frame), downloaded_at),
                )
            stats[status] += 1
        except Exception as exc:
            with conn:
                conn.execute(
                    "INSERT OR REPLACE INTO corporate_action_download_status VALUES (?, 'failed', 0, ?, ?)",
                    (stock_code, downloaded_at, str(exc)[:1000]),
                )
            stats["failed"] += 1
        if index == 1 or index % 25 == 0 or index == len(stock_codes):
            print(f"CNINFO {index}/{len(stock_codes)}: {stats}", flush=True)
    return stats


def _fetch_baostock_worker(stock_code: str, years: list[str], output_queue) -> None:
    import baostock as bs

    prefixed_code = ("sh." if stock_code.startswith(("5", "6")) else "sz.") + stock_code
    login = bs.login()
    if login.error_code != "0":
        output_queue.put(("error", f"Baostock login failed: {login.error_msg}"))
        return
    fields = None
    rows = []
    try:
        for year in years:
            result = bs.query_dividend_data(prefixed_code, year=year, yearType="operate")
            if result.error_code != "0":
                output_queue.put(("error", f"{year}: {result.error_msg}"))
                return
            fields = result.fields
            while result.next():
                rows.append(result.get_row_data())
    finally:
        bs.logout()
    output_queue.put(("ok", (fields or [], rows)))


def fetch_baostock_actions_once(
    stock_code: str,
    years: list[str],
    timeout_seconds: float,
) -> tuple[list[str], list[list[str]]]:
    context = mp.get_context("spawn")
    output_queue = context.Queue(maxsize=1)
    process = context.Process(
        target=_fetch_baostock_worker,
        args=(stock_code, years, output_queue),
    )
    process.start()
    try:
        status, payload = output_queue.get(timeout=timeout_seconds)
    except queue.Empty:
        process.terminate()
        process.join(timeout=5)
        if process.is_alive():
            process.kill()
            process.join(timeout=5)
        raise TimeoutError(f"Baostock fallback exceeded {timeout_seconds:g} seconds")
    finally:
        output_queue.close()
    process.join(timeout=5)
    if process.is_alive():
        process.terminate()
        process.join(timeout=5)
    if status == "error":
        raise RuntimeError(payload)
    return payload


def normalize_baostock_actions(
    stock_code: str,
    fields: list[str],
    rows: list[list[str]],
    downloaded_at: str,
) -> pd.DataFrame:
    columns = [
        "stock_code", "ex_date", "action_sequence", "announcement_date",
        "action_type", "record_date", "payment_date", "shares_credit_date",
        "report_period", "cash_dividend_gross_per_10", "stock_dividend_per_10",
        "transfer_per_10", "description", "source", "downloaded_at",
    ]
    if not rows:
        return pd.DataFrame(columns=columns)
    raw = pd.DataFrame(rows, columns=fields)
    frame = pd.DataFrame({
        "ex_date": clean_date(raw["dividOperateDate"]),
        "announcement_date": clean_date(raw["dividPlanAnnounceDate"]),
        "action_type": "dividend",
        "record_date": clean_date(raw["dividRegistDate"]),
        "payment_date": clean_date(raw["dividPayDate"]),
        "shares_credit_date": clean_date(raw["dividStockMarketDate"]),
        "report_period": "",
        "cash_dividend_gross_per_10": clean_number(raw["dividCashPsBeforeTax"]) * 10.0,
        "stock_dividend_per_10": clean_number(raw["dividStocksPs"]) * 10.0,
        "transfer_per_10": clean_number(raw["dividReserveToStockPs"]) * 10.0,
        "description": raw["dividCashStock"].fillna("").astype(str).str.strip(),
    })
    frame = frame[frame["ex_date"].notna()].drop_duplicates().sort_values(
        ["ex_date", "announcement_date", "description"], na_position="last"
    )
    frame["action_sequence"] = frame.groupby("ex_date").cumcount() + 1
    frame.insert(0, "stock_code", stock_code)
    frame["source"] = FALLBACK_SOURCE
    frame["downloaded_at"] = downloaded_at
    return frame[columns]


def download_failed_fallbacks(
    conn: sqlite3.Connection,
    timeout_seconds: float = 180.0,
) -> dict:
    failed_codes = [
        row[0]
        for row in conn.execute(
            "SELECT stock_code FROM corporate_action_download_status "
            "WHERE status = 'failed' ORDER BY stock_code"
        )
    ]
    changed = load_price_factors(conn)
    changed["year"] = changed["date"].str[:4]
    years_by_code = {
        str(code): sorted(rows["year"].unique().tolist())
        for code, rows in changed[changed["stock_code"].isin(failed_codes)].groupby("stock_code")
    }
    stats = {"requested": len(failed_codes), "success_fallback": 0, "empty_fallback": 0, "failed": 0}
    for index, stock_code in enumerate(failed_codes, start=1):
        years = years_by_code.get(stock_code, [])
        downloaded_at = datetime.now().isoformat(timespec="seconds")
        if not years:
            with conn:
                conn.execute(
                    "INSERT OR REPLACE INTO corporate_action_download_status VALUES "
                    "(?, 'empty_fallback', 0, ?, NULL)",
                    (stock_code, downloaded_at),
                )
            stats["empty_fallback"] += 1
            continue
        try:
            fields, rows = fetch_baostock_actions_once(stock_code, years, timeout_seconds)
            frame = normalize_baostock_actions(stock_code, fields, rows, downloaded_at)
            status = "empty_fallback" if frame.empty else "success_fallback"
            with conn:
                conn.execute("DELETE FROM corporate_actions WHERE stock_code = ?", (stock_code,))
                if not frame.empty:
                    conn.executemany(
                        "INSERT INTO corporate_actions VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        frame.itertuples(index=False, name=None),
                    )
                conn.execute(
                    "INSERT OR REPLACE INTO corporate_action_download_status VALUES (?, ?, ?, ?, NULL)",
                    (stock_code, status, len(frame), downloaded_at),
                )
            stats[status] += 1
        except Exception as exc:
            with conn:
                conn.execute(
                    "UPDATE corporate_action_download_status SET error_message = ? WHERE stock_code = ?",
                    (f"fallback: {exc}"[:1000], stock_code),
                )
            stats["failed"] += 1
        print(
            f"Baostock fallback {index}/{len(failed_codes)} {stock_code} years={years}: {stats}",
            flush=True,
        )
    return stats


def load_price_factors(conn: sqlite3.Connection) -> pd.DataFrame:
    query = """
        SELECT
            r.date,
            r.stock_code,
            r.close_raw,
            a.close_adj
        FROM daily_price_raw AS r
        JOIN daily_price_adjusted AS a
          ON a.date = r.date AND a.stock_code = r.stock_code
        WHERE r.close_raw > 0 AND a.close_adj > 0
        ORDER BY r.stock_code, r.date
    """
    frame = pd.read_sql_query(query, conn, dtype={"stock_code": "string"})
    frame["fore_adjust_factor"] = frame["close_adj"] / frame["close_raw"]
    grouped = frame.groupby("stock_code", sort=False)
    for column in ["date", "close_raw", "close_adj", "fore_adjust_factor"]:
        frame[f"previous_{column}"] = grouped[column].shift(1)
    changed = ~np.isclose(
        frame["fore_adjust_factor"],
        frame["previous_fore_adjust_factor"],
        rtol=1e-7,
        atol=1e-10,
        equal_nan=True,
    )
    return frame[changed & frame["previous_fore_adjust_factor"].notna()].copy()


def rebuild_factor_events(
    conn: sqlite3.Connection,
    manage_transaction: bool = True,
) -> dict:
    factors = load_price_factors(conn)
    minimum_date, maximum_date = conn.execute(
        "SELECT MIN(date), MAX(date) FROM daily_price_raw"
    ).fetchone()
    actions = pd.read_sql_query(
        "SELECT * FROM corporate_action_daily WHERE ex_date BETWEEN ? AND ? "
        "ORDER BY stock_code, ex_date",
        conn,
        params=(minimum_date, maximum_date),
        dtype={"stock_code": "string"},
    )
    merged = factors.merge(
        actions,
        how="left",
        left_on=["stock_code", "date"],
        right_on=["stock_code", "ex_date"],
    )
    merged["action_count"] = merged["action_count"].fillna(0).astype(int)
    merged["factor_ratio"] = merged["fore_adjust_factor"] / merged["previous_fore_adjust_factor"]
    merged["adjusted_return"] = merged["close_adj"] / merged["previous_close_adj"] - 1.0
    has_action = merged["action_count"] > 0
    merged["ledger_gross_return"] = np.where(
        has_action,
        (
            merged["close_raw"] * merged["share_multiplier"]
            + merged["cash_dividend_gross_per_share"]
        ) / merged["previous_close_raw"] - 1.0,
        np.nan,
    )
    merged["return_residual"] = merged["ledger_gross_return"] - merged["adjusted_return"]
    residual = merged["return_residual"].abs()
    merged["validation_status"] = np.select(
        [~has_action, residual <= 0.002, residual <= 0.01],
        ["factor_only", "matched", "review"],
        default="mismatch",
    )
    rows = []
    for row in merged.itertuples(index=False):
        rows.append((
            str(row.stock_code), str(row.date), str(row.previous_date),
            float(row.previous_fore_adjust_factor), float(row.fore_adjust_factor),
            float(row.factor_ratio), float(row.previous_close_raw), float(row.close_raw),
            float(row.previous_close_adj), float(row.close_adj), float(row.adjusted_return),
            int(row.action_count),
            None if pd.isna(row.cash_dividend_gross_per_share) else float(row.cash_dividend_gross_per_share),
            None if pd.isna(row.share_multiplier) else float(row.share_multiplier),
            None if pd.isna(row.ledger_gross_return) else float(row.ledger_gross_return),
            None if pd.isna(row.return_residual) else float(row.return_residual),
            str(row.validation_status), FACTOR_SOURCE,
        ))
    transaction = conn if manage_transaction else nullcontext()
    with transaction:
        conn.execute("DELETE FROM adjustment_factor_events")
        conn.executemany(
            "INSERT INTO adjustment_factor_events VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            rows,
        )
        conn.execute(
            "DELETE FROM data_quality_issues WHERE table_name IN ('corporate_actions', 'adjustment_factor_events')"
        )
        status_counts = merged["validation_status"].value_counts().to_dict()
        for status in ["factor_only", "review", "mismatch"]:
            count = int(status_counts.get(status, 0))
            if count:
                severity = "warning" if status != "mismatch" else "blocker"
                conn.execute(
                    "INSERT INTO data_quality_issues(table_name, issue_type, severity, details) "
                    "VALUES ('adjustment_factor_events', ?, ?, ?)",
                    (
                        f"corporate_action_{status}", severity,
                        json.dumps({"event_count": count}, ensure_ascii=False),
                    ),
                )
        action_dates = set(zip(actions["stock_code"].astype(str), actions["ex_date"].astype(str)))
        factor_dates = set(zip(merged["stock_code"].astype(str), merged["date"].astype(str)))
        action_without_factor = len(action_dates - factor_dates)
        if action_without_factor:
            conn.execute(
                "INSERT INTO data_quality_issues(table_name, issue_type, severity, details) "
                "VALUES ('corporate_actions', 'action_without_local_factor_change', 'warning', ?)",
                (json.dumps({"event_count": action_without_factor}, ensure_ascii=False),),
            )
        metadata = {
            "corporate_actions_source": SOURCE,
            "corporate_actions_downloaded_at": datetime.now().isoformat(timespec="seconds"),
            "corporate_actions_model": "gross_cash_dividend_and_bonus_transfer_shares",
            "adjustment_factor_source": FACTOR_SOURCE,
            "adjustment_factor_event_count": str(len(merged)),
            "corporate_action_row_count": str(len(pd.read_sql_query('SELECT 1 FROM corporate_actions', conn))),
            "corporate_action_factor_only_count": str(int(status_counts.get('factor_only', 0))),
            "corporate_action_review_count": str(int(status_counts.get('review', 0))),
            "corporate_action_mismatch_count": str(int(status_counts.get('mismatch', 0))),
        }
        conn.executemany("INSERT OR REPLACE INTO metadata(key, value) VALUES (?, ?)", metadata.items())
    return {
        "factor_events": len(merged),
        "status_counts": {str(key): int(value) for key, value in status_counts.items()},
        "action_dates": len(action_dates),
        "action_without_factor": action_without_factor,
    }


def export_tables(conn: sqlite3.Connection, output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    manifest = {
        "created_at": datetime.now().isoformat(timespec="seconds"),
        "files": {},
    }
    for table in [
        "corporate_actions", "corporate_action_download_status",
        "corporate_action_daily", "adjustment_factor_events",
    ]:
        frame = pd.read_sql_query(f"SELECT * FROM {table}", conn)
        path = output_dir / f"{table}.csv"
        frame.to_csv(path, index=False, encoding="utf-8-sig")
        manifest["files"][path.name] = {
            "rows": len(frame),
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        }
    (output_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )


def import_archived_actions(conn: sqlite3.Connection, input_dir: Path) -> dict:
    action_path = input_dir / "corporate_actions.csv"
    status_path = input_dir / "corporate_action_download_status.csv"
    if not action_path.exists() or not status_path.exists():
        raise FileNotFoundError(
            f"archived corporate-action files are incomplete under {input_dir}"
        )
    manifest_path = input_dir / "manifest.json"
    if manifest_path.exists():
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        for path in [action_path, status_path]:
            expected = manifest.get("files", {}).get(path.name, {}).get("sha256")
            actual = hashlib.sha256(path.read_bytes()).hexdigest()
            if not expected or actual != expected:
                raise ValueError(f"archived corporate-action checksum mismatch: {path.name}")
    actions = pd.read_csv(action_path, dtype={"stock_code": "string"})
    statuses = pd.read_csv(status_path, dtype={"stock_code": "string"})
    expected_actions = [row[1] for row in conn.execute("PRAGMA table_info(corporate_actions)")]
    expected_statuses = [
        row[1] for row in conn.execute("PRAGMA table_info(corporate_action_download_status)")
    ]
    if list(actions.columns) != expected_actions:
        raise ValueError("archived corporate_actions.csv schema does not match SQLite")
    if list(statuses.columns) != expected_statuses:
        raise ValueError(
            "archived corporate_action_download_status.csv schema does not match SQLite"
        )
    actions = actions.astype(object).where(pd.notna(actions), None)
    statuses = statuses.astype(object).where(pd.notna(statuses), None)
    with conn:
        conn.execute("DELETE FROM corporate_actions")
        conn.execute("DELETE FROM corporate_action_download_status")
        conn.executemany(
            "INSERT INTO corporate_actions VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            actions.itertuples(index=False, name=None),
        )
        conn.executemany(
            "INSERT INTO corporate_action_download_status VALUES (?, ?, ?, ?, ?)",
            statuses.itertuples(index=False, name=None),
        )
    return {"actions": len(actions), "statuses": len(statuses)}


def main() -> None:
    parser = argparse.ArgumentParser(description="Build the corporate-action ledger")
    parser.add_argument("--database", type=Path, default=Path("data_v5/csi300_2020_present.sqlite"))
    parser.add_argument("--refresh", action="store_true", help="redownload codes already completed")
    parser.add_argument("--skip-download", action="store_true", help="only rebuild factors and audit")
    parser.add_argument(
        "--import-dir", type=Path, default=None,
        help="restore archived action and download-status CSV files without network access",
    )
    parser.add_argument("--limit", type=int, default=None, help="download only the first N codes")
    parser.add_argument("--retries", type=int, default=3)
    parser.add_argument("--request-timeout", type=float, default=20.0)
    parser.add_argument("--fallback-timeout", type=float, default=180.0)
    parser.add_argument("--export-dir", type=Path, default=Path("data_v5/corporate_actions"))
    args = parser.parse_args()
    with sqlite3.connect(args.database) as conn:
        ensure_schema(conn)
        codes = [row[0] for row in conn.execute("SELECT stock_code FROM security ORDER BY stock_code")]
        if args.limit is not None:
            codes = codes[:args.limit]
        download_stats = None
        fallback_stats = None
        import_stats = None
        if args.import_dir is not None:
            import_stats = import_archived_actions(conn, args.import_dir)
        elif not args.skip_download:
            download_stats = download_all_actions(
                conn, codes, args.refresh, args.retries, args.request_timeout
            )
            fallback_stats = download_failed_fallbacks(conn, args.fallback_timeout)
        audit = rebuild_factor_events(conn)
        export_tables(conn, args.export_dir)
    result = {
        "database": str(args.database),
        "download": download_stats,
        "fallback": fallback_stats,
        "import": import_stats,
        "audit": audit,
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
