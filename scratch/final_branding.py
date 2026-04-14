import os

replacements = {
    "com.example.licenta": "com.cityscape.app",
    "Licenta": "CityScape",
    "licenta": "cityscape"
}

root_dirs = ["app/src/main"]

for root_dir in root_dirs:
    for subdir, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith((".java", ".xml", ".gradle")):
                path = os.path.join(subdir, file)
                try:
                    with open(path, 'r', encoding='utf-8') as f:
                        content = f.read()
                    
                    new_content = content
                    for old, new in replacements.items():
                        new_content = new_content.replace(old, new)
                    
                    if new_content != content:
                        with open(path, 'w', encoding='utf-8') as f:
                            f.write(new_content)
                        print(f"Updated {path}")
                except Exception as e:
                    print(f"Error processing {path}: {e}")
