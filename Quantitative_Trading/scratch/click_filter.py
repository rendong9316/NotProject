"""模拟真实点击：主题=指数调样 → 搜索 → 翻页 → 收集沪深300公告链接"""
import sys, json
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

collected = []

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=120)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN')
    pg = ctx.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsCenter', timeout=90000, wait_until='domcontentloaded')
    pg.wait_for_timeout(5000)

    # 筛选区祖先: input[2] 所在的 filter 容器
    filter_root = pg.locator('input[placeholder]').nth(2).evaluate(
        "e => { let el = e.parentElement; while (el && el.innerText && el.innerText.length < 100) el = el.parentElement; return el.className; }"
    )
    print('filter root class:', filter_root)

    # 点击"主题"下拉
    theme_trigger = pg.locator('text=主题').first
    theme_trigger.click()
    pg.wait_for_timeout(1500)
    # 下拉项
    opts = pg.locator('[class*=dropdown] li, [class*=option], [role=option], ul li')
    labels = []
    for i in range(opts.count()):
        try:
            t = opts.nth(i).inner_text(timeout=1000).strip()
            if t:
                labels.append(t)
        except Exception:
            pass
    print('下拉选项:', labels)

    # 选择"指数调样"
    target = None
    for i in range(opts.count()):
        try:
            if opts.nth(i).inner_text(timeout=1000).strip() == '指数调样':
                target = opts.nth(i)
                break
        except Exception:
            pass
    if target:
        target.click()
        print('已点击 指数调样')
    else:
        print('未找到 指数调样 选项!')

    pg.wait_for_timeout(2000)
    # 点击搜索按钮
    search_btn = pg.locator('text=搜索').first
    search_btn.click()
    pg.wait_for_timeout(4000)

    # 读列表第一页
    rows = pg.locator('table tr, [class*=table] tr')
    print('表行数:', rows.count())
    browser.close()
