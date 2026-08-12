from __future__ import annotations

import argparse
import json
import sqlite3
from dataclasses import asdict, replace
from pathlib import Path

import pandas as pd

from research.config import ExecutionConfig, FactorConfig
from research.data import (
    load_calendar,
    load_factor_panel,
    load_membership_intervals,
    lookback_start,
)
from research.diagnostics import (
    factor_ic_and_quantiles,
    ic_summary,
    monthly_return_metrics,
    selected_monthly_returns,
)
from research.engine import run_backtest
from research.factors import compute_factor_panel, generate_monthly_signals
from research.report import execution_diagnostics, performance_metrics
ROOT = Path(__file__).resolve().parents[2]



CANDIDATES = {
    "balanced_monthly_30": FactorConfig(),
    "low_vol_monthly_30": FactorConfig(strategy="low_vol"),
    "low_vol_monthly_50_buffer10": FactorConfig(
        strategy="low_vol", top_n=50, selection_buffer=10
    ),
    "low_vol_bimonthly_50_buffer10": FactorConfig(
        strategy="low_vol",
        top_n=50,
        rebalance_interval_months=2,
        selection_buffer=10,
    ),
    "low_vol_quarterly_50_buffer20": FactorConfig(
        strategy="low_vol",
        top_n=50,
        rebalance_interval_months=3,
        selection_buffer=20,
    ),
    "balanced_bimonthly_50_buffer10": FactorConfig(
        top_n=50,
        rebalance_interval_months=2,
        selection_buffer=10,
    ),
    "fast_balanced_bimonthly_30_buffer10": FactorConfig(
        top_n=30,
        momentum_lookback=126,
        momentum_skip=10,
        volatility_lookback=40,
        rebalance_interval_months=2,
        selection_buffer=10,
    ),
}


def database_metadata(database: Path) -> dict[str, str]:
    with sqlite3.connect(database) as conn:
        return dict(conn.execute("SELECT key, value FROM metadata"))


def trading_year_bounds(calendar: list[str], year: int) -> tuple[str, str] | None:
    dates = [day for day in calendar if day.startswith(f"{year}-")]
    return (dates[0], dates[-1]) if dates else None


def flatten_metrics(prefix: str, metrics: dict) -> dict:
    return {f"{prefix}_{key}": value for key, value in metrics.items()}


def yearly_metrics(candidate: str, returns: pd.Series) -> list[dict]:
    rows = []
    for year, values in returns.groupby(returns.index.year):
        rows.append({
            "candidate": candidate,
            "year": int(year),
            **performance_metrics(values),
        })
    return rows


