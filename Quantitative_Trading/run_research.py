from __future__ import annotations

import argparse
import json
import sqlite3
import time
from dataclasses import asdict
from pathlib import Path

from research.config import ExecutionConfig, FactorConfig, STRATEGIES
from research.data import (
    load_benchmark,
    load_calendar,
    load_factor_panel,
    load_membership_intervals,
    lookback_start,
)
from research.engine import run_backtest
from research.factors import compute_factor_panel, generate_monthly_signals
from research.report import write_report


def database_metadata(database: Path) -> dict[str, str]:
    with sqlite3.connect(database) as conn:
        return dict(conn.execute("SELECT key, value FROM metadata"))


def main() -> None:
    parser = argparse.ArgumentParser(description="Run CSI 300 monthly cross-sectional research")
    parser.add_argument("--database", type=Path, default=Path("data_v5/csi300_2020_present.sqlite"))
    parser.add_argument("--benchmark", type=Path, default=Path("data_v5/benchmarks/csi300_daily.csv"))
    parser.add_argument(
        "--benchmark-return-type", choices=["price_index", "total_return"],
        default="price_index",
    )
    parser.add_argument("--benchmark-source", default=None)
    parser.add_argument("--strategy", choices=sorted(STRATEGIES), default="momentum_low_vol")
    parser.add_argument("--start-date", default="2021-01-04")
    parser.add_argument("--end-date", default=None)
    parser.add_argument("--top-n", type=int, default=30)
    parser.add_argument("--rebalance-interval-months", type=int, default=1)
    parser.add_argument("--selection-buffer", type=int, default=0)
    parser.add_argument("--momentum-lookback", type=int, default=252)
    parser.add_argument("--momentum-skip", type=int, default=21)
    parser.add_argument("--volatility-lookback", type=int, default=60)
    parser.add_argument("--liquidity-lookback", type=int, default=20)
    parser.add_argument("--liquidity-exclusion-quantile", type=float, default=0.20)
    parser.add_argument("--initial-cash", type=float, default=10_000_000.0)
    parser.add_argument("--lot-size", type=int, default=100)
    parser.add_argument("--invest-fraction", type=float, default=0.95)
    parser.add_argument("--commission", type=float, default=0.0003)
    parser.add_argument("--minimum-commission", type=float, default=5.0)
    parser.add_argument("--sell-stamp-duty", type=float, default=0.001)
    parser.add_argument("--sell-stamp-duty-current", type=float, default=0.0005)
    parser.add_argument("--transfer-fee", type=float, default=0.00001)
    parser.add_argument("--transfer-fee-legacy", type=float, default=0.00002)
    parser.add_argument(
        "--flat-fee-schedule", action="store_true",
        help="use --sell-stamp-duty and --transfer-fee for the entire period",
    )
    parser.add_argument("--slippage", type=float, default=0.001)
    parser.add_argument("--order-retry-days", type=int, default=5)
    parser.add_argument("--output-dir", type=Path, default=None)
    args = parser.parse_args()

    factor_config = FactorConfig(
        strategy=args.strategy,
        top_n=args.top_n,
        rebalance_interval_months=args.rebalance_interval_months,
        selection_buffer=args.selection_buffer,
        momentum_lookback=args.momentum_lookback,
        momentum_skip=args.momentum_skip,
        volatility_lookback=args.volatility_lookback,
        liquidity_lookback=args.liquidity_lookback,
        liquidity_exclusion_quantile=(
            0.0 if args.strategy == "equal_weight" else args.liquidity_exclusion_quantile
        ),
        invest_fraction=args.invest_fraction,
    )
    execution_config = ExecutionConfig(
        initial_cash=args.initial_cash,
        lot_size=args.lot_size,
        broker_commission=args.commission,
        minimum_commission=args.minimum_commission,
        sell_stamp_duty=args.sell_stamp_duty,
        sell_stamp_duty_current=args.sell_stamp_duty_current,
        transfer_fee=args.transfer_fee,
        transfer_fee_legacy=args.transfer_fee_legacy,
        use_historical_fee_schedule=not args.flat_fee_schedule,
        slippage=args.slippage,
        order_retry_days=args.order_retry_days,
    )
    factor_config.validate()
    execution_config.validate()
    started = time.time()
    calendar = load_calendar(args.database)
    end_date = args.end_date or calendar[-1]
    if args.start_date >= end_date:
        raise ValueError("start_date must be earlier than end_date")
    if args.start_date < calendar[0] or end_date > calendar[-1]:
        raise ValueError(f"requested range must stay within {calendar[0]} and {calendar[-1]}")
    load_start = lookback_start(calendar, args.start_date, factor_config.momentum_lookback + 10)
    print(f"Loading factor data: {load_start} to {end_date}")
    raw_panel = load_factor_panel(args.database, load_start, end_date)
    factor_panel = compute_factor_panel(raw_panel, factor_config)
    intervals = load_membership_intervals(args.database)
    signals = generate_monthly_signals(
        factor_panel,
        intervals,
        calendar,
        args.start_date,
        end_date,
        factor_config,
    )
    if signals.empty:
        raise RuntimeError("no signals generated; extend the date range or reduce lookback requirements")
    print(
        f"Signals: {len(signals)} rows, {signals['signal_date'].nunique()} dates, "
        f"{signals['stock_code'].nunique()} stocks"
    )
    result = run_backtest(
        args.database,
        signals,
        calendar,
        args.start_date,
        end_date,
        execution_config,
    )
    benchmark = load_benchmark(
        args.benchmark,
        args.start_date,
        end_date,
        allow_price_index_download=args.benchmark_return_type == "price_index",
    )
    output_dir = args.output_dir or Path(
        f"research_outputs/{args.strategy}_{args.start_date}_{end_date}"
    )
    metadata = database_metadata(args.database)
    metadata.update({
        "database": str(args.database),
        "factor_config": json.dumps(asdict(factor_config), ensure_ascii=False),
        "benchmark": str(args.benchmark),
        "benchmark_source": args.benchmark_source or (
            "baostock_sh.000300_unadjusted"
            if args.benchmark_return_type == "price_index"
            else "external_user_supplied"
        ),
        "benchmark_return_type": args.benchmark_return_type,
        "portfolio_valuation_price": "close_raw",
        "corporate_actions_modelled": "True",
        "corporate_action_cash_basis": "gross_before_investor_specific_tax",
        "corporate_action_recognition": "ex_date",
        "security_transitions_modelled": "True",
        "security_transition_recognition": "target_new_shares_listing_date",
        "security_transition_fractional_rule": "nearest_integer_max_one_share_error",
        "historical_st_filter": "True",
        "st_price_limit_model": "approximately_5_percent",
        "fill_model": "daily_bar_market_full_fill_or_skip",
        "elapsed_seconds": f"{time.time() - started:.2f}",
    })
    summary = write_report(
        output_dir,
        args.strategy,
        signals,
        result,
        benchmark,
        metadata,
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"Output: {output_dir.resolve()}")


if __name__ == "__main__":
    main()
