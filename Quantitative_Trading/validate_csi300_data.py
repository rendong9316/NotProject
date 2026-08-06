"""Independent validator for the versioned CSI 300 data snapshot."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd


def validate(root: Path) -> dict:
    raw_files = sorted((root / "daily_raw").glob("*.csv"))
    adjusted_files = sorted((root / "daily_adjusted").glob("*.csv"))
    report = {
        "raw_files": len(raw_files), "adjusted_files": len(adjusted_files),
        "errors": [], "warnings": [], "vwap_outliers": [],
    }
    expected_raw = ["date", "code", "name", "open_raw", "high_raw", "low_raw", "close_raw", "volume_shares", "amount_cny", "trade_status"]
    expected_adjusted = ["date", "code", "open_adj", "high_adj", "low_adj", "close_adj"]
    for path in raw_files:
        try:
            code = path.name.split("_", 1)[0]
            frame = pd.read_csv(path, dtype={"code": "string", "name": "string"})
            if list(frame.columns) != expected_raw:
                report["errors"].append({"file": path.name, "issue": "schema"})
                continue
            nums = frame[["open_raw", "high_raw", "low_raw", "close_raw", "volume_shares", "amount_cny"]].apply(pd.to_numeric, errors="coerce")
            dates = pd.to_datetime(frame["date"], errors="coerce")
            traded = frame.trade_status.eq(1)
            prices = nums[["open_raw", "high_raw", "low_raw", "close_raw"]]
            bad = {
                "date": int(dates.isna().sum() + dates.duplicated().sum() + (dates.diff().dropna() <= pd.Timedelta(0)).sum()),
                "price_numeric": int((~np.isfinite(prices)).sum().sum()),
                "trade_numeric": int((traded & (~np.isfinite(nums.volume_shares) | ~np.isfinite(nums.amount_cny))).sum()),
                "ohlc": int(((nums.high_raw < nums.low_raw) | (nums.high_raw < nums.open_raw) | (nums.high_raw < nums.close_raw) | (nums.low_raw > nums.open_raw) | (nums.low_raw > nums.close_raw)).sum()),
                "nonpositive_price": int((prices <= 0).sum().sum()),
                "negative_trade_data": int(((nums[["volume_shares", "amount_cny"]] < 0).any(axis=1)).sum()),
                "trade_status": int((~frame.trade_status.isin([0, 1])).sum()),
                "code": int(frame.code.dropna().astype(str).map(lambda x: x.zfill(6)).ne(code).any()),
            }
            for issue, count in bad.items():
                if count:
                    report["errors"].append({"file": path.name, "issue": issue, "count": count})
            report.setdefault("rows", 0); report["rows"] += len(frame)
            report.setdefault("suspended_rows", 0); report["suspended_rows"] += int((~traded).sum())
            valid = traded & (nums.volume_shares > 0) & (nums.amount_cny > 0)
            vwap = nums.amount_cny / nums.volume_shares
            deviation = pd.concat(
                [(nums.low_raw - vwap) / nums.low_raw, (vwap - nums.high_raw) / nums.high_raw], axis=1
            ).max(axis=1)
            for i in frame.index[valid & (deviation > 0.001)]:
                report["vwap_outliers"].append({
                    "file": path.name, "date": frame.at[i, "date"],
                    "vwap": float(vwap.at[i]), "low": float(nums.at[i, "low_raw"]),
                    "high": float(nums.at[i, "high_raw"]), "deviation": float(deviation.at[i]),
                })
            adjusted_path = root / "daily_adjusted" / path.name
            if not adjusted_path.exists():
                continue
            adjusted = pd.read_csv(adjusted_path, dtype={"code": "string"})
            if list(adjusted.columns) != expected_adjusted:
                report["errors"].append({"file": adjusted_path.name, "issue": "adjusted_schema"})
                continue
            adjusted_dates = pd.to_datetime(adjusted["date"], errors="coerce")
            adjusted_nums = adjusted[["open_adj", "high_adj", "low_adj", "close_adj"]].apply(pd.to_numeric, errors="coerce")
            adjusted_bad = {
                "adjusted_dates": int(not dates.equals(adjusted_dates)),
                "adjusted_numeric": int((~np.isfinite(adjusted_nums)).sum().sum()),
                "adjusted_nonpositive": int((adjusted_nums <= 0).sum().sum()),
                "adjusted_code": int(adjusted.code.dropna().astype(str).map(lambda x: x.zfill(6)).ne(code).any()),
            }
            for issue, count in adjusted_bad.items():
                if count:
                    report["errors"].append({"file": adjusted_path.name, "issue": issue, "count": count})
        except Exception as exc:
            report["errors"].append({"file": path.name, "issue": str(exc)})
    if len(raw_files) != len(adjusted_files):
        report["warnings"].append("raw and adjusted file counts differ")
    if report["vwap_outliers"]:
        report["warnings"].append(
            f"{len(report['vwap_outliers'])} traded rows have VWAP outside raw OHLC by more than 0.1%"
        )
    return report


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path, default=Path("data_v5"), nargs="?")
    args = parser.parse_args()
    report = validate(args.root)
    out = args.root / "data_quality_report.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    raise SystemExit(1 if report["errors"] else 0)


if __name__ == "__main__":
    main()
