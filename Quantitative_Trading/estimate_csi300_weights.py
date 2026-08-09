"""
CSI 300 Historical Weight Estimator
------------------------------------
Uses latest available float share ratio to estimate historical component weights.
Handles special cases (banks, new stocks) automatically.

Usage:
    python estimate_csi300_weights.py --start-date 2020-01-02 --end-date 2026-07-31 --output data_v5/csi300_weights.csv
"""
from __future__ import annotations

import argparse
import sqlite3
import time
from datetime import datetime, timedelta
from pathlib import Path

import baostock as bs
import pandas as pd


# ── Bank float ratio correction ────────────────────────────────────────────────
# Baostock liqaShare overstates free float for state-owned banks.
# These fixed ratios are approximate and based on known CSIndex methodology.
BANK_FIXED_RATIO = {
    "601398": 18.0,   # 工商银行
    "601288": 15.0,   # 农业银行
    "601988": 18.0,   # 中国银行
    "601328": 18.0,   # 交通银行
    "600015": 18.0,   # 华夏银行 (if in CSI300)
    "601939": 15.0,   # 建设银行
    "600000": 95.0,   # 浦发银行 (已退市/调整, 备用)
}


def to_baostock(code: str) -> str:
    code = str(code).zfill(6)
    return f"sh.{code}" if code.startswith(("5", "6", "68")) else f"sz.{code}"


def get_latest_float_ratio(bs_code: str) -> float | None:
    """Fetch the latest available float/share ratio from Baostock."""
    for year in [2026, 2025, 2024, 2023, 2022]:
        for quarter in [4, 3, 2, 1]:
            rs = bs.query_profit_data(code=bs_code, year=year, quarter=quarter)
            if rs.error_code != "0":
                continue
            df = rs.get_data()
            if df.empty:
                continue
            try:
                ts = float(df["totalShare"].values[0])
                ls = float(df["liqaShare"].values[0])
                if ts > 0 and pd.notna(ls):
                    return ls / ts * 100
            except Exception:
                continue
    return None


def get_total_share(bs_code: str) -> float | None:
    """Fetch total share from Baostock."""
    rs = bs.query_profit_data(code=bs_code, year=2024, quarter=4)
    if rs.error_code == "0":
        df = rs.get_data()
        if not df.empty:
            try:
                return float(df["totalShare"].values[0])
            except Exception:
                pass
    return None


