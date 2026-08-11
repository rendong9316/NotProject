"""Incrementally update the CSI 300 research database with auditable archives."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import sqlite3
import tempfile
import time
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from pathlib import Path

import baostock as bs
import pandas as pd

from apply_official_membership import (
    apply_update as apply_official_update,
    load_adjustments,
    load_raw_snapshots,
    match_official_snapshots,
)
from build_corporate_actions import (
    ensure_schema as ensure_corporate_schema,
    fetch_actions,
    normalize_actions,
    rebuild_factor_events,
)


INDEX_CODE = "000300"
INDEX_BAOSTOCK_CODE = "sh.000300"
PRICE_START_DATE = "2020-01-01"
OVERLAP_TRADING_DAYS = 10
QFQ_RATIO_TOLERANCE = 1e-5
RAW_FIELDS = "date,open,high,low,close,volume,amount,tradestatus,isST"
ADJUSTED_FIELDS = "date,open,high,low,close"
BENCHMARK_FIELDS = "date,open,high,low,close,volume,amount"


AUDIT_SCHEMA = """
CREATE TABLE IF NOT EXISTS baostock_membership_snapshots_raw (
    index_code TEXT NOT NULL,
    observed_date TEXT NOT NULL,
    stock_code TEXT NOT NULL,
    weight REAL,
    announcement_date TEXT,
    source TEXT NOT NULL,
    source_url TEXT,
    archived_at TEXT NOT NULL,
    PRIMARY KEY (index_code, observed_date, stock_code)
);
CREATE TABLE IF NOT EXISTS update_runs (
    run_id TEXT PRIMARY KEY,
    started_at TEXT NOT NULL,
    completed_at TEXT NOT NULL,
    previous_max_date TEXT,
    requested_end_date TEXT NOT NULL,
    new_max_date TEXT,
    status TEXT NOT NULL,
    official_excel_sha256 TEXT NOT NULL,
    archive_path TEXT NOT NULL,
    details_json TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS price_revision_audit (
    revision_id INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id TEXT NOT NULL,
    stock_code TEXT NOT NULL,
    revision_type TEXT NOT NULL,
    first_date TEXT,
    last_date TEXT,
    changed_rows INTEGER NOT NULL,
    adjustment_scale REAL,
    details_json TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS membership_observation_runs (
    query_date TEXT NOT NULL,
    source_update_date TEXT NOT NULL,
    set_sha256 TEXT NOT NULL,
    run_id TEXT NOT NULL,
    observed_at TEXT NOT NULL,
    PRIMARY KEY (query_date, source_update_date)
);
CREATE TABLE IF NOT EXISTS security_update_state (
    stock_code TEXT PRIMARY KEY,
    last_attempt_at TEXT NOT NULL,
    last_success_at TEXT,
    latest_price_date TEXT,
    status TEXT NOT NULL,
    details TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_price_revision_run
    ON price_revision_audit(run_id, stock_code);
"""


@dataclass
class Stage:
    run_id: str
    started_at: str
    previous_max_date: str
    requested_end_date: str
    raw: pd.DataFrame
    adjusted: pd.DataFrame
    status: pd.DataFrame
    observations: pd.DataFrame
    benchmark: pd.DataFrame
    actions: pd.DataFrame
    action_errors: dict[str, str]
    attempted_codes: list[str]
    archive_dir: Path


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def sha256_codes(codes: list[str]) -> str:
    return hashlib.sha256("\n".join(sorted(codes)).encode("ascii")).hexdigest()


def exchange_code(stock_code: str) -> str:
    prefix = "sh." if stock_code.startswith(("5", "6")) else "sz."
    return prefix + stock_code


def exchange_name(stock_code: str) -> str:
    return "SH" if stock_code.startswith(("5", "6")) else "SZ"


def query_history(
    code: str,
    fields: str,
    start_date: str,
    end_date: str,
    adjustflag: str,
    retries: int = 3,
) -> pd.DataFrame:
    last_error = "unknown error"
    for attempt in range(retries):
        result = bs.query_history_k_data_plus(
            code,
            fields,
            start_date=start_date,
            end_date=end_date,
            frequency="d",
            adjustflag=adjustflag,
        )
        if result.error_code == "0":
            frame = result.get_data()
            return frame if frame is not None else pd.DataFrame()
        last_error = result.error_msg
        if attempt + 1 < retries:
            time.sleep(2 ** attempt)
    raise RuntimeError(f"{code}: Baostock query failed: {last_error}")


def normalize_raw(stock_code: str, frame: pd.DataFrame, downloaded_at: str) -> tuple[pd.DataFrame, pd.DataFrame]:
    raw_columns = [
        "date", "stock_code", "open_raw", "high_raw", "low_raw", "close_raw",
        "volume_shares", "amount_cny", "trade_status", "price_source",
    ]
    status_columns = [
        "date", "stock_code", "is_st", "trade_status", "source", "downloaded_at",
    ]
    if frame.empty:
        return pd.DataFrame(columns=raw_columns), pd.DataFrame(columns=status_columns)
    required = {"date", "open", "high", "low", "close", "volume", "amount", "tradestatus", "isST"}
    missing = required - set(frame.columns)
    if missing:
        raise ValueError(f"{stock_code}: raw response missing columns: {sorted(missing)}")
    values = frame.copy()
    for column in ["open", "high", "low", "close", "volume", "amount"]:
        values[column] = pd.to_numeric(values[column], errors="coerce")
    values["trade_status"] = pd.to_numeric(values["tradestatus"], errors="raise").astype(int)
    values["is_st"] = pd.to_numeric(values["isST"], errors="raise").astype(int)
    values["date"] = pd.to_datetime(values["date"], errors="raise").dt.strftime("%Y-%m-%d")
    if values["date"].duplicated().any() or not values["trade_status"].isin([0, 1]).all():
        raise ValueError(f"{stock_code}: invalid raw dates or trade status")
    traded = values["trade_status"].eq(1)
    prices = values[["open", "high", "low", "close"]]
    invalid_ohlc = (
        (values["high"] < values["low"])
        | (values["high"] < values["open"])
        | (values["high"] < values["close"])
        | (values["low"] > values["open"])
        | (values["low"] > values["close"])
    )
    if invalid_ohlc.any() or (traded & (prices.isna().any(axis=1) | prices.le(0).any(axis=1))).any():
        raise ValueError(f"{stock_code}: invalid raw OHLC response")
    raw = pd.DataFrame({
        "date": values["date"],
        "stock_code": stock_code,
        "open_raw": values["open"],
        "high_raw": values["high"],
        "low_raw": values["low"],
        "close_raw": values["close"],
        "volume_shares": values["volume"].round().astype("Int64"),
        "amount_cny": values["amount"],
        "trade_status": values["trade_status"],
        "price_source": "baostock_incremental",
    })
    status = pd.DataFrame({
        "date": values["date"],
        "stock_code": stock_code,
        "is_st": values["is_st"],
        "trade_status": values["trade_status"],
        "source": "baostock",
        "downloaded_at": downloaded_at,
    })
    return raw[raw_columns], status[status_columns]


def normalize_adjusted(stock_code: str, frame: pd.DataFrame) -> pd.DataFrame:
    columns = [
        "date", "stock_code", "open_adj", "high_adj", "low_adj", "close_adj",
        "adjustment_source",
    ]
    if frame.empty:
        return pd.DataFrame(columns=columns)
    required = {"date", "open", "high", "low", "close"}
    missing = required - set(frame.columns)
    if missing:
        raise ValueError(f"{stock_code}: adjusted response missing columns: {sorted(missing)}")
    values = frame.copy()
    values["date"] = pd.to_datetime(values["date"], errors="raise").dt.strftime("%Y-%m-%d")
    for column in ["open", "high", "low", "close"]:
        values[column] = pd.to_numeric(values[column], errors="coerce")
    prices = values[["open", "high", "low", "close"]]
    if values["date"].duplicated().any() or prices.isna().any().any() or prices.le(0).any().any():
        raise ValueError(f"{stock_code}: invalid adjusted response")
    output = pd.DataFrame({
        "date": values["date"],
        "stock_code": stock_code,
        "open_adj": values["open"],
        "high_adj": values["high"],
        "low_adj": values["low"],
        "close_adj": values["close"],
        "adjustment_source": "baostock_qfq_incremental",
    })
    return output[columns]


def query_membership(query_date: str) -> tuple[str, pd.DataFrame]:
    result = bs.query_hs300_stocks(query_date)
    if result.error_code != "0":
        raise RuntimeError(f"membership {query_date}: {result.error_msg}")
    frame = result.get_data()
    if frame is None or len(frame) != 300:
        raise ValueError(f"membership {query_date}: expected 300 rows")
    update_dates = frame["updateDate"].dropna().astype(str).unique()
    if len(update_dates) != 1:
        raise ValueError(f"membership {query_date}: ambiguous source dates")
    output = pd.DataFrame({
        "query_date": query_date,
        "source_update_date": str(update_dates[0]),
        "stock_code": frame["code"].astype(str).str.split(".").str[-1].str.zfill(6),
        "stock_name": frame["code_name"].astype(str),
        "source": "baostock_history_snapshot_observation",
    }).sort_values("stock_code")
    if output["stock_code"].nunique() != 300:
        raise ValueError(f"membership {query_date}: duplicate component codes")
    return str(update_dates[0]), output.reset_index(drop=True)


def concat_frames(frames: list[pd.DataFrame], columns: list[str]) -> pd.DataFrame:
    populated = [frame for frame in frames if not frame.empty]
    if not populated:
        return pd.DataFrame(columns=columns)
    return pd.concat(populated, ignore_index=True)[columns]


def read_database_state(database: Path) -> dict:
    with sqlite3.connect(database) as conn:
        integrity = conn.execute("PRAGMA quick_check").fetchone()[0]
        if integrity != "ok":
            raise RuntimeError(f"database quick_check failed: {integrity}")
        maximum = conn.execute("SELECT MAX(date) FROM trading_calendar").fetchone()[0]
        codes = [row[0] for row in conn.execute("SELECT stock_code FROM security ORDER BY stock_code")]
        names = dict(conn.execute("SELECT stock_code, name FROM security"))
        formal = {
            row[0]
            for row in conn.execute(
                "SELECT stock_code FROM universe_membership "
                "WHERE index_code = ? AND valid_from <= ? AND ? < valid_to",
                (INDEX_CODE, maximum, maximum),
            )
        }
        existing_runs = {
            row[0]
            for row in conn.execute(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='update_runs'"
            )
        }
    return {"maximum": maximum, "codes": codes, "names": names, "formal": formal, "audit": bool(existing_runs)}


def query_benchmark(start_date: str, end_date: str) -> pd.DataFrame:
    frame = query_history(
        INDEX_BAOSTOCK_CODE, BENCHMARK_FIELDS, start_date, end_date, "3"
    )
    columns = ["date", "open", "high", "low", "close", "volume", "amount"]
    if frame.empty:
        return pd.DataFrame(columns=columns)
    frame = frame[columns].copy()
    frame["date"] = pd.to_datetime(frame["date"], errors="raise").dt.strftime("%Y-%m-%d")
    for column in columns[1:]:
        frame[column] = pd.to_numeric(frame[column], errors="raise")
    if frame["date"].duplicated().any() or frame["close"].le(0).any():
        raise ValueError("invalid benchmark response")
    return frame


def select_start_date(conn: sqlite3.Connection, stock_code: str, global_overlap: str, active: bool) -> str:
    first_date, last_date = conn.execute(
        "SELECT MIN(date), MAX(date) FROM daily_price_raw WHERE stock_code = ?",
        (stock_code,),
    ).fetchone()
    if first_date is None:
        return PRICE_START_DATE
    if active and last_date < global_overlap:
        return last_date
    return global_overlap


def fetch_stage(
    database: Path,
    excel: Path,
    end_date: str | None,
    archive_root: Path,
) -> Stage:
    state = read_database_state(database)
    started_at = datetime.now().isoformat(timespec="seconds")
    run_id = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
    candidate_end = end_date or date.today().isoformat()
    login = bs.login()
    if login.error_code != "0":
        raise RuntimeError(f"Baostock login failed: {login.error_msg}")
    try:
        print(
            f"Increment run {run_id}: database={state['maximum']} candidate={candidate_end}",
            flush=True,
        )
        benchmark = query_benchmark(state["maximum"], candidate_end)
        new_benchmark = benchmark[benchmark["date"] > state["maximum"]].copy()
        requested_end = (
            str(new_benchmark["date"].max()) if not new_benchmark.empty else state["maximum"]
        )
        new_dates = sorted(new_benchmark["date"].astype(str).unique())
        print(
            f"Benchmark: new trading days={len(new_dates)} target={requested_end}",
            flush=True,
        )
        observation_frames = []
        observed_codes: set[str] = set()
        observed_names: dict[str, str] = {}
        for query_date in new_dates or [requested_end]:
            _, observation = query_membership(query_date)
            observation_frames.append(observation)
            observed_codes.update(observation["stock_code"].astype(str))
            observed_names.update(zip(observation["stock_code"], observation["stock_name"]))
        print(
            f"Membership observations: queries={len(new_dates or [requested_end])} "
            f"unique_codes={len(observed_codes)}",
            flush=True,
        )

        adjustments = load_adjustments(excel)
        future_incoming = set(
            adjustments.loc[adjustments["event_date"] >= state["maximum"], "in_code"].astype(str)
        )
        target_codes = (
            sorted(set(state["codes"]) | observed_codes | future_incoming)
            if new_dates
            else []
        )
        print(f"Price targets: {len(target_codes)} securities", flush=True)
        with sqlite3.connect(database) as conn:
            calendar = [
                row[0]
                for row in conn.execute(
                    "SELECT date FROM trading_calendar WHERE date <= ? ORDER BY date",
                    (state["maximum"],),
                )
            ]
            overlap = calendar[max(0, len(calendar) - OVERLAP_TRADING_DAYS)]

        raw_frames: list[pd.DataFrame] = []
        adjusted_frames: list[pd.DataFrame] = []
        status_frames: list[pd.DataFrame] = []
        affected_actions: set[str] = set(future_incoming - set(state["codes"]))
        with sqlite3.connect(database) as conn:
            for index, code in enumerate(target_codes, start=1):
                active = code in state["formal"] or code in observed_codes
                start = select_start_date(conn, code, overlap, active)
                raw_source = query_history(exchange_code(code), RAW_FIELDS, start, requested_end, "3")
                adjusted_source = query_history(
                    exchange_code(code), ADJUSTED_FIELDS, start, requested_end, "2"
                )
                raw, status = normalize_raw(code, raw_source, started_at)
                adjusted = normalize_adjusted(code, adjusted_source)
                if set(raw["date"]) != set(adjusted["date"]):
                    raise ValueError(f"{code}: raw and adjusted response dates differ")
                raw_frames.append(raw)
                adjusted_frames.append(adjusted)
                status_frames.append(status)
                if not adjusted.empty:
                    old = pd.read_sql_query(
                        "SELECT date, close_adj FROM daily_price_adjusted "
                        "WHERE stock_code = ? AND date BETWEEN ? AND ?",
                        conn,
                        params=(code, str(adjusted["date"].min()), str(adjusted["date"].max())),
                    )
                    common = old.merge(adjusted[["date", "close_adj"]], on="date", suffixes=("_old", "_new"))
                    if not common.empty:
                        ratios = common["close_adj_new"] / common["close_adj_old"]
                        scale = float(ratios.median())
                        residual = (ratios / scale - 1.0).abs().max()
                        if residual > QFQ_RATIO_TOLERANCE:
                            full_source = query_history(
                                exchange_code(code), ADJUSTED_FIELDS,
                                PRICE_START_DATE, requested_end, "2",
                            )
                            adjusted = normalize_adjusted(code, full_source)
                            adjusted_frames[-1] = adjusted
                            affected_actions.add(code)
                        elif not math.isclose(scale, 1.0, rel_tol=1e-7):
                            affected_actions.add(code)
                if index == 1 or index % 50 == 0 or index == len(target_codes):
                    print(f"Prices {index}/{len(target_codes)}", flush=True)

        action_frames = []
        action_errors: dict[str, str] = {}
        for code in sorted(affected_actions):
            try:
                action_frames.append(
                    normalize_actions(code, fetch_actions(code), started_at)
                )
            except Exception as exc:
                action_errors[code] = f"{type(exc).__name__}: {exc}"
        print(
            f"Corporate-action refresh: requested={len(affected_actions)} "
            f"errors={len(action_errors)}",
            flush=True,
        )
        action_columns = [
            "stock_code", "ex_date", "action_sequence", "announcement_date",
            "action_type", "record_date", "payment_date", "shares_credit_date",
            "report_period", "cash_dividend_gross_per_10", "stock_dividend_per_10",
            "transfer_per_10", "description", "source", "downloaded_at",
        ]
        actions = concat_frames(action_frames, action_columns)
    finally:
        bs.logout()

    archive_dir = archive_root / run_id
    observations = concat_frames(
        observation_frames,
        ["query_date", "source_update_date", "stock_code", "stock_name", "source"],
    )
    return Stage(
        run_id=run_id,
        started_at=started_at,
        previous_max_date=state["maximum"],
        requested_end_date=requested_end,
        raw=concat_frames(raw_frames, [
            "date", "stock_code", "open_raw", "high_raw", "low_raw", "close_raw",
            "volume_shares", "amount_cny", "trade_status", "price_source",
        ]),
        adjusted=concat_frames(adjusted_frames, [
            "date", "stock_code", "open_adj", "high_adj", "low_adj", "close_adj",
            "adjustment_source",
        ]),
        status=concat_frames(status_frames, [
            "date", "stock_code", "is_st", "trade_status", "source", "downloaded_at",
        ]),
        observations=observations,
        benchmark=benchmark,
        actions=actions,
        action_errors=action_errors,
        attempted_codes=target_codes,
        archive_dir=archive_dir,
    )


def write_archive(stage: Stage, excel: Path, status: str = "staged", error: str | None = None) -> dict:
    stage.archive_dir.mkdir(parents=True, exist_ok=True)
    files = {
        "prices_raw.csv": stage.raw,
        "prices_adjusted.csv": stage.adjusted,
        "security_status.csv": stage.status,
        "membership_observations.csv": stage.observations,
        "benchmark.csv": stage.benchmark,
        "corporate_actions.csv": stage.actions,
        "security_targets.csv": pd.DataFrame({"stock_code": stage.attempted_codes}),
    }
    file_manifest = {}
    for name, frame in files.items():
        path = stage.archive_dir / name
        frame.to_csv(path, index=False, encoding="utf-8-sig")
        file_manifest[name] = {"rows": len(frame), "sha256": sha256_file(path)}
    manifest = {
        "run_id": stage.run_id,
        "created_at": stage.started_at,
        "previous_max_date": stage.previous_max_date,
        "requested_end_date": stage.requested_end_date,
        "status": status,
        "official_excel": str(excel),
        "official_excel_sha256": sha256_file(excel),
        "action_errors": stage.action_errors,
        "error": error,
        "files": file_manifest,
    }
    (stage.archive_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return manifest


def verify_archive(path: Path) -> dict:
    manifest_path = path / "manifest.json"
    if not manifest_path.exists():
        raise FileNotFoundError(f"increment manifest missing: {manifest_path}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for name, item in manifest.get("files", {}).items():
        file_path = path / name
        if not file_path.exists() or sha256_file(file_path) != item.get("sha256"):
            raise ValueError(f"increment archive checksum mismatch: {file_path}")
        if len(pd.read_csv(file_path)) != int(item.get("rows", -1)):
            raise ValueError(f"increment archive row-count mismatch: {file_path}")
    return manifest


def table_frame(path: Path, name: str, dtypes: dict | None = None) -> pd.DataFrame:
    file_path = path / name
    return pd.read_csv(file_path, dtype=dtypes or {})


def stage_from_archive(path: Path) -> Stage:
    manifest = verify_archive(path)
    targets_path = path / "security_targets.csv"
    attempted_codes = (
        pd.read_csv(targets_path, dtype={"stock_code": "string"})["stock_code"].astype(str).tolist()
        if targets_path.exists()
        else []
    )
    return Stage(
        run_id=manifest["run_id"],
        started_at=manifest["created_at"],
        previous_max_date=manifest["previous_max_date"],
        requested_end_date=manifest["requested_end_date"],
        raw=table_frame(path, "prices_raw.csv", {"stock_code": "string"}),
        adjusted=table_frame(path, "prices_adjusted.csv", {"stock_code": "string"}),
        status=table_frame(path, "security_status.csv", {"stock_code": "string"}),
        observations=table_frame(path, "membership_observations.csv", {"stock_code": "string"}),
        benchmark=table_frame(path, "benchmark.csv"),
        actions=table_frame(path, "corporate_actions.csv", {"stock_code": "string"}),
        action_errors=manifest.get("action_errors", {}),
        attempted_codes=attempted_codes,
        archive_dir=path,
    )


def upsert_rows(conn: sqlite3.Connection, table: str, columns: list[str], frame: pd.DataFrame) -> None:
    if frame.empty:
        return
    placeholders = ",".join("?" for _ in columns)
    updates = ",".join(f"{column}=excluded.{column}" for column in columns[2:])
    query = (
        f"INSERT INTO {table} ({','.join(columns)}) VALUES ({placeholders}) "
        f"ON CONFLICT({columns[0]},{columns[1]}) DO UPDATE SET {updates}"
    )
    values = frame[columns].astype(object).where(pd.notna(frame[columns]), None)
    conn.executemany(query, values.itertuples(index=False, name=None))


def current_factor_audit(conn: sqlite3.Connection) -> dict:
    counts = {
        str(status): int(count)
        for status, count in conn.execute(
            "SELECT validation_status, COUNT(*) FROM adjustment_factor_events "
            "GROUP BY validation_status"
        )
    }
    return {
        "factor_events": int(
            conn.execute("SELECT COUNT(*) FROM adjustment_factor_events").fetchone()[0]
        ),
        "status_counts": counts,
        "action_dates": int(
            conn.execute("SELECT COUNT(*) FROM corporate_action_daily").fetchone()[0]
        ),
        "action_without_factor": None,
        "reused_existing_audit": True,
    }


def adjustment_scale(conn: sqlite3.Connection, code: str, incoming: pd.DataFrame) -> tuple[float, str]:
    if incoming.empty:
        return 1.0, "no_data"
    old = pd.read_sql_query(
        "SELECT date, close_adj FROM daily_price_adjusted WHERE stock_code = ? "
        "AND date BETWEEN ? AND ?",
        conn,
        params=(code, str(incoming["date"].min()), str(incoming["date"].max())),
    )
    if old.empty:
        exists = conn.execute(
            "SELECT 1 FROM daily_price_adjusted WHERE stock_code = ? LIMIT 1", (code,)
        ).fetchone()
        return (1.0, "new_security" if not exists else "append_without_overlap")
    common = old.merge(incoming[["date", "close_adj"]], on="date", suffixes=("_old", "_new"))
    if common.empty:
        return 1.0, "append_without_overlap"
    ratios = common["close_adj_new"] / common["close_adj_old"]
    scale = float(ratios.median())
    residual = float((ratios / scale - 1.0).abs().max())
    stored_bounds = conn.execute(
        "SELECT MIN(date), MAX(date) FROM daily_price_adjusted WHERE stock_code = ?",
        (code,),
    ).fetchone()
    covers_history = (
        stored_bounds[0] is not None
        and str(incoming["date"].min()) <= stored_bounds[0]
        and str(incoming["date"].max()) >= stored_bounds[1]
    )
    if residual > QFQ_RATIO_TOLERANCE and covers_history:
        return 1.0, "full_reload"
    if not math.isfinite(scale) or scale <= 0 or residual > QFQ_RATIO_TOLERANCE:
        raise ValueError(
            f"{code}: non-constant qfq revision requires a full adjusted-history stage; "
            f"residual={residual:.8g}"
        )
    return scale, "scale_and_upsert" if not math.isclose(scale, 1.0, rel_tol=1e-7) else "upsert"


def apply_stage(database: Path, stage: Stage, excel: Path) -> dict:
    completed_at = datetime.now().isoformat(timespec="seconds")
    revisions = []
    with sqlite3.connect(database, timeout=30) as conn:
        conn.execute("PRAGMA foreign_keys = ON")
        conn.executescript(AUDIT_SCHEMA)
        ensure_corporate_schema(conn)
        if conn.execute("SELECT 1 FROM update_runs WHERE run_id = ?", (stage.run_id,)).fetchone():
            return {"run_id": stage.run_id, "status": "already_applied"}
        current_max = conn.execute("SELECT MAX(date) FROM trading_calendar").fetchone()[0]
        if current_max != stage.previous_max_date:
            raise RuntimeError(
                f"database changed after staging: expected {stage.previous_max_date}, got {current_max}"
            )
        conn.execute("BEGIN IMMEDIATE")
        try:
            observation_names = dict(
                zip(stage.observations["stock_code"].astype(str), stage.observations["stock_name"].astype(str))
            ) if not stage.observations.empty else {}
            all_codes = sorted(set(stage.raw["stock_code"].astype(str)) | set(observation_names))
            for code in all_codes:
                rows = stage.raw[stage.raw["stock_code"].astype(str).eq(code)]
                first = None if rows.empty else str(rows["date"].min())
                last = None if rows.empty else str(rows["date"].max())
                existing = conn.execute(
                    "SELECT name, first_seen, last_seen FROM security WHERE stock_code = ?", (code,)
                ).fetchone()
                name = observation_names.get(code, existing[0] if existing else code)
                first_seen = min(filter(None, [first, existing[1] if existing else None]), default=None)
                last_seen = max(filter(None, [last, existing[2] if existing else None]), default=None)
                conn.execute(
                    "INSERT INTO security VALUES (?, ?, ?, ?, ?) "
                    "ON CONFLICT(stock_code) DO UPDATE SET name=excluded.name, "
                    "first_seen=excluded.first_seen, last_seen=excluded.last_seen",
                    (code, name, exchange_name(code), first_seen, last_seen),
                )

            for code, incoming in stage.adjusted.groupby("stock_code", sort=True):
                code = str(code)
                scale, mode = adjustment_scale(conn, code, incoming)
                earliest = str(incoming["date"].min()) if not incoming.empty else None
                if mode == "scale_and_upsert":
                    cursor = conn.execute(
                        "UPDATE daily_price_adjusted SET open_adj=open_adj*?, high_adj=high_adj*?, "
                        "low_adj=low_adj*?, close_adj=close_adj*?, adjustment_source=? "
                        "WHERE stock_code=? AND date < ?",
                        (scale, scale, scale, scale, "baostock_qfq_rescaled", code, earliest),
                    )
                    changed = cursor.rowcount
                elif mode == "full_reload":
                    changed = conn.execute(
                        "DELETE FROM daily_price_adjusted WHERE stock_code = ?", (code,)
                    ).rowcount
                else:
                    changed = 0
                revisions.append({
                    "stock_code": code,
                    "revision_type": mode,
                    "first_date": earliest,
                    "last_date": str(incoming["date"].max()) if not incoming.empty else None,
                    "changed_rows": changed,
                    "adjustment_scale": scale,
                })

            upsert_rows(conn, "daily_price_raw", list(stage.raw.columns), stage.raw)
            upsert_rows(conn, "daily_price_adjusted", list(stage.adjusted.columns), stage.adjusted)
            upsert_rows(conn, "daily_security_status", list(stage.status.columns), stage.status)
            for trading_day in sorted(stage.benchmark["date"].astype(str).unique()):
                conn.execute(
                    "INSERT OR IGNORE INTO trading_calendar(date, is_trading_day) VALUES (?, 1)",
                    (trading_day,),
                )

            observed_at = completed_at
            for (query_date, source_date), rows in stage.observations.groupby(
                ["query_date", "source_update_date"], sort=True
            ):
                codes = sorted(rows["stock_code"].astype(str))
                set_hash = sha256_codes(codes)
                existing = conn.execute(
                    "SELECT stock_code FROM baostock_membership_snapshots_raw "
                    "WHERE index_code=? AND observed_date=? ORDER BY stock_code",
                    (INDEX_CODE, str(source_date)),
                ).fetchall()
                if existing and [row[0] for row in existing] != codes:
                    raise ValueError(f"membership source date changed: {source_date}")
                if not existing:
                    conn.executemany(
                        "INSERT INTO baostock_membership_snapshots_raw VALUES "
                        "(?, ?, ?, NULL, NULL, ?, ?, ?)",
                        [
                            (
                                INDEX_CODE, str(source_date), str(row.stock_code),
                                str(row.source), "http://baostock.com", observed_at,
                            )
                            for row in rows.itertuples(index=False)
                        ],
                    )
                conn.execute(
                    "INSERT OR REPLACE INTO membership_observation_runs VALUES (?, ?, ?, ?, ?)",
                    (str(query_date), str(source_date), set_hash, stage.run_id, observed_at),
                )

            if not stage.actions.empty:
                for code, rows in stage.actions.groupby("stock_code", sort=True):
                    conn.execute("DELETE FROM corporate_actions WHERE stock_code = ?", (str(code),))
                    values = rows.astype(object).where(pd.notna(rows), None)
                    conn.executemany(
                        "INSERT INTO corporate_actions VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        values.itertuples(index=False, name=None),
                    )
                    conn.execute(
                        "INSERT OR REPLACE INTO corporate_action_download_status "
                        "(stock_code, status, row_count, downloaded_at, error_message) "
                        "VALUES (?, ?, ?, ?, ?)",
                        (str(code), "success" if len(rows) else "empty", len(rows), completed_at, None),
                    )

            prices_or_actions_changed = (
                not stage.raw.empty
                or not stage.adjusted.empty
                or not stage.actions.empty
            )
            audit = (
                rebuild_factor_events(conn, manage_transaction=False)
                if prices_or_actions_changed
                else current_factor_audit(conn)
            )
            if int(audit["status_counts"].get("mismatch", 0)):
                raise RuntimeError(f"corporate-action mismatch after increment: {audit}")

            conn.execute(
                "DELETE FROM data_quality_issues WHERE issue_type = 'member_price_missing'"
            )
            missing_members = conn.execute(
                "SELECT date, stock_code FROM daily_universe "
                "WHERE close_raw IS NULL OR close_adj IS NULL"
            ).fetchall()
            conn.executemany(
                "INSERT INTO data_quality_issues "
                "(table_name, issue_date, stock_code, issue_type, severity, details) "
                "VALUES ('daily_universe', ?, ?, 'member_price_missing', 'warning', ?)",
                [
                    (missing_date, code, "Member retained with NULL price and is_tradeable=0")
                    for missing_date, code in missing_members
                ],
            )

            for revision in revisions:
                conn.execute(
                    "INSERT INTO price_revision_audit "
                    "(run_id, stock_code, revision_type, first_date, last_date, changed_rows, "
                    "adjustment_scale, details_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    (
                        stage.run_id, revision["stock_code"], revision["revision_type"],
                        revision["first_date"], revision["last_date"], revision["changed_rows"],
                        revision["adjustment_scale"], json.dumps({}, ensure_ascii=False),
                    ),
                )
            state_codes = stage.attempted_codes
            for code in state_codes:
                latest = conn.execute(
                    "SELECT MAX(date) FROM daily_price_raw WHERE stock_code = ?", (code,)
                ).fetchone()[0]
                fetched = not stage.raw[stage.raw["stock_code"].astype(str).eq(code)].empty
                conn.execute(
                    "INSERT OR REPLACE INTO security_update_state VALUES (?, ?, ?, ?, ?, ?)",
                    (
                        code, completed_at, completed_at if fetched else None, latest,
                        "success" if fetched else "no_new_data", "",
                    ),
                )
            new_max = conn.execute("SELECT MAX(date) FROM trading_calendar").fetchone()[0]
            details = {
                "raw_rows_staged": len(stage.raw),
                "adjusted_rows_staged": len(stage.adjusted),
                "status_rows_staged": len(stage.status),
                "membership_observation_rows": len(stage.observations),
                "action_errors": stage.action_errors,
                "factor_audit": audit,
                "missing_member_prices": len(missing_members),
            }
            conn.execute(
                "INSERT INTO update_runs VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (
                    stage.run_id, stage.started_at, completed_at, stage.previous_max_date,
                    stage.requested_end_date, new_max, "success", sha256_file(excel),
                    str(stage.archive_dir), json.dumps(details, ensure_ascii=False),
                ),
            )
            metadata = {
                "price_rows": str(conn.execute("SELECT COUNT(*) FROM daily_price_raw").fetchone()[0]),
                "security_count": str(conn.execute("SELECT COUNT(*) FROM security").fetchone()[0]),
                "daily_security_status_rows": str(conn.execute("SELECT COUNT(*) FROM daily_security_status").fetchone()[0]),
                "daily_security_status_st_rows": str(conn.execute(
                    "SELECT COUNT(*) FROM daily_security_status WHERE is_st = 1"
                ).fetchone()[0]),
                "daily_security_status_downloaded_at": str(conn.execute(
                    "SELECT MAX(downloaded_at) FROM daily_security_status"
                ).fetchone()[0]),
                "incremental_last_run_id": stage.run_id,
                "incremental_last_success_at": completed_at,
                "incremental_data_end_date": str(new_max),
            }
            conn.executemany(
                "INSERT OR REPLACE INTO metadata(key, value) VALUES (?, ?)", metadata.items()
            )
            conn.commit()
        except Exception:
            conn.rollback()
            raise
    rescaled = [
        item for item in revisions
        if item["revision_type"] in {"scale_and_upsert", "full_reload"}
    ]
    revision_counts = pd.Series(
        [item["revision_type"] for item in revisions], dtype="string"
    ).value_counts().to_dict()
    return {
        "run_id": stage.run_id,
        "status": "success",
        "new_max_date": new_max,
        "revision_counts": {str(key): int(value) for key, value in revision_counts.items()},
        "rescaled_securities": rescaled,
        "factor_audit": audit,
    }


def official_event_state(database: Path, excel: Path, after_close_from: str) -> dict:
    adjustments = load_adjustments(excel)
    with sqlite3.connect(database) as conn:
        metadata = dict(conn.execute("SELECT key, value FROM metadata"))
        applied_count = int(metadata.get("membership_official_event_count", 0))
        calendar = [row[0] for row in conn.execute("SELECT date FROM trading_calendar ORDER BY date")]
        raw, _ = load_raw_snapshots(conn)
        maximum = calendar[-1]
        formal = {
            row[0]
            for row in conn.execute(
                "SELECT stock_code FROM universe_membership "
                "WHERE index_code=? AND valid_from <= ? AND ? < valid_to",
                (INDEX_CODE, maximum, maximum),
            )
        }
    total_count = int(adjustments["event_date"].nunique())
    if total_count <= applied_count:
        latest_observed_date = max(raw)
        latest_observed = raw[latest_observed_date]
        if latest_observed != formal:
            return {
                "status": "observed_change_unconfirmed",
                "events": total_count,
                "source_update_date": latest_observed_date,
                "observed_not_formal": sorted(latest_observed - formal),
                "formal_not_observed": sorted(formal - latest_observed),
            }
        return {"status": "current", "events": total_count}
    try:
        snapshots, matches = match_official_snapshots(
            adjustments, raw, calendar, after_close_from
        )
    except ValueError as exc:
        return {"status": "pending_official_adjustment", "events": total_count, "reason": str(exc)}
    return {
        "status": "ready",
        "events": total_count,
        "snapshot_dates": list(snapshots),
        "matches": matches,
    }


def mark_membership_state(database: Path, state: dict) -> None:
    pending = state["status"] in {
        "pending_official_adjustment", "observed_change_unconfirmed",
    }
    with sqlite3.connect(database) as conn:
        conn.execute(
            "INSERT OR REPLACE INTO metadata(key, value) VALUES (?, ?)",
            ("pending_official_adjustment", "True" if pending else "False"),
        )
        conn.execute(
            "DELETE FROM data_quality_issues WHERE issue_type = 'unverified_component_change'"
        )
        if pending:
            conn.execute(
                "INSERT INTO data_quality_issues(table_name, issue_type, severity, details) "
                "VALUES ('universe_membership', 'unverified_component_change', 'warning', ?)",
                (json.dumps(state, ensure_ascii=False),),
            )
            conn.execute(
                "UPDATE update_runs SET status = 'success_pending_official' "
                "WHERE run_id = (SELECT run_id FROM update_runs ORDER BY completed_at DESC LIMIT 1)"
            )
        conn.commit()


def maybe_apply_official(database: Path, excel: Path, root: Path, state: dict) -> dict:
    if state["status"] != "ready":
        mark_membership_state(database, state)
        return state
    result = apply_official_update(
        database,
        excel,
        root / "membership_snapshots_official_regular.csv",
        root / "official_membership_update_report.json",
        root / "backups",
        root / "database_manifest.json",
        "2021-01-01",
    )
    mark_membership_state(database, {"status": "current"})
    return {"status": "applied", "report": result}


def update_benchmark_csv(stage: Stage, output: Path) -> None:
    existing = pd.read_csv(output, dtype={"date": "string"}) if output.exists() else pd.DataFrame()
    combined = pd.concat([existing, stage.benchmark], ignore_index=True)
    combined = combined.drop_duplicates("date", keep="last").sort_values("date")
    output.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(prefix=output.stem + ".", suffix=".csv", dir=output.parent)
    os.close(fd)
    try:
        combined.to_csv(temp_name, index=False, encoding="utf-8-sig")
        os.replace(temp_name, output)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)


def replay_archives(
    database: Path,
    replay_dir: Path,
    excel: Path,
    finalize_official: bool = True,
) -> list[dict]:
    results = []
    paths = (
        [replay_dir]
        if (replay_dir / "manifest.json").exists()
        else sorted(item for item in replay_dir.iterdir() if item.is_dir())
    )
    for path in paths:
        stage = stage_from_archive(path)
        with sqlite3.connect(database) as conn:
            current_max = conn.execute("SELECT MAX(date) FROM trading_calendar").fetchone()[0]
            applied = conn.execute(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='update_runs'"
            ).fetchone() and conn.execute(
                "SELECT 1 FROM update_runs WHERE run_id=?", (stage.run_id,)
            ).fetchone()
        if not applied and stage.previous_max_date != current_max:
            if stage.requested_end_date <= current_max:
                results.append({
                    "run_id": stage.run_id,
                    "status": "skipped_superseded",
                    "current_max_date": current_max,
                })
                continue
            raise RuntimeError(
                f"cannot replay {stage.run_id}: expected database date "
                f"{stage.previous_max_date}, got {current_max}"
            )
        result = apply_stage(database, stage, excel)
        if finalize_official:
            state = official_event_state(database, excel, "2021-01-01")
            result["membership"] = maybe_apply_official(
                database, excel, database.parent, state
            )
            update_benchmark_csv(
                stage, database.parent / "benchmarks" / "csi300_daily.csv"
            )
        results.append(result)
    return results


def main() -> None:
    parser = argparse.ArgumentParser(description="Incrementally update the CSI 300 research database")
    parser.add_argument("--database", type=Path, default=Path("data_v5/csi300_2020_present.sqlite"))
    parser.add_argument("--excel", type=Path, default=Path("CSI300_remake_report.xlsx"))
    parser.add_argument("--end-date", default=None)
    parser.add_argument("--archive-root", type=Path, default=Path("data_v5/increments"))
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--full-validate", action="store_true")
    parser.add_argument("--replay-dir", type=Path, default=None)
    args = parser.parse_args()
    if args.replay_dir is not None:
        print(json.dumps(
            replay_archives(args.database, args.replay_dir, args.excel),
            ensure_ascii=False,
            indent=2,
        ))
        return
    stage = fetch_stage(args.database, args.excel, args.end_date, args.archive_root)
    manifest = write_archive(stage, args.excel)
    if args.dry_run:
        print(json.dumps({"status": "dry_run", "manifest": manifest}, ensure_ascii=False, indent=2))
        return
    try:
        result = apply_stage(args.database, stage, args.excel)
        membership = maybe_apply_official(
            args.database,
            args.excel,
            args.database.parent,
            official_event_state(args.database, args.excel, "2021-01-01"),
        )
        update_benchmark_csv(stage, args.database.parent / "benchmarks" / "csi300_daily.csv")
        write_archive(stage, args.excel, status="success")
        output = {"increment": result, "membership": membership}
        if args.full_validate:
            from validate_csi300_sqlite import validate

            validation = validate(args.database)
            output["validation"] = validation
            if validation["errors"]:
                raise RuntimeError(f"post-update validation failed: {validation['errors']}")
        print(json.dumps(output, ensure_ascii=False, indent=2))
    except Exception as exc:
        write_archive(stage, args.excel, status="failed", error=f"{type(exc).__name__}: {exc}")
        raise


if __name__ == "__main__":
    main()
