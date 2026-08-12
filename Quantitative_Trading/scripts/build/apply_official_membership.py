"""Apply official regular CSI 300 adjustments to the existing SQLite database."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sqlite3
from datetime import datetime
from pathlib import Path

import pandas as pd
ROOT = Path(__file__).resolve().parents[2]



INDEX_CODE = "000300"
BASELINE_SOURCE = "baostock_baseline_official_change_cross_checked"
OFFICIAL_SOURCE = "csindex_official_regular_manual_reconstruction"
EXCEL_HEADERS = [
    "\u8c03\u51fa\u4ee3\u7801",
    "\u8c03\u51fa\u540d\u79f0",
    "\u8c03\u5165\u4ee3\u7801",
    "\u8c03\u5165\u540d\u79f0",
]
EVENT_DATE_HEADER_PREFIX = "\u751f\u6548\u65e5\u671f"


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
CREATE INDEX IF NOT EXISTS idx_baostock_membership_observed
    ON baostock_membership_snapshots_raw(index_code, observed_date);
CREATE TABLE IF NOT EXISTS official_adjustments (
    index_code TEXT NOT NULL,
    official_event_date TEXT NOT NULL,
    valid_from TEXT NOT NULL,
    out_code TEXT NOT NULL,
    out_name TEXT NOT NULL,
    in_code TEXT NOT NULL,
    in_name TEXT NOT NULL,
    adjustment_type TEXT NOT NULL,
    matched_observed_date TEXT NOT NULL,
    source_file TEXT NOT NULL,
    source_sha256 TEXT NOT NULL,
    verification_status TEXT NOT NULL,
    PRIMARY KEY (index_code, official_event_date, out_code, in_code)
);
CREATE INDEX IF NOT EXISTS idx_official_adjustments_valid_from
    ON official_adjustments(index_code, valid_from);
"""


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def table_exists(conn: sqlite3.Connection, name: str) -> bool:
    row = conn.execute(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", (name,)
    ).fetchone()
    return row is not None


def load_adjustments(path: Path) -> pd.DataFrame:
    frame = pd.read_excel(path)
    headers = list(frame.columns[:5])
    if headers[:4] != EXCEL_HEADERS or not str(headers[4]).startswith(EVENT_DATE_HEADER_PREFIX):
        raise ValueError(f"unexpected Excel headers: {list(frame.columns[:5])}")
    frame = frame.iloc[:, :5].copy()
    frame.columns = ["out_code", "out_name", "in_code", "in_name", "event_date"]
    if frame.isna().any().any():
        raise ValueError(f"official adjustment input contains missing values: {frame.isna().sum().to_dict()}")
    for column in ["out_code", "in_code"]:
        frame[column] = (
            frame[column].astype("string").str.replace(r"\.0$", "", regex=True).str.strip().str.zfill(6)
        )
        if (~frame[column].str.fullmatch(r"\d{6}")).any():
            raise ValueError(f"invalid stock codes in {column}")
    for column in ["out_name", "in_name"]:
        frame[column] = frame[column].astype("string").str.strip()
        if frame[column].eq("").any():
            raise ValueError(f"blank stock names in {column}")
    frame["event_date"] = pd.to_datetime(frame["event_date"], errors="coerce").dt.strftime("%Y-%m-%d")
    if frame["event_date"].isna().any():
        raise ValueError("invalid official event dates")
    if frame.duplicated(["event_date", "out_code"]).any():
        raise ValueError("duplicate outgoing codes within an official event")
    if frame.duplicated(["event_date", "in_code"]).any():
        raise ValueError("duplicate incoming codes within an official event")
    sizes = frame.groupby("event_date").agg(out_count=("out_code", "nunique"), in_count=("in_code", "nunique"))
    if not sizes["out_count"].eq(sizes["in_count"]).all():
        raise ValueError("official events have unequal incoming and outgoing counts")
    return frame.sort_values(["event_date", "out_code"]).reset_index(drop=True)


