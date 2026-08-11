"""直接用 queryAnnouncementById 抓取公告正文"""
import sys, json, time
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

ANNOUNCEMENTS = [
    (1205, '2009-12-14'),
    (1607, '2010-07-16'),
    (1385, '2010-06-17'),
    (6150, '2010-12-13'),
    (2714, '2011-06-13'),
    (792,  '2011-08-05'),
    (6687, '2011-12-12'),
    (6699, '2012-06-11'),
    (3242, '2012-12-17'),
    (2723, '2013-06-17'),
    (3453, '2013-08-13'),
    (6750, '2013-12-02'),
    (3670, '2014-06-03'),
    (3596, '2014-12-01'),
    (4084, '2015-06-01'),
    (6802, '2015-05-14'),
    (4272, '2015-11-30'),
    (2882, '2015-12-28'),
    (3855, '2016-05-30'),
    (4003, '2016-11-28'),
    (12583, '2017-05-31'),
    (12590, '2017-11-27'),
    (11518, '2018-05-28'),
    (11859, '2018-12-03'),
    (11379, '2019-06-03'),
    (11529, '2019-12-02'),
]

results = {}
with sync_playwright() as p:
    b = p.chromium.launch(headless=True)
    pg = b.new_page()
    pg.goto('https://www.csindex.com.cn/#/about/newsDetail?id=6150', timeout=60000, wait_until='domcontentloaded')
    pg.wait_for_timeout(3000)
    for aid, pub_date in ANNOUNCEMENTS:
        try:
            r = pg.request.get(f'https://www.csindex.com.cn/csindex-home/announcement/queryAnnouncementById?id={aid}')
            d = r.json()
            if d.get('code') == '200':
                content = d['data']
                results[aid] = {'publish_date': pub_date, 'detail': content}
                print(f'{pub_date} id={aid} OK title={content.get("title","")[:40]}', flush=True)
            else:
                print(f'{pub_date} id={aid} 非200: {str(d)[:100]}', flush=True)
        except Exception as e:
            print(f'{pub_date} id={aid} 异常: {e}', flush=True)
        time.sleep(0.3)
    b.close()

with open('scratch/csindex_announcements_2010s_api.json', 'w', encoding='utf-8') as f:
    json.dump(results, f, ensure_ascii=False, indent=1)
print('保存完成, 条数:', len(results))
