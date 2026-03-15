import os
import glob
import re

def modify_java_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Apply multiple regex replacements
    content = re.sub(r'\bint\s+id\b', 'String id', content)
    content = re.sub(r'\bint\s+userId\b', 'String userId', content)
    content = re.sub(r'\bint\s+groupId\b', 'String groupId', content)
    content = re.sub(r'\bint\s+creatorId\b', 'String creatorId', content)
    content = re.sub(r'\bint\s+fromUserId\b', 'String fromUserId', content)
    content = re.sub(r'\bint\s+toUserId\b', 'String toUserId', content)
    
    # Specific ID returns / passing
    content = re.sub(r'\bgetUserId\(\)\s*==\s*-1', 'getUserId() == null', content)
    content = re.sub(r'public\s+int\s+getUserId', 'public String getUserId', content)
    content = re.sub(r'prefs\.getInt\(\s*KEY_USER_ID,\s*-1\s*\)', 'prefs.getString(KEY_USER_ID, null)', content)
    content = re.sub(r'editor\.putInt\(\s*KEY_USER_ID', 'editor.putString(KEY_USER_ID', content)

    # Convert Dao queries returning long for inserts to String or void
    content = re.sub(r'long\s+insert\(', 'void insert(', content)
    content = re.sub(r'long\s+insertMember\(', 'void insertMember(', content)
    content = re.sub(r'long\s+insertGroup\(', 'void insertGroup(', content)

    # Some room DB updates
    content = re.sub(r'autoGenerate\s*=\s*true', 'autoGenerate = false', content)

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

# Apply to all model classes
for file in glob.glob('app/src/main/java/com/example/licenta/model/*.java'):
    modify_java_file(file)

# Apply to all data handling classes
for file in glob.glob('app/src/main/java/com/example/licenta/data/*.java'):
    modify_java_file(file)

print("Applied int -> String conversions successfully in Models and DAOs!")
