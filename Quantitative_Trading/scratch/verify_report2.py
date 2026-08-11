"""核验比对（第二阶段：解析+比对，数据已抓取）"""
import sys, json, re, os
sys.stdout.reconfigure(encoding='utf-8')
import pandas as pd
import openpyxl

def norm_code(c):
    c = str(c).strip()
    m = re.search(r'\d{6}', c)
    return m.group(0) if m else c

def parse_body_list(text):
    text = text.replace('沪深300', '沪深 300')
    tokens = [ln.strip() for ln in text.split('\n') if ln.strip()]
    add, remove = [], []

    def is_stop(tok):
        return bool(re.match(r'^中证\s*(100|500|香港|红利)', tok) or
                    re.match(r'^沪深\s*300\s*指数备选名单', tok) or
                    tok.startswith('附'))

    i0 = -1
    for k, t in enumerate(tokens):
        if t == '股票代码':
            i0 = k
            break
    if i0 >= 0:
        i = i0 + 4
        while i + 3 < len(tokens):
            if is_stop(tokens[i]):
                break
            if re.match(r'^[A-Z0-9.\-]{2,10}$', tokens[i]) and re.match(r'^[A-Z0-9.\-]{2,10}$', tokens[i + 2]):
                remove.append({'code': norm_code(tokens[i]), 'name': tokens[i + 1]})
                add.append({'code': norm_code(tokens[i + 2]), 'name': tokens[i + 3]})
                i += 4
            else:
                i += 1
    return add, remove

def parse_attach(path):
    xl = pd.ExcelFile(path)
    add, remove = [], []
    for sh in xl.sheet_names:
        s = sh.strip()
        try:
            df = pd.read_excel(path, sheet_name=sh, header=0)
        except Exception:
            continue
        df.columns = [str(c).strip() for c in df.columns]
        if len(df.columns) < 4:
            df = pd.read_excel(path, sheet_name=sh, header=None)
        rows = df.values.tolist()
        if s in ('调入', '调出'):
            for r in rows:
                row = [str(x).strip() for x in r.tolist()] if hasattr(r, 'tolist') else [str(x).strip() for x in r]
                if len(row) < 4 or row[1] != '沪深300':
                    continue
                item = {'code': norm_code(row[2]), 'name': row[3]}
                (add if s == '调入' else remove).append(item)
        elif '备选' in s:
            for r in rows:
                row = [str(x).strip() for x in r.tolist()] if hasattr(r, 'tolist') else [str(x).strip() for x in r]
                if len(row) < 5 or row[1] != '沪深300':
                    continue
                add.append({'code': norm_code(row[3]), 'name': row[4]})
    return add, remove

def parse_2025(df_plain):
    add, remove = [], []
    for r in df_plain.values.tolist():
        row = [str(x).strip() for x in r]
        if not row or row[0] not in ('000300', '沪深300'):
            continue
        if len(row) > 4 and row[2] and row[2] != 'nan':
            remove.append({'code': norm_code(row[2]), 'name': row[3]})
        if len(row) > 5 and row[4] and row[4] != 'nan':
            add.append({'code': norm_code(row[4]), 'name': row[5]})
    return add, remove

old_bodies = json.load(open('scratch/csindex_announcements_2010s.json', encoding='utf-8'))
verify_news = {f.replace('.json', ''): json.load(open(f'scratch/verify_news/{f}', encoding='utf-8'))
               for f in os.listdir('scratch/verify_news') if f.endswith('.json')}

