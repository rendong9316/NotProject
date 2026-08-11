import sys
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    b = p.chromium.launch(headless=True)
    pg = b.new_page()
    for aid in [4084, 6802, 11529]:
        pg.goto(f'https://www.csindex.com.cn/#/about/newsDetail?id={aid}', timeout=60000, wait_until='domcontentloaded')
        pg.wait_for_timeout(4000)
        txt = pg.inner_text('body')
        idx = txt.find('发布时间')
        print(f'=== id={aid} ===')
        print(txt[max(0,idx-60):idx+200])
        print()
    b.close()
