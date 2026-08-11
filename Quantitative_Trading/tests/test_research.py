from __future__ import annotations

import sqlite3
from pathlib import Path

import pandas as pd
import pytest

from research.config import ExecutionConfig, FactorConfig
from research.data import (
    load_benchmark,
    load_corporate_action_schedule,
    load_execution_prices,
)
from research.engine import (
    AshareCommission,
    at_price_limit,
    fee_rates_for_date,
    run_backtest,
    transition_share_quantity,
)
from research.factors import (
    compute_factor_panel,
    generate_monthly_signals,
    monthly_signal_dates,
)


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


def test_quarterly_signal_dates_use_calendar_quarter_ends():
    calendar = pd.bdate_range("2023-01-02", "2024-01-05").strftime("%Y-%m-%d").tolist()
    pairs = monthly_signal_dates(
        calendar, calendar[0], calendar[-1], interval_months=3
    )
    assert [signal[5:7] for signal, _ in pairs] == ["03", "06", "09", "12"]
    assert all(execution > signal for signal, execution in pairs)
    with pytest.raises(ValueError, match="interval_months"):
        monthly_signal_dates(calendar, calendar[0], calendar[-1], interval_months=0)


def test_selection_buffer_retains_existing_holding_within_buffer_rank():
    calendar = pd.bdate_range("2023-01-02", "2023-04-03").strftime("%Y-%m-%d").tolist()
    signal_dates = [pair[0] for pair in monthly_signal_dates(calendar, calendar[0], calendar[-1])]
    volatility_by_date = {
        signal_dates[0]: {"000001": 0.10, "000002": 0.20, "000003": 0.30},
        signal_dates[1]: {"000001": 0.20, "000002": 0.10, "000003": 0.30},
        signal_dates[2]: {"000001": 0.30, "000002": 0.10, "000003": 0.20},
    }
    rows = []
    for date, values in volatility_by_date.items():
        for code, volatility in values.items():
            rows.append({
                "date": date,
                "stock_code": code,
                "close_adj": 10.0,
                "momentum": 0.0,
                "volatility": volatility,
                "adv": 100_000_000.0,
                "trade_status": 1,
                "is_st": 0,
            })
    intervals = pd.DataFrame([
        {"stock_code": code, "valid_from": "2020-01-01", "valid_to": "9999-12-31"}
        for code in ["000001", "000002", "000003"]
    ])
    signals = generate_monthly_signals(
        pd.DataFrame(rows),
        intervals,
        calendar,
        calendar[0],
        calendar[-1],
        FactorConfig(
            strategy="low_vol",
            top_n=1,
            selection_buffer=1,
            liquidity_exclusion_quantile=0.0,
        ),
    )
    selected = signals.groupby("signal_date")["stock_code"].first().tolist()
    assert selected == ["000001", "000001", "000002"]


def test_factor_config_rejects_invalid_rebalance_controls():
    with pytest.raises(ValueError, match="rebalance_interval_months"):
        FactorConfig(rebalance_interval_months=0).validate()
    with pytest.raises(ValueError, match="selection_buffer"):
        FactorConfig(selection_buffer=-1).validate()


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


def test_execution_prices_include_close_before_requested_start(tmp_path: Path):
    database = tmp_path / "previous_close.sqlite"
    with sqlite3.connect(database) as conn:
        conn.execute(
            "CREATE TABLE daily_price_raw (date TEXT, stock_code TEXT, open_raw REAL, "
            "high_raw REAL, low_raw REAL, close_raw REAL, volume_shares INTEGER, "
            "amount_cny REAL, trade_status INTEGER)"
        )
        conn.execute(
            "CREATE TABLE daily_security_status (date TEXT, stock_code TEXT, "
            "is_st INTEGER, trade_status INTEGER)"
        )
        for day, close in [("2024-01-02", 10.0), ("2024-01-03", 11.0)]:
            conn.execute(
                "INSERT INTO daily_price_raw VALUES (?, '000001', ?, ?, ?, ?, 1000, 10000, 1)",
                (day, close, close, close, close),
            )
            conn.execute(
                "INSERT INTO daily_security_status VALUES (?, '000001', 0, 1)",
                (day,),
            )
    prices = load_execution_prices(
        database, ["000001"], "2024-01-03", "2024-01-03"
    )
    assert prices.loc[0, "prev_close_raw"] == pytest.approx(10.0)


