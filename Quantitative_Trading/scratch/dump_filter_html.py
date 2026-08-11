"""Dump 筛选区 HTML"""
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
            const root = document.querySelector('input[placeholder="请输入关键字"]').closest('div[class*="filter"], div[class*="search"], form');
            return root ? root.outerHTML.slice(0, 8000) : 'not found';
        }
    """)
    print(html)
    browser.close()
