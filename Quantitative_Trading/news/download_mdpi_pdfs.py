# -*- coding: utf-8 -*-
import sys
import io
import os
import time

from playwright.sync_api import sync_playwright

def log(msg):
    print(msg, flush=True)

PDF_DIR = r"D:\Desktop\single_target_with-turn\pdfs"
os.makedirs(PDF_DIR, exist_ok=True)

TASKS = [
    {"name": "Aerospace_10-8-698_Adaptive_IMM_UKF.pdf", "page": "https://www.mdpi.com/2226-4310/10/8/698"},
    {"name": "RemoteSensing_16-6-1051_Adaptive_IMM_PD_Radar.pdf", "page": "https://www.mdpi.com/2072-4292/16/6/1051"},
    {"name": "RemoteSensing_18-2-321_MultiFeature_Association.pdf", "page": "https://www.mdpi.com/2072-4292/18/2/321"},
    {"name": "Electronics_14-17-3461_MultiRadar_Track_Fusion.pdf", "page": "https://www.mdpi.com/2079-9292/14/17/3461"},
]


def save_if_pdf(body, dest):
    if len(body) > 10000 and body[:5] == b"%PDF-":
        with open(dest, "wb") as f:
            f.write(body)
        return True
    return False


with sync_playwright() as p:
    context = p.chromium.launch_persistent_context(
        user_data_dir=os.path.join(os.environ.get("TEMP", "."), "pw_mdpi_profile5"),
        channel="msedge",
        headless=False,
        accept_downloads=True,
        viewport={"width": 1366, "height": 900},
    )
    log("浏览器已启动")

    for t in TASKS:
        dest = os.path.join(PDF_DIR, t["name"])
        if os.path.exists(dest) and os.path.getsize(dest) > 100000:
            log(f"跳过(已存在): {t['name']}")
            continue
        ok = False
        page = context.new_page()
        downloads = []
        pdf_bodies = []
        page.on("download", lambda dl: downloads.append(dl))
        page.on("response", lambda r: pdf_bodies.append(r))
        try:
            log(f"== 开始 {t['name']} ==")
            page.goto(t["page"], timeout=45000, wait_until="domcontentloaded")
            time.sleep(4)
            log(f"文章页已打开: {page.title()[:50]}")

            link = page.query_selector("a[href*='/pdf']")
            if not link:
                raise RuntimeError("未找到PDF按钮")
            pdf_url = link.get_attribute("href")
            if pdf_url.startswith("/"):
                pdf_url = "https://www.mdpi.com" + pdf_url
            log(f"PDF直链: {pdf_url[:80]}")

            try:
                page.goto(pdf_url, timeout=15000, wait_until="commit")
            except Exception as e:
                log(f"(导航中断/超时: {type(e).__name__})")

            for round_i in range(10):
                time.sleep(5)
                if downloads:
                    dl = downloads[0]
                    tmp = dest + ".part"
                    try:
                        dl.save_as(tmp)
                        size = os.path.getsize(tmp)
                        with open(tmp, "rb") as f:
                            head = f.read(5)
                        log(f"  第{round_i+1}轮: 捕获下载 size={size} head={head}")
                        if size > 10000 and head == b"%PDF-":
                            os.replace(tmp, dest)
                            log(f"  下载成功(下载事件): {size/1024:.0f} KB")
                            ok = True
                            break
                        os.remove(tmp)
                    except Exception as e:
                        log(f"  下载保存失败: {type(e).__name__}")
                for r in pdf_bodies:
                    try:
                        body = r.body()
                    except Exception:
                        continue
                    if save_if_pdf(body, dest):
                        log(f"  下载成功(响应体): {len(body)/1024:.0f} KB, {r.url[:70]}")
                        ok = True
                        break
                if ok:
                    break
                log(f"  第{round_i+1}轮: 尚未拿到PDF")
            if not ok:
                log(f"  FAILED: {t['name']}")
        except Exception as e:
            log(f"  FAILED: {t['name']}: {e}")
        finally:
            page.close()
        time.sleep(6)

    context.close()
    log("全部完成")
