# -*- coding: utf-8 -*-
import sys
import io
import os
import time
import json
import urllib.parse

from playwright.sync_api import sync_playwright

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

def log(msg):
    print(msg, flush=True)

QUERIES = [
    "coordinated turn tracking",
    "covariance intersection fusion",
    "bistatic radar tracking",
    "interacting multiple model maneuvering",
    "strong tracking filter",
    "maneuvering target tracking survey",
]

OUT = r"D:\Desktop\single_target_with-turn\papers\arxiv_search_2026-08-11.json"

with sync_playwright() as p:
    context = p.chromium.launch_persistent_context(
        user_data_dir=os.path.join(os.environ.get("TEMP", "."), "pw_arxiv"),
        channel="msedge",
        headless=False,
        viewport={"width": 1366, "height": 900},
    )
    page = context.new_page()
    results = {}
    for q in QUERIES:
        page = context.new_page()
        url = "https://arxiv.org/search/?searchtype=all&query=" + urllib.parse.quote(q)
        log(f"== 检索: {q} ==")
        page.goto(url, timeout=60000, wait_until="domcontentloaded")
        time.sleep(6)
        log(f"  最终URL: {page.url[:100]}")
        cnt_text = page.inner_text("h1, .no-result")
        log(f"  页面提示: {cnt_text.strip()[:80]}")
        items = page.query_selector_all("li.arxiv-result")
        entries = []
        for it in items[:8]:
            try:
                title_el = it.query_selector(".title")
                title = title_el.inner_text().replace("\n", " ").strip()
                link_el = it.query_selector("p.list-title a")
                pdf_el = it.query_selector("a[href*='/pdf/']")
                abstract_el = it.query_selector("span.abstract-full")
                abstract = abstract_el.inner_text().strip() if abstract_el else ""
                entry = {
                    "title": title,
                    "page": link_el.get_attribute("href") if link_el else "",
                    "pdf": pdf_el.get_attribute("href") if pdf_el else "",
                    "abstract": abstract[:300],
                }
                entries.append(entry)
            except Exception as e:
                log(f"  解析失败: {e}")
        results[q] = entries
        log(f"  命中 {len(entries)} 条")
        time.sleep(2)

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    log(f"已保存 {OUT}")
    time.sleep(2)
    context.close()
