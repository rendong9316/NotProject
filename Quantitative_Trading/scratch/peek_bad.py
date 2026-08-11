import json, sys
sys.stdout.reconfigure(encoding='utf-8')
data = json.load(open('scratch/csindex_announcements_2010s.json', encoding='utf-8'))
print(data['4084']['text'])
