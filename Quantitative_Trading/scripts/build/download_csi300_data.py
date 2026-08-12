"""Download a reproducible CSI 300 daily data snapshot.

The output keeps raw and forward-adjusted prices separate.  It never fills
missing prices and records zero-volume rows as non-trading rows.
"""
from __future__ import annotations

import argparse
import json
import os
import random
import re
import tempfile
import time
from datetime import date, datetime, timedelta
from pathlib import Path

import akshare as ak
import baostock as bs
import pandas as pd
ROOT = Path(__file__).resolve().parents[2]


START_DATE = "2020-01-01"
DEFAULT_OUTPUT_ROOT = ROOT / "data"
RAW_ADJUSTFLAG = "3"  # Baostock's currently observed raw-price response.
ADJUSTED_ADJUSTFLAG = "2"  # Forward adjusted (qfq) prices.
FIELDS_WITH_STATUS = "date,open,high,low,close,volume,amount,tradestatus"
FIELDS_WITHOUT_STATUS = "date,open,high,low,close,volume,amount"
PRICE_COLUMNS = ["open", "high", "low", "close"]


def normalize_components(frame: pd.DataFrame) -> pd.DataFrame:
    """Normalize the current component snapshot without losing leading zeros."""
    stocks = frame[["成分券代码", "成分券名称"]].dropna().copy()
    stocks.columns = ["code", "name"]
    stocks["code"] = stocks["code"].astype(str).str.strip().str.replace(r"\.0$", "", regex=True).str.zfill(6)
    stocks["name"] = stocks["name"].astype(str).str.strip()
    stocks = stocks[stocks["code"].str.fullmatch(r"\d{6}")]
    return stocks.drop_duplicates("code").sort_values("code").reset_index(drop=True)


def get_components() -> pd.DataFrame:
    frame = ak.index_stock_cons_weight_csindex(symbol="000300")
    stocks = normalize_components(frame)
    if len(stocks) != 300:
        raise RuntimeError(f"expected 300 components, got {len(stocks)}")
    return stocks


def baostock_code(code: str) -> str:
    return f"sh.{code}" if code.startswith(("5", "6", "68")) else f"sz.{code}"


def query_stock(code: str, start_date: str, end_date: str, adjustflag: str) -> pd.DataFrame:
    """Query one stock, falling back when the status field is unavailable."""
    result = None
    for attempt in range(3):
        result = bs.query_history_k_data_plus(
            baostock_code(code), FIELDS_WITH_STATUS, start_date=start_date,
            end_date=end_date, frequency="d", adjustflag=adjustflag,
        )
        if result.error_code == "0":
            break
        if result.error_code == "10001001" or "未登录" in result.error_msg:
            login = bs.login()
            if login.error_code != "0":
                raise RuntimeError(f"re-login failed: {login.error_msg}")
            time.sleep(0.2 * (attempt + 1))
            continue
        if "tradestatus" in result.error_msg.lower():
            result = bs.query_history_k_data_plus(
                baostock_code(code), FIELDS_WITHOUT_STATUS, start_date=start_date,
                end_date=end_date, frequency="d", adjustflag=adjustflag,
            )
        break
    assert result is not None
    if result.error_code != "0":
        raise RuntimeError(f"{result.error_code}: {result.error_msg}")
    frame = result.get_data()
    if frame is None or frame.empty:
        raise RuntimeError("empty response")
    for col in PRICE_COLUMNS + ["volume", "amount"]:
        frame[col] = pd.to_numeric(frame[col], errors="coerce")
    if "tradestatus" in frame:
        frame["trade_status"] = pd.to_numeric(frame.pop("tradestatus"), errors="coerce")
    else:
        frame["trade_status"] = ((frame["volume"] > 0) & (frame["amount"] > 0)).astype("Int8")
    frame["date"] = pd.to_datetime(frame["date"], errors="coerce").dt.strftime("%Y-%m-%d")
    frame["volume"] = frame["volume"].round().astype("Int64")
    frame["trade_status"] = frame["trade_status"].fillna(0).astype("Int8")
    return frame[["date"] + PRICE_COLUMNS + ["volume", "amount", "trade_status"]]


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


