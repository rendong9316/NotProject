import sys
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

responses = []
def on_response(resp):
    if 'queryAnnouncementById' in resp.url:
        try:
            responses.append((resp.status, resp.text()[:300]))
        except Exception as e:
            responses.append((resp.status, f'ERR {e}'))

with sync_playwright() as p:
    b = p.chromium.launch(headless=True)
    pg = b.new_page()
    pg.on('response', on_response)
    for aid in [6150, 4084, 11529]:
        responses.clear()
        pg.goto(f'https://www.csindex.com.cn/#/about/newsDetail?id={aid}', timeout=60000, wait_until='domcontentloaded')
        pg.wait_for_timeout(4000)
        print(f'=== id={aid} ===')
        for s, t in responses:
            print('status:', s, '| body:', t)
        if not responses:
            print('  无API响应!')
    b.close()
