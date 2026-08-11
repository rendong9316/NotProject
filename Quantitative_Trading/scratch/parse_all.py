"""统一解析：2010-2019 沪深300定期调整名单（正文表格 + 附件Excel）"""
import sys, json, re, os
sys.stdout.reconfigure(encoding='utf-8')
import pandas as pd

ALL_ADJ = {
    '2009-12-14': {'id': '1205', 'src': 'body'},
    '2010-06-17': {'id': '1385', 'src': 'body'},
    '2010-12-13': {'id': '6150', 'src': 'body'},
    '2011-06-13': {'id': '2714', 'src': 'body'},
    '2011-12-12': {'id': '6687', 'src': 'body'},
    '2012-06-11': {'id': '6699', 'src': 'body'},
    '2012-12-17': {'id': '3242', 'src': 'body'},
    '2013-06-17': {'id': '2723', 'src': 'body'},
    '2013-12-02': {'id': '6750', 'src': 'body'},
    '2014-06-03': {'id': '3670', 'src': 'body'},
    '2014-12-01': {'id': '3596', 'src': 'body'},
    '2015-06-01': {'id': '4084', 'src': 'body'},
    '2015-11-30': {'id': '4272', 'src': 'attach', 'file': '201511302cons.xls'},
    '2016-05-30': {'id': '3855', 'src': 'attach', 'file': '201605302cons.xls'},
    '2016-11-28': {'id': '4003', 'src': 'attach', 'file': '20161212cons.xlsx'},
    '2017-05-31': {'id': '12583', 'src': 'attach', 'file': '1511334410841442.xlsx'},
    '2017-11-27': {'id': '12590', 'src': 'attach', 'file': '1519719074537344.xlsx'},
    '2018-05-28': {'id': '11518', 'src': 'attach', 'file': '1535531071241051.xlsx'},
    '2018-12-03': {'id': '11859', 'src': 'attach', 'file': '1545023652142456.xlsx'},
    '2019-06-03': {'id': '11379', 'src': 'attach', 'file': '1560322933287092.xls'},
    '2019-12-02': {'id': '11529', 'src': 'attach', 'file': '1576217266220014.xlsx'},
}


def norm_code(c):
    c = str(c).strip()
    c = re.sub(r'\D', '', c) if not re.match(r'^\d{6}$', c) else c
    return c.zfill(6) if c else c


def parse_body_list(text):
    """从正文解析 沪深300 调整名单和备选名单（token 流）"""
    text = text.replace('沪深300', '沪深 300')
    tokens = [ln.strip() for ln in text.split('\n') if ln.strip()]
    add, remove, backup = [], [], []

    def is_stop(tok):
        return bool(re.match(r'^中证\s*(100|500|香港|红利)', tok) or
                    re.match(r'^沪深\s*300\s*指数备选名单', tok) or
                    tok.startswith('附'))

    # --- 调整名单 ---
    try:
        i0 = tokens.index('股票代码')
    except ValueError:
        i0 = -1
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

    # --- 备选名单 ---
    try:
        j0 = next(k for k, t in enumerate(tokens) if re.match(r'^沪深\s*300\s*指数备选名单', t))
    except StopIteration:
        j0 = -1
    if j0 >= 0:
        k = j0 + 1
        while k < len(tokens) and not tokens[k].startswith(('排序', '股票代码')):
            k += 1
        if k < len(tokens) and tokens[k].startswith('股票代码'):
            k += 1
        while k + 2 < len(tokens):
            if re.match(r'^中证\s*(100|500|香港|红利)', tokens[k]):
                break
            if re.match(r'^\d{1,6}$', tokens[k]):
                code, name = tokens[k + 1], tokens[k + 2]
                if k + 3 < len(tokens) and re.match(r'^\d{1,6}$', tokens[k + 3]):
                    backup.append({'rank': tokens[k], 'code': norm_code(code), 'name': name})
                    backup.append({'rank': tokens[k + 3], 'code': norm_code(tokens[k + 4]), 'name': tokens[k + 5]})
                    k += 6
                else:
                    backup.append({'rank': tokens[k], 'code': norm_code(code), 'name': name})
                    k += 3
            else:
                k += 1
    return add, remove, backup


def parse_attach(path):
    xl = pd.ExcelFile(path)
    rec = {}
    for sh in xl.sheet_names:
        df = pd.read_excel(path, sheet_name=sh, header=0)
        df.columns = [str(c).strip() for c in df.columns]
        hs300 = df[df.iloc[:, 1].astype(str).str.strip() == '沪深300']
        key = {'调入': 'add', '调出': 'remove', '备选名单': 'backup'}[sh]
        if key == 'backup':
            code_col, name_col, rank_col = 3, 4, 2
        else:
            code_col, name_col, rank_col = 2, 3, None
        rows = []
        for _, r in hs300.iterrows():
            item = {'code': norm_code(r.iloc[code_col]), 'name': str(r.iloc[name_col]).strip()}
            if rank_col is not None:
                item['rank'] = str(r.iloc[rank_col]).strip()
            rows.append(item)
        rec[key] = rows
    return rec


result = {}
old_bodies = json.load(open('scratch/csindex_announcements_2010s.json', encoding='utf-8'))
raw_news = {f.replace('.json', ''): json.load(open(f'scratch/raw_news/{f}', encoding='utf-8'))
            for f in os.listdir('scratch/raw_news') if f.endswith('.json')}

for date, info in ALL_ADJ.items():
    if info['src'] == 'body':
        bid = info['id']
        if bid in raw_news:
            body = raw_news[bid]['body']
        else:
            body = old_bodies[bid]['text']
        add, remove, backup = parse_body_list(body)
        rec = {'add': add, 'remove': remove, 'backup': backup}
    else:
        rec = parse_attach(os.path.join('scratch/attach', info['file']))
    result[date] = {'id': info['id'], **rec}
    print(date, '调出', len(rec['remove']), '调入', len(rec['add']), '备选', len(rec['backup']))

with open('scratch/hs300_adjustments_full.json', 'w', encoding='utf-8') as f:
    json.dump(result, f, ensure_ascii=False, indent=2)
print('\n已保存 scratch/hs300_adjustments_full.json')
