"""慢速重抓缺失公告正文，校验标题匹配"""
import sys, json, time
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright

MISSING = [
    (4084, '2015-06-01', '关于调整沪深300和中证香港100等指数样本股的公告'),
    (6802, '2015-05-14', '关于调整沪深300和中证香港300等指数样本股的公告'),
    (4272, '2015-11-30', '关于调整沪深300和中证香港100等指数样本股的公告'),
    (2882, '2015-12-28', '关于调整沪深300等指数样本股的公告'),
    (3855, '2016-05-30', '关于调整沪深300和中证香港100等指数样本股的公告'),
    (4003, '2016-11-28', '关于调整沪深300和中证香港100等指数样本股的公告'),
    (12583, '2017-05-31', '关于调整沪深300和中证香港100等指数样本股的公告'),
    (12590, '2017-11-27', '关于调整沪深300和中证香港100等指数样本股的公告'),
    (11518, '2018-05-28', '关于调整沪深300和中证香港100等指数样本股的公告'),
    (11859, '2018-12-03', '关于调整沪深300和中证香港100等指数样本股的公告'),
    (11379, '2019-06-03', '关于调整沪深300和中证香港100等指数样本股的公告'),
    (11529, '2019-12-02', '关于调整沪深300和中证香港100等指数样本股的公告'),
]

results = {}
with sync_playwright() as p:
    b = p.chromium.launch(headless=True)
    pg = b.new_page()
    for aid, pub_date, title in MISSING:
        ok = False
        for attempt in range(3):
            try:
                pg.goto(f'https://www.csindex.com.cn/#/about/newsDetail?id={aid}', timeout=60000, wait_until='domcontentloaded')
                pg.wait_for_timeout(4000)
                txt = pg.inner_text('body')
                if pub_date in txt and ('调整沪深 300' in txt or '调整沪深300' in txt or '样本股' in txt):
                    results[aid] = {'publish_date': pub_date, 'title': title, 'text': txt}
                    ok = True
                    print(f'{pub_date} id={aid} OK 长度={len(txt)}', flush=True)
                    break
                else:
                    print(f'{pub_date} id={aid} 尝试{attempt+1} 内容不匹配', flush=True)
            except Exception as e:
                print(f'{pub_date} id={aid} 尝试{attempt+1} 异常: {e}', flush=True)
            time.sleep(5)
        if not ok:
            print(f'{pub_date} id={aid} 最终失败', flush=True)
        time.sleep(3)
    b.close()

with open('scratch/csindex_missing_retry.json', 'w', encoding='utf-8') as f:
    json.dump(results, f, ensure_ascii=False, indent=1)
print('保存, 成功条数:', len(results))
