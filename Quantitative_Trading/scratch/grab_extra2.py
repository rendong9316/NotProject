"""抓 6271/6802 完整正文+附件"""
import sys, json, re, os
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

TARGETS = ['6271', '6802']

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
        m3 = re.search(r'发布时间：[^\n]+\n', text)
        start = m3.end() if m3 else text.find('正文')
        end = text.find('中证指数有限公司', start)
        body = text[start:end] if start != -1 else ''
        print(f'完整正文 {len(body)} 字:')
        print(body[:2000].replace('\n', ' '))
        print()
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
        import time
        time.sleep(13)
    browser.close()
