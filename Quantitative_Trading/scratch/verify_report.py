"""真实浏览器重新抓取公告，核验 CSI300_remake_report.xlsx"""
import sys, json, re, os, time
sys.stdout.reconfigure(encoding='utf-8')
import pandas as pd
import openpyxl
from playwright.sync_api import sync_playwright

VERIFY = [
    {'id': '6150',   'eff': '2011-01-04', 'desc': '定期(老正文)'},
    {'id': '4003',   'eff': '2016-12-12', 'desc': '定期(附件1)'},
    {'id': '11529',  'eff': '2019-12-16', 'desc': '定期(附件1)'},
    {'id': '11429',  'eff': '2020-06-15', 'desc': '定期(2020后)'},
    {'id': '1335',   'eff': '2010-01-04', 'desc': '临时-补充公告'},
    {'id': '1607',   'eff': '2010-07-29', 'desc': '临时-农行IPO'},
    {'id': '792',    'eff': '2011-08-23', 'desc': '临时-百联/友谊'},
    {'id': '3453',   'eff': '2013-09-18', 'desc': '临时-美的'},
    {'id': '6271',   'eff': '2015-01-26', 'desc': '临时-宏源(附件丢失?)'},
    {'id': '6802',   'eff': '2015-05-20', 'desc': '临时-北车/东方明珠(附件丢失?)'},
    {'id': '15546',  'eff': '2025-03-04', 'desc': '临时-海通'},
    {'id': '1006022','eff': '2025-09-05', 'desc': '临时-中国重工'},
]

os.makedirs('scratch/verify_news', exist_ok=True)
os.makedirs('scratch/verify_attach', exist_ok=True)

def norm_code(c):
    c = str(c).strip()
    m = re.search(r'\d{6}', c)
    return m.group(0) if m else c

def parse_body_list(text):
    text = text.replace('沪深300', '沪深 300')
    tokens = [ln.strip() for ln in text.split('\n') if ln.strip()]
    add, remove, backup = [], [], []

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
        if len(df.columns) < 2:
            continue
        col1 = df.iloc[:, 1].astype(str).str.strip()
        if s in ('调入', '调出'):
            hs300 = df[col1 == '沪深300']
            if hs300.empty:
                hs300 = df  # 单指数文件兜底
            for _, r in hs300.iterrows():
                item = {'code': norm_code(r.iloc[2]), 'name': str(r.iloc[3]).strip()}
                (add if s == '调入' else remove).append(item)
        else:
            if col1.eq('沪深300').any():
                hs300 = df[col1 == '沪深300']
                for _, r in hs300.iterrows():
                    item = {'code': norm_code(r.iloc[3]), 'name': str(r.iloc[4]).strip()}
                    add.append(item)
            else:
                for _, r in df.iterrows():
                    row = [str(x).strip() for x in r.tolist()]
                    if row[0] == '沪深300':
                        add.append({'code': norm_code(row[3]), 'name': row[4]})
    return add, remove

def parse_2025_single(df):
    add, remove = [], []
    for _, r in df.iterrows():
        row = [str(x).strip() for x in r.tolist()]
        if not row or row[0] not in ('000300', '沪深300'):
            continue
        if len(row) > 4 and row[2] and row[2] != 'nan':
            remove.append({'code': norm_code(row[2]), 'name': row[3]})
        if len(row) > 5 and row[4] and row[4] != 'nan':
            add.append({'code': norm_code(row[4]), 'name': row[5]})
    return add, remove

