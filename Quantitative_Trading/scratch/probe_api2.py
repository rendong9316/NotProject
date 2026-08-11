import sys
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    b = p.chromium.launch(headless=True)
    pg = b.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsDetail?id=6150', timeout=60000, wait_until='domcontentloaded')
    pg.wait_for_timeout(3000)
    r = pg.request.get('https://www.csindex.com.cn/csindex-home/announcement/queryAnnouncementById?id=6150')
    print('status:', r.status)
    print('content-type:', r.headers.get('content-type'))
    body = r.text()
    print('body前500字:', body[:500])
    b.close()
