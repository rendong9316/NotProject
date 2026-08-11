"""抓取 2015-2019 公告附件 Excel"""
import sys, json, time, re, os
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

TARGETS = ['4084', '4272', '3855', '4003', '12583', '12590', '11518', '11379', '11529']
os.makedirs('scratch/attach', exist_ok=True)
seen = {}

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=50)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN',
                              storage_state='scratch/csindex_state.json')
    pg = ctx.new_page()

    for nid in TARGETS:
        print(f'\n=== {nid} ===', flush=True)
        pg.goto(f'https://www.csindex.com.cn/#/about/newsDetail?id={nid}',
                timeout=90000, wait_until='domcontentloaded')
        pg.wait_for_timeout(11000)
        links = pg.evaluate("""
            () => {
                const out = [];
                document.querySelectorAll('a').forEach(a => {
                    const t = (a.innerText || '').trim();
                    const href = a.href || '';
                    if (href.includes('oss-ch.csindex.com.cn')) {
                        out.push({text: t.slice(0, 80), href});
                    }
                });
                return out;
            }
        """)
        for L in links:
            fn = L['href'].split('/')[-1]
            ext = os.path.splitext(fn)[1]
            print('  附件:', L['text'], '->', fn, flush=True)
            if fn in seen:
                print('  跳过重复')
                continue
            seen[fn] = True
            try:
                resp = ctx.request.get(L['href'], timeout=60000)
                print('  下载状态:', resp.status, '大小:', len(resp.body()), flush=True)
                if resp.status == 200:
                    with open(os.path.join('scratch/attach', fn), 'wb') as f:
                        f.write(resp.body())
            except Exception as e:
                print('  下载失败:', e)
            time.sleep(3)
        time.sleep(12)

    browser.close()
print('\n完成，附件列表:', list(seen.keys()))
