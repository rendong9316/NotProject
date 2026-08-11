import json, sys, hashlib
sys.stdout.reconfigure(encoding='utf-8')
data = json.load(open('scratch/csindex_announcements_2010s.json', encoding='utf-8'))
for aid, d in data.items():
    h = hashlib.md5(d['text'].encode('utf-8')).hexdigest()[:8]
    head = d['text'][:80].replace('\n', ' ')
    print(aid, d['publish_date'], len(d['text']), h, '|', head)
