from __future__ import annotations

import math

import numpy as np
import pandas as pd

from .config import FactorConfig
from .factors import members_on


def monthly_return_metrics(returns: pd.Series) -> dict[str, float | int | None]:
    clean = returns.dropna().astype(float)
    if clean.empty:
        return {"observations": 0}
    periods_per_year = 12.0
    if len(clean) > 1:
        dates = pd.to_datetime(clean.index, errors="coerce")
        day_gaps = pd.Series(dates).diff().dt.days.dropna()
        day_gaps = day_gaps[day_gaps > 0]
        if not day_gaps.empty:
            periods_per_year = 365.25 / float(day_gaps.median())
    equity = (1.0 + clean).cumprod()
    volatility = clean.std(ddof=1)
    drawdown = equity / equity.cummax() - 1.0
    return {
        "observations": int(len(clean)),
        "estimated_periods_per_year": periods_per_year,
        "total_return": float(equity.iloc[-1] - 1.0),
        "annual_return": float(equity.iloc[-1] ** (periods_per_year / len(clean)) - 1.0),
        "annual_volatility": (
            float(volatility * math.sqrt(periods_per_year)) if len(clean) > 1 else 0.0
        ),
        "sharpe_zero_rate": (
            float(clean.mean() / volatility * math.sqrt(periods_per_year))
            if volatility > 0 else None
        ),
        "max_drawdown": float(drawdown.min()),
    }


def selected_monthly_returns(
    signals: pd.DataFrame,
    factor_panel: pd.DataFrame,
) -> pd.Series:
    if signals.empty:
        return pd.Series(dtype=float)
    prices = factor_panel.pivot(index="date", columns="stock_code", values="close_adj")
    signal_groups = list(signals.groupby("signal_date", sort=True))
    output: dict[str, float] = {}
    for index in range(len(signal_groups) - 1):
        _, current = signal_groups[index]
        _, following = signal_groups[index + 1]
        entry_date = str(current["execution_date"].iloc[0])
        exit_date = str(following["execution_date"].iloc[0])
        if entry_date not in prices.index or exit_date not in prices.index:
            continue
        codes = current["stock_code"].astype(str).tolist()
        entry = prices.loc[entry_date].reindex(codes)
        exit_prices = prices.loc[exit_date].reindex(codes)
        returns = (exit_prices / entry - 1.0).replace([np.inf, -np.inf], np.nan).dropna()
        if not returns.empty:
            output[exit_date] = float(returns.mean())
    result = pd.Series(output, name="gross_screening_return", dtype=float)
    result.index = pd.to_datetime(result.index)
    return result.sort_index()


def factor_ic_and_quantiles(
    factor_panel: pd.DataFrame,
    signals: pd.DataFrame,
    membership_intervals: pd.DataFrame,
    config: FactorConfig,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    if signals.empty:
        return pd.DataFrame(), pd.DataFrame()
    prices = factor_panel.pivot(index="date", columns="stock_code", values="close_adj")
    rows_by_date = {date: rows for date, rows in factor_panel.groupby("date", sort=False)}
    signal_dates = sorted(signals["signal_date"].unique())
    ic_rows = []
    quantile_rows = []
    for index in range(len(signal_dates) - 1):
        signal_date = signal_dates[index]
        next_signal_date = signal_dates[index + 1]
        execution_date = str(
            signals.loc[signals["signal_date"].eq(signal_date), "execution_date"].iloc[0]
        )
        next_execution_date = str(
            signals.loc[signals["signal_date"].eq(next_signal_date), "execution_date"].iloc[0]
        )
        current = rows_by_date.get(signal_date)
        if current is None or execution_date not in prices.index or next_execution_date not in prices.index:
            continue
        frame = current[
            current["stock_code"].isin(members_on(membership_intervals, signal_date))
            &
            current["momentum"].notna()
            & current["volatility"].notna()
            & current["adv"].notna()
            & current["trade_status"].eq(1)
            & current["is_st"].eq(0)
        ].copy()
        if frame.empty:
            continue
        codes = frame["stock_code"].astype(str)
        frame["forward_return"] = (
            prices.loc[next_execution_date].reindex(codes).to_numpy()
            / prices.loc[execution_date].reindex(codes).to_numpy()
            - 1.0
        )
        frame = frame.replace([np.inf, -np.inf], np.nan).dropna(subset=["forward_return"])
        if len(frame) < 20:
            continue
        frame["momentum_score"] = frame["momentum"].rank(pct=True, method="average")
        frame["low_vol_score"] = (-frame["volatility"]).rank(pct=True, method="average")
        frame["combined_score"] = 0.5 * frame["momentum_score"] + 0.5 * frame["low_vol_score"]
        ic_rows.append({
            "signal_date": signal_date,
            "next_signal_date": next_signal_date,
            "stock_count": len(frame),
            "momentum_ic": frame["momentum_score"].corr(
                frame["forward_return"], method="spearman"
            ),
            "low_vol_ic": frame["low_vol_score"].corr(
                frame["forward_return"], method="spearman"
            ),
            "combined_ic": frame["combined_score"].corr(
                frame["forward_return"], method="spearman"
            ),
        })
        try:
            frame["quantile"] = pd.qcut(
                frame["combined_score"], 5, labels=False, duplicates="drop"
            ) + 1
        except ValueError:
            continue
        for quantile, group in frame.groupby("quantile"):
            quantile_rows.append({
                "signal_date": signal_date,
                "quantile": int(quantile),
                "stock_count": len(group),
                "mean_forward_return": float(group["forward_return"].mean()),
            })
    return pd.DataFrame(ic_rows), pd.DataFrame(quantile_rows)


def ic_summary(ic_frame: pd.DataFrame) -> dict[str, dict[str, float | int | None]]:
    output = {}
    for column in ["momentum_ic", "low_vol_ic", "combined_ic"]:
        clean = ic_frame[column].dropna() if column in ic_frame else pd.Series(dtype=float)
        standard_deviation = clean.std(ddof=1)
        output[column] = {
            "observations": int(len(clean)),
            "mean": float(clean.mean()) if not clean.empty else None,
            "hit_rate": float((clean > 0).mean()) if not clean.empty else None,
            "annualized_ic_ir": (
                float(clean.mean() / standard_deviation * math.sqrt(12.0))
                if len(clean) > 1 and standard_deviation > 0 else None
            ),
            "t_statistic": (
                float(clean.mean() / (standard_deviation / math.sqrt(len(clean))))
                if len(clean) > 1 and standard_deviation > 0 else None
            ),
        }
    return output
