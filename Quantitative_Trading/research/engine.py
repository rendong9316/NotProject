from __future__ import annotations

import math
from dataclasses import asdict
from pathlib import Path

import backtrader as bt
import pandas as pd

from .config import ExecutionConfig
from .data import (
    load_corporate_action_schedule,
    load_execution_prices,
    load_security_transition_schedule,
)


class AshareData(bt.feeds.PandasData):
    lines = ("trade_status", "prev_close_raw", "is_st", "amount_cny")
    params = (
        ("trade_status", "trade_status"),
        ("prev_close_raw", "prev_close_raw"),
        ("is_st", "is_st"),
        ("amount_cny", "amount_cny"),
    )


class AshareCommission(bt.CommInfoBase):
    params = (
        ("broker_commission", 0.0003),
        ("minimum_commission", 5.0),
        ("sell_stamp_duty", 0.001),
        ("transfer_fee", 0.00001),
        ("stocklike", True),
        ("commtype", bt.CommInfoBase.COMM_PERC),
    )

    def _getcommission(self, size, price, pseudoexec):
        if not size:
            return 0.0
        value = abs(size) * price
        broker_fee = max(value * self.p.broker_commission, self.p.minimum_commission)
        transfer_fee = value * self.p.transfer_fee
        stamp_duty = value * self.p.sell_stamp_duty if size < 0 else 0.0
        return broker_fee + transfer_fee + stamp_duty


def board_price_limit(stock_code: str, trade_date: str, is_st: bool = False) -> float:
    if is_st:
        return 0.05
    if stock_code.startswith("688"):
        return 0.20
    if stock_code.startswith(("300", "301")) and trade_date >= "2020-08-24":
        return 0.20
    return 0.10


def fee_rates_for_date(trade_date: str, config: ExecutionConfig) -> tuple[float, float]:
    if not config.use_historical_fee_schedule:
        return config.sell_stamp_duty, config.transfer_fee
    stamp_duty = (
        config.sell_stamp_duty_current
        if trade_date >= "2023-08-28"
        else config.sell_stamp_duty
    )
    transfer_fee = (
        config.transfer_fee
        if trade_date >= "2022-04-29"
        else config.transfer_fee_legacy
    )
    return stamp_duty, transfer_fee


def at_price_limit(
    stock_code: str,
    trade_date: str,
    open_price: float,
    previous_close: float,
    side: str,
    is_st: bool = False,
) -> bool:
    if not open_price or not previous_close or previous_close <= 0:
        return False
    change = open_price / previous_close - 1.0
    limit = board_price_limit(stock_code, trade_date, is_st)
    tolerance = 0.002
    return change >= limit - tolerance if side == "buy" else change <= -limit + tolerance


def transition_share_quantity(source_shares: float, exchange_ratio: float, rule: str) -> int:
    if source_shares < 0 or not math.isfinite(source_shares):
        raise ValueError("source transition shares must be finite and non-negative")
    if exchange_ratio <= 0 or not math.isfinite(exchange_ratio):
        raise ValueError("exchange ratio must be finite and positive")
    if rule != "nearest_integer_max_one_share_error":
        raise ValueError(f"unsupported transition fractional-share rule: {rule}")
    return math.floor(source_shares * exchange_ratio + 0.5)


