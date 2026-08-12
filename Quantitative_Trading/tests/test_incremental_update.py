from __future__ import annotations

import json
import sqlite3
from pathlib import Path

import pandas as pd
import pytest

from scripts.build.build_csi300_sqlite import SCHEMA
from scripts.build.update_csi300_incremental import (
    AUDIT_SCHEMA,
    Stage,
    adjustment_scale,
    apply_stage,
    normalize_adjusted,
    normalize_raw,
    stage_from_archive,
    replay_archives,
    verify_archive,
    write_archive,
)


def raw_source(dates: list[str]) -> pd.DataFrame:
    return pd.DataFrame({
        "date": dates,
        "open": ["10.0"] * len(dates),
        "high": ["10.5"] * len(dates),
        "low": ["9.5"] * len(dates),
        "close": ["10.2"] * len(dates),
        "volume": ["100000"] * len(dates),
        "amount": ["1020000"] * len(dates),
        "tradestatus": ["1"] * len(dates),
        "isST": ["0"] * len(dates),
    })


def adjusted_source(dates: list[str], closes: list[float]) -> pd.DataFrame:
    return pd.DataFrame({
        "date": dates,
        "open": closes,
        "high": [value + 0.2 for value in closes],
        "low": [value - 0.2 for value in closes],
        "close": closes,
    })


def test_normalize_baostock_frames():
    raw, status = normalize_raw(
        "000001", raw_source(["2024-01-02"]), "2024-01-03T18:00:00"
    )
    adjusted = normalize_adjusted(
        "000001", adjusted_source(["2024-01-02"], [9.8])
    )
    assert raw.loc[0, "volume_shares"] == 100000
    assert raw.loc[0, "trade_status"] == 1
    assert status.loc[0, "is_st"] == 0
    assert adjusted.loc[0, "close_adj"] == pytest.approx(9.8)


def test_adjustment_scale_and_full_reload_detection(tmp_path: Path):
    database = tmp_path / "scale.sqlite"
    with sqlite3.connect(database) as conn:
        conn.execute(
            "CREATE TABLE daily_price_adjusted "
            "(date TEXT, stock_code TEXT, close_adj REAL)"
        )
        conn.executemany(
            "INSERT INTO daily_price_adjusted VALUES (?, '000001', ?)",
            [("2024-01-02", 10.0), ("2024-01-03", 11.0)],
        )
        constant = pd.DataFrame({
            "date": ["2024-01-02", "2024-01-03"],
            "close_adj": [5.0, 5.5],
        })
        scale, mode = adjustment_scale(conn, "000001", constant)
        assert scale == pytest.approx(0.5)
        assert mode == "scale_and_upsert"

        nonconstant = pd.DataFrame({
            "date": ["2024-01-02", "2024-01-03"],
            "close_adj": [5.0, 6.0],
        })
        scale, mode = adjustment_scale(conn, "000001", nonconstant)
        assert scale == 1.0
        assert mode == "full_reload"


