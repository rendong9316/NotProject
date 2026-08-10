"""Download, archive, import, and validate daily historical ST status."""
from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
import time
from datetime import datetime
from pathlib import Path

import baostock as bs
import pandas as pd


SCHEMA = """
CREATE TABLE IF NOT EXISTS daily_security_status (
    date TEXT NOT NULL,
    stock_code TEXT NOT NULL,
    is_st INTEGER NOT NULL CHECK (is_st IN (0, 1)),
    trade_status INTEGER NOT NULL CHECK (trade_status IN (0, 1)),
    source TEXT NOT NULL,
    downloaded_at TEXT NOT NULL,
    PRIMARY KEY (date, stock_code)
);
CREATE INDEX IF NOT EXISTS idx_security_status_code_date
    ON daily_security_status(stock_code, date);
"""


def exchange_code(stock_code: str) -> str:
    prefix = "sh." if stock_code.startswith(("5", "6")) else "sz."
    return prefix + stock_code


def fetch_code(
    stock_code: str,
    start_date: str,
    end_date: str,
    downloaded_at: str,
    retries: int,
) -> list[tuple]:
    last_error = "unknown error"
    for attempt in range(retries):
        result = bs.query_history_k_data_plus(
            exchange_code(stock_code),
            "date,code,tradestatus,isST",
            start_date=start_date,
            end_date=end_date,
            frequency="d",
            adjustflag="3",
        )
        if result.error_code == "0":
            rows = []
            while result.next():
                date, _, trade_status, is_st = result.get_row_data()
                if date and trade_status in {"0", "1"} and is_st in {"0", "1"}:
                    rows.append((date, stock_code, int(is_st), int(trade_status), "baostock", downloaded_at))
            return rows
        last_error = result.error_msg
        if attempt + 1 < retries:
            time.sleep(2 ** attempt)
    raise RuntimeError(f"{stock_code}: Baostock status download failed: {last_error}")


def download_status(
    conn: sqlite3.Connection,
    start_date: str,
    end_date: str,
    retries: int,
) -> dict:
    codes = [row[0] for row in conn.execute("SELECT stock_code FROM security ORDER BY stock_code")]
    login = bs.login()
    if login.error_code != "0":
        raise RuntimeError(f"Baostock login failed: {login.error_msg}")
    downloaded_at = datetime.now().isoformat(timespec="seconds")
    total_rows = 0
    failures = []
    try:
        with conn:
            conn.execute("DELETE FROM daily_security_status")
        for index, code in enumerate(codes, start=1):
            try:
                rows = fetch_code(code, start_date, end_date, downloaded_at, retries)
                with conn:
                    conn.executemany(
                        "INSERT INTO daily_security_status VALUES (?, ?, ?, ?, ?, ?)", rows
                    )
                total_rows += len(rows)
            except Exception as exc:
                failures.append({"stock_code": code, "error": str(exc)})
            if index == 1 or index % 50 == 0 or index == len(codes):
                print(
                    f"Status {index}/{len(codes)} rows={total_rows} failures={len(failures)}",
                    flush=True,
                )
    finally:
        bs.logout()
    if failures:
        raise RuntimeError(json.dumps(failures, ensure_ascii=False))
    return {"securities": len(codes), "rows": total_rows, "downloaded_at": downloaded_at}


def archive_status(conn: sqlite3.Connection, output_dir: Path) -> dict:
    output_dir.mkdir(parents=True, exist_ok=True)
    frame = pd.read_sql_query(
        "SELECT * FROM daily_security_status ORDER BY stock_code, date",
        conn,
        dtype={"stock_code": "string"},
    )
    path = output_dir / "daily_security_status.csv"
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


