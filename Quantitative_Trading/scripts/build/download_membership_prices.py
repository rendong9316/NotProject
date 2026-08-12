"""Download price histories for membership stocks missing from the v5 archive."""
from __future__ import annotations

import argparse
import json
import random
import time
from datetime import datetime
from pathlib import Path

import baostock as bs
import pandas as pd

from csi300_download_v5 import atomic_csv, download_one
ROOT = Path(__file__).resolve().parents[2]



def existing_codes(root: Path) -> set[str]:
    return {p.name.split("_", 1)[0] for p in (root / "daily_raw").glob("*.csv")}


def repair_numeric_names(root: Path, stocks: pd.DataFrame) -> int:
    repaired = 0
    names = dict(zip(stocks["code"], stocks["name"].astype(str)))
    for code, name in names.items():
        raw_matches = list((root / "daily_raw").glob(f"{code}_*.csv"))
        adjusted_matches = list((root / "daily_adjusted").glob(f"{code}_*.csv"))
        if len(raw_matches) != 1 or len(adjusted_matches) != 1:
            continue
        raw_path, adjusted_path = raw_matches[0], adjusted_matches[0]
        raw_suffix = raw_path.stem.split("_", 1)[1]
        adjusted_suffix = adjusted_path.stem.split("_", 1)[1]
        if not raw_suffix.isdigit() or not adjusted_suffix.isdigit():
            continue
        frame = pd.read_csv(raw_path, dtype={"code": "string", "name": "string"})
        frame["name"] = name
        new_raw = root / "daily_raw" / f"{code}_{name}.csv"
        new_adjusted = root / "daily_adjusted" / f"{code}_{name}.csv"
        atomic_csv(frame, new_raw)
        adjusted_path.replace(new_adjusted)
        raw_path.unlink()
        repaired += 1
    return repaired


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT / "data")
    parser.add_argument("--membership", type=Path, default=ROOT / "data" / "membership_snapshots.csv")
    parser.add_argument("--start-date", default="2020-01-01")
    parser.add_argument("--end-date", default="2026-08-05")
    parser.add_argument("--delay", type=float, default=0.2)
    args = parser.parse_args()
    frame = pd.read_csv(args.membership, dtype={"stock_code": "string", "stock_name": "string"})
    stocks = frame.sort_values("effective_date").drop_duplicates("stock_code", keep="last")
    stocks = stocks[["stock_code", "stock_name"]].rename(columns={"stock_code": "code", "stock_name": "name"})
    stocks["code"] = stocks["code"].astype(str).str.zfill(6)
    repaired = repair_numeric_names(args.root, stocks)
    if repaired:
        print(f"repaired {repaired} numeric stock names", flush=True)
    present = existing_codes(args.root)
    missing = stocks[~stocks.code.isin(present)].reset_index(drop=True)
    manifest = {
        "created_at": datetime.now().isoformat(timespec="seconds"),
        "start_date": args.start_date, "end_date": args.end_date,
        "target_stock_count": len(stocks), "covered_before_run": len(set(stocks.code) & present),
        "requested": len(missing), "results": [],
    }
    login = bs.login()
    if login.error_code != "0":
        raise RuntimeError(login.error_msg)
    try:
        for i, row in missing.iterrows():
            code, name = row["code"], str(row["name"])
            try:
                result = download_one(code, name, args.start_date, args.end_date, args.root, overwrite=False)
                result["name"] = name
                print(f"[{i + 1}/{len(missing)}] {code} {name}: {result['status']}", flush=True)
            except Exception as exc:
                result = {"code": code, "name": name, "status": "failed", "error": str(exc)}
                print(f"[{i + 1}/{len(missing)}] {code} {name}: FAILED {exc}", flush=True)
            manifest["results"].append(result)
            time.sleep(random.uniform(args.delay * 0.8, args.delay * 1.2))
    finally:
        bs.logout()
    manifest["final_raw_files"] = len(list((args.root / "daily_raw").glob("*.csv")))
    manifest["final_adjusted_files"] = len(list((args.root / "daily_adjusted").glob("*.csv")))
    final_codes = existing_codes(args.root)
    manifest["covered_after_run"] = len(set(stocks.code) & final_codes)
    manifest["missing_after_run"] = sorted(set(stocks.code) - final_codes)
    path = args.root / "historical_price_manifest.json"
    path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"saved {path}")


if __name__ == "__main__":
    main()