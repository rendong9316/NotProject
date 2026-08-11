"""慢速抓取 2015-2019 沪深300定期调整公告正文"""
import sys, json, time, re, os
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

TARGETS = [
    ('4084',  '2015-06-01'),
    ('4272',  '2015-11-30'),
    ('3855',  '2016-05-30'),
    ('4003',  '2016-11-28'),
    ('12583', '2017-05-31'),
    ('12590', '2017-11-27'),
    ('11518', '2018-05-28'),
    ('11379', '2019-06-03'),
    ('11529', '2019-12-02'),
]

os.makedirs('scratch/raw_news', exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=50)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN',
                              storage_state='scratch/csindex_state.json')
    pg = ctx.new_page()

    for nid, expect_date in TARGETS:
        print(f'\n=== 抓取 id={nid} (期望 {expect_date}) ===', flush=True)
        pg.goto(f'https://www.csindex.com.cn/#/about/newsDetail?id={nid}',
                timeout=90000, wait_until='domcontentloaded')
        pg.wait_for_timeout(12000)

        text = pg.evaluate("() => document.body.innerText")
        # 找标题
        m = re.search(r'关于调整[\s\S]{5,60}?公告', text)
        title = m.group(0).strip() if m else '未找到标题'
        m2 = re.search(r'发布时间：(\d{4}-\d{2}-\d{2})', text)
        date = m2.group(1) if m2 else ''
        # 截取正文：从标题开始 到 附件标记/中证指数有限公司 结束
        start = text.find(title)
        end = text.find('附 件', start)
        if end == -1:
            end = text.find('附件', start)
        body = text[start:end] if start != -1 else ''
        print(f'标题: {title}')
        print(f'日期: {date}  正文长度: {len(body)}')
        print('开头:', body[:80].replace('\n', ' '))

        ok = (date == expect_date) or (len(body) > 300)
        out = {
            'id': nid, 'expected_date': expect_date, 'actual_date': date,
            'title': title, 'body': body,
        }
        with open(f'scratch/raw_news/{nid}.json', 'w', encoding='utf-8') as f:
            json.dump(out, f, ensure_ascii=False, indent=2)
        print('OK' if ok else '!!! 校验失败 !!!')
        time.sleep(13)

    browser.close()
