"""真实浏览器完整操作 v2：处理 header 遮罩"""
import sys, json, time
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

def pick(pg, label, option):
    item = pg.locator('.filter-wrap .filter-item').filter(has_text=label).first
    trigger = item.locator('.drop-down')
    try:
        trigger.scroll_into_view_if_needed()
        trigger.click(force=True)
    except Exception:
        trigger.click(force=True)
    pg.wait_for_timeout(1200)
    opt = item.locator('.overlay-items .link-btn').filter(has_text=option).first
    opt.click(force=True)
    pg.wait_for_timeout(800)
    pg.keyboard.press('Escape')
    pg.wait_for_timeout(600)

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=80)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN')
    pg = ctx.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsCenter', timeout=90000, wait_until='domcontentloaded')
    pg.wait_for_timeout(5000)

    pick(pg, '种类', '公告')
    pick(pg, '类别', '指数')
    pick(pg, '主题', '指数调样')
    pick(pg, '指数系列', '中证指数')

    date_input = pg.locator('input[placeholder*="开始日期"]')
    date_input.click(force=True)
    pg.wait_for_timeout(500)
    date_input.fill('2010-01-01 - 2019-12-31')
    pg.press('body', 'Enter')
    pg.wait_for_timeout(1000)

    search_btn = pg.locator('.filter-wrap button, .filter-wrap .ivu-btn').filter(has_text='搜索')
    print('搜索按钮数量:', search_btn.count())
    if search_btn.count():
        search_btn.first.scroll_into_view_if_needed()
        search_btn.first.click(force=True)
    pg.wait_for_timeout(6000)

    # 结果统计
    print('=== 结果 ===')
    for sel in ['.ivu-page-total', '[class*=page-total]', '.ivu-page']:
        el = pg.locator(sel)
        for i in range(el.count()):
            try:
                print(sel, '[', i, ']', el.nth(i).inner_text(timeout=2000).strip()[:100])
            except Exception:
                pass

    rows = pg.locator('.ivu-table-row')
    print('本页行数:', rows.count())
    for i in range(min(rows.count(), 20)):
        try:
            print(' ', rows.nth(i).inner_text(timeout=2000).replace('\n', ' | ')[:130])
        except Exception:
            pass

    ctx.storage_state(path='scratch/csindex_state.json')
    browser.close()
