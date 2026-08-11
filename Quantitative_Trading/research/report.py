from __future__ import annotations

import json
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


def performance_metrics(returns: pd.Series) -> dict[str, float | int | None]:
    clean = returns.dropna().astype(float)
    if clean.empty:
        return {"observations": 0}
    equity = (1.0 + clean).cumprod()
    total_return = float(equity.iloc[-1] - 1.0)
    annual_return = float(equity.iloc[-1] ** (252.0 / len(clean)) - 1.0)
    annual_volatility = float(clean.std(ddof=1) * np.sqrt(252.0)) if len(clean) > 1 else 0.0
    sharpe = float(clean.mean() / clean.std(ddof=1) * np.sqrt(252.0)) if clean.std(ddof=1) > 0 else None
    drawdown = equity / equity.cummax() - 1.0
    return {
        "observations": int(len(clean)),
        "total_return": total_return,
        "annual_return": annual_return,
        "annual_volatility": annual_volatility,
        "sharpe_zero_rate": sharpe,
        "max_drawdown": float(drawdown.min()),
    }


def benchmark_returns(frame: pd.DataFrame) -> pd.Series:
    series = frame.set_index(pd.to_datetime(frame["date"]))["close"].astype(float)
    return series.pct_change(fill_method=None).dropna()


def execution_diagnostics(orders: pd.DataFrame, equity: pd.Series) -> dict[str, float | int | None]:
    if orders.empty or "status" not in orders:
        return {"completed_orders": 0}
    completed = orders[orders["status"].eq("Completed")].copy()
    if completed.empty:
        return {"completed_orders": 0}
    traded_notional = float(completed["traded_notional"].sum())
    explicit_fees = float(completed["commission"].sum())
    slippage_cost = float(completed["slippage_cost"].sum())
    average_equity = float(equity.mean()) if not equity.empty else 0.0
    observations = max(len(equity), 1)
    participation = completed["participation_rate"].dropna().astype(float)
    return {
        "completed_orders": int(len(completed)),
        "gross_traded_notional": traded_notional,
        "explicit_fees": explicit_fees,
        "estimated_slippage_cost": slippage_cost,
        "total_modelled_execution_cost": explicit_fees + slippage_cost,
        "gross_turnover_over_average_equity": (
            traded_notional / average_equity if average_equity > 0 else None
        ),
        "annualized_gross_turnover": (
            traded_notional / average_equity * 252.0 / observations
            if average_equity > 0 else None
        ),
        "median_daily_amount_participation": (
            float(participation.median()) if not participation.empty else None
        ),
        "p95_daily_amount_participation": (
            float(participation.quantile(0.95)) if not participation.empty else None
        ),
        "max_daily_amount_participation": (
            float(participation.max()) if not participation.empty else None
        ),
    }


def write_report(
    output_dir: Path,
    strategy_name: str,
    signals: pd.DataFrame,
    result: dict,
    benchmark: pd.DataFrame,
    metadata: dict,
) -> dict:
    output_dir.mkdir(parents=True, exist_ok=True)
    strategy_returns = result["daily_returns"]
    benchmark_daily = benchmark_returns(benchmark)
    aligned = pd.concat(
        [strategy_returns.rename("strategy_return"), benchmark_daily.rename("benchmark_return")],
        axis=1,
    ).fillna(0.0)
    curves = (1.0 + aligned).cumprod()
    curves.columns = ["strategy_equity", "benchmark_equity"]
    curves.index.name = "date"

    warnings = [
        "Corporate actions use gross cash dividends; investor-specific dividend tax is not modelled.",
        "Factor-only events are treated as synthetic share multipliers and are not literal exchange settlements.",
        "Dividend receivables and bonus shares are recognized on the ex-date for daily-bar research.",
        "Merger share swaps use officially disclosed ratios and listing dates; registry-level fractional allocation is approximated to the nearest whole share.",
        "Membership includes official semiannual adjustments but omits temporary adjustment dates.",
        "Historical ST status is included, but IPO no-limit periods and exact daily price cages are unavailable.",
    ]
    if metadata.get("benchmark_return_type") == "price_index":
        warnings.append(
            "The CSI 300 benchmark is a price index and excludes dividends; excess return is not total-return comparable."
        )
    summary = {
        "strategy": strategy_name,
        "warnings": warnings,
        "strategy_metrics": performance_metrics(aligned["strategy_return"]),
        "benchmark_metrics": performance_metrics(aligned["benchmark_return"]),
        "signal_rows": int(len(signals)),
        "signal_dates": int(signals["signal_date"].nunique()),
        "rebalance_dates": len(result["rebalance_dates"]),
        "completed_orders": int((result["orders"].get("status") == "Completed").sum()) if not result["orders"].empty else 0,
        "skipped_orders": int(len(result["skipped_orders"])),
        "corporate_actions_applied": int(len(result["corporate_actions"])),
        "factor_fallback_actions": int(
            result["corporate_actions"]["method"].eq("factor_fallback").sum()
        ) if not result["corporate_actions"].empty else 0,
        "security_transitions_applied": int(len(result["security_transitions"])),
        "execution_diagnostics": execution_diagnostics(result["orders"], result["equity"]),
        "final_value": result["final_value"],
        "metadata": metadata,
        "execution_config": result["execution_config"],
    }
    signals.to_csv(output_dir / "signals.csv", index=False, encoding="utf-8-sig")
    result["orders"].to_csv(output_dir / "orders.csv", index=False, encoding="utf-8-sig")
    result["skipped_orders"].to_csv(output_dir / "skipped_orders.csv", index=False, encoding="utf-8-sig")
    result["corporate_actions"].to_csv(
        output_dir / "corporate_actions.csv", index=False, encoding="utf-8-sig"
    )
    result["security_transitions"].to_csv(
        output_dir / "security_transitions.csv", index=False, encoding="utf-8-sig"
    )
    curves.to_csv(output_dir / "equity_curve.csv", encoding="utf-8-sig")
    (output_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    axis = curves.plot(figsize=(11, 6), color=["#176B87", "#222222"], linewidth=1.5)
    axis.set_title(f"{strategy_name} vs CSI 300")
    axis.set_xlabel("Date")
    axis.set_ylabel("Growth of 1")
    axis.grid(alpha=0.25)
    figure = axis.get_figure()
    figure.tight_layout()
    figure.savefig(output_dir / "equity_curve.png", dpi=160)
    plt.close(figure)
    return summary
