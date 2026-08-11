# -*- coding: utf-8 -*-
import sys
import io
import os
import time

from playwright.sync_api import sync_playwright

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

def log(msg):
    print(msg, flush=True)

PDF_DIR = r"D:\Desktop\single_target_with-turn\pdfs"
os.makedirs(PDF_DIR, exist_ok=True)

TASKS = [
    {"name": "arxiv_2607.13573_IMMNet.pdf", "url": "https://arxiv.org/pdf/2607.13573"},
    {"name": "arxiv_2511.20294_SAFE_IMM.pdf", "url": "https://arxiv.org/pdf/2511.20294"},
    {"name": "arxiv_2603.16768_Overlapping_CI.pdf", "url": "https://arxiv.org/pdf/2603.16768"},
    {"name": "arxiv_2603.20402_CI_SDP.pdf", "url": "https://arxiv.org/pdf/2603.20402"},
    {"name": "arxiv_2411.13201_Fused_Bistatic.pdf", "url": "https://arxiv.org/pdf/2411.13201"},
    {"name": "arxiv_2410.05883_PCRLB_Geometry.pdf", "url": "https://arxiv.org/pdf/2410.05883"},
]

with sync_playwright() as p:
    context = p.chromium.launch_persistent_context(
        user_data_dir=os.path.join(os.environ.get("TEMP", "."), "pw_arxiv_dl"),
        channel="msedge",
        headless=False,
        accept_downloads=True,
        viewport={"width": 1366, "height": 900},
    )
    for t in TASKS:
        dest = os.path.join(PDF_DIR, t["name"])
        if os.path.exists(dest) and os.path.getsize(dest) > 50000:
            log(f"跳过(已存在): {t['name']}")
            continue
        page = context.new_page()
        responses = []
        page.on("response", lambda r: responses.append(r))
        try:
            log(f"== 下载: {t['name']} ==")
            try:
                page.goto(t["url"], timeout=30000, wait_until="commit")
            except Exception as e:
                log(f"(导航中断: {type(e).__name__})")
            ok = False
            for _ in range(8):
                time.sleep(3)
                for r in responses:
                    try:
                        body = r.body()
                    except Exception:
                        continue
                    if len(body) > 50000 and body[:5] == b"%PDF-":
                        with open(dest, "wb") as f:
                            f.write(body)
                        log(f"  下载成功(响应体): {len(body)/1024:.0f} KB, {r.url[:70]}")
                        ok = True
                        break
                if ok:
                    break
            if not ok:
                resp = context.request.get(t["url"], timeout=60000, max_redirects=10)
                body = resp.body()
                if len(body) > 50000 and body[:5] == b"%PDF-":
                    with open(dest, "wb") as f:
                        f.write(body)
                    log(f"  下载成功(request): {len(body)/1024:.0f} KB")
                    ok = True
            if not ok:
                log(f"  FAILED: {t['name']}")
        except Exception as e:
            log(f"  FAILED: {t['name']}: {e}")
        finally:
            page.close()
        time.sleep(4)
    context.close()
    log("全部完成")
