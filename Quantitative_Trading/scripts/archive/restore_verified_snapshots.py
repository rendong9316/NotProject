from pathlib import Path
"""恢复被 backfill 覆盖的 8 期 baostock 实测快照（source 应保留 unverified/observation）"""
import sys, sqlite3
ROOT = Path(__file__).resolve().parents[2]

sys.stdout.reconfigure(encoding='utf-8')
BAK = ROOT / "data" / "backups" / "csi300_2010_present_before_xlsx_sync_20260812.sqlite"
CUR = ROOT / "data" / "csi300_2010_present.sqlite"

bak = sqlite3.connect(BAK)
cur = sqlite3.connect(CUR)

bak_verified = {r[0] for r in bak.execute(
    "SELECT DISTINCT observed_date FROM baostock_membership_snapshots_raw "
    "WHERE source NOT LIKE '%backfill%'")}
print('备份中的实测快照:', sorted(bak_verified))

cur_backfill = {r[0] for r in cur.execute(
    "SELECT DISTINCT observed_date FROM baostock_membership_snapshots_raw "
    "WHERE source LIKE '%backfill%'")}
overlap = sorted(bak_verified & cur_backfill)
print('被覆盖的实测日期(与当前 backfill 重叠):', overlap)

restored = []
for d in overlap:
    bak_rows = bak.execute(
        "SELECT index_code, observed_date, stock_code, weight, announcement_date, source, source_url, archived_at "
        "FROM baostock_membership_snapshots_raw WHERE observed_date=? AND source NOT LIKE '%backfill%' "
        "ORDER BY stock_code", (d,)).fetchall()
    cur_src = cur.execute("SELECT DISTINCT source FROM baostock_membership_snapshots_raw WHERE observed_date=?", (d,)).fetchall()
    cur_bk = cur.execute(
        "SELECT stock_code FROM baostock_membership_snapshots_raw WHERE observed_date=? AND source LIKE '%backfill%'",
        (d,)).fetchall()
    bak_codes = {r[2] for r in bak_rows}
    cur_codes = {r[0] for r in cur_bk}
    same = bak_codes == cur_codes and len(bak_rows) == 300
    for r in bak_rows:
        cur.execute("INSERT OR REPLACE INTO baostock_membership_snapshots_raw VALUES (?,?,?,?,?,?,?,?)", r)
    restored.append((d, bak_rows[0][5] if bak_rows else '?', 'content_same' if same else 'CONTENT_DIFF'))
cur.commit()
print('已恢复:', restored)
for s in ('baostock_history_snapshot_observation', 'baostock_history_snapshot_unverified'):
    n = cur.execute('SELECT COUNT(*), COUNT(DISTINCT observed_date) FROM baostock_membership_snapshots_raw WHERE source=?', (s,)).fetchone()
    print(f'{s}: {n}')
print('raw total:', cur.execute('SELECT COUNT(*), COUNT(DISTINCT observed_date) FROM baostock_membership_snapshots_raw').fetchone())
cur.close()
bak.close()