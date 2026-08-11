"""检查 2010-2014 正文的表格结构"""
import sys, json, re
sys.stdout.reconfigure(encoding='utf-8')

d = json.load(open('scratch/csindex_announcements_2010s.json', encoding='utf-8'))

for k in ['1205', '1385', '6150', '2714', '6687', '3453', '2882', '3670', '3596']:
    v = d[k]
    t = v['text']
    # 找"调整名单"和"备选名单"位置
    print('='*60)
    print(k, v['publish_date'])
    for m in re.finditer(r'(沪深\s*300[^\n]{0,20}(调整|备选)名单[^\n]{0,10}[:：]?)', t):
        print('  段:', m.group(1).replace('\n', ''))
    # 打印调整名单后 15 行原始（repr 显示 tab）
    idx = t.find('沪深 300 指数样本股调整名单')
    if idx == -1:
        idx = t.find('沪深300指数样本股调整名单')
    if idx >= 0:
        seg = t[idx:idx+900]
        print('  原始片段:')
        for line in seg.split('\n')[:16]:
            print('   |', repr(line[:90]))