def rank_candidates(summary: pd.DataFrame) -> pd.DataFrame:
    ranked = summary.copy()
    components = {
        "post_2023_sharpe_zero_rate": (False, 0.40),
        "cost_max_drawdown": (False, 0.20),
        "execution_annualized_gross_turnover": (True, 0.20),
        "positive_year_ratio": (False, 0.20),
    }
    score = pd.Series(0.0, index=ranked.index)
    for column, (ascending, weight) in components.items():
        rank_column = f"rank_{column}"
        ranked[rank_column] = ranked[column].rank(
            method="min", ascending=ascending, na_option="bottom"
        )
        score += weight * ranked[rank_column]
    ranked["robustness_rank_score"] = score
    ranked = ranked.sort_values(
        [
            "robustness_rank_score",
            "execution_annualized_gross_turnover",
            "post_2023_sharpe_zero_rate",
            "candidate",
        ],
        ascending=[True, True, False, True],
    ).reset_index(drop=True)
    ranked["robustness_rank"] = range(1, len(ranked) + 1)
    return ranked


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Run parameter, factor, cost, and walk-forward validation"
    )
    parser.add_argument(
        "--database", type=Path, default=ROOT / "data" / "csi300_2010_present.sqlite"
    )
    parser.add_argument("--start-date", default="2021-01-04")
    parser.add_argument("--end-date", default=None)
    parser.add_argument("--initial-cash", type=float, default=10_000_000.0)
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("research_outputs/validation_low_turnover"),
    )
    args = parser.parse_args()

    for config in CANDIDATES.values():
        config.validate()
    calendar = load_calendar(args.database)
    end_date = args.end_date or calendar[-1]
    if args.start_date >= end_date:
        raise ValueError("start_date must be earlier than end_date")
    if args.start_date < calendar[0] or end_date > calendar[-1]:
        raise ValueError(f"requested range must stay within {calendar[0]} and {calendar[-1]}")
    max_lookback = max(config.momentum_lookback for config in CANDIDATES.values())
    load_start = lookback_start(calendar, args.start_date, max_lookback + 10)
    raw_panel = load_factor_panel(args.database, load_start, end_date)
    intervals = load_membership_intervals(args.database)

    factor_panels = {}
    factor_panel_cache = {}
    signals_by_candidate = {}
    screening_returns = {}
    sensitivity_rows = []
    for name, config in CANDIDATES.items():
        print(f"Preparing candidate: {name}", flush=True)
        factor_key = (
            config.momentum_lookback,
            config.momentum_skip,
            config.volatility_lookback,
            config.liquidity_lookback,
        )
        if factor_key not in factor_panel_cache:
            factor_panel_cache[factor_key] = compute_factor_panel(raw_panel, config)
        factor_panel = factor_panel_cache[factor_key]
        signals = generate_monthly_signals(
            factor_panel, intervals, calendar, args.start_date, end_date, config
        )
        gross_returns = selected_monthly_returns(signals, factor_panel)
        factor_panels[name] = factor_panel
        signals_by_candidate[name] = signals
        screening_returns[name] = gross_returns
        sensitivity_rows.append({
            "candidate": name,
            **asdict(config),
            **flatten_metrics("gross_screening", monthly_return_metrics(gross_returns)),
        })

    base_panel = factor_panels["balanced_monthly_30"]
    base_signals = signals_by_candidate["balanced_monthly_30"]
    ic_frame, quantile_frame = factor_ic_and_quantiles(
        base_panel, base_signals, intervals, CANDIDATES["balanced_monthly_30"]
    )

    execution_config = ExecutionConfig(initial_cash=args.initial_cash)
    zero_cost_config = replace(
        execution_config,
        broker_commission=0.0,
        minimum_commission=0.0,
        sell_stamp_duty=0.0,
        sell_stamp_duty_current=0.0,
        transfer_fee=0.0,
        transfer_fee_legacy=0.0,
        slippage=0.0,
    )
    candidate_rows = []
    candidate_year_rows = []
    candidate_daily_returns = {}
    for name, config in CANDIDATES.items():
        print(f"Formal cost backtest: {name}", flush=True)
        cost_result = run_backtest(
            args.database,
            signals_by_candidate[name],
            calendar,
            args.start_date,
            end_date,
            execution_config,
        )
        zero_result = run_backtest(
            args.database,
            signals_by_candidate[name],
            calendar,
            args.start_date,
            end_date,
            zero_cost_config,
        )
        cost_metrics = performance_metrics(cost_result["daily_returns"])
        zero_metrics = performance_metrics(zero_result["daily_returns"])
        post_2023 = cost_result["daily_returns"][
            cost_result["daily_returns"].index >= pd.Timestamp("2023-01-01")
        ]
        year_rows = yearly_metrics(name, cost_result["daily_returns"])
        positive_year_ratio = (
            sum(row.get("total_return", 0.0) > 0 for row in year_rows) / len(year_rows)
            if year_rows else 0.0
        )
        candidate_rows.append({
            "candidate": name,
            **asdict(config),
            **flatten_metrics("cost", cost_metrics),
            **flatten_metrics("zero_cost", zero_metrics),
            **flatten_metrics("post_2023", performance_metrics(post_2023)),
            **flatten_metrics(
                "execution",
                execution_diagnostics(cost_result["orders"], cost_result["equity"]),
            ),
            "total_return_cost_drag": (
                zero_metrics.get("total_return", 0.0) - cost_metrics.get("total_return", 0.0)
            ),
            "positive_year_ratio": positive_year_ratio,
            "signal_dates": int(signals_by_candidate[name]["signal_date"].nunique()),
        })
        candidate_year_rows.extend(year_rows)
        candidate_daily_returns[name] = cost_result["daily_returns"]

    candidate_summary = rank_candidates(pd.DataFrame(candidate_rows))
    recommended_candidate = str(candidate_summary.iloc[0]["candidate"])
    walk_forward_rows = []
    stitched_returns = []
    available_years = sorted({int(day[:4]) for day in calendar})
    test_years = [year for year in range(2023, int(end_date[:4]) + 1) if year in available_years]
    for test_year in test_years:
        train_start_year = test_year - 2
        train_start_bounds = trading_year_bounds(calendar, train_start_year)
        train_end_bounds = trading_year_bounds(calendar, test_year - 1)
        test_bounds = trading_year_bounds(calendar, test_year)
        if not train_start_bounds or not train_end_bounds or not test_bounds:
            continue
        train_start = max(args.start_date, train_start_bounds[0])
        train_end = train_end_bounds[1]
        test_start = test_bounds[0]
        test_end = min(end_date, test_bounds[1])
        if train_start >= train_end or test_start >= test_end:
            continue
        candidate_scores = {}
        for name, returns in screening_returns.items():
            training = returns[(returns.index >= train_start) & (returns.index <= train_end)]
            candidate_scores[name] = monthly_return_metrics(training).get("sharpe_zero_rate")
        selected_name = max(
            candidate_scores,
            key=lambda name: (
                float("-inf") if candidate_scores[name] is None else candidate_scores[name]
            ),
        )
        test_signals = signals_by_candidate[selected_name]
        test_signals = test_signals[
            test_signals["execution_date"].between(test_start, test_end)
        ].copy()
        if test_signals.empty:
            continue
        print(
            f"Walk-forward {test_year}: selected {selected_name} from {train_start} to {train_end}",
            flush=True,
        )
        result = run_backtest(
            args.database, test_signals, calendar, test_start, test_end, execution_config
        )
        daily_metrics = performance_metrics(result["daily_returns"])
        costs = execution_diagnostics(result["orders"], result["equity"])
        walk_forward_rows.append({
            "test_year": test_year,
            "train_start": train_start,
            "train_end": train_end,
            "test_start": test_start,
            "test_end": test_end,
            "selected_candidate": selected_name,
            "training_gross_sharpe": candidate_scores[selected_name],
            **flatten_metrics("test", daily_metrics),
            **flatten_metrics("execution", costs),
        })
        stitched_returns.append(result["daily_returns"])

    stitched = (
        pd.concat(stitched_returns).sort_index()
        if stitched_returns else pd.Series(dtype=float)
    )
    stitched = stitched[~stitched.index.duplicated(keep="first")]

    args.output_dir.mkdir(parents=True, exist_ok=True)
    sensitivity = pd.DataFrame(sensitivity_rows)
    walk_forward = pd.DataFrame(walk_forward_rows)
    candidate_summary.to_csv(
        args.output_dir / "candidate_formal_backtests.csv", index=False, encoding="utf-8-sig"
    )
    pd.DataFrame(candidate_year_rows).to_csv(
        args.output_dir / "candidate_yearly_metrics.csv", index=False, encoding="utf-8-sig"
    )
    pd.DataFrame(candidate_daily_returns).to_csv(
        args.output_dir / "candidate_daily_returns.csv", encoding="utf-8-sig"
    )
    pd.concat(
        [signals.assign(candidate=name) for name, signals in signals_by_candidate.items()],
        ignore_index=True,
    ).to_csv(args.output_dir / "candidate_signals.csv", index=False, encoding="utf-8-sig")
    sensitivity.to_csv(
        args.output_dir / "parameter_sensitivity.csv", index=False, encoding="utf-8-sig"
    )
    walk_forward.to_csv(
        args.output_dir / "walk_forward.csv", index=False, encoding="utf-8-sig"
    )
    ic_frame.to_csv(args.output_dir / "factor_ic.csv", index=False, encoding="utf-8-sig")
    quantile_frame.to_csv(
        args.output_dir / "factor_quantile_returns.csv", index=False, encoding="utf-8-sig"
    )
    pd.DataFrame(screening_returns).to_csv(
        args.output_dir / "candidate_gross_monthly_returns.csv", encoding="utf-8-sig"
    )
    stitched.rename("walk_forward_return").to_csv(
        args.output_dir / "walk_forward_daily_returns.csv", encoding="utf-8-sig"
    )
    summary = {
        "research_status": "diagnostic_not_live_trading_approval",
        "parameter_screening_basis": (
            "adjusted-close execution-date to next execution-date gross equal-weight returns"
        ),
        "walk_forward_rule": (
            "choose highest gross periodic-return Sharpe on prior two calendar years"
        ),
        "formal_candidate_ranking_rule": (
            "40% post-2023 cost Sharpe, 20% cost max drawdown, "
            "20% annualized turnover, 20% positive-year ratio; lower rank score is better; "
            "ties prefer lower turnover then higher post-2023 Sharpe"
        ),
        "recommended_research_candidate": recommended_candidate,
        "candidates": {name: asdict(config) for name, config in CANDIDATES.items()},
        "factor_ic": ic_summary(ic_frame),
        "stitched_walk_forward_metrics": performance_metrics(stitched),
        "formal_candidate_results": candidate_summary.to_dict("records"),
        "database_metadata": database_metadata(args.database),
    }
    (args.output_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"Output: {args.output_dir.resolve()}")


if __name__ == "__main__":
    main()