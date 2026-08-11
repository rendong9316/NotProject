"""12807 页面所有链接 + 猜测附件URL"""
import sys
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=50)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN',
                              storage_state='scratch/csindex_state.json')
    pg = ctx.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsDetail?id=12807', timeout=90000, wait_until='domcontentloaded')
    pg.wait_for_timeout(12000)
    links = pg.evaluate("""
        () => {
            const out = [];
            document.querySelectorAll('a').forEach(a => {
                const href = a.href || '';
                const t = (a.innerText || '').trim();
                if (href && !href.startsWith('javascript')) {
                    out.push({text: t.slice(0, 50), href: href.slice(0, 160)});
                }
            });
            return out;
        }
    """)
    for L in links:
        print(L['text'], '||', L['href'])
    browser.close()

# 猜测附件 URL
import requests
cands = [
    'https://oss-ch.csindex.com.cn/static/html/csindex/public/sseportal/upload/files/upload/20181217cons.xlsx',
    'https://oss-ch.csindex.com.cn/static/html/csindex/public/sseportal/upload/files/upload/20181217cons.xls',
    'https://oss-ch.csindex.com.cn/static/html/csindex/public/sseportal/upload/files/upload/20181203cons.xlsx',
    'https://oss-ch.csindex.com.cn/static/html/csindex/public/sseportal/upload/files/upload/20181203cons.xls',
]
for u in cands:
    try:
        r = requests.get(u, timeout=30)
        print(u.split('/')[-1], r.status_code, len(r.content))
    except Exception as e:
        print(u.split('/')[-1], 'ERR', e)