def load_raw_snapshots(conn: sqlite3.Connection) -> tuple[dict[str, set[str]], list[tuple]]:
    if table_exists(conn, "baostock_membership_snapshots_raw"):
        archived = conn.execute(
            "SELECT index_code, observed_date, stock_code, weight, announcement_date, source, source_url "
            "FROM baostock_membership_snapshots_raw WHERE index_code = ? ORDER BY observed_date, stock_code",
            (INDEX_CODE,),
        ).fetchall()
    else:
        archived = []
    if not archived:
        archived = conn.execute(
            "SELECT index_code, effective_date, stock_code, weight, announcement_date, source, source_url "
            "FROM membership_snapshots WHERE index_code = ? ORDER BY effective_date, stock_code",
            (INDEX_CODE,),
        ).fetchall()
    snapshots: dict[str, set[str]] = {}
    for _, observed_date, stock_code, *_ in archived:
        snapshots.setdefault(observed_date, set()).add(stock_code)
    if not snapshots or any(len(codes) != 300 for codes in snapshots.values()):
        raise ValueError("Baostock raw snapshots are missing or do not contain 300 unique stocks")
    return snapshots, archived


def next_trading_day(calendar: list[str], event_date: str) -> str:
    try:
        return next(day for day in calendar if day > event_date)
    except StopIteration as exc:
        raise ValueError(f"trading calendar does not extend beyond {event_date}") from exc


def official_valid_from(calendar: list[str], event_date: str, after_close_from: str) -> str:
    if event_date >= after_close_from:
        return next_trading_day(calendar, event_date)
    if event_date not in set(calendar):
        raise ValueError(f"legacy effective date is not a trading day: {event_date}")
    return event_date


def match_official_snapshots(
    adjustments: pd.DataFrame,
    raw_snapshots: dict[str, set[str]],
    calendar: list[str],
    after_close_from: str,
) -> tuple[dict[str, set[str]], list[dict]]:
    raw_dates = sorted(raw_snapshots)
    baseline_date = raw_dates[0]
    transitions = []
    for previous_date, observed_date in zip(raw_dates, raw_dates[1:]):
        previous = raw_snapshots[previous_date]
        current = raw_snapshots[observed_date]
        transitions.append({
            "previous_date": previous_date,
            "observed_date": observed_date,
            "out": previous - current,
            "in": current - previous,
        })

    official_snapshots = {baseline_date: set(raw_snapshots[baseline_date])}
    matches = []
    for event_date, group in adjustments.groupby("event_date", sort=True):
        outgoing = set(group["out_code"])
        incoming = set(group["in_code"])
        if event_date < baseline_date:
            baseline = official_snapshots[baseline_date]
            if not outgoing.isdisjoint(baseline) or not incoming.issubset(baseline):
                raise ValueError(f"baseline snapshot fails official adjustment cross-check for {event_date}")
            matches.append({
                "event_date": event_date,
                "valid_from": baseline_date,
                "matched_observed_date": baseline_date,
                "previous_observed_date": None,
                "change_count": len(outgoing),
                "baseline_cross_check": True,
            })
            continue

        exact = [item for item in transitions if item["out"] == outgoing and item["in"] == incoming]
        if len(exact) != 1:
            raise ValueError(f"expected one exact Baostock transition match for {event_date}, found {len(exact)}")
        matched = exact[0]
        valid_from = official_valid_from(calendar, event_date, after_close_from)
        if valid_from in official_snapshots:
            raise ValueError(f"duplicate official snapshot date: {valid_from}")
        official_snapshots[valid_from] = set(raw_snapshots[matched["observed_date"]])
        matches.append({
            "event_date": event_date,
            "valid_from": valid_from,
            "matched_observed_date": matched["observed_date"],
            "previous_observed_date": matched["previous_date"],
            "change_count": len(outgoing),
            "baseline_cross_check": False,
        })

    if any(len(codes) != 300 for codes in official_snapshots.values()):
        raise ValueError("official reconstructed snapshots do not contain exactly 300 stocks")
    return dict(sorted(official_snapshots.items())), matches


