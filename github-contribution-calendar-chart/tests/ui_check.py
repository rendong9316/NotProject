from pathlib import Path
from playwright.sync_api import sync_playwright


ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / "test-artifacts"
ARTIFACTS.mkdir(exist_ok=True)


def assert_no_page_overflow(page):
    dimensions = page.evaluate(
        """() => ({
            scrollWidth: document.documentElement.scrollWidth,
            clientWidth: document.documentElement.clientWidth
        })"""
    )
    assert dimensions["scrollWidth"] <= dimensions["clientWidth"], dimensions


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(headless=True)
    page = browser.new_page(viewport={"width": 1440, "height": 900})
    console_errors = []
    page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)
    page.goto("http://127.0.0.1:4783", wait_until="networkidle")
    page.wait_for_selector(".day")

    assert page.title() == "本地 Git 提交热力图"
    assert page.locator(".repo-row").count() > 0
    assert page.locator(".day").count() >= 371
    assert "提交记录" in page.locator("h1").inner_text()
    assert_no_page_overflow(page)
    page.screenshot(path=ARTIFACTS / "desktop.png", full_page=True)

    page.locator("select").select_option("2025")
    page.wait_for_load_state("networkidle")
    assert "2025" in page.locator("h1").inner_text()

    first_repo = page.locator(".repo-row").first
    first_repo.click()
    page.wait_for_load_state("networkidle")

    mobile = browser.new_page(viewport={"width": 390, "height": 844})
    mobile.goto("http://127.0.0.1:4783", wait_until="networkidle")
    mobile.wait_for_selector(".day")
    assert_no_page_overflow(mobile)
    mobile.screenshot(path=ARTIFACTS / "mobile.png", full_page=True)

    assert not console_errors, console_errors
    browser.close()
