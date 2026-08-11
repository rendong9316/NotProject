"""找 主题 下拉的 DOM"""
import sys
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=60)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN')
    pg = ctx.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsCenter', timeout=90000, wait_until='domcontentloaded')
    pg.wait_for_timeout(5000)

    html = pg.evaluate("""
        () => {
            const walk = (el, depth) => {
                if (depth > 4 || !el) return null;
                const t = el.innerText || '';
                if (t.startsWith('种类') && t.includes('主题') && t.length < 200) {
                    return el.outerHTML.slice(0, 6000);
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
    print(html if html else '未找到')
    browser.close()
