"""真实浏览器操作 v3：带诊断"""
import sys, json, time
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=80)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN')
    pg = ctx.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsCenter', timeout=90000, wait_until='domcontentloaded')
    pg.wait_for_timeout(5000)

    def dbg(tag):
        n = pg.locator('.filter-wrap .filter-item').count()
        print(f'[{tag}] filter-item 计数: {n}')
        return n

    dbg('初始')

    # 只筛选：主题=指数调样
    item = pg.locator('.filter-wrap .filter-item').filter(has_text='主题').first
    print('主题 item 数量:', pg.locator('.filter-wrap .filter-item').filter(has_text='主题').count())
    trigger = item.locator('.drop-down')
    trigger.scroll_into_view_if_needed()
    trigger.click(force=True)
    pg.wait_for_timeout(1500)
    print('点击后 overlay 是否可见:',
          item.locator('.overlay-items').evaluate("e => getComputedStyle(e).display"))
    opts = item.locator('.overlay-items .link-btn')
    print('选项数:', opts.count())
    for i in range(opts.count()):
        print('  ', opts.nth(i).inner_text().strip())
    opts.filter(has_text='指数调样').first.click(force=True)
    pg.wait_for_timeout(1500)
    dbg('选完主题')
    pg.keyboard.press('Escape')
    pg.wait_for_timeout(800)
    dbg('Esc后')

    # 日期范围
    date_input = pg.locator('input[placeholder*="开始日期"]')
    date_input.click(force=True)
    pg.wait_for_timeout(500)
    date_input.fill('2010-01-01 - 2019-12-31')
    pg.press('body', 'Enter')
    pg.wait_for_timeout(1500)
    dbg('日期填完')

    # 搜索
    search_btn = pg.locator('.filter-wrap button, .filter-wrap .ivu-btn').filter(has_text='搜索')
    print('搜索按钮数量:', search_btn.count())
    if search_btn.count():
        search_btn.first.scroll_into_view_if_needed()
        search_btn.first.click(force=True)
    else:
        print('未找到搜索按钮!')
        for b in pg.locator('.ivu-btn').all():
            try:
                t = b.inner_text(timeout=1500).strip()
                if t:
                    print('  btn:', t[:30])
            except Exception:
                pass
    pg.wait_for_timeout(6000)
    dbg('搜索后')

    rows = pg.locator('.ivu-table-row')
    print('本页行数:', rows.count())
    for i in range(min(rows.count(), 20)):
        try:
            print(' ', rows.nth(i).inner_text(timeout=2000).replace('\n', ' | ')[:130])
        except Exception:
            pass

    ctx.storage_state(path='scratch/csindex_state.json')
    browser.close()
