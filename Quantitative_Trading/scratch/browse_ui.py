"""真实浏览器操作：打开官网新闻中心，模拟点击筛选/翻页，验证能否获取历史公告"""
import sys, time
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=100)
    ctx = browser.new_context(
        viewport={'width': 1440, 'height': 900},
        locale='zh-CN',
        user_agent='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
    )
    pg = ctx.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsCenter', timeout=90000, wait_until='domcontentloaded')
    pg.wait_for_timeout(6000)

    # 页面结构探测：找到主题筛选下拉
    print('=== 页面文本(部分) ===')
    txt = pg.inner_text('body')
    print(txt[800:1800])
    print()

    # 找下拉框：主题筛选
    selectors = ['[class*=select]', 'input[placeholder]', '.el-input', 'li', '[role=listbox]']
    for sel in selectors:
        try:
            n = pg.locator(sel).count()
            if n:
                print(f'{sel}: {n} 个')
                for i in range(min(n, 5)):
                    try:
                        t = pg.locator(sel).nth(i).inner_text(timeout=2000).strip().replace('\n', ' | ')[:80]
                        print(f'   [{i}] {t}')
                    except Exception:
                        pass
        except Exception:
            pass
    browser.close()
