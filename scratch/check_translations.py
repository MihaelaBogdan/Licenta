import xml.etree.ElementTree as ET
import re

tree = ET.parse('../app/src/main/res/values/strings.xml')
root = tree.getroot()

romanian_chars = re.compile(r'[ăâîșțĂÂÎȘȚ]')
romanian_words = ['de', 'la', 'pe', 'cu', 'in', 'o', 'si', 'nu', 'mai', 'este', 'sunt', 'esti', 'esti', 'vremea', 'orasul', 'locatii', 'evenimente', 'activitate', 'vizitate', 'populare']

print("--- STRINGS TO REVIEW IN ENGLISH strings.xml ---")
count = 0
for string in root.findall('string'):
    name = string.attrib.get('name', '')
    text = string.text or ''
    
    # Check if name starts with auto_
    if name.startswith('auto_'):
        # Check if it has Romanian characters or common Romanian words
        has_ro_char = bool(romanian_chars.search(text))
        
        words = [w.lower() for w in re.split(r'\W+', text) if w]
        has_ro_words = sum(1 for w in words if w in romanian_words) >= 1
        
        # Suspect bad translations based on our analysis
        bad_words = ['almost', 'against', 'programmer', 'insigne', 'insignile', 'pray', 'conversion', 'flying', 'cheers']
        has_bad_words = any(bw in text.lower() for bw in bad_words)
        
        if has_ro_char or has_ro_words or has_bad_words:
            print(f'{name}: "{text}"')
            count += 1

print(f"\nTotal suspicious strings: {count}")
