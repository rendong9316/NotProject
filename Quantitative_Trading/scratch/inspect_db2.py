import sys
sys.stdout.reconfigure(encoding='utf-8')
import sqlite3, os

p = 'data_v5/csi300_2020_present.sqlite'
print('文件大小:', os.path.getsize(p)//1024//1024, 'MB')
con = sqlite3.connect(p)
cur = con.cursor()
rows = cur.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall()
for (name,) in rows:
    try:
        cnt = cur.execute(f'SELECT COUNT(*) FROM "{name}"').fetchone()[0]
        print(f'{name}: {cnt}')
    except Exception as e:
        print(name, 'ERR', e)
# 找 membership 相关表的日期范围
for (name,) in rows:
    if 'member' in name.lower() or 'snapshot' in name.lower():
        cols = [c[1] for c in cur.execute(f'PRAGMA table_info("{name}")').fetchall()]
        print('\n==', name, 'columns:', cols)
        if len(cols) >= 2:
            date_col = cols[0]
            try:
                r = cur.execute(f'SELECT MIN("{date_col}"), MAX("{date_col}") FROM "{name}"').fetchone()
                print('   范围:', r)
            except Exception as e:
                print('   ERR', e)
con.close()
