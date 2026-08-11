"""真实浏览器完整操作：筛选沪深300指数调样公告并翻页收集"""
import sys, json, time
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

def pick(pg, label, option):
    """点击指定筛选标签，选择选项"""
    item = pg.locator('.filter-wrap .filter-item').filter(has_text=label).first
    trigger = item.locator('.drop-down')
    trigger.click()
    pg.wait_for_timeout(1200)
    opt = item.locator('.overlay-items .link-btn').filter(has_text=option).first
    opt.click()
    pg.wait_for_timeout(800)

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

    # 日期范围 2010-01-01 ~ 2019-12-31
    date_input = pg.locator('input[placeholder="开始日期         -     结束日期    "]')
    date_input.click()
    pg.wait_for_timeout(500)
    date_input.fill('2010-01-01 - 2019-12-31')
    pg.press('body', 'Enter')
    pg.wait_for_timeout(1000)

    # 点击搜索按钮（filter-wrap 里的）
    search_btn = pg.locator('.filter-wrap button, .filter-wrap .ivu-btn').filter(has_text='搜索')
    print('搜索按钮数量:', search_btn.count())
    if search_btn.count():
        search_btn.first.scroll_into_view_if_needed()
        search_btn.first.click()
    else:
        # 直接找页面上的搜索按钮
        btns = pg.locator('.ivu-btn').filter(has_text='搜索')
        print('备选按钮数量:', btns.count())
        if btns.count():
            btns.first.click()
    pg.wait_for_timeout(5000)

    # 检查结果数
    print('=== 结果统计 ===')
    total_el = pg.locator('.ivu-table-page, .ivu-page-total, [class*=total]')
    for i in range(total_el.count()):
        print(f'  [{i}]', total_el.nth(i).inner_text(timeout=2000)[:80])

    # 看第一页列表
    print('=== 第一页行 ===')
    rows = pg.locator('.ivu-table-row')
    print('行数:', rows.count())
    for i in range(min(rows.count(), 20)):
        try:
            print(' ', rows.nth(i).inner_text(timeout=2000).replace('\n', ' | ')[:130])
        except Exception:
            pass

    # 保存会话状态供后续脚本复用
    ctx.storage_state(path='scratch/csindex_state.json')
    browser.close()
