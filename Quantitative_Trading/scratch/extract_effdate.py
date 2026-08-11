"""提取每条公告的生效日期"""
import sys, json, re, os
sys.stdout.reconfigure(encoding='utf-8')

old_bodies = json.load(open('scratch/csindex_announcements_2010s.json', encoding='utf-8'))
raw_news = {f.replace('.json', ''): json.load(open(f'scratch/raw_news/{f}', encoding='utf-8'))
            for f in os.listdir('scratch/raw_news') if f.endswith('.json')}

BODIES = {
    '2009-12-14': '1205', '2010-06-17': '1385', '2010-12-13': '6150',
    '2011-06-13': '2714', '2011-12-12': '6687', '2012-06-11': '6699',
    '2012-12-17': '3242', '2013-06-17': '2723', '2013-12-02': '6750',
    '2014-06-03': '3670', '2014-12-01': '3596', '2015-06-01': '4084',
    '2015-11-30': '4272', '2016-05-30': '3855', '2016-11-28': '4003',
    '2017-05-31': '12583', '2017-11-27': '12590', '2018-05-28': '11518',
    '2018-12-03': '11859', '2019-06-03': '11379', '2019-12-02': '11529',
}

PAT = re.compile(r'(?:决定|于|自|从|在)[于从\s]*(\d{4})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日')
PAT2 = re.compile(r'(\d{4})\s*年\s*(\d{1,2})\s*月\s*(?:第)?\s*(\d{1,2})\s*(?:个)?\s*交易')
PAT3 = re.compile(r'(\d{4})\s*年\s*(\d{1,2})\s*月\s*第一个交易日')

for date, bid in BODIES.items():
    if bid in raw_news:
        text = raw_news[bid]['body']
    else:
        text = old_bodies[bid]['text']
    hits = []
    for m in PAT3.finditer(text):
        hits.append(f'{m.group(1)}-{int(m.group(2)):02d}-01(第一交易日)')
    for m in PAT.finditer(text):
        hits.append(f'{m.group(1)}-{int(m.group(2)):02d}-{int(m.group(3)):02d}')
    print(date, '->', hits[:3])
