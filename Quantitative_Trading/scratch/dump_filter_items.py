"""Dump 筛选项 HTML（种类/类别/主题/指数系列）"""
import sys
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=60)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN')
    pg = ctx.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsCenter', timeout=90000, wait_until='domcontentloaded')
    pg.wait_for_timeout(5000)

    items = pg.locator('.filter-wrap .filter-item')
    print('filter-item 数量:', items.count())
    for i in range(items.count()):
        html = items.nth(i).evaluate("e => e.outerHTML")
        print(f'=== filter-item[{i}] ===')
        print(html[:1500])
        print()
    browser.close()
