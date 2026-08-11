# -*- coding: utf-8 -*-
import sys
import io
import time

from playwright.sync_api import sync_playwright

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

URL = "https://www.mdpi.com/2226-4310/10/8/698/pdf?version=1691398026"

with sync_playwright() as p:
    context = p.chromium.launch_persistent_context(
        user_data_dir=r"C:\Users\rendong\AppData\Local\Temp\pw_mdpi_debug2",
        channel="msedge",
        headless=False,
    )
    page = context.new_page()
    page.goto("https://www.mdpi.com/2226-4310/10/8/698", timeout=60000, wait_until="domcontentloaded")
    time.sleep(5)

    def handler(route):
        resp = route.fetch(timeout=90000)
        body = resp.body()
        print("=== 响应体内容 ===")
        print(body[:2400].decode("utf-8", errors="replace"))
        route.fulfill(status=200, content_type="text/html", body=body)

    page.route("**/698/pdf**", handler)
    try:
        page.goto(URL, timeout=30000, wait_until="commit")
    except Exception as e:
        print(f"导航中断: {type(e).__name__}")
    time.sleep(10)
    context.close()
