# -*- coding: utf-8 -*-
import sys
import io
import time
import urllib.parse
from playwright.sync_api import sync_playwright

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

def log(msg):
    print(msg, flush=True)

QUERY = "track fusion covariance intersection"
url = "https://arxiv.org/search/?searchtype=all&query=" + urllib.parse.quote(QUERY)

with sync_playwright() as p:
    context = p.chromium.launch_persistent_context(
        user_data_dir=r"C:\Users\rendong\AppData\Local\Temp\pw_arxiv2",
        channel="msedge",
        headless=False,
    )
    page = context.new_page()
    page.goto(url, timeout=60000, wait_until="domcontentloaded")
    time.sleep(6)
    log("URL: " + page.url)
    page.screenshot(path=r"C:\Users\rendong\AppData\Local\Temp\opencode\arxiv_debug.png")
    body = page.inner_text("body")
    log("=== 页面文本(前1500字) ===")
    log(body[:1500])
    log("=== 结果条数 ===")
    log(str(len(page.query_selector_all("li.arxiv-result"))))
    context.close()
