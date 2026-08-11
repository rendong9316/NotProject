"""查看附件区域 HTML"""
import sys
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=60)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN',
                              storage_state='scratch/csindex_state.json')
    pg = ctx.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsDetail?id=4272', timeout=90000, wait_until='domcontentloaded')
    pg.wait_for_timeout(12000)

    res = pg.evaluate("""
        () => {
            const out = [];
            document.querySelectorAll('a').forEach(a => {
                const t = (a.innerText || '').trim();
                if (t.includes('附件') || t.includes('名单') || t.includes('xlsx') || t.includes('pdf') || (a.href || '').includes('download')) {
                    out.push({text: t.slice(0, 60), href: a.href, attrs: Array.from(a.attributes).reduce((o, x) => {o[x.name]=x.value; return o;}, {})});
                }
            });
            return out;
        }
    """)
    for r in res:
        print(r)
    print('=== 附件附近 HTML ===')
    h = pg.evaluate("""
        () => {
            const walk = (el, depth) => {
                if (!el || depth > 6) return null;
                const t = el.innerText || '';
                if (t.includes('附 件') && el.children.length <= 8 && t.length < 500) {
                    return el.outerHTML.slice(0, 2000);
                }
                for (const c of el.children) {
                    const r = walk(c, depth + 1);
                    if (r) return r;
                }
                return null;
            };
            return walk(document.body, 0);
        }
    """)
    print(h)
    browser.close()
