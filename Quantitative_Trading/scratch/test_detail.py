"""测试详情页：id=4084（之前 API 失败的）"""
import sys
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=80)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN',
                              storage_state='scratch/csindex_state.json')
    pg = ctx.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsDetail?id=4084', timeout=90000, wait_until='domcontentloaded')
    pg.wait_for_timeout(8000)

    # 找正文容器
    text = pg.evaluate("""
        () => {
            const cand = [];
            document.querySelectorAll('*').forEach(el => {
                const t = (el.innerText || '');
                if (t.includes('附件') && t.length > 2000) {
                    const w = el.getBoundingClientRect().width;
                    cand.push({tag: el.tagName, cls: (el.className||'').toString().slice(0,60), len: t.length, w: Math.round(w)});
                }
            });
            return cand.slice(0, 20);
        }
    """)
    for c in text:
        print(c)
    print('=== 标题 ===')
    h = pg.evaluate("() => document.title")
    print(h)
    browser.close()
