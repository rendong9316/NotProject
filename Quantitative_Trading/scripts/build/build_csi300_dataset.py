"""Build one backtest-ready long table from the validated v5 snapshot."""
from __future__ import annotations

import argparse
import importlib.util
import os
import tempfile
from pathlib import Path

import pandas as pd
ROOT = Path(__file__).resolve().parents[2]



def build(root: Path) -> pd.DataFrame:
    frames = []
    for raw_path in sorted((root / "daily_raw").glob("*.csv")):
        adjusted_path = root / "daily_adjusted" / raw_path.name
        if not adjusted_path.exists():
            raise FileNotFoundError(f"missing adjusted file for {raw_path.name}")
        raw = pd.read_csv(raw_path, dtype={"code": "string", "name": "string"})
        adjusted = pd.read_csv(adjusted_path, dtype={"code": "string"})
        merged = raw.merge(adjusted, on=["date", "code"], how="inner", validate="one_to_one")
        if len(merged) != len(raw) or len(merged) != len(adjusted):
            raise ValueError(f"row mismatch for {raw_path.name}")
        merged["is_tradeable"] = (
            merged["trade_status"].eq(1)
            & merged["volume_shares"].fillna(0).gt(0)
            & merged["amount_cny"].fillna(0).gt(0)
        )
        vwap = merged["amount_cny"] / merged["volume_shares"]
        deviation = pd.concat(
            [(merged["low_raw"] - vwap) / merged["low_raw"],
             (vwap - merged["high_raw"]) / merged["high_raw"]], axis=1,
        ).max(axis=1)
        merged["quality_flag"] = "ok"
        merged.loc[merged["is_tradeable"] & deviation.gt(0.001), "quality_flag"] = "vwap_outside_ohlc"
        frames.append(merged)
    data = pd.concat(frames, ignore_index=True)
    data["date"] = pd.to_datetime(data["date"])
    data = data.sort_values(["date", "code"], kind="stable").reset_index(drop=True)
    if data.duplicated(["date", "code"]).any():
        raise ValueError("duplicate date/code keys in combined data")
    return data


def atomic_csv(frame: pd.DataFrame, path: Path) -> None:
    fd, temp_name = tempfile.mkstemp(prefix=path.stem + ".", suffix=".csv", dir=path.parent)
    os.close(fd)
    try:
        frame.to_csv(temp_name, index=False, encoding="utf-8-sig")
        os.replace(temp_name, path)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path, default=ROOT / "data", nargs="?")
    args = parser.parse_args()
    data = build(args.root)
    csv_path = args.root / "daily_combined.csv"
    atomic_csv(data, csv_path)
    print(f"wrote {len(data):,} rows to {csv_path}")
    if importlib.util.find_spec("pyarrow") or importlib.util.find_spec("fastparquet"):
        parquet_path = args.root / "daily_combined.parquet"
        data.to_parquet(parquet_path, index=False)
        print(f"wrote {parquet_path}")
    else:
        print("Parquet engine unavailable; CSV is the canonical combined output")


if __name__ == "__main__":
    main()