import os
import glob
import re

def fix_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    if "import androidx.annotation.NonNull;" not in content:
        content = content.replace("import androidx.room.PrimaryKey;", "import androidx.room.PrimaryKey;\nimport androidx.annotation.NonNull;")
    
    # Simple replacement
    content = content.replace("    public String id;", "    @NonNull\n    public String id;")
    content = content.replace("    @NonNull\n    @NonNull", "    @NonNull") # cleanup if doubled
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

for filepath in glob.glob('app/src/main/java/com/example/licenta/model/*.java'):
    fix_file(filepath)

print("Fixed NonNull annotations in models.")
