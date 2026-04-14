import os
import re

old_pkg = "com.example.licenta"
new_pkg = "com.cityscape.app"

root_dir = "app/src/main"

for subdir, dirs, files in os.walk(root_dir):
    for file in files:
        if file.endswith(".java") or file.endswith(".xml"):
            path = os.path.join(subdir, file)
            with open(path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            new_content = content.replace(old_pkg, new_pkg)
            
            if new_content != content:
                with open(path, 'w', encoding='utf-8') as f:
                    f.write(new_content)
                print(f"Updated {path}")
