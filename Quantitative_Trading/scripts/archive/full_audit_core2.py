from pathlib import Path
import sys, sqlite3, json
ROOT = Path(__file__).resolve().parents[2]

sys.stdout.reconfigure(encoding='utf-8')
DB = ROOT / "data" / "csi300_2010_present.sqlite"
con = sqlite3.connect(DB)
def step(name, fn):
    print(f'[{name}] ...', flush=True)
    r = fn()
    print(f'[{name}] {r}', flush=True)
    return r

r1 = step('integrity', lambda: con.execute('PRAGMA quick_check').fetchone()[0])
r2 = step('counts', lambda: con.execute(
    "SELECT (SELECT COUNT(*) FROM daily_price_raw), (SELECT COUNT(*) FROM daily_price_adjusted), "
    "(SELECT COUNT(*) FROM daily_security_status), (SELECT COUNT(*) FROM trading_calendar)").fetchone())
r3 = step('price_adjusted_keydiff', lambda: con.execute(
    "SELECT COUNT(*) FROM daily_price_raw r LEFT JOIN daily_price_adjusted a USING(date, stock_code) WHERE a.stock_code IS NULL").fetchone()[0])
r4 = step('status_keydiff', lambda: con.execute(
    "SELECT COUNT(*) FROM daily_price_raw r LEFT JOIN daily_security_status s USING(date, stock_code) WHERE s.stock_code IS NULL").fetchone()[0])
r5 = step('status_disagree', lambda: con.execute(
    "SELECT COUNT(*) FROM daily_price_raw r JOIN daily_security_status s USING(date, stock_code) WHERE r.trade_status <> s.trade_status").fetchone()[0])
r6 = step('invalid_ohlc', lambda: con.execute(
    "SELECT COUNT(*) FROM daily_price_raw WHERE high_raw < low_raw OR high_raw < open_raw OR high_raw < close_raw OR low_raw > open_raw OR low_raw > close_raw").fetchone()[0])
r7 = step('calendar_diff', lambda: con.execute(
    "SELECT COUNT(*) FROM trading_calendar c WHERE is_trading_day=1 AND NOT EXISTS (SELECT 1 FROM daily_price_raw r WHERE r.date=c.date)").fetchone()[0])
r8 = step('universe', lambda: con.execute(
    "SELECT COUNT(*), COUNT(DISTINCT valid_from), MIN(valid_from), MAX(valid_to) FROM universe_membership").fetchone())
r9 = step('overlap', lambda: con.execute(
    "SELECT COUNT(*) FROM universe_membership a JOIN universe_membership b ON a.stock_code=b.stock_code AND a.index_code=b.index_code AND a.valid_from < b.valid_from AND b.valid_from < a.valid_to").fetchone()[0])
r10 = step('bad_sizes', lambda: con.execute(
    "SELECT COUNT(*) FROM (SELECT valid_from FROM universe_membership GROUP BY valid_from HAVING COUNT(*)<>300)").fetchone()[0])
r11 = step('snapshot_sizes', lambda: con.execute(
    "SELECT COUNT(*) FROM (SELECT effective_date FROM membership_snapshots GROUP BY effective_date HAVING COUNT(*)<>300)").fetchone()[0])
r12 = step('ca_counts', lambda: con.execute(
    "SELECT (SELECT COUNT(*) FROM corporate_actions), (SELECT COUNT(*) FROM adjustment_factor_events), "
    "(SELECT COUNT(*) FROM adjustment_factor_events WHERE validation_status='mismatch'), "
    "(SELECT COUNT(*) FROM adjustment_factor_events WHERE validation_status='factor_only'), "
    "(SELECT COUNT(*) FROM adjustment_factor_events WHERE validation_status='review'), "
    "(SELECT COUNT(*) FROM corporate_action_download_status WHERE status='failed')").fetchone())
r13 = step('transitions', lambda: con.execute(
    "SELECT COUNT(*) FROM security_transitions").fetchone()[0])
r14 = step('invalid_trans', lambda: con.execute(
    "SELECT COUNT(*) FROM security_transitions t LEFT JOIN security s ON s.stock_code=t.source_stock_code "
    "LEFT JOIN security d ON d.stock_code=t.target_stock_code "
    "LEFT JOIN daily_price_raw pb ON pb.stock_code=t.source_stock_code AND pb.date=t.record_date "
    "LEFT JOIN daily_price_raw pe ON pe.stock_code=t.target_stock_code AND pe.date=t.event_date "
    "WHERE s.stock_code IS NULL OR d.stock_code IS NULL OR pb.stock_code IS NULL OR pe.stock_code IS NULL "
    "OR t.exchange_ratio <= 0 OR t.verification_status <> 'official_sse_verified'").fetchone()[0])
r15 = step('st_rows', lambda: con.execute('SELECT COUNT(*) FROM daily_security_status WHERE is_st=1').fetchone()[0])
r16 = step('update_runs', lambda: con.execute(
    'SELECT run_id, status, new_max_date FROM update_runs ORDER BY completed_at DESC LIMIT 1').fetchone())
r17 = step('quality', lambda: con.execute(
    'SELECT issue_type, severity, COUNT(*) FROM data_quality_issues GROUP BY issue_type, severity').fetchall())
r18 = step('member_price_missing', lambda: con.execute("""
    SELECT date, stock_code FROM (
      SELECT c.date, m.stock_code FROM trading_calendar c JOIN universe_membership m
        ON c.date >= m.valid_from AND c.date < m.valid_to
      WHERE c.is_trading_day=1
      LEFT JOIN daily_price_raw r ON r.date=c.date AND r.stock_code=m.stock_code
      WHERE r.stock_code IS NULL)""").fetchall())
print('MEMBER_MISSING:', r18[:10], 'count=', len(r18))
con.close()
print('DONE')