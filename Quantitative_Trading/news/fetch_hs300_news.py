# -*- coding: utf-8 -*-
import sys
import io
import time
import urllib.parse

from playwright.sync_api import sync_playwright

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

KEYWORD = "沪深300"
SO_URL = "https://so.eastmoney.com/news/s?keyword=" + urllib.parse.quote(KEYWORD)
OUTPUT = "hs300_news.txt"

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False)
    page = browser.new_page()
    page.goto(SO_URL, timeout=30000, wait_until="domcontentloaded")
    time.sleep(5)

    page.screenshot(path="baidu_news.png")
    print("URL:", page.url)

    lines = [f"关键词: {KEYWORD}", f"来源: 东方财富资讯搜索", f"页面: {page.url}", "-" * 60]

    items = page.query_selector_all(".news_item, .search-item, li")
    count = 0
    for it in items:
        title_el = it.query_selector("a")
        if not title_el:
            continue
        title = title_el.inner_text().strip()
        link = title_el.get_attribute("href") or ""
        if not title or KEYWORD not in title:
            continue
        if not link.startswith("http") or "stock.eastmoney.com" not in link:
            continue
        lines.append(f"[{count+1}] {title}")
        lines.append(f"    链接: {link}")
        lines.append("")
        count += 1
        if count >= 20:
            break

    if count == 0:
        body = page.inner_text("body")[:2000]
        lines.append("未匹配到结构化条目，页面原始文本预览：")
        lines.append(body)

    with open(OUTPUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    print(f"已保存 {count} 条新闻到 {OUTPUT}")
    time.sleep(5)
    browser.close()
