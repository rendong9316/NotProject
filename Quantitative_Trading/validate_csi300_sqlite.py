"""Validate the CSI 300 SQLite database and emit a machine-readable report."""
from __future__ import annotations

import argparse
import json
import sqlite3
from pathlib import Path


def scalar(conn: sqlite3.Connection, query: str):
    return conn.execute(query).fetchone()[0]


def object_exists(conn: sqlite3.Connection, name: str, object_type: str = "table") -> bool:
    return conn.execute(
        "SELECT 1 FROM sqlite_master WHERE type = ? AND name = ?", (object_type, name)
    ).fetchone() is not None


def validate(path: Path) -> dict:
    report = {"database": str(path), "errors": [], "warnings": [], "counts": {}}
    with sqlite3.connect(path) as conn:
        integrity = scalar(conn, "PRAGMA integrity_check")
        if integrity != "ok":
            report["errors"].append(f"integrity_check: {integrity}")
        counts = report["counts"]
        for table in [
            "security", "trading_calendar", "daily_price_raw", "daily_price_adjusted",
            "components_snapshot", "membership_snapshots", "membership_weight_intervals",
            "universe_membership", "daily_universe",
        ]:
            counts[table] = scalar(conn, f"SELECT COUNT(*) FROM {table}")
        if counts["daily_price_raw"] != counts["daily_price_adjusted"]:
            report["errors"].append("raw and adjusted row counts differ")
        if scalar(conn, "SELECT COUNT(*) FROM security WHERE stock_code NOT GLOB '[0-9][0-9][0-9][0-9][0-9][0-9]'"):
            report["errors"].append("invalid stock codes")
        if scalar(conn, "SELECT COUNT(*) FROM daily_price_raw WHERE high_raw < low_raw OR high_raw < open_raw OR high_raw < close_raw OR low_raw > open_raw OR low_raw > close_raw"):
            report["errors"].append("invalid raw OHLC rows")
        if counts["membership_snapshots"] == 0:
            report["errors"].append("historical membership snapshots are not loaded")
        else:
            bad_snapshots = conn.execute(
                "SELECT index_code, effective_date, COUNT(*) FROM membership_snapshots GROUP BY index_code, effective_date HAVING COUNT(*) <> 300"
            ).fetchall()
            if bad_snapshots:
                report["errors"].append(f"invalid membership snapshot sizes: {bad_snapshots}")
            bad_daily = conn.execute(
                "SELECT date, COUNT(*) FROM daily_universe GROUP BY date HAVING COUNT(*) <> 300 LIMIT 20"
            ).fetchall()
            if bad_daily:
                report["errors"].append(f"daily universe does not contain 300 members: {bad_daily}")
            missing_prices = scalar(conn, "SELECT COUNT(*) FROM daily_universe WHERE close_raw IS NULL OR close_adj IS NULL")
            if missing_prices:
                report["warnings"].append(
                    f"{missing_prices} member rows have no source price and remain non-tradeable"
                )
            overlapping = scalar(
                conn,
                "SELECT COUNT(*) FROM universe_membership AS a JOIN universe_membership AS b "
                "ON a.index_code = b.index_code AND a.stock_code = b.stock_code "
                "AND a.valid_from < b.valid_from AND b.valid_from < a.valid_to",
            )
            if overlapping:
                report["errors"].append(f"overlapping membership intervals: {overlapping}")

        metadata = dict(conn.execute("SELECT key, value FROM metadata"))
        counts["membership_scope"] = metadata.get("membership_scope", "unspecified")
        if metadata.get("membership_scope") == "official_regular_adjustments_only":
            if metadata.get("membership_officially_verified") != "True":
                report["errors"].append("official regular membership scope is not marked verified")
            for table in ["official_adjustments", "baostock_membership_snapshots_raw"]:
                if not object_exists(conn, table):
                    report["errors"].append(f"official membership audit table is missing: {table}")
            if object_exists(conn, "official_adjustments"):
                counts["official_adjustments"] = scalar(conn, "SELECT COUNT(*) FROM official_adjustments")
                if counts["official_adjustments"] == 0:
                    report["errors"].append("official membership is marked verified but has no adjustment rows")
            if object_exists(conn, "baostock_membership_snapshots_raw"):
                counts["baostock_membership_snapshots_raw"] = scalar(
                    conn, "SELECT COUNT(*) FROM baostock_membership_snapshots_raw"
                )
                if counts["baostock_membership_snapshots_raw"] == 0:
                    report["errors"].append("raw Baostock membership audit snapshots are empty")
        if metadata.get("temporary_adjustments_included") == "False":
            report["warnings"].append("temporary constituent adjustments are intentionally omitted")
        issues = conn.execute(
            "SELECT severity, COUNT(*) FROM data_quality_issues GROUP BY severity"
        ).fetchall()
        counts["quality_issues"] = dict(issues)
        warning_count = counts["quality_issues"].get("warning", 0)
        if warning_count:
            report["warnings"].append(f"{warning_count} recorded data warnings")
    return report


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("database", type=Path, nargs="?", default=Path("data_v5/csi300_2020_present.sqlite"))
    parser.add_argument("--report", type=Path, default=Path("data_v5/database_quality_report.json"))
    args = parser.parse_args()
    report = validate(args.database)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    raise SystemExit(1 if report["errors"] else 0)


if __name__ == "__main__":
    main()
