"""查看附件 Excel 结构"""
import sys, glob, os
sys.stdout.reconfigure(encoding='utf-8')
import pandas as pd

files = sorted(glob.glob('scratch/attach/*'))
for f in files:
    ext = os.path.splitext(f)[1].lower()
    print(f'\n===== {os.path.basename(f)} =====')
    try:
        if ext == '.txt':
            print(open(f, encoding='utf-8').read())
            continue
        xl = pd.ExcelFile(f)
        print('sheets:', xl.sheet_names)
        for sh in xl.sheet_names:
            df = pd.read_excel(f, sheet_name=sh, header=None, nrows=12)
            print(f'  [{sh}] shape(first12rows): {df.shape}')
            print(df.head(12).fillna('').to_string(max_colwidth=14))
    except Exception as e:
        print('  错误:', e)