# 每个公告的名单来源
SOURCES = {
    '6150':    {'src': 'body',    'use_old': False},
    '4003':    {'src': 'attach',  'file': 'scratch/verify_attach/4003_20161212cons.xlsx'},
    '11529':   {'src': 'attach',  'file': 'scratch/verify_attach/11529_1576217266220014.xlsx'},
    '11429':   {'src': 'attach',  'file': 'scratch/verify_attach/11429_1590972336738429.xlsx'},
    '1335':    {'src': 'body',    'use_old': False},
    '1607':    {'src': 'body',    'use_old': True},
    '792':     {'src': 'body',    'use_old': True},
    '3453':    {'src': 'body',    'use_old': True},
    '6271':    {'src': 'none'},
    '6802':    {'src': 'none'},
    '15546':   {'src': 'attach2025', 'file': 'scratch/verify_attach/15546_20250206175421-%E6%8C%87%E6%95%B0%E6%A0%B7%E6%9C%AC%E8%B0%83%E6%95%B4%E5%90%8D%E5%8D%95.xlsx'},
    '1006022': {'src': 'attach2025', 'file': 'scratch/verify_attach/1006022_20250722164043-%E6%8C%87%E6%95%B0%E6%A0%B7%E6%9C%AC%E8%B0%83%E6%95%B4%E5%90%8D%E5%8D%95.xlsx'},
}
EXPECT = {
    '6150': '2011-01-04', '4003': '2016-12-12', '11529': '2019-12-16', '11429': '2020-06-15',
    '1335': '2010-01-04', '1607': '2010-07-29', '792': '2011-08-23', '3453': '2013-09-18',
    '6271': '2015-01-26', '6802': '2015-05-20', '15546': '2025-03-04', '1006022': '2025-09-05',
}

report = openpyxl.load_workbook('CSI300_remake_report.xlsx')['Sheet1']
rep = {}
for r in report.iter_rows(min_row=2, values_only=True):
    if r[4]:
        d = str(r[4])[:10]
        rep.setdefault(d, []).append({
            'out': str(r[0]).strip(), 'out_n': r[1],
            'in': str(r[2]).strip() if r[2] else '', 'in_n': r[3],
        })

print('========== 核验结果（官网公告 vs 报告） ==========')
all_ok = True
for nid, info in SOURCES.items():
    eff = EXPECT[nid]
    rep_rows = rep.get(eff, [])
    print(f'\n--- {nid} (生效 {eff}) 报告{len(rep_rows)}条 ---')
    add = remove = []
    if info['src'] == 'body':
        body = verify_news[nid]['body']
        if info['use_old'] or (not add and len(body) < 300):
            body = old_bodies[nid]['text']
        add, remove = parse_body_list(body)
    elif info['src'] == 'attach':
        add, remove = parse_attach(info['file'])
    elif info['src'] == 'attach2025':
        df = pd.read_excel(info['file'], header=None)
        add, remove = parse_2025(df)
    if info['src'] == 'none':
        print('  官网确认无名单（附件丢失），仅核验调出代码')
        exp_outs = {x['out'] for x in rep_rows}
        known = {'6271': ['000562'], '6802': ['601299', '600832']}[nid]
        ok = known == sorted(exp_outs)
        all_ok &= ok
        print('  调出代码一致' if ok else f'  调出不一致! 报告={sorted(exp_outs)} 应有={known}')
        continue
    if not add and not remove:
        print('  无法解析名单!')
        all_ok = False
        continue
    got_out = {x['code'] for x in remove}
    got_in = {x['code'] for x in add}
    rep_out = {x['out'] for x in rep_rows}
    rep_in = {x['in'] for x in rep_rows if x['in'] and not x['in'].startswith('(')}
    ok = True
    if got_out:
        miss = got_out - rep_out; extra = rep_out - got_out
        if miss or extra:
            ok = False
            print(f'  调出 {len(got_out)} 不一致! 公告有报告无: {sorted(miss)}, 报告有公告无: {sorted(extra)}')
        else:
            print(f'  调出 {len(got_out)} 只 一致')
    if got_in:
        miss = got_in - rep_in; extra = rep_in - got_in
        if miss or extra:
            ok = False
            print(f'  调入 {len(got_in)} 不一致! 公告有报告无: {sorted(miss)}, 报告有公告无: {sorted(extra)}')
        else:
            print(f'  调入 {len(got_in)} 只 一致')
    all_ok &= ok

print('\n========== 总结 ==========')
print('全部抽查一致，报告准确' if all_ok else '存在差异！需修正')
