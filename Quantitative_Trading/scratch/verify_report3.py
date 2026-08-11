"""核验比对（第三阶段：通用解析+比对）"""
import sys, json, re, os
sys.stdout.reconfigure(encoding='utf-8')
import pandas as pd
import openpyxl

def norm_code(c):
    c = str(c).strip()
    m = re.search(r'\d{6}', c)
    return m.group(0) if m else c

def parse_body_generic(text):
    """通用正文表格解析：自动识别列序（调出/调入 或 纳入/删除）与 有无指数label"""
    text = text.replace('沪深300', '沪深 300')
    tokens = [ln.strip() for ln in text.split('\n') if ln.strip()]
    add, remove = [], []

    def is_stop(tok):
        return bool(re.match(r'^中证\s*(100|500|香港|红利)', tok) or
                    re.match(r'^沪深\s*300\s*指数备选名单', tok) or
                    tok.startswith('附'))

    # 1) 找表头
    head = -1
    for k in range(len(tokens) - 3):
        if tokens[k:k+4] == ['股票代码', '股票名称', '股票代码', '股票名称']:
            head = k
    if head < 0:
        return add, remove

    # 2) 表头前找列标题，定列序
    left_right = None  # ('out','in') or ('in','out')
    before = tokens[max(0, head-10):head]
    joined = '|'.join(before)
    for pair in [('调出名单', '调入名单'), ('调出', '调入'), ('纳入', '删除'), ('调入', '调出'), ('删除', '纳入')]:
        if pair[0] in before or pair[0].replace('名单', '') in before:
            pass
    # 用出现顺序：找两词中在before里各自最后出现的位置，出现早的在左
    def last_pos(w):
        for i in range(len(before) - 1, -1, -1):
            if before[i] == w or before[i].replace('名单', '') == w:
                return i
        return -1
    for cand in [('调出', '调入'), ('纳入', '删除')]:
        p1, p2 = last_pos(cand[0]), last_pos(cand[1])
        if p1 >= 0 and p2 >= 0:
            left_right = (cand[0], cand[1]) if p1 < p2 else (cand[1], cand[0])
            break
    if not left_right:
        left_right = ('out', 'in')  # 默认调出在左

    def col_map():
        # 返回 (left_col, right_col)
        if left_right[0] in ('调出', '调出名单'):
            return ('out', 'in')
        if left_right[0] in ('纳入',):
            return ('in', 'out')
        if left_right[0] in ('调入', '调入名单'):
            return ('in', 'out')
        return ('out', 'in')

    left_c, right_c = col_map()

    # 3) 数据行
    i = head + 4
    while i < len(tokens):
        if is_stop(tokens[i]):
            break
        # label 模式（5 token）？
        if i + 4 < len(tokens) and re.match(r'^(沪深\s*300|000300)$', tokens[i]) and \
           re.match(r'^[A-Z0-9.\-]{2,10}$', tokens[i+1]) and re.match(r'^[A-Z0-9.\-]{2,10}$', tokens[i+3]):
            left = {'code': norm_code(tokens[i+1]), 'name': tokens[i+2]}
            right = {'code': norm_code(tokens[i+3]), 'name': tokens[i+4]}
            (remove if left_c == 'out' else add).append(left)
            (remove if right_c == 'out' else add).append(right)
            i += 5
        elif i + 3 < len(tokens) and re.match(r'^[A-Z0-9.\-]{2,10}$', tokens[i]) and \
             re.match(r'^[A-Z0-9.\-]{2,10}$', tokens[i+2]):
            left = {'code': norm_code(tokens[i]), 'name': tokens[i+1]}
            right = {'code': norm_code(tokens[i+2]), 'name': tokens[i+3]}
            (remove if left_c == 'out' else add).append(left)
            (remove if right_c == 'out' else add).append(right)
            i += 4
        else:
            i += 1
    return add, remove

def parse_attach(path):
    xl = pd.ExcelFile(path)
    add, remove = [], []
    for sh in xl.sheet_names:
        s = sh.strip()
        if '备选' in s:
            continue
        try:
            df = pd.read_excel(path, sheet_name=sh, header=0)
        except Exception:
            continue
        if len(df.columns) < 4:
            df = pd.read_excel(path, sheet_name=sh, header=None)
        for r in df.values.tolist():
            row = [str(x).strip() for x in r]
            if len(row) < 4 or row[1] != '沪深300':
                continue
            item = {'code': norm_code(row[2]), 'name': row[3]}
            (add if s == '调入' else remove).append(item)
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

SOURCES = {
    '6150':    {'src': 'body', 'use_old': False},
    '4003':    {'src': 'attach', 'file': 'scratch/verify_attach/4003_20161212cons.xlsx'},
    '11529':   {'src': 'attach', 'file': 'scratch/verify_attach/11529_1576217266220014.xlsx'},
    '11429':   {'src': 'attach', 'file': 'scratch/verify_attach/11429_1590972336738429.xlsx'},
    '1335':    {'src': 'body', 'use_old': False},
    '1607':    {'src': 'body', 'use_old': True},
    '792':     {'src': 'body', 'use_old': True},
    '3453':    {'src': 'body', 'use_old': True},
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
    if info['src'] == 'body':
        body = verify_news[nid]['body']
        if info['use_old'] or len(body) < 300:
            body = old_bodies[nid]['text']
        add, remove = parse_body_generic(body)
    elif info['src'] == 'attach':
        add, remove = parse_attach(info['file'])
    elif info['src'] == 'attach2025':
        add, remove = parse_2025(pd.read_excel(info['file'], header=None))
    elif info['src'] == 'none':
        exp_outs = {x['out'] for x in rep_rows}
        known = {'6271': ['000562'], '6802': ['601299', '600832']}[nid]
        ok = sorted(known) == sorted(exp_outs)
        all_ok &= ok
        print('  官网无名单(附件丢失)，调出代码一致' if ok else f'  调出不一致! 报告={sorted(exp_outs)}')
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
    for tag, got, rep_s in [('调出', got_out, rep_out), ('调入', got_in, rep_in)]:
        miss = got - rep_s; extra = rep_s - got
        if miss or extra:
            ok = False
            print(f'  {tag} {len(got)} 不一致! 公告有报告无: {sorted(miss)}, 报告有公告无: {sorted(extra)}')
        else:
            print(f'  {tag} {len(got)} 只 一致')
    all_ok &= ok

print('\n========== 总结 ==========')
print('全部抽查一致，报告准确' if all_ok else '存在差异！')
