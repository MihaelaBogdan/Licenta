import os
import glob
import re

def fix_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Find the Context variable implicitly in Android
    # We use 'v.getContext()' or 'requireContext()' or 'this' based on the file type
    ctx = "this"
    if "Fragment" in filepath:
        ctx = "requireContext()"
    if "SessionManager" in filepath:
        ctx = "context"
    if "Adapter" in filepath:
        ctx = "context"

    original = content

    # Add hooks
    content = re.sub(r'(db\.activityDao\(\)\.insert\(([^)]+)\);)', 
                     r'\1\n                            com.example.licenta.data.SupabaseSyncManager.getInstance(' + ctx + ').pushActivityToCloud(\\2);', content)
    
    content = re.sub(r'(db\.activityDao\(\)\.update\(([^)]+)\);)', 
                     r'\1\n                            com.example.licenta.data.SupabaseSyncManager.getInstance(' + ctx + ').updateActivityInCloud(\\2);', content)

    content = re.sub(r'(db\.groupDao\(\)\.insertGroup\(([^)]+)\);)', 
                     r'\1\n                            com.example.licenta.data.SupabaseSyncManager.getInstance(' + ctx + ').pushGroupToCloud(\\2);', content)

    content = re.sub(r'(db\.groupDao\(\)\.insertMember\(([^)]+)\);)', 
                     r'\1\n                            com.example.licenta.data.SupabaseSyncManager.getInstance(' + ctx + ').pushMemberToCloud(\\2);', content)

    content = re.sub(r'(db\.scheduleDao\(\)\.insert\(([^)]+)\);)', 
                     r'\1\n                            com.example.licenta.data.SupabaseSyncManager.getInstance(' + ctx + ').pushScheduleToCloud(\\2);', content)

    content = re.sub(r'(db\.invitationDao\(\)\.insert\(([^)]+)\);)', 
                     r'\1\n                            com.example.licenta.data.SupabaseSyncManager.getInstance(' + ctx + ').pushInvitationToCloud(\\2);', content)

    content = re.sub(r'(db\.invitationDao\(\)\.update\(([^)]+)\);)', 
                     r'\1\n                            com.example.licenta.data.SupabaseSyncManager.getInstance(' + ctx + ').updateInvitationInCloud(\\2);', content)

    if original != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)

for filepath in glob.glob('app/src/main/java/com/example/licenta/**/*.java', recursive=True):
    fix_file(filepath)

print("Sync hooks injected successfully.")
