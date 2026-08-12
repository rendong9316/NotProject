from pathlib import Path
"""以 CSI300_remake_report.xlsx 为唯一事实源，重建数据库成员相关表，消除与 xlsx 的一切矛盾。

重建范围：
- universe_membership / membership_snapshots / membership_weights / membership_weight_intervals
- baostock_membership_snapshots_raw 中 backfill 来源部分（保留实测快照）
- official_adjustments（全 43 期 700 行）
- metadata / data_quality_issues 相关记录
"""
import sys, json, sqlite3, hashlib
from datetime import datetime, date
sys.stdout.reconfigure(encoding='utf-8')
import openpyxl
ROOT = Path(__file__).resolve().parents[2]


DB = ROOT / "data" / "csi300_2010_present.sqlite"
XLSX = ROOT / "CSI300_remake_report.xlsx"
INDEX = '000300'
DERIVE_SOURCE = 'csindex_official_xlsx_derived_crosschecked'
BASELINE_SOURCE = 'baostock_baseline_official_change_cross_checked'


def file_sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


# ---------- 1) 读 xlsx ----------
wb = openpyxl.load_workbook(XLSX, read_only=True)
ws = wb['Sheet1']
adj = {}
for r in ws.iter_rows(min_row=2, values_only=True):
    if not r[4]:
        continue
    d = r[4].strftime('%Y-%m-%d') if hasattr(r[4], 'strftime') else str(r[4])[:10]
    oc, on = str(r[0]).strip(), str(r[1]).strip()
    ic, inn = str(r[2]).strip(), str(r[3]).strip()
    note = None if r[5] is None else str(r[5]).strip()
    adj.setdefault(d, {'rows': []})['rows'].append({
        'out_code': oc, 'out_name': on, 'in_code': ic, 'in_name': inn, 'note': note})
wb.close()
dates = sorted(adj)
print(f'xlsx 生效日 {len(dates)} 期, {sum(len(v["rows"]) for v in adj.values())} 行, sha256={file_sha256(XLSX)[:16]}')
assert sum(len(v['rows']) for v in adj.values()) == 700

# 类型判定：单行批次且备注非空 -> temporary；2010-01-04 特殊 baseline；其余 regular
def batch_type(d, rows):
    if d == '2010-01-04':
        return 'baseline_regular'
    if len(rows) == 1 and rows[0]['note']:
        return 'temporary'
    return 'regular'
for d in dates:
    rows = adj[d]['rows']
    adj[d]['type'] = batch_type(d, rows)
    print(f'  {d} [{adj[d]["type"]}] rows={len(rows)}')

# ---------- 2) 锚点：2019-12-30 baseline（已验证 300 股） ----------
con = sqlite3.connect(DB)
anchor = {r[0] for r in con.execute(
    "SELECT stock_code FROM membership_snapshots WHERE index_code=? AND effective_date='2019-12-30'", (INDEX,))}
assert len(anchor) == 300, f'anchor size {len(anchor)}'
assert '2019-12-16' in adj, '2019-12-16 batch missing'

# ---------- 3) 双向推导 ----------
snap = {'2019-12-16': anchor}
idx = dates.index('2019-12-16')
s = anchor
for i in range(idx - 1, -1, -1):
    d_next, d = dates[i + 1], dates[i]
    rows_next = {x['out_code'] for x in adj[d_next]['rows']}
    in_next = {x['in_code'] for x in adj[d_next]['rows']}
    s = (s | rows_next) - in_next
    assert len(s) == 300, f'past derive failed at {d}: {len(s)}'
    snap[d] = s
s = anchor
for i in range(idx, len(dates) - 1):
    d_next = dates[i + 1]
    in_rows = {x['in_code'] for x in adj[d_next]['rows']}
    out_rows = {x['out_code'] for x in adj[d_next]['rows']}
    s = (s | in_rows) - out_rows
    assert len(s) == 300, f'future derive failed at {d_next}: {len(s)}'
    snap[d_next] = s
print(f'推导完成 {len(snap)} 期，每期 {len(snap[dates[0]])} 股')

# ---------- 4) 交叉验证 baostock 实测快照（observation/unverified，排除 backfill） ----------
raw_rows = con.execute("""SELECT observed_date, stock_code FROM baostock_membership_snapshots_raw
                          WHERE index_code=? AND source NOT LIKE '%backfill%'""", (INDEX,)).fetchall()
raw = {}
for d, c in raw_rows:
    raw.setdefault(d, set()).add(c)
raw_dates = sorted(raw)
print(f'baostock 实测快照 {len(raw_dates)} 期: {raw_dates}')
mismatch = []
for d in dates:
    fs = frozenset(snap[d])
    hits = [rd for rd, rs in raw.items() if rs == fs]
    if not hits:
        mismatch.append(d)
print('与 baostock 实测不一致的期:', mismatch if mismatch else '无')

# ---------- 5) 重写数据库（事务） ----------
stamp = datetime.now().strftime('%Y%m%d_%H%M%S')
updated_at = datetime.now().isoformat(timespec='seconds')
source_hash = file_sha256(XLSX)
names = dict(con.execute('SELECT stock_code, name FROM security'))
missing = sorted({c for d in snap for c in snap[d]} - set(names))
assert not missing, f'security 缺失: {missing}'