class MonthlyTargetStrategy(bt.Strategy):
    params = (
        ("targets", None),
        ("lot_size", 100),
        ("order_retry_days", 5),
        ("corporate_actions", None),
        ("security_transitions", None),
        ("last_price_dates", None),
        ("execution_config", None),
    )

    def __init__(self):
        self.calendar = self.datas[0]
        self.stock_data = {data._name: data for data in self.datas[1:]}
        self.order_records: list[dict] = []
        self.skipped_records: list[dict] = []
        self.corporate_action_records: list[dict] = []
        self.security_transition_records: list[dict] = []
        self.rebalance_dates: list[str] = []
        self.desired_sizes: dict[str, float] = {}
        self.retry_days_left = 0
        self.transition_dates_by_source = {
            event["source_stock_code"]: date
            for date, events in (self.p.security_transitions or {}).items()
            for event in events
        }

    def _current(self, data, trade_date: str) -> bool:
        return len(data) > 0 and data.datetime.date(0).isoformat() == trade_date

    def _tradeable(self, data, stock_code: str, trade_date: str, side: str) -> tuple[bool, str]:
        if not self._current(data, trade_date):
            return False, "no_price_bar"
        open_price = float(data.open[0])
        previous_close = float(data.prev_close_raw[0])
        if not math.isfinite(open_price) or open_price <= 0:
            return False, "invalid_open"
        if int(data.trade_status[0]) != 1:
            return False, "suspended"
        if math.isfinite(previous_close) and at_price_limit(
            stock_code, trade_date, open_price, previous_close, side,
            is_st=bool(int(data.is_st[0])),
        ):
            return False, "price_limit"
        return True, ""

    def _skip(self, trade_date: str, stock_code: str, side: str, reason: str) -> None:
        self.skipped_records.append({
            "date": trade_date,
            "stock_code": stock_code,
            "side": side,
            "reason": reason,
        })

    def _apply_corporate_actions(self, trade_date: str) -> None:
        for action in (self.p.corporate_actions or {}).get(trade_date, []):
            stock_code = action["stock_code"]
            data = self.stock_data.get(stock_code)
            if data is None:
                continue
            position = self.getposition(data)
            old_size = float(position.size)
            if old_size <= 0:
                continue
            cash_per_share = float(action["cash_dividend_gross_per_share"])
            share_multiplier = float(action["share_multiplier"])
            if not math.isfinite(share_multiplier) or share_multiplier <= 0:
                raise ValueError(
                    f"invalid corporate-action share multiplier: {stock_code} {trade_date}"
                )
            cash_amount = old_size * cash_per_share
            new_size = old_size * share_multiplier
            old_cost = float(position.price)
            position.size = new_size
            if new_size > 0 and old_cost > 0:
                position.price = max((old_cost - cash_per_share) / share_multiplier, 1e-12)
            if cash_amount:
                self.broker.add_cash(cash_amount)
            self.corporate_action_records.append({
                "date": trade_date,
                "stock_code": stock_code,
                "method": action["method"],
                "validation_status": action["validation_status"],
                "old_size": old_size,
                "new_size": new_size,
                "share_multiplier": share_multiplier,
                "cash_per_share": cash_per_share,
                "cash_amount": cash_amount,
            })

    def _apply_security_transitions(self, trade_date: str) -> None:
        for event in (self.p.security_transitions or {}).get(trade_date, []):
            source_code = event["source_stock_code"]
            target_code = event["target_stock_code"]
            source_data = self.stock_data.get(source_code)
            target_data = self.stock_data.get(target_code)
            if source_data is None:
                continue
            source_position = self.getposition(source_data)
            source_size = float(source_position.size)
            if source_size <= 0:
                continue
            if target_data is None or not self._current(target_data, trade_date):
                raise RuntimeError(
                    f"transition target has no event-date price: "
                    f"{source_code}->{target_code} {trade_date}"
                )
            ratio = float(event["exchange_ratio"])
            theoretical_target_size = source_size * ratio
            converted_target_size = transition_share_quantity(
                source_size, ratio, event["simulation_fractional_rule"]
            )
            target_position = self.getposition(target_data)
            existing_target_size = float(target_position.size)
            resulting_target_size = existing_target_size + converted_target_size
            source_cost = max(
                float(source_position.price) - float(event["cash_per_source_share"]),
                0.0,
            )
            existing_cost_value = existing_target_size * float(target_position.price)
            converted_cost_value = source_size * source_cost
            resulting_cost = (
                (existing_cost_value + converted_cost_value) / resulting_target_size
                if resulting_target_size > 0
                else 0.0
            )
            cash_amount = source_size * float(event["cash_per_source_share"])
            source_position.set(0.0, 0.0)
            target_position.set(resulting_target_size, resulting_cost)
            if cash_amount:
                self.broker.add_cash(cash_amount)
            self.security_transition_records.append({
                "date": trade_date,
                "source_stock_code": source_code,
                "target_stock_code": target_code,
                "event_type": event["event_type"],
                "source_size": source_size,
                "exchange_ratio": ratio,
                "theoretical_target_size": theoretical_target_size,
                "converted_target_size": converted_target_size,
                "rounding_share_difference": converted_target_size - theoretical_target_size,
                "existing_target_size": existing_target_size,
                "resulting_target_size": resulting_target_size,
                "cash_amount": cash_amount,
                "verification_status": event["verification_status"],
            })

    def _validate_terminal_positions(self, trade_date: str) -> None:
        for stock_code, data in self.stock_data.items():
            if float(self.getposition(data).size) <= 0:
                continue
            last_price_date = (self.p.last_price_dates or {}).get(stock_code)
            if last_price_date is None or trade_date <= last_price_date:
                continue
            transition_date = self.transition_dates_by_source.get(stock_code)
            if transition_date is not None and trade_date <= transition_date:
                continue
            raise RuntimeError(
                "unresolved terminal holding has no price or settlement event: "
                f"{stock_code}, last_price_date={last_price_date}, trade_date={trade_date}"
            )

    def _portfolio_value_at_open(self, trade_date: str) -> float:
        value = float(self.broker.getcash())
        for data in self.stock_data.values():
            size = float(self.getposition(data).size)
            if not size or len(data) == 0:
                continue
            price = float(data.open[0]) if self._current(data, trade_date) else float(data.close[0])
            if math.isfinite(price) and price > 0:
                value += size * price
        return value

    def _set_daily_fee_rates(self, trade_date: str) -> None:
        stamp_duty, transfer_fee = fee_rates_for_date(
            trade_date, self.p.execution_config
        )
        if not self.stock_data:
            return
        commission = self.broker.getcommissioninfo(next(iter(self.stock_data.values())))
        commission.p.sell_stamp_duty = stamp_duty
        commission.p.transfer_fee = transfer_fee

    def _process_open(self):
        trade_date = self.calendar.datetime.date(0).isoformat()
        self._set_daily_fee_rates(trade_date)
        self._apply_corporate_actions(trade_date)
        self._apply_security_transitions(trade_date)
        self._validate_terminal_positions(trade_date)
        target_weights = (self.p.targets or {}).get(trade_date)
        if target_weights is None and self.retry_days_left <= 0:
            return
        if target_weights is not None:
            self.rebalance_dates.append(trade_date)
            portfolio_value = self._portfolio_value_at_open(trade_date)
            self.desired_sizes = {}
            for stock_code, weight in target_weights.items():
                data = self.stock_data.get(stock_code)
                if data is None or not self._current(data, trade_date):
                    self.desired_sizes[stock_code] = 0
                    self._skip(trade_date, stock_code, "buy", "no_price_bar")
                    continue
                open_price = float(data.open[0])
                if not math.isfinite(open_price) or open_price <= 0:
                    self.desired_sizes[stock_code] = 0
                    self._skip(trade_date, stock_code, "buy", "invalid_open")
                    continue
                shares = math.floor(portfolio_value * weight / open_price / self.p.lot_size) * self.p.lot_size
                self.desired_sizes[stock_code] = max(0, shares)
                if shares == 0 and self.getposition(data).size == 0:
                    self._skip(trade_date, stock_code, "buy", "below_lot")
            self.retry_days_left = self.p.order_retry_days

        sell_orders = []
        buy_orders = []
        for stock_code, data in self.stock_data.items():
            current_size = float(self.getposition(data).size)
            target_size = self.desired_sizes.get(stock_code, 0)
            delta = target_size - current_size
            if delta < -1e-9:
                sell_orders.append((stock_code, data, -delta))
            elif delta > 1e-9:
                buy_orders.append((stock_code, data, delta))

        for stock_code, data, size in sell_orders:
            allowed, reason = self._tradeable(data, stock_code, trade_date, "sell")
            if allowed:
                self.sell(data=data, size=size)
            else:
                self._skip(trade_date, stock_code, "sell", reason)
        for stock_code, data, size in buy_orders:
            allowed, reason = self._tradeable(data, stock_code, trade_date, "buy")
            if allowed:
                self.buy(data=data, size=size)
            else:
                self._skip(trade_date, stock_code, "buy", reason)
        self.retry_days_left -= 1

    def prenext_open(self):
        self._process_open()

    def nextstart_open(self):
        self._process_open()

    def next_open(self):
        self._process_open()

    def notify_order(self, order):
        if order.status not in [order.Completed, order.Canceled, order.Margin, order.Rejected]:
            return
        execution_date = None
        if order.executed.dt:
            execution_date = bt.num2date(order.executed.dt).date().isoformat()
        executed_size = abs(float(order.executed.size))
        executed_price = float(order.executed.price or 0.0)
        reference_open = float(order.data.open[0])
        traded_notional = executed_size * executed_price
        slippage_per_share = (
            executed_price - reference_open
            if order.isbuy()
            else reference_open - executed_price
        )
        slippage_cost = max(0.0, executed_size * slippage_per_share)
        market_amount = float(order.data.amount_cny[0])
        participation_rate = (
            traded_notional / market_amount
            if math.isfinite(market_amount) and market_amount > 0
            else None
        )
        self.order_records.append({
            "date": execution_date,
            "stock_code": order.data._name,
            "side": "buy" if order.isbuy() else "sell",
            "status": order.getstatusname(),
            "size": float(order.executed.size),
            "price": float(order.executed.price or 0.0),
            "value": float(order.executed.value or 0.0),
            "commission": float(order.executed.comm or 0.0),
            "reference_open": reference_open,
            "traded_notional": traded_notional,
            "slippage_cost": slippage_cost,
            "market_amount_cny": market_amount,
            "participation_rate": participation_rate,
        })


