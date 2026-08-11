"""真实浏览器操作 v4：翻页收集沪深300指数调样公告链接"""
import sys, json, time
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=60)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN')
    pg = ctx.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsCenter', timeout=90000, wait_until='domcontentloaded')
    pg.wait_for_timeout(5000)

    # 筛选：主题=指数调样
    item = pg.locator('.filter-wrap .filter-item').filter(has_text='主题').first
    trigger = item.locator('.drop-down')
    trigger.scroll_into_view_if_needed()
    trigger.click(force=True)
    pg.wait_for_timeout(1500)
    item.locator('.overlay-items .link-btn').filter(has_text='指数调样').first.click(force=True)
    pg.wait_for_timeout(1200)

    # 日期
    date_input = pg.locator('input[placeholder*="开始日期"]')
    date_input.click(force=True)
    pg.wait_for_timeout(500)
    date_input.fill('2010-01-01 - 2019-12-31')
    pg.press('body', 'Enter')
    pg.wait_for_timeout(1000)

    # 搜索
    search_btn = pg.locator('.filter-wrap button, .filter-wrap .ivu-btn').filter(has_text='搜索').first
    search_btn.scroll_into_view_if_needed()
    search_btn.click(force=True)
    pg.wait_for_timeout(6000)

    hits = []
    page_count = 0
    while True:
        page_count += 1
        rows = pg.locator('.ivu-table-row')
        n = rows.count()
        print(f'--- 第 {page_count} 页, {n} 行 ---', flush=True)
        for i in range(n):
            try:
                text = rows.nth(i).inner_text(timeout=2000)
                if '沪深300' in text:
                    href = rows.nth(i).locator('a').first.get_attribute('href', timeout=1500) if rows.nth(i).locator('a').count() else None
                    linktext = rows.nth(i).locator('a').first.inner_text(timeout=1500) if rows.nth(i).locator('a').count() else None
                    print('  HIT:', text.replace('\n', ' | ')[:120], '| href=', href, '| link=', linktext)
                    hits.append({'page': page_count, 'row': i, 'text': text, 'href': href, 'linktext': linktext})
            except Exception:
                pass

        # 找下一页按钮
        next_btn = pg.locator('.ivu-page-next:not(.ivu-page-disabled)')
        if next_btn.count() and page_count < 150:
            next_btn.first.click(force=True)
            pg.wait_for_timeout(3200)
        else:
            break

    print(f'\n共翻 {page_count} 页，命中 {len(hits)} 条')
    with open('scratch/csindex_ui_hits.json', 'w', encoding='utf-8') as f:
        json.dump(hits, f, ensure_ascii=False, indent=2)
    ctx.storage_state(path='scratch/csindex_state.json')
    browser.close()
