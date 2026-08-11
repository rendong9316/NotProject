import sys
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    b = p.chromium.launch(headless=True)
    pg = b.new_page()
    pg.goto('http://www.sse.com.cn/market/sseindex/diclosure/c/c_20161128_4207421.shtml', timeout=60000, wait_until='domcontentloaded')
    pg.wait_for_timeout(4000)
    txt = pg.inner_text('body')
    print('正文前1500字:')
    print(txt[:1500])
    print()
    print('=== 所有链接 ===')
    links = pg.eval_on_selector_all('a', 'els => els.map(e => [e.innerText.trim(), e.href]).filter(x => x[0])')
    for t, h in links:
        print(t, '|', h)
    b.close()
