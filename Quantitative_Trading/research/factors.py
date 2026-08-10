from __future__ import annotations

import numpy as np
import pandas as pd

from .config import FactorConfig


FACTOR_COLUMNS = ["momentum", "volatility", "adv", "history_count"]


def compute_factor_panel(panel: pd.DataFrame, config: FactorConfig) -> pd.DataFrame:
    config.validate()
    required = {
        "date", "stock_code", "close_adj", "amount_cny", "trade_status", "is_st",
    }
    missing = required - set(panel.columns)
    if missing:
        raise ValueError(f"factor panel missing columns: {sorted(missing)}")
    frame = panel.sort_values(["stock_code", "date"]).copy()
    grouped = frame.groupby("stock_code", sort=False, group_keys=False)
    frame["return_1d"] = grouped["close_adj"].pct_change(fill_method=None)
    frame["momentum"] = grouped["close_adj"].transform(
        lambda values: values.shift(config.momentum_skip) / values.shift(config.momentum_lookback) - 1.0
    )
    frame["volatility"] = grouped["return_1d"].transform(
        lambda values: values.rolling(
            config.volatility_lookback,
            min_periods=config.volatility_lookback,
        ).std()
    ) * np.sqrt(252.0)
    positive_amount = frame["amount_cny"].where(frame["amount_cny"] > 0)
    frame["adv"] = positive_amount.groupby(frame["stock_code"], sort=False).transform(
        lambda values: values.rolling(
            config.liquidity_lookback,
            min_periods=config.liquidity_lookback,
        ).mean()
    )
    frame["history_count"] = grouped["close_adj"].cumcount() + 1
    return frame


def monthly_signal_dates(calendar: list[str], start_date: str, end_date: str) -> list[tuple[str, str]]:
    dates = pd.Series(pd.to_datetime(calendar), name="date")
    next_dates = dates.shift(-1)
    month_end = dates.dt.to_period("M") != next_dates.dt.to_period("M")
    pairs = []
    for signal, execution, is_month_end in zip(dates, next_dates, month_end):
        if not is_month_end or pd.isna(execution):
            continue
        signal_text = signal.strftime("%Y-%m-%d")
        execution_text = execution.strftime("%Y-%m-%d")
        if start_date <= signal_text < end_date and execution_text <= end_date:
            pairs.append((signal_text, execution_text))
    return pairs


def members_on(intervals: pd.DataFrame, date: str) -> set[str]:
    rows = intervals[(intervals["valid_from"] <= date) & (date < intervals["valid_to"])]
    return set(rows["stock_code"].astype(str))


def generate_monthly_signals(
    factor_panel: pd.DataFrame,
    membership_intervals: pd.DataFrame,
    calendar: list[str],
    start_date: str,
    end_date: str,
    config: FactorConfig,
) -> pd.DataFrame:
    config.validate()
    factor_by_date = {date: rows for date, rows in factor_panel.groupby("date", sort=False)}
    output = []
    for signal_date, execution_date in monthly_signal_dates(calendar, start_date, end_date):
        rows = factor_by_date.get(signal_date)
        if rows is None:
            continue
        eligible = rows[rows["stock_code"].isin(members_on(membership_intervals, signal_date))].copy()
        eligible = eligible[
            eligible["close_adj"].notna()
            & eligible["adv"].notna()
            & eligible["trade_status"].eq(1)
            & eligible["is_st"].eq(0)
        ]
        if eligible.empty:
            continue
        if config.strategy != "equal_weight" and config.liquidity_exclusion_quantile > 0:
            cutoff = eligible["adv"].quantile(config.liquidity_exclusion_quantile)
            eligible = eligible[eligible["adv"] >= cutoff]

        if config.strategy == "equal_weight":
            eligible["score"] = 1.0
            selected = eligible.sort_values("stock_code")
        elif config.strategy == "momentum":
            eligible = eligible[eligible["momentum"].notna()]
            eligible["score"] = eligible["momentum"].rank(pct=True, method="average")
            selected = eligible.nlargest(config.top_n, ["score", "adv"])
        elif config.strategy == "low_vol":
            eligible = eligible[eligible["volatility"].notna()]
            eligible["score"] = (-eligible["volatility"]).rank(pct=True, method="average")
            selected = eligible.nlargest(config.top_n, ["score", "adv"])
        else:
            eligible = eligible[eligible["momentum"].notna() & eligible["volatility"].notna()]
            momentum_rank = eligible["momentum"].rank(pct=True, method="average")
            low_vol_rank = (-eligible["volatility"]).rank(pct=True, method="average")
            eligible["score"] = 0.5 * momentum_rank + 0.5 * low_vol_rank
            selected = eligible.nlargest(config.top_n, ["score", "adv"])
        if selected.empty:
            continue
        target_weight = config.invest_fraction / len(selected)
        for row in selected.itertuples(index=False):
            output.append({
                "signal_date": signal_date,
                "execution_date": execution_date,
                "stock_code": str(row.stock_code),
                "target_weight": target_weight,
                "score": float(row.score),
                "momentum": None if pd.isna(row.momentum) else float(row.momentum),
                "volatility": None if pd.isna(row.volatility) else float(row.volatility),
                "adv": float(row.adv),
            })
    return pd.DataFrame(output)