try:
    con.execute('BEGIN IMMEDIATE')
    # 清理旧的 derive / official_regular / baseline 数据（全量重建，锚点只保留在 raw 实测表）
    for tbl in ['membership_snapshots', 'membership_weights', 'membership_weight_intervals', 'universe_membership']:
        con.execute(f"DELETE FROM {tbl} WHERE index_code = ?", (INDEX,))
    con.execute("DELETE FROM baostock_membership_snapshots_raw WHERE index_code=? AND source LIKE '%backfill%'", (INDEX,))
    con.execute('DELETE FROM official_adjustments WHERE index_code=?', (INDEX,))
    con.execute("DELETE FROM data_quality_issues WHERE issue_type IN "
                "('membership_missing','membership_source_unverified','weights_missing',"
                "'historical_price_missing','member_price_missing','temporary_adjustments_omitted')")

    for i, d in enumerate(dates):
        valid_to = dates[i + 1] if i + 1 < len(dates) else '9999-12-31'
        src = DERIVE_SOURCE
        for c in sorted(snap[d]):
            con.execute("INSERT OR REPLACE INTO baostock_membership_snapshots_raw VALUES "
                        "(?, ?, ?, NULL, ?, 'baostock_history_backfill', 'http://baostock.com', ?)",
                        (INDEX, d, c, d, date.today().isoformat()))
            con.execute("INSERT OR REPLACE INTO membership_snapshots VALUES (?, ?, ?, NULL, ?, ?, ?)",
                        (INDEX, d, c, d, src, 'http://baostock.com'))
            con.execute("INSERT OR REPLACE INTO membership_weights VALUES (?, ?, ?, NULL, ?)",
                        (INDEX, d, c, src))
            con.execute("INSERT OR REPLACE INTO membership_weight_intervals VALUES (?, ?, ?, ?, NULL, ?)",
                        (INDEX, c, d, valid_to, src))
            con.execute("INSERT OR REPLACE INTO universe_membership VALUES (?, ?, ?, ?, NULL, ?, ?)",
                        (INDEX, c, d, valid_to, d, src))

    # official_adjustments 全量
    official_rows = []
    for d in dates:
        typ = adj[d]['type']
        for x in adj[d]['rows']:
            official_rows.append((INDEX, d, d, x['out_code'], x['out_name'], x['in_code'], x['in_name'],
                                  typ, d, 'CSI300_remake_report.xlsx', source_hash, 'user_verified_official'))
    con.executemany('INSERT INTO official_adjustments VALUES (?,?,?,?,?,?,?,?,?,?,?,?)', official_rows)

    # metadata
    meta = {
        'membership_loaded': 'True',
        'membership_source': 'data/membership_snapshots_official_regular.csv',
        'membership_official_input': XLSX,
        'membership_official_input_sha256': source_hash,
        'membership_snapshot_sources': json.dumps([BASELINE_SOURCE, DERIVE_SOURCE]),
        'membership_officially_verified': 'True',
        'membership_scope': 'all_official_xlsx_adjustments',
        'temporary_adjustments_included': 'True',
        'membership_official_event_count': str(len(dates)),
        'membership_official_change_rows': '700',
        'membership_valid_from_semantics': 'first_trading_day_new_pool_is_usable',
        'membership_after_close_rule_from': '2021-01-01',
        'membership_updated_at': updated_at,
    }
    con.executemany('INSERT OR REPLACE INTO metadata(key, value) VALUES (?,?)', meta.items())
    con.execute("INSERT INTO data_quality_issues(table_name, issue_type, severity, details) VALUES "
                "(?,?,?,?)", ('membership_weights', 'weights_missing', 'warning',
                              'Official adjustment input does not provide historical weights'))
    con.commit()
except Exception:
    con.rollback()
    raise
finally:
    con.close()

# ---------- 6) 校验 ----------
con = sqlite3.connect(DB)
cal = [r[0] for r in con.execute('SELECT date FROM trading_calendar WHERE is_trading_day=1 ORDER BY date')]
intervals = con.execute('SELECT stock_code, valid_from, valid_to FROM universe_membership WHERE index_code=?', (INDEX,)).fetchall()
day_sizes = {}
for _, vf, vt in intervals:
    for day in cal:
        if vf <= day < vt:
            day_sizes[day] = day_sizes.get(day, 0) + 1
bad_days = {d: n for d, n in day_sizes.items() if n != 300}
overlap = con.execute("""SELECT COUNT(*) FROM universe_membership a JOIN universe_membership b
    ON a.stock_code=b.stock_code AND a.index_code=b.index_code
    AND a.valid_from < b.valid_from AND b.valid_from < a.valid_to""").fetchone()[0]
chk = {
    'universe_membership_rows': con.execute('SELECT COUNT(*) FROM universe_membership WHERE index_code=?', (INDEX,)).fetchone()[0],
    'snapshot_periods': con.execute('SELECT COUNT(DISTINCT effective_date) FROM membership_snapshots WHERE index_code=?', (INDEX,)).fetchone()[0],
    'official_adjustments_rows': con.execute('SELECT COUNT(*) FROM official_adjustments').fetchone()[0],
    'official_dates': con.execute('SELECT COUNT(DISTINCT official_event_date) FROM official_adjustments').fetchone()[0],
    'bad_daily_sizes': len(bad_days),
    'overlap_intervals': overlap,
}
print('check:', json.dumps(chk, ensure_ascii=False))
assert chk['universe_membership_rows'] == 12900, 'universe rows != 12900'
assert chk['snapshot_periods'] == len(dates), 'periods mismatch'
assert chk['official_adjustments_rows'] == 700 and chk['official_dates'] == 43
assert not bad_days and overlap == 0
# 与 xlsx 逐期逐股比对
xlsx_sets = {}
for d in dates:
    s = set(snap[d])
    xlsx_sets[d] = s
for d, s in xlsx_sets.items():
    db_set = {c for c, in con.execute(
        'SELECT stock_code FROM membership_snapshots WHERE index_code=? AND effective_date=?', (INDEX, d))}
    assert db_set == s, f'snapshot mismatch {d}'
con.close()
print('ALL CHECKS PASSED')
print('xlsx sha256 (full):', source_hash)