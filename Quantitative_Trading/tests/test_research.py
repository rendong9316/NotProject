from __future__ import annotations

import sqlite3
from pathlib import Path

import pandas as pd
import pytest

from research.config import ExecutionConfig, FactorConfig
from research.data import load_benchmark, load_corporate_action_schedule
from research.engine import AshareCommission, at_price_limit, fee_rates_for_date, run_backtest
from research.factors import compute_factor_panel, generate_monthly_signals


def test_commission_is_asymmetric_and_has_minimum():
    commission = AshareCommission(
        broker_commission=0.0003,
        minimum_commission=5.0,
        sell_stamp_duty=0.001,
        transfer_fee=0.00001,
    )
    assert commission._getcommission(100, 10.0, False) == pytest.approx(5.01)
    assert commission._getcommission(-100, 10.0, False) == pytest.approx(6.01)


def test_board_price_limits():
    assert at_price_limit("600000", "2024-01-02", 11.0, 10.0, "buy")
    assert not at_price_limit("300001", "2024-01-02", 11.0, 10.0, "buy")
    assert at_price_limit("300001", "2024-01-02", 12.0, 10.0, "buy")
    assert at_price_limit("600000", "2024-01-02", 9.0, 10.0, "sell")
    assert at_price_limit("600000", "2024-01-02", 10.5, 10.0, "buy", is_st=True)
    assert not at_price_limit("600000", "2024-01-02", 10.47, 10.0, "buy", is_st=True)


def test_historical_fee_schedule_boundaries():
    config = ExecutionConfig()
    assert fee_rates_for_date("2022-04-28", config) == pytest.approx((0.001, 0.00002))
    assert fee_rates_for_date("2022-04-29", config) == pytest.approx((0.001, 0.00001))
    assert fee_rates_for_date("2023-08-27", config) == pytest.approx((0.001, 0.00001))
    assert fee_rates_for_date("2023-08-28", config) == pytest.approx((0.0005, 0.00001))
    flat = ExecutionConfig(use_historical_fee_schedule=False)
    assert fee_rates_for_date("2024-01-02", flat) == pytest.approx((0.001, 0.00001))


def test_total_return_benchmark_never_falls_back_to_price_index(tmp_path: Path):
    missing = tmp_path / "h00300.csv"
    with pytest.raises(FileNotFoundError, match="total-return benchmark"):
        load_benchmark(
            missing,
            "2023-01-01",
            "2023-12-31",
            allow_price_index_download=False,
        )


def test_factor_signals_use_next_trading_day():
    dates = pd.bdate_range("2023-01-02", periods=90)
    rows = []
    for code, slope in [("000001", 0.003), ("000002", 0.001)]:
        for index, date in enumerate(dates):
            price = 10.0 * (1.0 + slope) ** index
            rows.append({
                "date": date.strftime("%Y-%m-%d"),
                "stock_code": code,
                "close_adj": price,
                "close_raw": price,
                "amount_cny": 100_000_000.0,
                "trade_status": 1,
                "is_st": 0,
            })
    panel = pd.DataFrame(rows)
    config = FactorConfig(
        strategy="momentum",
        top_n=1,
        momentum_lookback=20,
        momentum_skip=5,
        volatility_lookback=10,
        liquidity_lookback=5,
        liquidity_exclusion_quantile=0.0,
    )
    factors = compute_factor_panel(panel, config)
    intervals = pd.DataFrame([
        {"stock_code": "000001", "valid_from": "2020-01-01", "valid_to": "9999-12-31"},
        {"stock_code": "000002", "valid_from": "2020-01-01", "valid_to": "9999-12-31"},
    ])
    calendar = [date.strftime("%Y-%m-%d") for date in dates]
    signals = generate_monthly_signals(
        factors,
        intervals,
        calendar,
        calendar[0],
        calendar[-1],
        config,
    )
    assert not signals.empty
    assert (signals["execution_date"] > signals["signal_date"]).all()
    assert set(signals["stock_code"]) == {"000001"}


