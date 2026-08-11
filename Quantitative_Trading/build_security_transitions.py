"""Install and validate officially verified cross-security settlement events."""
from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
from datetime import datetime
from pathlib import Path

import pandas as pd


SCHEMA = """
CREATE TABLE IF NOT EXISTS security_transitions (
    source_stock_code TEXT NOT NULL,
    target_stock_code TEXT NOT NULL,
    record_date TEXT NOT NULL,
    event_date TEXT NOT NULL,
    exchange_ratio REAL NOT NULL CHECK (exchange_ratio > 0),
    cash_per_source_share REAL NOT NULL DEFAULT 0,
    event_type TEXT NOT NULL,
    official_fractional_rule TEXT NOT NULL,
    simulation_fractional_rule TEXT NOT NULL,
    implementation_announcement_url TEXT NOT NULL,
    result_announcement_url TEXT NOT NULL,
    implementation_art_code TEXT NOT NULL,
    result_art_code TEXT NOT NULL,
    verification_status TEXT NOT NULL,
    notes TEXT,
    installed_at TEXT NOT NULL,
    PRIMARY KEY (source_stock_code, event_date)
);
CREATE INDEX IF NOT EXISTS idx_security_transitions_event
    ON security_transitions(event_date, source_stock_code);
CREATE INDEX IF NOT EXISTS idx_security_transitions_target
    ON security_transitions(target_stock_code, event_date);
"""


TRANSITIONS = [
    {
        "source_stock_code": "600837",
        "target_stock_code": "601211",
        "record_date": "2025-03-03",
        "event_date": "2025-03-17",
        "exchange_ratio": 0.62,
        "cash_per_source_share": 0.0,
        "event_type": "share_swap_absorption_merger",
        "official_fractional_rule": "tail_ranking_random_tie_integer_allocation",
        "simulation_fractional_rule": "nearest_integer_max_one_share_error",
        "implementation_announcement_url": (
            "https://www.sse.com.cn/disclosure/listedinfo/announcement/c/new/"
            "2025-03-01/601211_20250301_WWHY.pdf"
        ),
        "result_announcement_url": (
            "https://www.sse.com.cn/disclosure/listedinfo/announcement/c/new/"
            "2025-03-14/601211_20250314_HTR8.pdf"
        ),
        "implementation_art_code": "AN202502281643613677",
        "result_art_code": "AN202503131644344196",
        "verification_status": "official_sse_verified",
        "notes": "Haitong Securities A shares converted to Guotai Haitong A shares.",
    },
    {
        "source_stock_code": "601989",
        "target_stock_code": "600150",
        "record_date": "2025-09-04",
        "event_date": "2025-09-16",
        "exchange_ratio": 0.1339,
        "cash_per_source_share": 0.0,
        "event_type": "share_swap_absorption_merger",
        "official_fractional_rule": "tail_ranking_random_tie_integer_allocation",
        "simulation_fractional_rule": "nearest_integer_max_one_share_error",
        "implementation_announcement_url": (
            "https://www.sse.com.cn/disclosure/listedinfo/announcement/c/new/"
            "2025-09-04/600150_20250904_TNGO.pdf"
        ),
        "result_announcement_url": (
            "https://www.sse.com.cn/disclosure/listedinfo/announcement/c/new/"
            "2025-09-12/600150_20250912_BPCR.pdf"
        ),
        "implementation_art_code": "AN202509031738492644",
        "result_art_code": "AN202509111741810030",
        "verification_status": "official_sse_verified",
        "notes": "China Shipbuilding Industry converted to CSSC Holdings.",
    },
]


def install_transitions(conn: sqlite3.Connection) -> int:
    conn.executescript(SCHEMA)
    installed_at = datetime.now().isoformat(timespec="seconds")
    columns = [row[1] for row in conn.execute("PRAGMA table_info(security_transitions)")]
    rows = [tuple([event.get(column) for column in columns[:-1]] + [installed_at]) for event in TRANSITIONS]
    placeholders = ",".join("?" for _ in columns)
    with conn:
        conn.executemany(
            f"INSERT OR REPLACE INTO security_transitions ({','.join(columns)}) "
            f"VALUES ({placeholders})",
            rows,
        )
    return len(rows)


def validate_transitions(conn: sqlite3.Connection) -> dict[str, int]:
    rows = conn.execute(
        "SELECT source_stock_code, target_stock_code, record_date, event_date, "
        "exchange_ratio, verification_status FROM security_transitions ORDER BY event_date"
    ).fetchall()
    errors = []
    for source, target, record_date, event_date, ratio, status in rows:
        source_exists = conn.execute(
            "SELECT 1 FROM security WHERE stock_code = ?", (source,)
        ).fetchone()
        target_exists = conn.execute(
            "SELECT 1 FROM security WHERE stock_code = ?", (target,)
        ).fetchone()
        source_record = conn.execute(
            "SELECT 1 FROM daily_price_raw WHERE stock_code = ? AND date = ?",
            (source, record_date),
        ).fetchone()
        target_event = conn.execute(
            "SELECT trade_status FROM daily_price_raw WHERE stock_code = ? AND date = ?",
            (target, event_date),
        ).fetchone()
        if not source_exists or not target_exists:
            errors.append(f"missing security master row: {source}->{target}")
        if not source_record:
            errors.append(f"missing source record-date bar: {source} {record_date}")
        if not target_event or target_event[0] != 1:
            errors.append(f"target is not tradeable on event date: {target} {event_date}")
        if ratio <= 0 or status != "official_sse_verified":
            errors.append(f"invalid or unverified transition: {source} {event_date}")
    if errors:
        raise ValueError("; ".join(errors))
    with conn:
        conn.executemany(
            "INSERT OR REPLACE INTO metadata(key, value) VALUES (?, ?)",
            {
                "security_transitions_rows": str(len(rows)),
                "security_transitions_source": "sse_official_implementation_and_result_announcements",
                "security_transitions_fractional_simulation": "nearest_integer_max_one_share_error",
            }.items(),
        )
    return {"rows": len(rows), "errors": 0}


def archive_transitions(conn: sqlite3.Connection, output_dir: Path) -> dict[str, object]:
    output_dir.mkdir(parents=True, exist_ok=True)
    frame = pd.read_sql_query(
        "SELECT * FROM security_transitions ORDER BY event_date, source_stock_code", conn
    )
    path = output_dir / "security_transitions.csv"
    frame.to_csv(path, index=False, encoding="utf-8-sig")
    manifest = {
        "created_at": datetime.now().isoformat(timespec="seconds"),
        "file": path.name,
        "rows": len(frame),
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
    }
    (output_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description="Build cross-security settlement events")
    parser.add_argument(
        "--database", type=Path, default=Path("data_v5/csi300_2020_present.sqlite")
    )
    parser.add_argument(
        "--export-dir", type=Path, default=Path("data_v5/security_transitions")
    )
    args = parser.parse_args()
    with sqlite3.connect(args.database) as conn:
        installed = install_transitions(conn)
        validation = validate_transitions(conn)
        archive = archive_transitions(conn, args.export_dir)
    print(json.dumps({"installed": installed, "validation": validation, "archive": archive}, indent=2))


if __name__ == "__main__":
    main()
