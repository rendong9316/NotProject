"""Dump 详情页结构"""
import sys
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=80)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN',
                              storage_state='scratch/csindex_state.json')
    pg = ctx.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsDetail?id=4084', timeout=90000, wait_until='domcontentloaded')
    pg.wait_for_timeout(10000)

    print('=== body 文本前 3000 ===')
    t = pg.evaluate("() => document.body.innerText")
    print(t[:3000])
    print('=== 关键元素 ===')
    for sel in ['.news-detail', '.detail', '.article', '[class*=detail]', '[class*=article]', '[class*=content]']:
        els = pg.locator(sel)
        for i in range(min(els.count(), 5)):
            try:
                tt = els.nth(i).inner_text(timeout=2000)
                print(sel, '[', i, '] len=', len(tt), '|', tt[:150].replace('\n', ' '))
            except Exception:
                pass
    browser.close()
