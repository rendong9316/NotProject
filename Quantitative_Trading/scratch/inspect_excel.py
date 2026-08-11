import pandas as pd
import sys
sys.stdout.reconfigure(encoding='utf-8')

df = pd.read_excel('CSI300_remake_report.xlsx')
cols = df.columns.tolist()
print('列:', cols)
print('日期列唯一值:')
d = pd.to_datetime(df[cols[4]])
for v in sorted(d.unique()):
    n = (d == v).sum()
    print(' ', v.date(), '条数:', n)
print('总行数:', len(df))