def import_status(conn: sqlite3.Connection, input_dir: Path) -> dict:
    path = input_dir / "daily_security_status.csv"
    manifest_path = input_dir / "manifest.json"
    if not path.exists() or not manifest_path.exists():
        raise FileNotFoundError(f"security-status archive is incomplete under {input_dir}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    actual_hash = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual_hash != manifest.get("sha256"):
        raise ValueError("security-status archive checksum mismatch")
    frame = pd.read_csv(path, dtype={"stock_code": "string"})
    expected = [row[1] for row in conn.execute("PRAGMA table_info(daily_security_status)")]
    if list(frame.columns) != expected or len(frame) != int(manifest.get("rows", -1)):
        raise ValueError("security-status archive schema or row count mismatch")
    values = frame.astype(object).where(pd.notna(frame), None)
    with conn:
        conn.execute("DELETE FROM daily_security_status")
        conn.executemany(
            "INSERT INTO daily_security_status VALUES (?, ?, ?, ?, ?, ?)",
            values.itertuples(index=False, name=None),
        )
    return {"rows": len(frame), "sha256": actual_hash}


def validate_status(conn: sqlite3.Connection) -> dict:
    raw_count = conn.execute("SELECT COUNT(*) FROM daily_price_raw").fetchone()[0]
    status_count = conn.execute("SELECT COUNT(*) FROM daily_security_status").fetchone()[0]
    missing = conn.execute(
        "SELECT COUNT(*) FROM daily_price_raw AS r "
        "LEFT JOIN daily_security_status AS s USING(date, stock_code) "
        "WHERE s.stock_code IS NULL"
    ).fetchone()[0]
    extra = conn.execute(
        "SELECT COUNT(*) FROM daily_security_status AS s "
        "LEFT JOIN daily_price_raw AS r USING(date, stock_code) "
        "WHERE r.stock_code IS NULL"
    ).fetchone()[0]
    trade_status_disagreements = conn.execute(
        "SELECT COUNT(*) FROM daily_price_raw AS r "
        "JOIN daily_security_status AS s USING(date, stock_code) "
        "WHERE r.trade_status <> s.trade_status"
    ).fetchone()[0]
    st_rows = conn.execute(
        "SELECT COUNT(*) FROM daily_security_status WHERE is_st = 1"
    ).fetchone()[0]
    result = {
        "raw_rows": raw_count,
        "status_rows": status_count,
        "missing": missing,
        "extra": extra,
        "trade_status_disagreements": trade_status_disagreements,
        "st_rows": st_rows,
    }
    if missing or extra or trade_status_disagreements:
        raise ValueError(f"daily security status failed reconciliation: {result}")
    downloaded_at = conn.execute(
        "SELECT MAX(downloaded_at) FROM daily_security_status"
    ).fetchone()[0]
    with conn:
        conn.executemany(
            "INSERT OR REPLACE INTO metadata(key, value) VALUES (?, ?)",
            {
                "daily_security_status_source": "baostock_isST",
                "daily_security_status_rows": str(status_count),
                "daily_security_status_st_rows": str(st_rows),
                "daily_security_status_downloaded_at": str(downloaded_at),
            }.items(),
        )
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description="Build historical daily ST status")
    parser.add_argument("--database", type=Path, default=Path("data_v5/csi300_2020_present.sqlite"))
    parser.add_argument("--import-dir", type=Path, default=None)
    parser.add_argument("--export-dir", type=Path, default=Path("data_v5/security_status"))
    parser.add_argument("--retries", type=int, default=3)
    args = parser.parse_args()
    with sqlite3.connect(args.database) as conn:
        conn.executescript(SCHEMA)
        start_date, end_date = conn.execute(
            "SELECT MIN(date), MAX(date) FROM daily_price_raw"
        ).fetchone()
        operation = (
            import_status(conn, args.import_dir)
            if args.import_dir is not None
            else download_status(conn, start_date, end_date, args.retries)
        )
        validation = validate_status(conn)
        archive = archive_status(conn, args.export_dir)
    print(json.dumps({"operation": operation, "validation": validation, "archive": archive}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