def test_cash_dividend_is_counted_once_in_portfolio_value(tmp_path: Path):
    database = tmp_path / "cash_dividend.sqlite"
    dates = pd.bdate_range("2024-01-02", periods=7).strftime("%Y-%m-%d").tolist()
    with sqlite3.connect(database) as conn:
        conn.execute(
            "CREATE TABLE daily_price_raw (date TEXT, stock_code TEXT, open_raw REAL, "
            "high_raw REAL, low_raw REAL, close_raw REAL, volume_shares INTEGER, "
            "amount_cny REAL, trade_status INTEGER)"
        )
        conn.execute(
            "CREATE TABLE daily_security_status (date TEXT, stock_code TEXT, "
            "is_st INTEGER, trade_status INTEGER)"
        )
        conn.execute(
            "CREATE TABLE corporate_action_daily (stock_code TEXT, ex_date TEXT, "
            "cash_dividend_gross_per_share REAL, share_multiplier REAL)"
        )
        conn.execute(
            "CREATE TABLE adjustment_factor_events (stock_code TEXT, ex_date TEXT, "
            "factor_ratio REAL, validation_status TEXT)"
        )
        for index, day in enumerate(dates):
            price = 10.0 if index < 4 else 9.0
            conn.execute(
                "INSERT INTO daily_price_raw VALUES (?, '000001', ?, ?, ?, ?, "
                "1000000, 10000000, 1)",
                (day, price, price, price, price),
            )
            conn.execute(
                "INSERT INTO daily_security_status VALUES (?, '000001', 0, 1)",
                (day,),
            )
        conn.execute(
            "INSERT INTO corporate_action_daily VALUES ('000001', ?, 1.0, 1.0)",
            (dates[4],),
        )
    signals = pd.DataFrame([{
        "signal_date": dates[0],
        "execution_date": dates[1],
        "stock_code": "000001",
        "target_weight": 0.50,
    }])
    result = run_backtest(
        database,
        signals,
        dates,
        dates[0],
        dates[-1],
        ExecutionConfig(
            initial_cash=100_000.0,
            broker_commission=0.0,
            minimum_commission=0.0,
            sell_stamp_duty=0.0,
            sell_stamp_duty_current=0.0,
            transfer_fee=0.0,
            transfer_fee_legacy=0.0,
            slippage=0.0,
        ),
    )
    assert result["final_value"] == pytest.approx(100_000.0)


