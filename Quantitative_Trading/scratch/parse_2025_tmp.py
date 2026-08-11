"""提取两次临时调整的沪深300名单"""
import sys
sys.stdout.reconfigure(encoding='utf-8')
import pandas as pd

files = {
    '海通证券退市(2025-02-06)': 'scratch/attach/20250206175421-%E6%8C%87%E6%95%B0%E6%A0%B7%E6%9C%AC%E8%B0%83%E6%95%B4%E5%90%8D%E5%8D%95.xlsx',
    '中国重工退市(2025-07-25)': 'scratch/attach/20250722164043-%E6%8C%87%E6%95%B0%E6%A0%B7%E6%9C%AC%E8%B0%83%E6%95%B4%E5%90%8D%E5%8D%95.xlsx',
}
for tag, f in files.items():
    print('=' * 40)
    print(tag)
    df = pd.read_excel(f, sheet_name='Sheet1', header=None, skiprows=2)
    df.columns = ['code', 'name', 'out_c', 'out_n', 'in_c', 'in_n']
    hs = df[df['name'].astype(str).str.strip() == '沪深300']
    for _, r in hs.iterrows():
        print('  调出', r['out_c'], r['out_n'], '->', '调入', r['in_c'], r['in_n'])
