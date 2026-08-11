"""抓取临时调整类公告正文：1335(2009-12-29补充), 6271(2015-01-22), 6802(2015-05-14)"""
import sys, json, re, os
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

TARGETS = ['1335', '6271', '6802']

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=50)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN',
                              storage_state='scratch/csindex_state.json')
    pg = ctx.new_page()

    for nid in TARGETS:
        print(f'\n=== {nid} ===', flush=True)
        pg.goto(f'https://www.csindex.com.cn/#/about/newsDetail?id={nid}',
                timeout=90000, wait_until='domcontentloaded')
        pg.wait_for_timeout(12000)
        text = pg.evaluate("() => document.body.innerText")
        m = re.search(r'(关于调整[\s\S]{5,60}?公告|补充公告)', text)
        title = m.group(0).strip() if m else '未找到标题'
        m2 = re.search(r'发布时间：(\d{4}-\d{2}-\d{2})', text)
        date = m2.group(1) if m2 else ''
        m3 = re.search(r'发布时间：[^\n]+\n', text)
        start = m3.end() if m3 else text.find('正文')
        end = text.find('附 件', start)
        if end == -1:
            end = text.find('附件', start)
        if end == -1:
            end = text.find('中证指数有限公司', start)
        body = text[start:end] if start != -1 else ''
        print(f'标题: {title} | 日期: {date} | 正文: {len(body)}')
        print('正文:', body[:500].replace('\n', ' '))
        with open(f'scratch/raw_news/{nid}.json', 'w', encoding='utf-8') as f:
            json.dump({'id': nid, 'actual_date': date, 'title': title, 'body': body},
                      f, ensure_ascii=False, indent=2)
        time_sleep = 13
        import time
        time.sleep(time_sleep)
    browser.close()
