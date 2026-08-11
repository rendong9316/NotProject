import sys
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    b = p.chromium.launch(headless=True)
    pg = b.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsDetail?id=4084', timeout=60000, wait_until='domcontentloaded')
    pg.wait_for_timeout(5000)
    txt = pg.inner_text('body')
    # 提取正文区
    idx = txt.find('正文')
    print(txt[idx:idx+1200])
    b.close()
