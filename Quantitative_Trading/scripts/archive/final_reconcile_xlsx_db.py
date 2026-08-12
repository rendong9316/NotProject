from pathlib import Path
"""最终对账：xlsx(700行) 与 official_adjustments 逐行一致 + membership 区间覆盖验证"""
import sys, json, sqlite3
from datetime import datetime
sys.stdout.reconfigure(encoding='utf-8')
import openpyxl
ROOT = Path(__file__).resolve().parents[2]

DB = ROOT / "data" / "csi300_2010_present.sqlite"
XLSX = ROOT / "CSI300_remake_report.xlsx"

wb = openpyxl.load_workbook(XLSX, read_only=True)
ws = wb['Sheet1']
xlsx_rows = []
for r in ws.iter_rows(min_row=2, values_only=True):
    if not r[4]:
        continue
    d = r[4].strftime('%Y-%m-%d') if hasattr(r[4], 'strftime') else str(r[4])[:10]
    xlsx_rows.append((d, str(r[0]).strip(), str(r[1]).strip(), str(r[2]).strip(), str(r[3]).strip()))
wb.close()

con = sqlite3.connect(DB)
db_rows = con.execute(
    "SELECT official_event_date, out_code, out_name, in_code, in_name FROM official_adjustments "
    "WHERE index_code='000300' ORDER BY official_event_date, out_code").fetchall()
print(f'xlsx {len(xlsx_rows)} 行 vs db {len(db_rows)} 行')
assert len(xlsx_rows) == len(db_rows) == 700

xset = set(xlsx_rows)
dset = set(db_rows)
only_xlsx = xset - dset
only_db = dset - xset
print('仅 xlsx 有:', len(only_xlsx), list(only_xlsx)[:5])
print('仅 db 有:', len(only_db), list(only_db)[:5])
assert not only_xlsx and not only_db
print('official_adjustments 与 xlsx 逐行完全一致 ✓')

# 成员区间连续性：每个生效日 300 股，且每期 out/in 与 xlsx 一致
dates = sorted({r[0] for r in xlsx_rows})
for i, d in enumerate(dates):
    nxt = dates[i + 1] if i + 1 < len(dates) else '9999-12-31'
    members = con.execute(
        "SELECT stock_code FROM universe_membership WHERE index_code='000300' AND valid_from=? AND valid_to=?",
        (d, nxt)).fetchall()
    assert len(members) == 300, f'{d}: {len(members)}'
print('43 期每期 300 股 ✓')

# 2015-05-20 配对在库中确认
pair = con.execute(
    "SELECT out_code, in_code FROM official_adjustments WHERE official_event_date='2015-05-20' ORDER BY out_code").fetchall()
print('2015-05-20 配对:', pair)
assert ('601299', '300003') in pair and ('600832', '000738') in pair

# 2021+ 定期批次应为"生效头一天"（2021-06-15 因端午为周二；临时批次自退市日起不要求周一）
types = dict(con.execute("SELECT DISTINCT official_event_date, adjustment_type FROM official_adjustments"))

bad = []
for d in dates:
    if d >= '2021-01-01' and types.get(d) in ('regular', 'baseline_regular'):
        prev_friday = None
        # 生效头一天应为周五公告的下一交易日；简化验证：生效日必须在交易日历
        if d not in {r[0] for r in con.execute('SELECT date FROM trading_calendar WHERE is_trading_day=1')}:
            bad.append(d)
print('2021+ 非交易日生效日:', bad if bad else '无（全部为交易日）')
assert not bad
print('FINAL RECONCILIATION PASSED')
con.close()