def build_membership_rows(
    snapshots: dict[str, set[str]], names: dict[str, str]
) -> tuple[list[tuple], list[tuple], list[tuple], list[tuple], pd.DataFrame]:
    dates = sorted(snapshots)
    snapshot_rows = []
    weight_rows = []
    weight_interval_rows = []
    csv_rows = []
    for index, effective_date in enumerate(dates):
        valid_to = dates[index + 1] if index + 1 < len(dates) else "9999-12-31"
        source = BASELINE_SOURCE if index == 0 else OFFICIAL_SOURCE
        for stock_code in sorted(snapshots[effective_date]):
            snapshot_rows.append((INDEX_CODE, effective_date, stock_code, None, None, source, None))
            weight_rows.append((INDEX_CODE, effective_date, stock_code, None, source))
            weight_interval_rows.append((INDEX_CODE, stock_code, effective_date, valid_to, None, source))
            csv_rows.append({
                "index_code": INDEX_CODE,
                "effective_date": effective_date,
                "stock_code": stock_code,
                "stock_name": names.get(stock_code, ""),
                "weight": None,
                "announcement_date": None,
                "source": source,
                "source_url": None,
            })

    interval_rows = []
    active: dict[str, str] = {}
    previous: set[str] = set()
    for effective_date in dates:
        current = snapshots[effective_date]
        for stock_code in sorted(current - previous):
            active[stock_code] = effective_date
        for stock_code in sorted(previous - current):
            start = active.pop(stock_code)
            interval_rows.append((INDEX_CODE, stock_code, start, effective_date, None, start, OFFICIAL_SOURCE))
        previous = current
    for stock_code, start in sorted(active.items()):
        source = BASELINE_SOURCE if start == dates[0] else OFFICIAL_SOURCE
        interval_rows.append((INDEX_CODE, stock_code, start, "9999-12-31", None, start, source))
    return snapshot_rows, weight_rows, weight_interval_rows, interval_rows, pd.DataFrame(csv_rows)


def expand_daily(intervals: list[tuple], calendar: list[str]) -> dict[str, set[str]]:
    result = {day: set() for day in calendar}
    for _, stock_code, valid_from, valid_to, *_ in intervals:
        for day in calendar:
            if valid_from <= day < valid_to:
                result[day].add(stock_code)
    return result


def backup_database(database: Path, backup_dir: Path, stamp: str) -> Path:
    backup_dir.mkdir(parents=True, exist_ok=True)
    backup_path = backup_dir / f"{database.stem}_before_official_membership_{stamp}.sqlite"
    with sqlite3.connect(database) as source, sqlite3.connect(backup_path) as target:
        source.backup(target)
        if target.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            raise RuntimeError("backup integrity check failed")
    return backup_path


