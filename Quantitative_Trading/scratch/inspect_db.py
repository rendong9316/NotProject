import sqlite3, sys
sys.stdout.reconfigure(encoding='utf-8')
conn = sqlite3.connect('data_v5/csi300_2020_present.sqlite')
tables = [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")]
print('表:', tables)
for t in ['official_adjustments', 'universe_membership']:
    if t in tables:
        cols = [d[1] for d in conn.execute(f'PRAGMA table_info({t})')]
        print(t, '列:', cols)
        rows = conn.execute(f'SELECT * FROM {t} LIMIT 6').fetchall()
        for r in rows:
            print(' ', r)