def make_database(path: Path) -> None:
    with sqlite3.connect(path) as conn:
        conn.executescript(SCHEMA)
        conn.executescript(
            "CREATE TABLE daily_security_status ("
            "date TEXT NOT NULL, stock_code TEXT NOT NULL, is_st INTEGER NOT NULL, "
            "trade_status INTEGER NOT NULL, source TEXT NOT NULL, downloaded_at TEXT NOT NULL, "
            "PRIMARY KEY(date, stock_code));"
        )
        conn.execute(
            "INSERT INTO security VALUES ('000001', 'Test', 'SZ', '2024-01-02', '2024-01-03')"
        )
        for day in ["2024-01-02", "2024-01-03"]:
            conn.execute("INSERT INTO trading_calendar VALUES (?, 1)", (day,))
            conn.execute(
                "INSERT INTO daily_price_raw VALUES (?, '000001', 10, 10.5, 9.5, 10.2, "
                "100000, 1020000, 1, 'fixture')",
                (day,),
            )
            conn.execute(
                "INSERT INTO daily_price_adjusted VALUES (?, '000001', 10, 10.5, 9.5, 10.2, 'fixture')",
                (day,),
            )
            conn.execute(
                "INSERT INTO daily_security_status VALUES (?, '000001', 0, 1, 'fixture', 'now')",
                (day,),
            )
        conn.execute(
            "INSERT INTO membership_snapshots VALUES "
            "('000300', '2024-01-02', '000001', NULL, NULL, 'fixture', NULL)"
        )
        conn.execute(
            "INSERT INTO membership_weights VALUES "
            "('000300', '2024-01-02', '000001', NULL, 'fixture')"
        )
        conn.execute(
            "INSERT INTO membership_weight_intervals VALUES "
            "('000300', '000001', '2024-01-02', '9999-12-31', NULL, 'fixture')"
        )
        conn.execute(
            "INSERT INTO universe_membership VALUES "
            "('000300', '000001', '2024-01-02', '9999-12-31', NULL, NULL, 'fixture')"
        )
        conn.execute("INSERT INTO metadata VALUES ('membership_official_event_count', '0')")


def make_stage(tmp_path: Path) -> Stage:
    dates = ["2024-01-02", "2024-01-03", "2024-01-04"]
    raw, status = normalize_raw("000001", raw_source(dates), "2024-01-04T18:00:00")
    adjusted = normalize_adjusted(
        "000001", adjusted_source(dates, [5.1, 5.1, 5.2])
    )
    benchmark = pd.DataFrame({
        "date": dates,
        "open": [1.0] * 3,
        "high": [1.0] * 3,
        "low": [1.0] * 3,
        "close": [1.0] * 3,
        "volume": [0.0] * 3,
        "amount": [0.0] * 3,
    })
    action_columns = [
        "stock_code", "ex_date", "action_sequence", "announcement_date",
        "action_type", "record_date", "payment_date", "shares_credit_date",
        "report_period", "cash_dividend_gross_per_10", "stock_dividend_per_10",
        "transfer_per_10", "description", "source", "downloaded_at",
    ]
    return Stage(
        run_id="20240104_180000_000000",
        started_at="2024-01-04T18:00:00",
        previous_max_date="2024-01-03",
        requested_end_date="2024-01-04",
        raw=raw,
        adjusted=adjusted,
        status=status,
        observations=pd.DataFrame(columns=[
            "query_date", "source_update_date", "stock_code", "stock_name", "source",
        ]),
        benchmark=benchmark,
        actions=pd.DataFrame([{
            "stock_code": "000001",
            "ex_date": "2024-01-04",
            "action_sequence": 1,
            "announcement_date": "2023-12-20",
            "action_type": "dividend",
            "record_date": "2024-01-03",
            "payment_date": "2024-01-04",
            "shares_credit_date": None,
            "report_period": "2023",
            "cash_dividend_gross_per_10": 2.0,
            "stock_dividend_per_10": 0.0,
            "transfer_per_10": 0.0,
            "description": "fixture",
            "source": "fixture",
            "downloaded_at": "2024-01-04T18:00:00",
        }], columns=action_columns),
        action_errors={},
        attempted_codes=["000001"],
        archive_dir=tmp_path / "increment",
    )