def apply_update(
    database: Path,
    excel: Path,
    output_csv: Path,
    report_path: Path,
    backup_dir: Path,
    manifest_path: Path,
    after_close_from: str,
) -> dict:
    adjustments = load_adjustments(excel)
    source_hash = file_sha256(excel)
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    updated_at = datetime.now().isoformat(timespec="seconds")

    with sqlite3.connect(database, timeout=30) as conn:
        if conn.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            raise RuntimeError("source database integrity check failed")
        calendar = [row[0] for row in conn.execute(
            "SELECT date FROM trading_calendar WHERE is_trading_day = 1 ORDER BY date"
        )]
        names = dict(conn.execute("SELECT stock_code, name FROM security"))
        raw_snapshots, archived_rows = load_raw_snapshots(conn)
        old_intervals = conn.execute(
            "SELECT index_code, stock_code, valid_from, valid_to, weight, weight_date, source "
            "FROM universe_membership WHERE index_code = ?",
            (INDEX_CODE,),
        ).fetchall()

    official_snapshots, matches = match_official_snapshots(
        adjustments, raw_snapshots, calendar, after_close_from
    )
    snapshot_rows, weight_rows, weight_intervals, interval_rows, csv_frame = build_membership_rows(
        official_snapshots, names
    )
    missing_security = sorted({row[2] for row in snapshot_rows} - set(names))
    if missing_security:
        raise ValueError(f"official membership stocks missing from security table: {missing_security}")

    old_daily = expand_daily(old_intervals, calendar)
    new_daily = expand_daily(interval_rows, calendar)
    invalid_daily = {day: len(codes) for day, codes in new_daily.items() if len(codes) != 300}
    if invalid_daily:
        raise ValueError(f"official daily universe does not contain 300 stocks: {invalid_daily}")
    changed_dates = [day for day in calendar if old_daily[day] != new_daily[day]]
    removed_member_days = sum(len(old_daily[day] - new_daily[day]) for day in changed_dates)
    added_member_days = sum(len(new_daily[day] - old_daily[day]) for day in changed_dates)

    output_csv.parent.mkdir(parents=True, exist_ok=True)
    temp_csv = output_csv.with_suffix(output_csv.suffix + ".tmp")
    csv_frame.to_csv(temp_csv, index=False, encoding="utf-8-sig")
    check_csv = pd.read_csv(temp_csv, dtype={"stock_code": "string"})
    if len(check_csv) != len(snapshot_rows):
        raise RuntimeError("derived membership CSV verification failed")

    backup_path = backup_database(database, backup_dir, stamp)
    official_map = {item["event_date"]: item for item in matches}
    official_rows = []
    for row in adjustments.itertuples(index=False):
        match = official_map[row.event_date]
        official_rows.append((
            INDEX_CODE, row.event_date, match["valid_from"], row.out_code, row.out_name,
            row.in_code, row.in_name, "regular", match["matched_observed_date"],
            excel.name, source_hash, "user_verified_official",
        ))

    try:
        conn = sqlite3.connect(database, timeout=30)
        conn.execute("PRAGMA foreign_keys = ON")
        conn.execute("BEGIN IMMEDIATE")
        for statement in AUDIT_SCHEMA.split(";"):
            if statement.strip():
                conn.execute(statement)
        if not conn.execute("SELECT 1 FROM baostock_membership_snapshots_raw LIMIT 1").fetchone():
            conn.executemany(
                "INSERT INTO baostock_membership_snapshots_raw VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                [(*row, updated_at) for row in archived_rows],
            )
        conn.execute("DELETE FROM official_adjustments WHERE index_code = ?", (INDEX_CODE,))
        conn.executemany("INSERT INTO official_adjustments VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", official_rows)

        for table in [
            "membership_snapshots", "membership_weights", "membership_weight_intervals", "universe_membership",
        ]:
            conn.execute(f"DELETE FROM {table} WHERE index_code = ?", (INDEX_CODE,))
        conn.executemany("INSERT INTO membership_snapshots VALUES (?, ?, ?, ?, ?, ?, ?)", snapshot_rows)
        conn.executemany("INSERT INTO membership_weights VALUES (?, ?, ?, ?, ?)", weight_rows)
        conn.executemany("INSERT INTO membership_weight_intervals VALUES (?, ?, ?, ?, ?, ?)", weight_intervals)
        conn.executemany("INSERT INTO universe_membership VALUES (?, ?, ?, ?, ?, ?, ?)", interval_rows)

        conn.execute(
            "DELETE FROM data_quality_issues WHERE issue_type IN "
            "('membership_missing', 'membership_source_unverified', 'weights_missing', "
            "'historical_price_missing', 'member_price_missing', 'temporary_adjustments_omitted')"
        )
        conn.execute(
            "INSERT INTO data_quality_issues(table_name, issue_type, severity, details) VALUES (?, ?, ?, ?)",
            ("membership_weights", "weights_missing", "warning", "Official regular adjustment input does not provide historical weights"),
        )
        conn.execute(
            "INSERT INTO data_quality_issues(table_name, issue_type, severity, details) VALUES (?, ?, ?, ?)",
            ("universe_membership", "temporary_adjustments_omitted", "warning", "Official semiannual regular adjustments are included; temporary adjustment dates are intentionally omitted"),
        )
        missing_daily_rows = conn.execute(
            "SELECT date, stock_code FROM daily_universe WHERE close_raw IS NULL OR close_adj IS NULL"
        ).fetchall()
        for missing_date, stock_code in missing_daily_rows:
            conn.execute(
                "INSERT INTO data_quality_issues(table_name, issue_date, stock_code, issue_type, severity, details) "
                "VALUES (?, ?, ?, ?, ?, ?)",
                ("daily_universe", missing_date, stock_code, "member_price_missing", "warning", "Member retained with NULL price and is_tradeable=0"),
            )

        metadata = {
            "membership_loaded": "True",
            "membership_source": str(output_csv),
            "membership_official_input": str(excel),
            "membership_official_input_sha256": source_hash,
            "membership_snapshot_sources": json.dumps([BASELINE_SOURCE, OFFICIAL_SOURCE]),
            "membership_officially_verified": "True",
            "membership_scope": "official_regular_adjustments_only",
            "temporary_adjustments_included": "False",
            "membership_official_event_count": str(adjustments["event_date"].nunique()),
            "membership_official_change_rows": str(len(adjustments)),
            "membership_valid_from_semantics": "first_trading_day_new_pool_is_usable",
            "membership_after_close_rule_from": after_close_from,
            "membership_updated_at": updated_at,
        }
        conn.executemany("INSERT OR REPLACE INTO metadata(key, value) VALUES (?, ?)", metadata.items())

        checks = {
            "integrity": conn.execute("PRAGMA integrity_check").fetchone()[0],
            "snapshot_rows": conn.execute("SELECT COUNT(*) FROM membership_snapshots WHERE index_code = ?", (INDEX_CODE,)).fetchone()[0],
            "snapshot_dates": conn.execute("SELECT COUNT(DISTINCT effective_date) FROM membership_snapshots WHERE index_code = ?", (INDEX_CODE,)).fetchone()[0],
            "interval_rows": conn.execute("SELECT COUNT(*) FROM universe_membership WHERE index_code = ?", (INDEX_CODE,)).fetchone()[0],
            "daily_rows": conn.execute("SELECT COUNT(*) FROM daily_universe WHERE index_code = ?", (INDEX_CODE,)).fetchone()[0],
            "bad_snapshot_sizes": conn.execute(
                "SELECT COUNT(*) FROM (SELECT effective_date FROM membership_snapshots WHERE index_code = ? "
                "GROUP BY effective_date HAVING COUNT(*) <> 300)", (INDEX_CODE,)
            ).fetchone()[0],
            "bad_daily_sizes": conn.execute(
                "SELECT COUNT(*) FROM (SELECT date FROM daily_universe WHERE index_code = ? "
                "GROUP BY date HAVING COUNT(*) <> 300)", (INDEX_CODE,)
            ).fetchone()[0],
            "missing_daily_prices": len(missing_daily_rows),
        }
        if checks["integrity"] != "ok" or checks["bad_snapshot_sizes"] or checks["bad_daily_sizes"]:
            raise RuntimeError(f"post-update validation failed: {checks}")
        conn.commit()
    except Exception:
        if "conn" in locals():
            conn.rollback()
        raise
    finally:
        if "conn" in locals():
            conn.close()

    os.replace(temp_csv, output_csv)
    report = {
        "updated_at": updated_at,
        "database": str(database),
        "backup": str(backup_path),
        "official_input": str(excel),
        "official_input_sha256": source_hash,
        "scope": "official_regular_adjustments_only",
        "temporary_adjustments_included": False,
        "after_close_rule_from": after_close_from,
        "official_event_count": int(adjustments["event_date"].nunique()),
        "official_change_rows": int(len(adjustments)),
        "snapshot_dates": list(official_snapshots),
        "matches": matches,
        "before": {
            "snapshot_dates": len(raw_snapshots),
            "snapshot_rows": sum(len(codes) for codes in raw_snapshots.values()),
            "interval_rows": len(old_intervals),
        },
        "after": checks,
        "daily_membership_diff": {
            "changed_trading_dates": len(changed_dates),
            "first_changed_date": changed_dates[0] if changed_dates else None,
            "last_changed_date": changed_dates[-1] if changed_dates else None,
            "removed_member_days": removed_member_days,
            "added_member_days": added_member_days,
        },
        "derived_membership_csv": str(output_csv),
    }
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    manifest = {}
    if manifest_path.exists():
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest.update({
        "membership_updated_at": updated_at,
        "membership_input": str(output_csv),
        "membership_official_input": str(excel),
        "membership_official_input_sha256": source_hash,
        "membership_snapshot_sources": [BASELINE_SOURCE, OFFICIAL_SOURCE],
        "membership_officially_verified": True,
        "membership_scope": "official_regular_adjustments_only",
        "temporary_adjustments_included": False,
        "membership_after_close_rule_from": after_close_from,
        "weights_available": False,
    })
    manifest.setdefault("result", {}).update({
        "membership_rows": checks["snapshot_rows"],
        "weight_intervals": len(weight_intervals),
        "intervals": checks["interval_rows"],
        "missing_daily_prices": checks["missing_daily_prices"],
    })
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return report


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--database", type=Path, default=ROOT / "data" / "csi300_2010_present.sqlite")
    parser.add_argument("--excel", type=Path, default=ROOT / "CSI300_remake_report.xlsx")
    parser.add_argument("--output-csv", type=Path, default=ROOT / "data" / "membership_snapshots_official_regular.csv")
    parser.add_argument("--report", type=Path, default=ROOT / "data" / "official_membership_update_report.json")
    parser.add_argument("--backup-dir", type=Path, default=ROOT / "data" / "backups")
    parser.add_argument("--manifest", type=Path, default=ROOT / "data" / "database_manifest.json")
    parser.add_argument("--after-close-from", default="2021-01-01")
    args = parser.parse_args()
    result = apply_update(
        args.database, args.excel, args.output_csv, args.report,
        args.backup_dir, args.manifest, args.after_close_from,
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()