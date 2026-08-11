"""全页找"种类"和"主题"文本的父容器结构"""
import sys
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=60)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN')
    pg = ctx.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsCenter', timeout=90000, wait_until='domcontentloaded')
    pg.wait_for_timeout(5000)

    # 用 text 定位"主题"并向上找容器
    res = pg.evaluate("""
        () => {
            const els = [];
            document.querySelectorAll('*').forEach(el => {
                if (el.childElementCount === 0 && (el.textContent || '').trim() === '主题') {
                    els.push(el);
                }
            });
            return els.map(el => {
                let path = [];
                let cur = el;
                for (let k = 0; k < 7 && cur; k++) {
                    path.push(cur.tagName + '.' + (cur.className || '').toString().split(' ').slice(0,3).join('.'));
                    cur = cur.parentElement;
                }
                return path.join(' < ');
            });
        }
    """)
    for r in res:
        print(r)
        print('---')
    browser.close()
