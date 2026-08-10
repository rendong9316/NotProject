from __future__ import annotations

import math
from dataclasses import asdict
from pathlib import Path

import backtrader as bt
import pandas as pd

from .config import ExecutionConfig
from .data import load_corporate_action_schedule, load_execution_prices


class AshareData(bt.feeds.PandasData):
    lines = ("trade_status", "prev_close_raw", "is_st")
    params = (
        ("trade_status", "trade_status"),
        ("prev_close_raw", "prev_close_raw"),
        ("is_st", "is_st"),
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


class MonthlyTargetStrategy(bt.Strategy):
    params = (
        ("targets", None),
        ("lot_size", 100),
        ("order_retry_days", 5),
        ("corporate_actions", None),
        ("execution_config", None),
    )

    def __init__(self):
        self.calendar = self.datas[0]
        self.stock_data = {data._name: data for data in self.datas[1:]}
        self.order_records: list[dict] = []
        self.skipped_records: list[dict] = []
        self.corporate_action_records: list[dict] = []
        self.rebalance_dates: list[str] = []
        self.desired_sizes: dict[str, float] = {}
        self.retry_days_left = 0

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

    def _apply_corporate_actions(self, trade_date: str) -> float:
        pending_cash = 0.0
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
                pending_cash += cash_amount
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
        return pending_cash

    def _portfolio_value_at_open(self, trade_date: str, pending_cash: float) -> float:
        value = float(self.broker.getcash()) + pending_cash
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
        pending_action_cash = self._apply_corporate_actions(trade_date)
        target_weights = (self.p.targets or {}).get(trade_date)
        if target_weights is None and self.retry_days_left <= 0:
            return
        if target_weights is not None:
            self.rebalance_dates.append(trade_date)
            portfolio_value = self._portfolio_value_at_open(trade_date, pending_action_cash)
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
        self.order_records.append({
            "date": execution_date,
            "stock_code": order.data._name,
            "side": "buy" if order.isbuy() else "sell",
            "status": order.getstatusname(),
            "size": float(order.executed.size),
            "price": float(order.executed.price or 0.0),
            "value": float(order.executed.value or 0.0),
            "commission": float(order.executed.comm or 0.0),
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
            "trade_status", "prev_close_raw", "is_st",
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
    prices = load_execution_prices(database, stock_codes, start_date, end_date)
    corporate_actions = load_corporate_action_schedule(
        database, stock_codes, start_date, end_date
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
    cerebro.addstrategy(
        MonthlyTargetStrategy,
        targets=targets,
        lot_size=config.lot_size,
        order_retry_days=config.order_retry_days,
        corporate_actions=action_schedule,
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
    order_columns = ["date", "stock_code", "side", "status", "size", "price", "value", "commission"]
    skipped_columns = ["date", "stock_code", "side", "reason"]
    action_columns = [
        "date", "stock_code", "method", "validation_status", "old_size", "new_size",
        "share_multiplier", "cash_per_share", "cash_amount",
    ]
    return {
        "daily_returns": daily_returns,
        "equity": equity,
        "orders": pd.DataFrame(strategy.order_records, columns=order_columns),
        "skipped_orders": pd.DataFrame(strategy.skipped_records, columns=skipped_columns),
        "corporate_actions": pd.DataFrame(
            strategy.corporate_action_records, columns=action_columns
        ),
        "rebalance_dates": strategy.rebalance_dates,
        "final_value": float(cerebro.broker.getvalue()),
        "execution_config": asdict(config),
    }
