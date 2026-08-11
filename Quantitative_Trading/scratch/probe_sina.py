import sys
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    b = p.chromium.launch(headless=True)
    pg = b.new_page()
    pg.goto('http://finance.sina.com.cn/roll/2016-05-30/doc-ifxsqxxs7886391.shtml', timeout=60000, wait_until='domcontentloaded')
    pg.wait_for_timeout(4000)
    txt = pg.inner_text('body')
    print(txt[:3500])
    b.close()
