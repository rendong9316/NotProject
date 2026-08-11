"""抓 15546(2025-02-06) 和 1006022(2025-07-25) 正文+附件"""
import sys, json, re, os, time
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

TARGETS = ['15546', '1006022']

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=50)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN',
                              storage_state='scratch/csindex_state.json')
    pg = ctx.new_page()

    for nid in TARGETS:
        print(f'\n=== {nid} ===', flush=True)
        pg.goto(f'https://www.csindex.com.cn/#/about/newsDetail?id={nid}',
                timeout=90000, wait_until='domcontentloaded')
        pg.wait_for_timeout(12000)
        text = pg.evaluate("() => document.body.innerText")
        m = re.search(r'(关于调整[\s\S]{5,60}?公告)', text)
        title = m.group(0).strip() if m else '未找到标题'
        m2 = re.search(r'发布时间：(\d{4}-\d{2}-\d{2})', text)
        date = m2.group(1) if m2 else ''
        m3 = re.search(r'发布时间：[^\n]+\n', text)
        start = m3.end() if m3 else text.find('正文')
        end = text.find('附 件', start)
        if end == -1:
            end = text.find('附件', start)
        if end == -1:
            end = text.find('中证指数有限公司', start)
        body = text[start:end] if start != -1 else ''
        print(f'标题: {title} | 日期: {date} | 正文: {len(body)} 字')
        print(body[:1500].replace('\n', ' '))
        print()
        with open(f'scratch/raw_news/{nid}.json', 'w', encoding='utf-8') as f:
            json.dump({'id': nid, 'actual_date': date, 'title': title, 'body': body},
                      f, ensure_ascii=False, indent=2)
        links = pg.evaluate("""
            () => {
                const out = [];
                document.querySelectorAll('a').forEach(a => {
                    const href = a.href || '';
                    if (href.includes('oss-ch.csindex.com.cn')) {
                        out.push({text: (a.innerText || '').trim().slice(0, 60), href});
                    }
                });
                return out;
            }
        """)
        for L in links:
            fn = L['href'].split('/')[-1]
            print('附件:', L['text'], '->', fn, flush=True)
            try:
                resp = ctx.request.get(L['href'], timeout=60000)
                if resp.status == 200:
                    with open(os.path.join('scratch/attach', fn), 'wb') as f:
                        f.write(resp.body())
                    print(' 已下载', len(resp.body()), '字节')
            except Exception as e:
                print(' 下载失败:', e)
        time.sleep(13)
    browser.close()
