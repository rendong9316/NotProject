"""Download point-in-time CSI 300 membership snapshots from Baostock."""
from __future__ import annotations

import argparse
import os
import tempfile
import time
from pathlib import Path

import baostock as bs
import pandas as pd
ROOT = Path(__file__).resolve().parents[2]



def query_snapshot(query_date: str) -> tuple[str, pd.DataFrame]:
    for attempt in range(3):
        result = bs.query_hs300_stocks(query_date)
        if result.error_code == "0":
            frame = result.get_data()
            if len(frame) != 300:
                raise RuntimeError(f"{query_date}: expected 300 stocks, got {len(frame)}")
            update_dates = frame["updateDate"].dropna().unique()
            if len(update_dates) != 1:
                raise RuntimeError(f"{query_date}: ambiguous update dates {update_dates}")
            return str(update_dates[0]), frame
        if result.error_code == "10001001" or "未登录" in result.error_msg:
            login = bs.login()
            if login.error_code != "0":
                raise RuntimeError(f"re-login failed: {login.error_msg}")
            time.sleep(0.3 * (attempt + 1))
            continue
        raise RuntimeError(f"{query_date}: {result.error_code} {result.error_msg}")
    raise RuntimeError(f"{query_date}: retries exhausted")


def atomic_csv(frame: pd.DataFrame, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(prefix=path.stem + ".", suffix=".tmp", dir=path.parent)
    os.close(fd)
    try:
        frame.to_csv(temp_name, index=False, encoding="utf-8-sig")
        os.replace(temp_name, path)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)


def collapse_unchanged_snapshots(frame: pd.DataFrame) -> pd.DataFrame:
    keep_dates = []
    previous_codes = None
    for effective_date, group in frame.groupby("effective_date", sort=True):
        codes = tuple(sorted(group["stock_code"].astype(str)))
        if codes != previous_codes:
            keep_dates.append(effective_date)
        previous_codes = codes
    return frame[frame["effective_date"].isin(keep_dates)].sort_values(
        ["effective_date", "stock_code"]
    ).reset_index(drop=True)


def collect(calendar: list[str], start_date: str, end_date: str) -> pd.DataFrame:
    eligible = [d for d in calendar if start_date <= d <= end_date]
    if not eligible:
        raise ValueError("calendar contains no dates in the requested range")
    snapshots = {}
    query_date = eligible[-1]
    while True:
        effective_date, frame = query_snapshot(query_date)
        if effective_date not in snapshots:
            snapshots[effective_date] = frame.copy()
            print(f"snapshot {effective_date}: {len(frame)} stocks", flush=True)
        previous = [d for d in calendar if d < effective_date]
        if effective_date < start_date or not previous:
            break
        query_date = previous[-1]
    rows = []
    for effective_date, frame in snapshots.items():
        for row in frame.itertuples(index=False):
            code = str(row.code).split(".")[-1].zfill(6)
            rows.append({
                "index_code": "000300", "effective_date": effective_date,
                "stock_code": code, "stock_name": str(row.code_name), "weight": None,
                "announcement_date": None, "source": "baostock_history_snapshot_unverified",
                "source_url": "http://baostock.com",
            })
    return collapse_unchanged_snapshots(pd.DataFrame(rows))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT / "data")
    parser.add_argument("--start-date", default="2020-01-01")
    parser.add_argument("--end-date", default="2026-08-05")
    parser.add_argument("--output", type=Path, default=ROOT / "data" / "membership_snapshots.csv")
    args = parser.parse_args()
    combined = pd.read_csv(args.root / "daily_combined.csv", usecols=["date"])
    calendar = sorted(combined["date"].astype(str).unique())
    login = bs.login()
    if login.error_code != "0":
        raise RuntimeError(login.error_msg)
    try:
        snapshots = collect(calendar, args.start_date, args.end_date)
    finally:
        bs.logout()
    atomic_csv(snapshots, args.output)
    print(f"wrote {len(snapshots)} rows across {snapshots.effective_date.nunique()} snapshots to {args.output}")


if __name__ == "__main__":
    main()