def _calendar_frame(calendar: list[str], start_date: str, end_date: str) -> pd.DataFrame:
    dates = pd.to_datetime([day for day in calendar if start_date <= day <= end_date])
    frame = pd.DataFrame(index=dates)
    for column in ["open", "high", "low", "close"]:
        frame[column] = 1.0
    frame["volume"] = 0.0
    frame["openinterest"] = 0.0
    return frame


def _stock_frame(rows: pd.DataFrame) -> pd.DataFrame:
    frame = rows.copy()
    frame.index = pd.to_datetime(frame.pop("date"))
    frame = frame.rename(columns={
        "open_raw": "open",
        "high_raw": "high",
        "low_raw": "low",
        "close_raw": "close",
        "volume_shares": "volume",
    })
    frame["openinterest"] = 0.0
    return frame[
        [
            "open", "high", "low", "close", "volume", "openinterest",
            "trade_status", "prev_close_raw", "is_st", "amount_cny",
        ]
    ]


def run_backtest(
    database: Path,
    signals: pd.DataFrame,
    calendar: list[str],
    start_date: str,
    end_date: str,
    config: ExecutionConfig,
) -> dict:
    config.validate()
    if signals.empty:
        raise ValueError("no signals were generated for the requested period")
    targets = {
        date: dict(zip(rows["stock_code"], rows["target_weight"]))
        for date, rows in signals.groupby("execution_date")
    }
    stock_codes = sorted(signals["stock_code"].astype(str).unique())
    security_transitions = load_security_transition_schedule(
        database, stock_codes, start_date, end_date
    )
    transition_targets = (
        security_transitions["target_stock_code"].astype(str).tolist()
        if not security_transitions.empty
        else []
    )
    loaded_stock_codes = sorted(set(stock_codes) | set(transition_targets))
    prices = load_execution_prices(database, loaded_stock_codes, start_date, end_date)
    corporate_actions = load_corporate_action_schedule(
        database, loaded_stock_codes, start_date, end_date
    )
    if prices.empty:
        raise ValueError("no execution prices were loaded")

    cerebro = bt.Cerebro(stdstats=False, cheat_on_open=True)
    cerebro.broker.setcash(config.initial_cash)
    cerebro.broker.set_coo(True)
    cerebro.broker.set_slippage_perc(config.slippage, slip_open=True, slip_match=True)
    cerebro.broker.addcommissioninfo(AshareCommission(
        broker_commission=config.broker_commission,
        minimum_commission=config.minimum_commission,
        sell_stamp_duty=config.sell_stamp_duty,
        transfer_fee=config.transfer_fee,
    ))
    calendar_data = bt.feeds.PandasData(dataname=_calendar_frame(calendar, start_date, end_date))
    cerebro.adddata(calendar_data, name="__calendar__")

    loaded_codes = []
    for stock_code, rows in prices.groupby("stock_code", sort=True):
        usable = rows.dropna(subset=["open_raw", "high_raw", "low_raw", "close_raw"])
        if usable.empty:
            continue
        feed = AshareData(dataname=_stock_frame(usable))
        cerebro.adddata(feed, name=str(stock_code))
        loaded_codes.append(str(stock_code))
    targets = {
        date: {code: weight for code, weight in weights.items() if code in loaded_codes}
        for date, weights in targets.items()
    }
    action_schedule = {
        date: rows.to_dict("records")
        for date, rows in corporate_actions.groupby("date", sort=False)
    }
    transition_schedule = {
        date: rows.to_dict("records")
        for date, rows in security_transitions.groupby("date", sort=False)
    }
    last_price_dates = prices.groupby("stock_code")["date"].max().to_dict()
    cerebro.addstrategy(
        MonthlyTargetStrategy,
        targets=targets,
        lot_size=config.lot_size,
        order_retry_days=config.order_retry_days,
        corporate_actions=action_schedule,
        security_transitions=transition_schedule,
        last_price_dates=last_price_dates,
        execution_config=config,
    )
    cerebro.addanalyzer(bt.analyzers.TimeReturn, _name="daily_returns", timeframe=bt.TimeFrame.Days)
    cerebro.addanalyzer(bt.analyzers.DrawDown, _name="drawdown")
    results = cerebro.run(runonce=False, preload=True)
    strategy = results[0]
    daily_returns = pd.Series(strategy.analyzers.daily_returns.get_analysis(), dtype=float)
    daily_returns.index = pd.to_datetime(daily_returns.index)
    daily_returns = daily_returns.sort_index()
    equity = config.initial_cash * (1.0 + daily_returns).cumprod()
    order_columns = [
        "date", "stock_code", "side", "status", "size", "price", "value",
        "commission", "reference_open", "traded_notional", "slippage_cost",
        "market_amount_cny", "participation_rate",
    ]
    skipped_columns = ["date", "stock_code", "side", "reason"]
    action_columns = [
        "date", "stock_code", "method", "validation_status", "old_size", "new_size",
        "share_multiplier", "cash_per_share", "cash_amount",
    ]
    transition_columns = [
        "date", "source_stock_code", "target_stock_code", "event_type",
        "source_size", "exchange_ratio", "theoretical_target_size",
        "converted_target_size", "rounding_share_difference", "existing_target_size",
        "resulting_target_size", "cash_amount", "verification_status",
    ]
    return {
        "daily_returns": daily_returns,
        "equity": equity,
        "orders": pd.DataFrame(strategy.order_records, columns=order_columns),
        "skipped_orders": pd.DataFrame(strategy.skipped_records, columns=skipped_columns),
        "corporate_actions": pd.DataFrame(
            strategy.corporate_action_records, columns=action_columns
        ),
        "security_transitions": pd.DataFrame(
            strategy.security_transition_records, columns=transition_columns
        ),
        "rebalance_dates": strategy.rebalance_dates,
        "final_value": float(cerebro.broker.getvalue()),
        "execution_config": asdict(config),
    }
