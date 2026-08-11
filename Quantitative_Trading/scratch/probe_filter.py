"""探测新闻中心筛选区 DOM 结构"""
import sys
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=100)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN')
    pg = ctx.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsCenter', timeout=90000, wait_until='domcontentloaded')
    pg.wait_for_timeout(6000)

    # 输入框 placeholder
    for i in range(pg.locator('input[placeholder]').count()):
        ph = pg.locator('input[placeholder]').nth(i).get_attribute('placeholder')
        print(f'input[{i}] placeholder={ph}')
    print('---')

    # 筛选区域文本
    for i in range(pg.locator('input[placeholder]').count()):
        inp = pg.locator('input[placeholder]').nth(i)
        try:
            parent = inp.evaluate("e => { let el = e; for (let k=0;k<6 && el.parentElement; k++) el = el.parentElement; return el.innerText; }")
            print(f'input[{i}] 祖先文本: {parent[:300]}')
            print('---')
        except Exception as ex:
            print(f'input[{i}] 异常: {ex}')
    browser.close()
