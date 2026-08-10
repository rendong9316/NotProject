from __future__ import annotations

from dataclasses import dataclass


STRATEGIES = {"equal_weight", "momentum", "low_vol", "momentum_low_vol"}


@dataclass(frozen=True)
class FactorConfig:
    strategy: str = "momentum_low_vol"
    top_n: int = 30
    momentum_lookback: int = 252
    momentum_skip: int = 21
    volatility_lookback: int = 60
    liquidity_lookback: int = 20
    liquidity_exclusion_quantile: float = 0.20
    invest_fraction: float = 0.95

    def validate(self) -> None:
        if self.strategy not in STRATEGIES:
            raise ValueError(f"unknown strategy: {self.strategy}")
        if self.top_n <= 0:
            raise ValueError("top_n must be positive")
        if self.momentum_lookback <= self.momentum_skip:
            raise ValueError("momentum_lookback must exceed momentum_skip")
        if not 0 <= self.liquidity_exclusion_quantile < 1:
            raise ValueError("liquidity_exclusion_quantile must be in [0, 1)")
        if not 0 < self.invest_fraction <= 1:
            raise ValueError("invest_fraction must be in (0, 1]")


@dataclass(frozen=True)
class ExecutionConfig:
    initial_cash: float = 10_000_000.0
    lot_size: int = 100
    broker_commission: float = 0.0003
    minimum_commission: float = 5.0
    sell_stamp_duty: float = 0.001
    sell_stamp_duty_current: float = 0.0005
    transfer_fee: float = 0.00001
    transfer_fee_legacy: float = 0.00002
    use_historical_fee_schedule: bool = True
    slippage: float = 0.001
    order_retry_days: int = 5

    def validate(self) -> None:
        if self.initial_cash <= 0:
            raise ValueError("initial_cash must be positive")
        if self.lot_size <= 0:
            raise ValueError("lot_size must be positive")
        if self.order_retry_days <= 0:
            raise ValueError("order_retry_days must be positive")
        for name in [
            "broker_commission", "sell_stamp_duty", "sell_stamp_duty_current",
            "transfer_fee", "transfer_fee_legacy", "slippage",
        ]:
            if getattr(self, name) < 0:
                raise ValueError(f"{name} cannot be negative")
