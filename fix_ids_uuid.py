import os
import glob
import re

def fix_model(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    if '@PrimaryKey' not in content or 'String id' not in content:
        return

    # Add id = UUID... to constructors
    # First, the default constructor
    content = re.sub(r'public\s+([A-Za-z0-9_]+)\s*\(\s*\)\s*\{', 
                     r'public \1() {\n        this.id = java.util.UUID.randomUUID().toString();', content)
    
    # Then any parameterized constructor
    # Find all matches of public ModelName(...) {
    model_name = os.path.basename(filepath).replace('.java', '')
    
    pattern = r'public\s+' + model_name + r'\s*\(([^)]+)\)\s*\{'
    
    def repl(match):
        params = match.group(1)
        # Avoid double injection if previously partially fixed
        if 'this.id =' in content: # heuristic
             pass
        return match.group(0) + '\n        this.id = java.util.UUID.randomUUID().toString();'

    content = re.sub(pattern, repl, content)

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

for filepath in glob.glob('app/src/main/java/com/example/licenta/model/*.java'):
    fix_model(filepath)
