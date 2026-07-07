import os
import requests
from dotenv import load_dotenv

load_dotenv(dotenv_path=".env")
SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_SERVICE_ROLE_KEY")

query = "Miha"
current_user_id = "8e875b0f-87f3-4e1a-bd08-1c506716f9cf"

headers = {
    "apikey": SUPABASE_KEY,
    "Authorization": f"Bearer {SUPABASE_KEY}"
}

url = f"{SUPABASE_URL}/rest/v1/user_profiles?or=(name.ilike.*{query}*,email.ilike.*{query}*)&limit=20"
res = requests.get(url, headers=headers)
print("Supabase status:", res.status_code)

users = res.json()
print("Raw users:", users)

filtered = []
for user in users:
    u_id = user.get("id")
    if u_id == current_user_id:
        user["is_me"] = True
        continue
    
    check_url = f"{SUPABASE_URL}/rest/v1/user_follows?follower_id=eq.{current_user_id}&following_id=eq.{u_id}&select=id"
    follow_res = requests.get(check_url, headers=headers).json()
    user["is_following"] = len(follow_res) > 0
    user["is_requested"] = False
    filtered.append(user)

print("Filtered users count:", len(filtered))
