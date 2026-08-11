"""iView Select 操作：主题=指数调样 → 搜索 → 翻页收集"""
import sys, time, json
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

all_links = []

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=100)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN')
    pg = ctx.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsCenter', timeout=90000, wait_until='domcontentloaded')
    pg.wait_for_timeout(5000)

    # 主题 select = .ivu-select 第3个（种类,类别,主题,指数系列）
    selects = pg.locator('.ivu-select')
    print('ivu-select 数量:', selects.count())
    theme_sel = selects.nth(2)
    theme_sel.click()
    pg.wait_for_timeout(1500)

    # dropdown 选项
    items = pg.locator('.ivu-select-item')
    print('dropdown items:', items.count())
    labels = []
    for i in range(items.count()):
        try:
            t = items.nth(i).inner_text(timeout=1000).strip()
            labels.append(t)
        except Exception:
            pass
    print('选项:', labels)

    # 点击 指数调样
    for i in range(items.count()):
        try:
            if items.nth(i).inner_text(timeout=1000).strip() == '指数调样':
                items.nth(i).click()
                print('已选择: 指数调样')
                break
        except Exception:
            pass
    pg.wait_for_timeout(1500)

    # 搜索按钮
    btn = pg.locator('button, .ivu-btn').filter(has_text='搜索')
    print('搜索按钮数量:', btn.count())
    if btn.count():
        btn.first.scroll_into_view_if_needed()
        btn.first.click()
        print('已点击搜索')
    pg.wait_for_timeout(5000)

    # 读结果
    print('=== 结果 ===')
    rows = pg.locator('.ivu-table-row')
    print('行数:', rows.count())
    for i in range(rows.count()):
        try:
            t = rows.nth(i).inner_text(timeout=2000)
            print('  ', t.replace('\n', ' | ')[:120])
        except Exception:
            pass
    browser.close()