def test_corporate_action_schedule_combines_explicit_and_factor_fallback(tmp_path: Path):
    database = tmp_path / "actions.sqlite"
    with sqlite3.connect(database) as conn:
        conn.execute(
            "CREATE TABLE corporate_action_daily (stock_code TEXT, ex_date TEXT, "
            "cash_dividend_gross_per_share REAL, share_multiplier REAL)"
        )
        conn.execute(
            "CREATE TABLE adjustment_factor_events (stock_code TEXT, ex_date TEXT, "
            "factor_ratio REAL, validation_status TEXT)"
        )
        conn.execute(
            "INSERT INTO corporate_action_daily VALUES "
            "('000001', '2024-06-14', 0.719, 1.0)"
        )
        conn.execute(
            "INSERT INTO adjustment_factor_events VALUES "
            "('000001', '2024-06-14', 1.07, 'matched'), "
            "('000002', '2024-07-01', 1.25, 'factor_only')"
        )
    schedule = load_corporate_action_schedule(
        database, ["000001", "000002"], "2024-01-01", "2024-12-31"
    )
    assert schedule[["stock_code", "method"]].to_dict("records") == [
        {"stock_code": "000001", "method": "explicit"},
        {"stock_code": "000002", "method": "factor_fallback"},
    ]
    assert schedule.loc[schedule["stock_code"].eq("000002"), "share_multiplier"].iloc[0] == 1.25


def test_backtrader_integration_executes_next_open(tmp_path: Path):
    database = tmp_path / "tiny.sqlite"
    dates = pd.bdate_range("2024-01-02", periods=8).strftime("%Y-%m-%d").tolist()
    with sqlite3.connect(database) as conn:
        conn.execute(
            "CREATE TABLE daily_price_raw (date TEXT, stock_code TEXT, open_raw REAL, "
            "high_raw REAL, low_raw REAL, close_raw REAL, volume_shares INTEGER, "
            "amount_cny REAL, trade_status INTEGER)"
        )
        conn.execute(
            "CREATE TABLE corporate_action_daily (stock_code TEXT, ex_date TEXT, "
            "cash_dividend_gross_per_share REAL, share_multiplier REAL)"
        )
        conn.execute(
            "CREATE TABLE adjustment_factor_events (stock_code TEXT, ex_date TEXT, "
            "factor_ratio REAL, validation_status TEXT)"
        )
        conn.execute(
            "CREATE TABLE daily_security_status (date TEXT, stock_code TEXT, "
            "is_st INTEGER, trade_status INTEGER)"
        )
        conn.executemany(
            "INSERT INTO daily_price_raw VALUES (?, '000001', ?, ?, ?, ?, 1000000, 10000000, 1)",
            [(date, 10.0 + i * 0.1, 10.2 + i * 0.1, 9.8 + i * 0.1, 10.1 + i * 0.1) for i, date in enumerate(dates)],
        )
        conn.executemany(
            "INSERT INTO daily_price_raw VALUES (?, '000002', ?, ?, ?, ?, 1000000, 10000000, 1)",
            [(date, 20.0, 20.2, 19.8, 20.1) for date in dates[5:]],
        )
        conn.execute(
            "UPDATE daily_price_raw SET trade_status = 0 WHERE stock_code = '000001' AND date = ?",
            (dates[2],),
        )
        conn.execute(
            "INSERT INTO daily_security_status "
            "SELECT date, stock_code, 0, trade_status FROM daily_price_raw"
        )
        conn.execute(
            "INSERT INTO corporate_action_daily VALUES ('000001', ?, 1.0, 2.0)",
            (dates[4],),
        )
    signals = pd.DataFrame([
        {
            "signal_date": dates[1],
            "execution_date": dates[2],
            "stock_code": "000001",
            "target_weight": 0.50,
        },
        {
            "signal_date": dates[5],
            "execution_date": dates[6],
            "stock_code": "000002",
            "target_weight": 0.50,
        },
    ])
    result = run_backtest(
        database,
        signals,
        dates,
        dates[0],
        dates[-1],
        ExecutionConfig(initial_cash=100_000.0, slippage=0.0),
    )
    assert not result["orders"].empty
    assert "Completed" in set(result["orders"]["status"])
    assert dates[2] in result["rebalance_dates"]
    assert dates[3] in set(result["orders"].loc[result["orders"]["status"] == "Completed", "date"])
    assert "suspended" in set(result["skipped_orders"]["reason"])
    assert len(result["corporate_actions"]) == 1
    action = result["corporate_actions"].iloc[0]
    assert action["cash_amount"] > 0
    assert action["new_size"] == pytest.approx(action["old_size"] * 2.0)
    assert result["final_value"] > 0