def test_archive_checksum_and_transactional_apply(tmp_path: Path):
    database = tmp_path / "research.sqlite"
    make_database(database)
    excel = tmp_path / "official.xlsx"
    excel.write_bytes(b"fixture")
    stage = make_stage(tmp_path)
    manifest = write_archive(stage, excel)
    assert manifest["files"]["prices_raw.csv"]["rows"] == 3
    assert verify_archive(stage.archive_dir)["run_id"] == stage.run_id
    restored = stage_from_archive(stage.archive_dir)
    assert len(restored.raw) == 3
    assert restored.attempted_codes == ["000001"]

    result = apply_stage(database, stage, excel)
    assert result["status"] == "success"
    with sqlite3.connect(database) as conn:
        assert conn.execute("SELECT MAX(date) FROM trading_calendar").fetchone()[0] == "2024-01-04"
        assert conn.execute("SELECT COUNT(*) FROM update_runs").fetchone()[0] == 1
        assert conn.execute(
            "SELECT COUNT(*) FROM daily_price_raw WHERE date='2024-01-04'"
        ).fetchone()[0] == 1
        assert conn.execute(
            "SELECT COUNT(*) FROM daily_security_status WHERE date='2024-01-04'"
        ).fetchone()[0] == 1
        metadata = dict(conn.execute("SELECT key, value FROM metadata"))
        assert metadata["daily_security_status_rows"] == "3"
        assert metadata["daily_security_status_st_rows"] == "0"
        assert metadata["daily_security_status_downloaded_at"] == "2024-01-04T18:00:00"
        action_status = conn.execute(
            "SELECT status, row_count, downloaded_at FROM corporate_action_download_status "
            "WHERE stock_code='000001'"
        ).fetchone()
        assert action_status[:2] == ("success", 1)
        assert action_status[2]

    assert apply_stage(database, stage, excel)["status"] == "already_applied"
    manifest_path = stage.archive_dir / "manifest.json"
    payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    payload["files"]["prices_raw.csv"]["sha256"] = "0" * 64
    manifest_path.write_text(json.dumps(payload), encoding="utf-8")
    with pytest.raises(ValueError, match="checksum mismatch"):
        verify_archive(stage.archive_dir)


def test_replay_accepts_one_increment_directory(tmp_path: Path):
    database = tmp_path / "research.sqlite"
    make_database(database)
    excel = tmp_path / "official.xlsx"
    excel.write_bytes(b"fixture")
    stage = make_stage(tmp_path)
    write_archive(stage, excel)
    results = replay_archives(
        database, stage.archive_dir, excel, finalize_official=False
    )
    assert results[0]["status"] == "success"


def test_replay_skips_superseded_retry_archive(tmp_path: Path):
    database = tmp_path / "research.sqlite"
    make_database(database)
    excel = tmp_path / "official.xlsx"
    excel.write_bytes(b"fixture")
    parent = tmp_path / "increments"
    first = make_stage(tmp_path)
    first.archive_dir = parent / "001"
    write_archive(first, excel)
    retry = make_stage(tmp_path)
    retry.run_id = "20240104_180100_000000"
    retry.archive_dir = parent / "002"
    write_archive(retry, excel)
    results = replay_archives(
        database, parent, excel, finalize_official=False
    )
    assert [item["status"] for item in results] == [
        "success", "skipped_superseded",
    ]


def test_noop_stage_preserves_security_update_state(tmp_path: Path):
    database = tmp_path / "research.sqlite"
    make_database(database)
    excel = tmp_path / "official.xlsx"
    excel.write_bytes(b"fixture")
    first = make_stage(tmp_path)
    apply_stage(database, first, excel)
    with sqlite3.connect(database) as conn:
        before = conn.execute(
            "SELECT status, last_success_at FROM security_update_state "
            "WHERE stock_code='000001'"
        ).fetchone()
    noop = make_stage(tmp_path)
    noop.run_id = "20240104_190000_000000"
    noop.previous_max_date = "2024-01-04"
    noop.raw = noop.raw.iloc[0:0]
    noop.adjusted = noop.adjusted.iloc[0:0]
    noop.status = noop.status.iloc[0:0]
    noop.actions = noop.actions.iloc[0:0]
    noop.attempted_codes = []
    apply_stage(database, noop, excel)
    with sqlite3.connect(database) as conn:
        after = conn.execute(
            "SELECT status, last_success_at FROM security_update_state "
            "WHERE stock_code='000001'"
        ).fetchone()
    assert after == before
