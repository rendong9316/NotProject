import sys
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

reqs = []
def on_request(req):
    if req.resource_type in ('xhr', 'fetch'):
        reqs.append((req.method, req.url, req.post_data))

with sync_playwright() as p:
    b = p.chromium.launch(headless=True)
    pg = b.new_page()
    pg.on('request', on_request)
    pg.goto('https://www.csindex.com.cn/#/about/newsDetail?id=4084', timeout=60000, wait_until='domcontentloaded')
    pg.wait_for_timeout(6000)
    for m, u, pd in reqs:
        print(m, u)
        if pd:
            print('   BODY:', pd)
    print('---标题---')
    print(pg.title())
    b.close()
