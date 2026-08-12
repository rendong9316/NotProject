"""官方抽查：1) 2026-07-31 官方权重文件 vs 库最新成员 vs components 2) 官网公告文本抽查关键批次"""
import sys, csv, json, sqlite3, hashlib
from pathlib import Path
sys.stdout.reconfigure(encoding='utf-8')
ROOT = Path(__file__).resolve().parents[2]
con = sqlite3.connect(ROOT / "data" / "csi300_2010_present.sqlite")

# ---- 1. 官方权重文件 ----
wpath = ROOT / "evidence" / "csi300_official_weights_20260731.csv"
rows = list(csv.reader(open(wpath, encoding='utf-8-sig')))
print('权重文件头部:', rows[0])
wrows = rows[1:]
print('权重文件行数:', len(wrows))
wcodes = set()
for r in wrows:
    if len(r) >= 2:
        wcodes.add(str(r[0]).strip().zfill(6))
print('权重文件代码集大小:', len(wcodes))
for r in wrows[:3]:
    print('  权重样本:', r)

# 最新成员期（2026-06-15 生效）
latest = con.execute(
    "SELECT DISTINCT valid_from FROM universe_membership ORDER BY valid_from DESC LIMIT 1").fetchone()[0]
members = {c for c, in con.execute(
    "SELECT stock_code FROM universe_membership WHERE index_code='000300' AND valid_from=?", (latest,))}
print('库最新成员期:', latest, len(members))
diff_w = wcodes - members
diff_m = members - wcodes
print('权重文件独有:', sorted(diff_w)[:10], f'({len(diff_w)})')
print('库独有:', sorted(diff_m)[:10], f'({len(diff_m)})')

# components_snapshot
comp = {c for c, in con.execute('SELECT stock_code FROM components_snapshot')}
print('components_snapshot:', len(comp), '| diff vs members:', len(comp ^ members))

# ---- 2. 官网公告正文抽查 ----
news = json.load(open(ROOT / "evidence" / "news2020_2026.json", encoding='utf-8'))
print('\n官网公告抽查（news2020_2026.json）:')
import re
checks = [
    ('12470', '2021-06-15'), ('13888', '2021-12-13'), ('14223', '2022-06-13'),
    ('14497', '2022-12-12'), ('14796', '2023-06-12'), ('15044', '2023-12-11'),
    ('15267', '2024-06-17'), ('15471', '2024-12-16'), ('15690', '2025-06-16'),
    ('3006000', '2025-12-15'), ('3006137', '2026-06-15'),
    ('15546', '2025-03-04'), ('1006022', '2025-09-05'),
]
for aid, expect in checks:
    d = news.get(aid)
    if not d:
        print(f'  {aid}: 缺公告'); continue
    body = d.get('body', '')[:200].replace('\n', ' ')
    print(f'  {aid} [{d.get("publish_date")}] 期望生效 {expect}: {body[:90]}')

# ---- 3. 抽查官方权重文件中最新批次调入股 ----
print('\n2026-06-15 批次调入股权重抽查:')
for oc, on, ic, inn in con.execute(
    "SELECT out_code, out_name, in_code, in_name FROM official_adjustments WHERE official_event_date='2026-06-15' ORDER BY in_code"):
    pass
in_codes = [r[0] for r in con.execute(
    "SELECT in_code FROM official_adjustments WHERE official_event_date='2026-06-15'")]
for c in in_codes[:5]:
    hit = [r for r in wrows if str(r[0]).strip().zfill(6) == c]
    print(f'  {c}: 权重文件存在={bool(hit)}' + (f' 权重={hit[0][2] if len(hit[0])>2 else "?"}' if hit else ''))
con.close()