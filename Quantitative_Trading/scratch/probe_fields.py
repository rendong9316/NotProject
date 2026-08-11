import sys, json
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    b = p.chromium.launch(headless=True)
    pg = b.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsCenter', timeout=60000, wait_until='domcontentloaded')
    pg.wait_for_timeout(4000)
    body = pg.evaluate("""async () => {
        const r = await fetch('/csindex-home/announcement/queryAnnouncementByVo', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({"lang":"cn","classlist":["index"],"indexlist":["csi_index"],"page":{"desc":"","key":"","page":1,"rows":5,"sortBy":""},"related_topics":["index_rebalance"],"typelist":["announcement"]})
        });
        return r.text();
    }""")
    d = json.loads(body)
    print('code:', d.get('code'))
    rows = d.get('data', [])
    if rows:
        print('首行完整字段:', json.dumps(rows[0], ensure_ascii=False, indent=1))
    b.close()
