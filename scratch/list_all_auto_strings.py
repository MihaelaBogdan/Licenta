import xml.etree.ElementTree as ET

tree = ET.parse('../app/src/main/res/values/strings.xml')
root = tree.getroot()

auto_strings = []
for string in root.findall('string'):
    name = string.attrib.get('name', '')
    text = string.text or ''
    if name.startswith('auto_'):
        auto_strings.append((name, text))

print(f"Total auto strings: {len(auto_strings)}")
for name, text in sorted(auto_strings):
    print(f'{name}: "{text}"')
