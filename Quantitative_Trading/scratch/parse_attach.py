"""解析附件 Excel，提取沪深300调入/调出/备选名单"""
import sys, json, os
sys.stdout.reconfigure(encoding='utf-8')
import pandas as pd

FILES = {
    '201511302cons.xls':      '2015-11-30',
    '201605302cons.xls':      '2016-05-30',
    '20161212cons.xlsx':      '2016-11-28',
    '1511334410841442.xlsx':  '2017-05-31',
    '1519719074537344.xlsx':  '2017-11-27',
    '1535531071241051.xlsx':  '2018-05-28',
    '1560322933287092.xls':   '2019-06-03',
    '1576217266220014.xlsx':  '2019-12-02',
}

out = {}
for fn, date in FILES.items():
    path = os.path.join('scratch/attach', fn)
    xl = pd.ExcelFile(path)
    rec = {'date': date, 'file': fn}
    for sh in xl.sheet_names:
        df = pd.read_excel(path, sheet_name=sh, header=0)
        df.columns = [str(c).strip() for c in df.columns]
        hs300 = df[df.iloc[:, 1].astype(str).str.strip() == '沪深300']
        key = {'调入': 'add', '调出': 'remove', '备选名单': 'backup'}[sh]
        if key == 'backup':
            code_col, name_col = 3, 4
        else:
            code_col, name_col = 2, 3
        rows = hs300.iloc[:, code_col].astype(str).str.strip().tolist(), hs300.iloc[:, name_col].astype(str).str.strip().tolist()
        rec[key] = [{'code': str(c).zfill(6) if str(c).isdigit() else str(c), 'name': str(n)} for c, n in zip(*rows)]
    out[date] = rec
    print(date, {k: len(v) for k, v in rec.items() if isinstance(v, list)})

with open('scratch/hs300_adjustments.json', 'w', encoding='utf-8') as f:
    json.dump(out, f, ensure_ascii=False, indent=2)
print('\n已保存 scratch/hs300_adjustments.json')
