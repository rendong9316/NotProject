from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()
    audio_events = []
    page.on("console", lambda m: audio_events.append(f"[{m.type}] {m.text}"))
    page.on("pageerror", lambda e: audio_events.append(f"ERROR: {e}"))
    page.goto("http://localhost:18765/quiz.html")
    page.wait_for_load_state("networkidle")
    # Click to start a quiz and try to trigger sound
    page.click("button.mode-btn.blue")
    page.wait_for_timeout(1000)
    for l in audio_events:
        print(l)
    browser.close()
