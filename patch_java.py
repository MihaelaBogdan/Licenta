import os
import glob
import re

def fix_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # SessionManager.java fixes
    content = content.replace('userId == -1', 'userId == null')
    
    # Login, Register, Welcome, CalendarFragment fixes:
    # long userId = db.userDao().insert(newUser);
    # newUser.id = (int) userId;
    # changes to -> db.userDao().insert(newUser); (newUser doesn't update id if room returns void)
    
    # We will use regex to remove "long someId = " and ".id = (int) someId;"
    content = re.sub(r'long\s+\w+Id\s*=\s*(db\.[^;]+);', r'\1;', content)
    content = re.sub(r'\w+\.id\s*=\s*\(int\)\s*\w+Id;', '', content)
    
    # Fix 'a.id == group.activityId'
    content = content.replace('a.id == group.activityId', 'a.id != null && a.id.equals(group.activityId)')

    # Add missing String coversions in fields: 
    # ActivityGroup.activityId, ActivityGroup.creatorId, etc.
    content = re.sub(r'\bint\s+activityId\b', 'String activityId', content)
    content = re.sub(r'\bint\s+placeId\b', 'String placeId', content)
    content = re.sub(r'public\s+ActivityGroup\(int\s+activityId', 'public ActivityGroup(String activityId', content)

    # In DAOs
    content = re.sub(r'getGroupForActivity\(int\s+activityId\)', 'getGroupForActivity(String activityId)', content)

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

# Refactor models
for filepath in glob.glob('app/src/main/java/com/example/licenta/**/*.java', recursive=True):
    fix_file(filepath)

print("Java files patched successfully.")
