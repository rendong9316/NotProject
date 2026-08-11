# -*- coding: utf-8 -*-
import sys
import io
import time

from playwright.sync_api import sync_playwright

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

LINKS = [
    "http://stock.eastmoney.com/a/202608103836079413.html",
    "http://stock.eastmoney.com/a/202608103836555249.html",
    "http://stock.eastmoney.com/a/202608073835272277.html",
    "http://stock.eastmoney.com/a/202608073835162133.html",
    "http://stock.eastmoney.com/a/202608073835237146.html",
    "http://stock.eastmoney.com/a/202608063833962528.html",
    "http://stock.eastmoney.com/a/202608053832644429.html",
    "http://stock.eastmoney.com/a/202608043831456091.html",
    "http://stock.eastmoney.com/a/202608053832534737.html",
    "http://stock.eastmoney.com/a/202608043831342778.html",
]

OUTPUT = "news_articles_raw.txt"

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False)
    page = browser.new_page()
    lines = []
    for i, url in enumerate(LINKS, 1):
        try:
            page.goto(url, timeout=30000, wait_until="domcontentloaded")
            time.sleep(3)
            title = ""
            t_el = page.query_selector("h1")
            if t_el:
                title = t_el.inner_text().strip()
            body_el = page.query_selector("#ContentBody")
            if not body_el:
                body_el = page.query_selector(".txtinfos, .article-body")
            body = body_el.inner_text().strip() if body_el else "(正文未抓取到)"
            lines.append(f"===== 文章[{i}] =====")
            lines.append(f"标题: {title}")
            lines.append(body)
            lines.append("")
        except Exception as e:
            lines.append(f"===== 文章[{i}] 抓取失败: {e} =====")
            lines.append("")
        print(f"已抓取 {i}/{len(LINKS)}")

    with open(OUTPUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"保存到 {OUTPUT}")
    browser.close()