def test_security_transition_converts_position_without_trade_cost(tmp_path: Path):
    database = tmp_path / "security_transition.sqlite"
    dates = pd.bdate_range("2024-01-02", periods=7).strftime("%Y-%m-%d").tolist()
    with sqlite3.connect(database) as conn:
        conn.execute(
            "CREATE TABLE daily_price_raw (date TEXT, stock_code TEXT, open_raw REAL, "
            "high_raw REAL, low_raw REAL, close_raw REAL, volume_shares INTEGER, "
            "amount_cny REAL, trade_status INTEGER)"
        )
        conn.execute(
            "CREATE TABLE daily_security_status (date TEXT, stock_code TEXT, "
            "is_st INTEGER, trade_status INTEGER)"
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
            "CREATE TABLE security_transitions (source_stock_code TEXT, "
            "target_stock_code TEXT, record_date TEXT, event_date TEXT, "
            "exchange_ratio REAL, cash_per_source_share REAL, event_type TEXT, "
            "official_fractional_rule TEXT, simulation_fractional_rule TEXT, "
            "verification_status TEXT)"
        )
        for day in dates[:3]:
            conn.execute(
                "INSERT INTO daily_price_raw VALUES (?, '000001', 10, 10, 10, 10, "
                "1000000, 10000000, 1)",
                (day,),
            )
            conn.execute(
                "INSERT INTO daily_security_status VALUES (?, '000001', 0, 1)",
                (day,),
            )
        for day in dates:
            conn.execute(
                "INSERT INTO daily_price_raw VALUES (?, '000002', 20, 20, 20, 20, "
                "1000000, 20000000, 1)",
                (day,),
            )
            conn.execute(
                "INSERT INTO daily_security_status VALUES (?, '000002', 0, 1)",
                (day,),
            )
        conn.execute(
            "INSERT INTO security_transitions VALUES "
            "('000001', '000002', ?, ?, 0.5, 0, 'share_swap_absorption_merger', "
            "'registry_integer_allocation', 'nearest_integer_max_one_share_error', "
            "'official_sse_verified')",
            (dates[2], dates[4]),
        )
    signals = pd.DataFrame([{
        "signal_date": dates[0],
        "execution_date": dates[1],
        "stock_code": "000001",
        "target_weight": 0.50,
    }])
    zero_cost = ExecutionConfig(
        initial_cash=100_000.0,
        lot_size=1,
        broker_commission=0.0,
        minimum_commission=0.0,
        sell_stamp_duty=0.0,
        sell_stamp_duty_current=0.0,
        transfer_fee=0.0,
        transfer_fee_legacy=0.0,
        slippage=0.0,
    )
    result = run_backtest(database, signals, dates, dates[0], dates[-1], zero_cost)
    assert result["final_value"] == pytest.approx(100_000.0)
    assert len(result["security_transitions"]) == 1
    transition = result["security_transitions"].iloc[0]
    assert transition["source_size"] == pytest.approx(5_000.0)
    assert transition["converted_target_size"] == pytest.approx(2_500.0)
    assert transition["resulting_target_size"] == pytest.approx(2_500.0)


def test_unresolved_terminal_holding_blocks_backtest(tmp_path: Path):
    database = tmp_path / "unresolved_terminal.sqlite"
    dates = pd.bdate_range("2024-01-02", periods=5).strftime("%Y-%m-%d").tolist()
    with sqlite3.connect(database) as conn:
        conn.execute(
            "CREATE TABLE daily_price_raw (date TEXT, stock_code TEXT, open_raw REAL, "
            "high_raw REAL, low_raw REAL, close_raw REAL, volume_shares INTEGER, "
            "amount_cny REAL, trade_status INTEGER)"
        )
        conn.execute(
            "CREATE TABLE daily_security_status (date TEXT, stock_code TEXT, "
            "is_st INTEGER, trade_status INTEGER)"
        )
        conn.execute(
            "CREATE TABLE corporate_action_daily (stock_code TEXT, ex_date TEXT, "
            "cash_dividend_gross_per_share REAL, share_multiplier REAL)"
        )
        conn.execute(
            "CREATE TABLE adjustment_factor_events (stock_code TEXT, ex_date TEXT, "
            "factor_ratio REAL, validation_status TEXT)"
        )
        for day in dates[:3]:
            conn.execute(
                "INSERT INTO daily_price_raw VALUES (?, '000001', 10, 10, 10, 10, "
                "1000000, 10000000, 1)",
                (day,),
            )
            conn.execute(
                "INSERT INTO daily_security_status VALUES (?, '000001', 0, 1)",
                (day,),
            )
    signals = pd.DataFrame([{
        "signal_date": dates[0],
        "execution_date": dates[1],
        "stock_code": "000001",
        "target_weight": 0.50,
    }])
    with pytest.raises(RuntimeError, match="unresolved terminal holding"):
        run_backtest(
            database,
            signals,
            dates,
            dates[0],
            dates[-1],
            ExecutionConfig(initial_cash=100_000.0, slippage=0.0),
        )


def test_transition_share_quantity_uses_disclosed_rounding_approximation():
    assert transition_share_quantity(65_200, 0.1339, "nearest_integer_max_one_share_error") == 8_730
    with pytest.raises(ValueError, match="unsupported"):
        transition_share_quantity(100, 0.62, "preserve_fraction")
