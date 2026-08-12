"""Build a typed SQLite database from the versioned v5 CSV snapshot.

Historical membership is optional.  Without a membership snapshot input the
database contains prices and a current-components snapshot, but its
universe_membership table remains empty by design.
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import sqlite3
import tempfile
from datetime import date, datetime
from pathlib import Path

import pandas as pd
ROOT = Path(__file__).resolve().parents[2]


MEMBERSHIP_COLUMNS = [
    "index_code", "effective_date", "stock_code", "weight",
    "announcement_date", "source", "source_url",
]


SCHEMA = """
PRAGMA foreign_keys = ON;
CREATE TABLE security (
    stock_code TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    exchange TEXT NOT NULL,
    first_seen TEXT,
    last_seen TEXT
);
CREATE TABLE trading_calendar (
    date TEXT PRIMARY KEY,
    is_trading_day INTEGER NOT NULL CHECK (is_trading_day IN (0, 1))
);
CREATE TABLE daily_price_raw (
    date TEXT NOT NULL,
    stock_code TEXT NOT NULL,
    open_raw REAL,
    high_raw REAL,
    low_raw REAL,
    close_raw REAL,
    volume_shares INTEGER,
    amount_cny REAL,
    trade_status INTEGER NOT NULL CHECK (trade_status IN (0, 1)),
    price_source TEXT NOT NULL,
    PRIMARY KEY (date, stock_code)
);
CREATE TABLE daily_price_adjusted (
    date TEXT NOT NULL,
    stock_code TEXT NOT NULL,
    open_adj REAL,
    high_adj REAL,
    low_adj REAL,
    close_adj REAL,
    adjustment_source TEXT NOT NULL,
    PRIMARY KEY (date, stock_code)
);
CREATE TABLE components_snapshot (
    snapshot_date TEXT NOT NULL,
    index_code TEXT NOT NULL,
    stock_code TEXT NOT NULL,
    name TEXT NOT NULL,
    source TEXT NOT NULL,
    PRIMARY KEY (snapshot_date, index_code, stock_code)
);
CREATE TABLE membership_snapshots (
    index_code TEXT NOT NULL,
    effective_date TEXT NOT NULL,
    stock_code TEXT NOT NULL,
    weight REAL,
    announcement_date TEXT,
    source TEXT NOT NULL,
    source_url TEXT,
    PRIMARY KEY (index_code, effective_date, stock_code)
);
CREATE TABLE membership_weights (
    index_code TEXT NOT NULL,
    effective_date TEXT NOT NULL,
    stock_code TEXT NOT NULL,
    weight REAL,
    source TEXT NOT NULL,
    PRIMARY KEY (index_code, effective_date, stock_code)
);
CREATE TABLE membership_weight_intervals (
    index_code TEXT NOT NULL,
    stock_code TEXT NOT NULL,
    valid_from TEXT NOT NULL,
    valid_to TEXT NOT NULL,
    weight REAL,
    source TEXT NOT NULL,
    PRIMARY KEY (index_code, stock_code, valid_from),
    CHECK (valid_from < valid_to)
);
CREATE TABLE universe_membership (
    index_code TEXT NOT NULL,
    stock_code TEXT NOT NULL,
    valid_from TEXT NOT NULL,
    valid_to TEXT NOT NULL,
    weight REAL,
    weight_date TEXT,
    source TEXT NOT NULL,
    PRIMARY KEY (index_code, stock_code, valid_from),
    CHECK (valid_from < valid_to)
);
CREATE TABLE data_quality_issues (
    issue_id INTEGER PRIMARY KEY AUTOINCREMENT,
    table_name TEXT NOT NULL,
    issue_date TEXT,
    stock_code TEXT,
    issue_type TEXT NOT NULL,
    severity TEXT NOT NULL,
    details TEXT NOT NULL
);
CREATE TABLE metadata (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
CREATE INDEX idx_raw_code_date ON daily_price_raw(stock_code, date);
CREATE INDEX idx_adj_code_date ON daily_price_adjusted(stock_code, date);
CREATE INDEX idx_membership_date ON universe_membership(index_code, valid_from, valid_to);
CREATE INDEX idx_membership_code ON universe_membership(stock_code, valid_from);
CREATE INDEX idx_weights_lookup ON membership_weights(index_code, stock_code, effective_date);
CREATE INDEX idx_weight_intervals ON membership_weight_intervals(index_code, valid_from, valid_to, stock_code);
CREATE VIEW daily_universe AS
SELECT
    c.date,
    m.index_code,
    m.stock_code,
    (
      SELECT w.weight FROM membership_weights AS w
      WHERE w.index_code = m.index_code
        AND w.stock_code = m.stock_code
        AND w.effective_date <= c.date
      ORDER BY w.effective_date DESC LIMIT 1
    ) AS weight,
    (
      SELECT w.effective_date FROM membership_weights AS w
      WHERE w.index_code = m.index_code
        AND w.stock_code = m.stock_code
        AND w.effective_date <= c.date
      ORDER BY w.effective_date DESC LIMIT 1
    ) AS weight_date,
    r.open_raw, r.high_raw, r.low_raw, r.close_raw,
    a.open_adj, a.high_adj, a.low_adj, a.close_adj,
    r.volume_shares, r.amount_cny, r.trade_status,
    CASE WHEN r.trade_status = 1
           AND r.volume_shares > 0
           AND r.amount_cny > 0
         THEN 1 ELSE 0 END AS is_tradeable,
    r.price_source,
    a.adjustment_source
FROM trading_calendar AS c
JOIN universe_membership AS m
  ON c.date >= m.valid_from AND c.date < m.valid_to
LEFT JOIN daily_price_raw AS r
  ON r.date = c.date AND r.stock_code = m.stock_code
LEFT JOIN daily_price_adjusted AS a
  ON a.date = c.date AND a.stock_code = m.stock_code;
"""


def exchange_for(code: str) -> str:
    return "SH" if code.startswith(("5", "6")) else "SZ"


def none_if_nan(value):
    return None if pd.isna(value) else value


def read_price_files(root: Path):
    raw_files = sorted((root / "daily_raw").glob("*.csv"))
    if not raw_files:
        raise FileNotFoundError(f"no raw files under {root / 'daily_raw'}")
    securities = {}
    calendar = set()
    raw_rows = []
    adjusted_rows = []
    for raw_path in raw_files:
        adjusted_path = root / "daily_adjusted" / raw_path.name
        if not adjusted_path.exists():
            raise FileNotFoundError(f"missing adjusted file: {adjusted_path}")
        raw = pd.read_csv(raw_path, dtype={"code": "string", "name": "string"})
        adjusted = pd.read_csv(adjusted_path, dtype={"code": "string"})
        if len(raw) != len(adjusted) or not raw["date"].equals(adjusted["date"]):
            raise ValueError(f"raw/adjusted mismatch: {raw_path.name}")
        code = str(raw["code"].iloc[0]).zfill(6)
        name = str(raw["name"].iloc[0])
        dates = raw["date"].astype(str)
        securities[code] = (name, dates.min(), dates.max())
        calendar.update(dates.tolist())
        for row, adj in zip(raw.itertuples(index=False), adjusted.itertuples(index=False)):
            raw_rows.append((
                str(row.date), code, none_if_nan(row.open_raw), none_if_nan(row.high_raw),
                none_if_nan(row.low_raw), none_if_nan(row.close_raw), none_if_nan(row.volume_shares),
                none_if_nan(row.amount_cny), int(row.trade_status), "baostock",
            ))
            adjusted_rows.append((
                str(adj.date), code, none_if_nan(adj.open_adj), none_if_nan(adj.high_adj),
                none_if_nan(adj.low_adj), none_if_nan(adj.close_adj), "baostock_qfq",
            ))
    return securities, calendar, raw_rows, adjusted_rows


def read_membership(path: Path | None):
    if path is None:
        return [], [], [], []
    frame = pd.read_csv(path, dtype={"index_code": "string", "stock_code": "string", "source": "string"})
    missing = [c for c in MEMBERSHIP_COLUMNS if c not in frame.columns]
    if missing:
        raise ValueError(f"membership file missing columns: {missing}")
    frame["index_code"] = frame["index_code"].astype(str).str.strip()
    frame["stock_code"] = frame["stock_code"].astype(str).str.strip().str.replace(r"\.0$", "", regex=True).str.zfill(6)
    frame["effective_date"] = pd.to_datetime(frame["effective_date"], errors="coerce").dt.strftime("%Y-%m-%d")
    frame["weight"] = pd.to_numeric(frame["weight"], errors="coerce")
    if frame["effective_date"].isna().any() or frame.duplicated(["index_code", "effective_date", "stock_code"]).any():
        raise ValueError("membership dates or keys are invalid")
    if (~frame["stock_code"].str.fullmatch(r"\d{6}")).any() or frame["source"].isna().any():
        raise ValueError("membership codes or sources are invalid")
    snapshot_sizes = frame.groupby(["index_code", "effective_date"])["stock_code"].nunique()
    if not snapshot_sizes.eq(300).all():
        bad = snapshot_sizes[~snapshot_sizes.eq(300)].to_dict()
        raise ValueError(f"membership snapshots must contain 300 unique stocks: {bad}")
    snapshots = [tuple(None if pd.isna(row[c]) else row[c] for c in MEMBERSHIP_COLUMNS) for _, row in frame.iterrows()]
    weights = [(r[0], r[1], r[2], r[3], r[5]) for r in snapshots]
    weight_intervals = []
    for index_code, index_frame in frame.groupby("index_code"):
        dates = sorted(index_frame["effective_date"].unique())
        for i, effective_date in enumerate(dates):
            valid_to = dates[i + 1] if i + 1 < len(dates) else "9999-12-31"
            for row in index_frame[index_frame.effective_date.eq(effective_date)].itertuples(index=False):
                weight_intervals.append((
                    index_code, row.stock_code, effective_date, valid_to,
                    none_if_nan(row.weight), str(row.source),
                ))
    intervals = []
    for index_code, index_frame in frame.groupby("index_code"):
        dates = sorted(index_frame["effective_date"].unique())
        sets = {d: set(index_frame.loc[index_frame.effective_date == d, "stock_code"]) for d in dates}
        for stock_code in sorted(index_frame["stock_code"].unique()):
            group = index_frame[index_frame.stock_code.eq(stock_code)]
            start = None
            for i, effective_date in enumerate(dates):
                present = stock_code in sets[effective_date]
                if present and start is None:
                    start = effective_date
                next_date = dates[i + 1] if i + 1 < len(dates) else "9999-12-31"
                if start is not None and (not present or next_date == "9999-12-31"):
                    end = effective_date if not present else next_date
                    if start < end:
                        first_weight = group.loc[group.effective_date.eq(start), "weight"]
                        weight = none_if_nan(first_weight.iloc[0]) if len(first_weight) else None
                        intervals.append((index_code, stock_code, start, end, weight, start, "membership_snapshot"))
                    start = None if not present else start
    return snapshots, weights, weight_intervals, intervals


def build_database(root: Path, output: Path, membership_path: Path | None) -> dict:
    securities, calendar, raw_rows, adjusted_rows = read_price_files(root)
    snapshots, weights, weight_intervals, intervals = read_membership(membership_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(prefix=output.stem + ".", suffix=".sqlite.tmp", dir=output.parent)
    os.close(fd)
    try:
        with sqlite3.connect(temp_name) as conn:
            conn.executescript(SCHEMA)
            conn.executemany("INSERT INTO security VALUES (?, ?, ?, ?, ?)", [(c, n, exchange_for(c), first, last) for c, (n, first, last) in securities.items()])
            conn.executemany("INSERT INTO trading_calendar VALUES (?, 1)", [(d,) for d in sorted(calendar)])
            conn.executemany("INSERT INTO daily_price_raw VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", raw_rows)
            conn.executemany("INSERT INTO daily_price_adjusted VALUES (?, ?, ?, ?, ?, ?, ?)", adjusted_rows)
            manifest_path = root / "manifest.json"
            if manifest_path.exists():
                snapshot_date = json.loads(manifest_path.read_text(encoding="utf-8")).get("created_at", date.today().isoformat())[:10]
            else:
                snapshot_date = date.today().isoformat()
            components_path = root / "components.csv"
            if components_path.exists():
                components = pd.read_csv(components_path, dtype={"code": "string", "name": "string"})
                conn.executemany("INSERT INTO components_snapshot VALUES (?, '000300', ?, ?, ?)", [(snapshot_date, str(r.code).zfill(6), str(r.name), "akshare") for r in components.itertuples(index=False)])
            conn.executemany("INSERT INTO membership_snapshots VALUES (?, ?, ?, ?, ?, ?, ?)", snapshots)
            conn.executemany("INSERT INTO membership_weights VALUES (?, ?, ?, ?, ?)", weights)
            conn.executemany("INSERT INTO membership_weight_intervals VALUES (?, ?, ?, ?, ?, ?)", weight_intervals)
            conn.executemany("INSERT INTO universe_membership VALUES (?, ?, ?, ?, ?, ?, ?)", intervals)
            if not snapshots:
                conn.execute(
                    "INSERT INTO data_quality_issues(table_name, issue_type, severity, details) VALUES (?, ?, ?, ?)",
                    ("universe_membership", "membership_missing", "blocker", "Historical membership snapshots have not been loaded"),
                )
            snapshot_sources = sorted({str(row[5]) for row in snapshots})
            if any("unverified" in source for source in snapshot_sources):
                conn.execute(
                    "INSERT INTO data_quality_issues(table_name, issue_type, severity, details) VALUES (?, ?, ?, ?)",
                    ("membership_snapshots", "membership_source_unverified", "warning", "Baostock snapshots require official announcement verification"),
                )
            if snapshots and all(row[3] is None for row in snapshots):
                conn.execute(
                    "INSERT INTO data_quality_issues(table_name, issue_type, severity, details) VALUES (?, ?, ?, ?)",
                    ("membership_weights", "weights_missing", "warning", "Free membership snapshots do not provide rebalance weights"),
                )
            missing_members = sorted({row[2] for row in snapshots} - set(securities))
            for code in missing_members:
                conn.execute(
                    "INSERT INTO data_quality_issues(table_name, stock_code, issue_type, severity, details) VALUES (?, ?, ?, ?, ?)",
                    ("daily_price_raw", code, "historical_price_missing", "blocker", "Membership stock has no imported price history"),
                )
            missing_daily_rows = conn.execute(
                "SELECT date, stock_code FROM daily_universe WHERE close_raw IS NULL OR close_adj IS NULL"
            ).fetchall()
            for missing_date, code in missing_daily_rows:
                conn.execute(
                    "INSERT INTO data_quality_issues(table_name, issue_date, stock_code, issue_type, severity, details) VALUES (?, ?, ?, ?, ?, ?)",
                    ("daily_universe", missing_date, code, "member_price_missing", "warning", "Member retained with NULL price and is_tradeable=0"),
                )
            quality_path = root / "data_quality_report.json"
            if quality_path.exists():
                quality = json.loads(quality_path.read_text(encoding="utf-8"))
                for item in quality.get("vwap_outliers", []):
                    conn.execute(
                        "INSERT INTO data_quality_issues(table_name, issue_date, stock_code, issue_type, severity, details) VALUES (?, ?, ?, ?, ?, ?)",
                        ("daily_price_raw", item.get("date"), item.get("file", "")[:6], "vwap_outside_ohlc", "warning", json.dumps(item, ensure_ascii=False)),
                    )
            metadata = {
                "dataset_root": str(root), "price_rows": str(len(raw_rows)),
                "security_count": str(len(securities)), "membership_loaded": str(bool(snapshots)),
                "membership_source": str(membership_path or "none"),
                "membership_snapshot_sources": json.dumps(snapshot_sources, ensure_ascii=False),
                "membership_officially_verified": str(bool(snapshots) and not any("unverified" in source for source in snapshot_sources)),
            }
            conn.executemany("INSERT INTO metadata VALUES (?, ?)", metadata.items())
            conn.commit()
        conn.close()
        os.replace(temp_name, output)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)
    return {
        "securities": len(securities), "price_rows": len(raw_rows),
        "membership_rows": len(snapshots), "weight_intervals": len(weight_intervals),
        "intervals": len(intervals),
        "missing_member_prices": len(missing_members),
        "missing_daily_prices": len(missing_daily_rows),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT / "data")
    parser.add_argument("--output", type=Path, default=ROOT / "data" / "csi300_2010_present.sqlite")
    parser.add_argument("--membership", type=Path, default=None)
    args = parser.parse_args()
    result = build_database(args.root, args.output, args.membership)
    if args.membership:
        membership_frame = pd.read_csv(args.membership, dtype={"source": "string"})
        sources = sorted(membership_frame["source"].dropna().astype(str).unique())
        officially_verified = not any("unverified" in source for source in sources)
        weights_available = pd.to_numeric(membership_frame["weight"], errors="coerce").notna().any()
    else:
        sources, officially_verified, weights_available = [], False, False
    manifest = {
        "created_at": datetime.now().isoformat(timespec="seconds"),
        "database": str(args.output), "dataset_root": str(args.root),
        "membership_input": str(args.membership or "none"), "result": result,
        "membership_snapshot_sources": sources,
        "membership_officially_verified": officially_verified,
        "weights_available": bool(weights_available),
    }
    (args.output.parent / "database_manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()