from playwright.sync_api import sync_playwright


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(headless=True)
    page = browser.new_page(viewport={"width": 1440, "height": 900})
    console_errors = []
    refresh_requests = []
    page.on(
        "console",
        lambda message: console_errors.append(message.text)
        if message.type == "error"
        else None,
    )
    page.on(
        "request",
        lambda request: refresh_requests.append(request.method)
        if request.url.endswith("/api/refresh")
        else None,
    )

    page.goto("http://127.0.0.1:4783", wait_until="networkidle")
    refresh = page.get_by_role("button", name="刷新提交")
    assert refresh.is_visible()
    assert page.get_by_title("扫描所有磁盘以发现新仓库").is_visible()

    refresh.click()
    page.locator(".status-banner").wait_for(timeout=30_000)
    assert "已检查" in page.locator(".status-banner").inner_text()
    assert refresh_requests == ["POST"]
    assert not console_errors, console_errors
    browser.close()