def download_one(code: str, name: str, start_date: str, end_date: str, root: Path, overwrite: bool) -> dict:
    raw_path = root / "daily_raw" / f"{code}_{name}.csv"
    adjusted_path = root / "daily_adjusted" / f"{code}_{name}.csv"
    if raw_path.exists() and adjusted_path.exists() and not overwrite:
        return {"code": code, "status": "skipped"}
    raw = query_stock(code, start_date, end_date, RAW_ADJUSTFLAG)
    adjusted = query_stock(code, start_date, end_date, ADJUSTED_ADJUSTFLAG)
    if not raw["date"].equals(adjusted["date"]):
        raise RuntimeError("raw and adjusted date ranges differ")
    raw = raw.rename(columns={c: f"{c}_raw" for c in PRICE_COLUMNS})
    raw = raw.rename(columns={"volume": "volume_shares", "amount": "amount_cny"})
    raw.insert(1, "code", code)
    raw.insert(2, "name", name)
    adjusted = adjusted.rename(columns={c: f"{c}_adj" for c in PRICE_COLUMNS})
    adjusted = adjusted[["date"] + [f"{c}_adj" for c in PRICE_COLUMNS]]
    adjusted.insert(1, "code", code)
    atomic_csv(raw, raw_path)
    atomic_csv(adjusted, adjusted_path)
    return {"code": code, "rows": len(raw), "status": "downloaded"}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--start-date", default=START_DATE)
    parser.add_argument("--end-date", default=(date.today() - timedelta(days=1)).isoformat())
    parser.add_argument("--output-root", type=Path, default=DEFAULT_OUTPUT_ROOT)
    parser.add_argument("--max-stocks", type=int, default=0, help="only download the first N stocks")
    parser.add_argument("--overwrite", action="store_true")
    parser.add_argument("--delay", type=float, default=0.2)
    args = parser.parse_args()
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", args.end_date):
        raise ValueError("end date must be YYYY-MM-DD")
    stocks = get_components()
    if args.max_stocks > 0:
        stocks = stocks.head(args.max_stocks)
    args.output_root.mkdir(parents=True, exist_ok=True)
    manifest = {
        "created_at": datetime.now().isoformat(timespec="seconds"),
        "start_date": args.start_date,
        "end_date": args.end_date,
        "raw_adjustflag": RAW_ADJUSTFLAG,
        "adjusted_adjustflag": ADJUSTED_ADJUSTFLAG,
        "component_count": len(stocks),
        "results": [],
    }
    login = bs.login()
    if login.error_code != "0":
        raise RuntimeError(login.error_msg)
    try:
        for i, row in stocks.iterrows():
            code, name = row["code"], row["name"]
            try:
                result = download_one(code, name, args.start_date, args.end_date, args.output_root, args.overwrite)
                print(f"[{i + 1}/{len(stocks)}] {code} {name}: {result['status']}", flush=True)
            except Exception as exc:
                result = {"code": code, "name": name, "status": "failed", "error": str(exc)}
                print(f"[{i + 1}/{len(stocks)}] {code} {name}: FAILED {exc}", flush=True)
            manifest["results"].append(result)
            time.sleep(random.uniform(args.delay * 0.8, args.delay * 1.2))
    finally:
        bs.logout()
    atomic_csv(pd.DataFrame(stocks), args.output_root / "components.csv")
    raw_files = list((args.output_root / "daily_raw").glob("*.csv"))
    adjusted_files = list((args.output_root / "daily_adjusted").glob("*.csv"))
    manifest["final_raw_files"] = len(raw_files)
    manifest["final_adjusted_files"] = len(adjusted_files)
    manifest["complete_pairs"] = len({p.name for p in raw_files} & {p.name for p in adjusted_files})
    (args.output_root / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"saved manifest: {args.output_root / 'manifest.json'}")


if __name__ == "__main__":
    main()