# 抓取阶段
with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=50)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN',
                              storage_state='scratch/csindex_state.json')
    pg = ctx.new_page()

    for v in VERIFY:
        nid = v['id']
        print(f'\n=== {nid} ({v["desc"]}) ===', flush=True)
        try:
            pg.goto(f'https://www.csindex.com.cn/#/about/newsDetail?id={nid}',
                    timeout=90000, wait_until='domcontentloaded')
            pg.wait_for_timeout(11000)
            text = pg.evaluate("() => document.body.innerText")
            m = re.search(r'(关于调整[\s\S]{5,60}?公告)', text)
            title = m.group(0).strip() if m else '未找到标题'
            m2 = re.search(r'发布时间：(\d{4}-\d{2}-\d{2})', text)
            date = m2.group(1) if m2 else ''
            start = m2.end() if m2 else text.find(title)
            end = text.find('附件', start)
            if end == -1:
                end = text.find('中证指数有限公司', start)
            if end == -1:
                end = start + 4000
            body = text[start:end] if start != -1 else ''
            print(f'标题: {title} | 日期: {date} | 正文长度: {len(body)}')
            with open(f'scratch/verify_news/{nid}.json', 'w', encoding='utf-8') as f:
                json.dump({'id': nid, 'date': date, 'title': title, 'body': body},
                          f, ensure_ascii=False, indent=2)
            links = pg.evaluate("""() => {
                const out = [];
                document.querySelectorAll('a').forEach(a => {
                    const href = a.href || '';
                    if (href.includes('oss-ch.csindex.com.cn')) out.push({text: (a.innerText||'').trim().slice(0,60), href});
                });
                return out;
            }""")
            v['attach'] = []
            for L in links:
                fn = L['href'].split('/')[-1]
                print('  附件: ' + L['text'] + ' -> ' + fn, flush=True)
                try:
                    resp = ctx.request.get(L['href'], timeout=60000)
                    if resp.status == 200:
                        path = os.path.join('scratch/verify_attach', f'{nid}_{fn}')
                        with open(path, 'wb') as f:
                            f.write(resp.body())
                        v['attach'].append(path)
                        print(f'    已下载 {len(resp.body())} 字节')
                except Exception as e:
                    print('    下载失败:', e)
            v['date'] = date
            v['body'] = body
        except Exception as e:
            print('  抓取失败:', e)
        time.sleep(12)
    browser.close()

# 解析阶段
report = openpyxl.load_workbook('CSI300_remake_report.xlsx')['Sheet1']
rep = {}
for r in report.iter_rows(min_row=2, values_only=True):
    if r[4]:
        d = str(r[4])[:10]
        rep.setdefault(d, []).append({
            'out': str(r[0]).strip(), 'out_n': r[1],
            'in': str(r[2]).strip() if r[2] else '', 'in_n': r[3],
        })

print('\n\n========== 核验结果 ==========')
all_ok = True
for v in VERIFY:
    eff = v['eff']
    nid = v['id']
    print(f'\n--- {nid} {v["desc"]} (生效 {eff}) ---')
    rep_rows = rep.get(eff, [])
    if not rep_rows:
        print('  报告无该生效日记录!')
        all_ok = False
        continue
    # 解析公告名单
    add, remove = [], []
    if v.get('attach'):
        for ap in v['attach']:
            if '2025' in v['desc'] or nid in ('15546', '1006022'):
                a2, r2 = [], []
                for sh in pd.ExcelFile(ap).sheet_names:
                    df = pd.read_excel(ap, sheet_name=sh, header=None)
                    a, r = parse_2025_single(df)
                    a2 += a; r2 += r
                add += a2; remove += r2
            else:
                a, r = parse_attach(ap)
                add += a; remove += r
    elif v.get('body') and len(v['body']) > 300:
        add, remove = parse_body_list(v['body'])
        if not add and not remove:
            m = re.search(r'(调出\s*[^。]+\d{6}[^。]+。?|调入\s*[^。]+\d{6}[^。]+。?)', v['body'])
            print('  正文无表格名单' + ('' if not m else ', 有摘要句'))
    else:
        print('  无附件且正文过短(官网未提供名单)')
    # 报告侧集合
    rep_out = {x['out'] for x in rep_rows}
    rep_in = {x['in'] for x in rep_rows if x['in'] and not x['in'].startswith('(')}
    got_out = {x['code'] for x in remove}
    got_in = {x['code'] for x in add}
    if not got_out and not got_in:
        print(f'  报告{len(rep_rows)}条, 公告未抓到名单, 无法比对')
        continue
    # 比对调出
    if got_out:
        miss_out = got_out - rep_out
        extra_out = rep_out - got_out
        if miss_out or extra_out:
            all_ok = False
            print(f'  调出不一致! 公告有报告无: {sorted(miss_out)}, 报告有公告无: {sorted(extra_out)}')
        else:
            print(f'  调出 {len(got_out)} 只: 一致')
    if got_in:
        miss_in = got_in - rep_in
        extra_in = rep_in - got_in
        if miss_in or extra_in:
            all_ok = False
            print(f'  调入不一致! 公告有报告无: {sorted(miss_in)}, 报告有公告无: {sorted(extra_in)}')
        else:
            print(f'  调入 {len(got_in)} 只: 一致')
    if nid in ('6271', '6802') and v.get('attach'):
        print('  [好消息] 官网附件恢复了!')

print('\n========== 结论 ==========')
print('全部一致' if all_ok else '存在差异, 见上方')