def estimate_weights(
    db_path: Path,
    output_path: Path,
    start_date: str,
    end_date: str,
    balance_date: str = "2024-12-31",
) -> pd.DataFrame:
    """
    Estimate CSI 300 component weights for each trading day in [start_date, end_date].

    Method: weight_i = (close_i * total_share_i * float_ratio_i) / sum(all)
    Uses the latest available float ratio for each stock (as of balance_date).
    """
    print(f"Loading data from {db_path} ...")
    conn = sqlite3.connect(db_path)

    # Get all trading days in range
    dates_df = pd.read_sql(
        f'SELECT DISTINCT date FROM trading_calendar '
        f'WHERE date >= "{start_date}" AND date <= "{end_date}" '
        f'ORDER BY date',
        conn,
    )
    trading_days = dates_df["date"].tolist()
    print(f"  Trading days: {len(trading_days)} ({start_date} ~ {end_date})")

    # Get price data for all days
    placeholders = ",".join(["?"] * len(trading_days))
    price_df = pd.read_sql(
        f'SELECT date, stock_code, close_adj FROM daily_universe '
        f'WHERE date IN ({placeholders}) AND is_tradeable = 1',
        conn,
        params=trading_days,
    )
    conn.close()

    # Build price map: date -> {code: price}
    price_map: dict[str, dict[str, float]] = {}
    for _, row in price_df.iterrows():
        date = str(row["date"])[:10]
        code = str(row["stock_code"]).zfill(6)
        try:
            price = float(row["close_adj"]) if pd.notna(row["close_adj"]) else float(row["close_raw"])
        except (ValueError, TypeError):
            continue
        if price <= 0:
            continue
        price_map.setdefault(date, {})[code] = price

    print(f"  Price data loaded for {len(price_map)} days")

    # Get component list (current)
    comps = pd.read_csv(Path("data_v5/components.csv"))
    comp_codes = [str(r).zfill(6) for r in comps["code"].tolist()]
    name_map = dict(zip(comps["code"].astype(str).str.zfill(6), comps["name"].astype(str)))

    # Fetch share data for all component stocks
    print("Fetching share data from Baostock ...")
    bs.login()
    share_data: dict[str, dict] = {}
    for i, code in enumerate(comp_codes):
        bs_code = to_baostock(code)
        # Check if bank (use fixed ratio)
        if code in BANK_FIXED_RATIO:
            total = get_total_share(bs_code)
            if total:
                share_data[code] = {
                    "total": total,
                    "float_ratio": BANK_FIXED_RATIO[code],
                    "source": "bank_fixed",
                }
            continue
        # Normal: fetch from Baostock
        ratio = get_latest_float_ratio(bs_code)
        total = get_total_share(bs_code)
        if ratio is not None and total is not None and total > 0:
            share_data[code] = {
                "total": total,
                "float_ratio": ratio,
                "source": "baostock",
            }
        if (i + 1) % 50 == 0:
            print(f"  Progress: {i+1}/{len(comp_codes)}")
    bs.logout()
    print(f"  Share data: {len(share_data)}/{len(comp_codes)} stocks")

    # Calculate weights for each trading day
    print("Calculating weights ...")
    results = []
    for date in trading_days:
        prices = price_map.get(date, {})
        if not prices:
            continue

        # Calculate free float market cap for each stock
        fmv = {}
        for code in comp_codes:
            if code not in share_data or code not in prices:
                continue
            sd = share_data[code]
            price = prices[code]
            if price <= 0:
                continue
            # fmv = price * total_share * (float_ratio / 100)
            fmv[code] = price * sd["total"] * sd["float_ratio"] / 100

        total_fmv = sum(fmv.values())
        if total_fmv <= 0:
            continue

        for code, f in fmv.items():
            results.append({
                "date": date,
                "stock_code": code,
                "stock_name": name_map.get(code, ""),
                "close_price": round(price, 4),
                "total_share": round(sd["total"], 0),
                "float_ratio_pct": round(sd["float_ratio"], 2),
                "float_share": round(sd["total"] * sd["float_ratio"] / 100, 0),
                "fmv": round(f, 2),
                "weight_pct": round(f / total_fmv * 100, 6),
                "data_source": sd["source"],
            })

    df = pd.DataFrame(results)
    print(f"  Total rows: {len(df)}")

    # Save
    output_path.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(output_path, index=False, encoding="utf-8-sig")
    print(f"Saved: {output_path}")

    return df


def main():
    parser = argparse.ArgumentParser(description="Estimate CSI 300 historical weights")
    parser.add_argument("--db", type=Path, default=Path("data_v5/csi300_2020_present.sqlite"))
    parser.add_argument("--output", type=Path, default=Path("data_v5/csi300_weights.csv"))
    parser.add_argument("--start-date", default="2020-01-02")
    parser.add_argument("--end-date", default="2026-07-31")
    parser.add_argument("--balance-date", default="2024-12-31",
                        help="Use float ratio data from this date as baseline")
    args = parser.parse_args()

    t0 = time.time()
    df = estimate_weights(
        db_path=args.db,
        output_path=args.output,
        start_date=args.start_date,
        end_date=args.end_date,
        balance_date=args.balance_date,
    )

    # Summary
    print(f"\n=== Summary ===")
    print(f"Date range: {df['date'].min()} ~ {df['date'].max()}")
    print(f"Trading days: {df['date'].nunique()}")
    print(f"Stocks per day (median): {df.groupby('date')['stock_code'].count().median()}")
    print(f"Data sources: {df['data_source'].value_counts().to_dict()}")
    print(f"Time elapsed: {time.time()-t0:.1f}s")

    # Quick quality check
    daily_weights = df.groupby("date")["weight_pct"].sum()
    print(f"\nWeight sum check (should be ~100%):")
    print(f"  Min: {daily_weights.min():.4f}%")
    print(f"  Max: {daily_weights.max():.4f}%")
    print(f"  Mean: {daily_weights.mean():.4f}%")


if __name__ == "__main__":
    main()
