import json

with open('backend/data/intents.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

total_patterns = 0
ro_char = set('ăâîșțĂÂÎȘȚ')
ro_keywords = ['vreau', 'unde', 'bun', 'sunt', 'imi', 'ce', 'ma', 'sa', 'cu', 'nu', 'de', 'la', 'si', 'te', 'o', 'ii', 'ei', 'al', 'in']
ro_count = 0
en_count = 0

for intent in data['intents']:
    for p in intent['patterns']:
        total_patterns += 1
        pl = p.lower()
        words = pl.split()
        if any(c in ro_char for c in pl) or any(w in words for w in ro_keywords):
            ro_count += 1
        else:
            en_count += 1

print(f'Total patterns: {total_patterns}')
print(f'Romanian patterns (est): {ro_count}')
print(f'English patterns (est): {en_count}')
print(f'Number of intents: {len(data["intents"])}')

# Show intents with few or no Romanian patterns
print('\nIntents with few Romanian patterns:')
for intent in data['intents']:
    ro_p = 0
    for p in intent['patterns']:
        pl = p.lower()
        if any(c in ro_char for c in pl) or any(w in pl.split() for w in ro_keywords):
            ro_p += 1
    total_p = len(intent['patterns'])
    ratio = ro_p / total_p if total_p > 0 else 0
    if ratio < 0.3:
        print(f"  {intent['tag']}: {ro_p}/{total_p} RO ({ratio:.0%})")
