import sys
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    b = p.chromium.launch(headless=True)
    pg = b.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsDetail?id=6150', timeout=60000, wait_until='domcontentloaded')
    pg.wait_for_timeout(3000)
    body = pg.evaluate("async () => { const r = await fetch('/csindex-home/announcement/queryAnnouncementById?id=6150'); return r.text(); }")
    print('原始响应前300字:')
    print(body[:300])
    print('...')
    # 也看看浏览器里页面自己发出的请求响应头
    api_resp = pg.expect_response(lambda r: 'queryAnnouncementById' in r.url)
    pg.reload(wait_until='domcontentloaded')
    resp = api_resp.value
    print('页面自身请求状态:', resp.status)
    txt = resp.text()
    print('页面自身响应前200字:', txt[:200])
    b.close()
