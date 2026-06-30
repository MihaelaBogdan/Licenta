import requests
import os
from dotenv import load_dotenv

backend_dir = os.path.dirname(os.path.abspath(__file__))
load_dotenv(os.path.join(backend_dir, '.env'))

SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_KEY")

if not SUPABASE_URL or not SUPABASE_KEY:
    print("❌ Critical: SUPABASE_URL or SUPABASE_KEY missing in .env")
    exit(1)

headers = {
    "apikey": SUPABASE_KEY,
    "Authorization": f"Bearer {SUPABASE_KEY}",
}

# We will delete the posts we seeded based on their user_ids
seeded_user_ids = ["admin_uuid", "test_user_uuid", "mihaela_uuid"]

for uid in seeded_user_ids:
    url = f"{SUPABASE_URL}/rest/v1/feed_posts?user_id=eq.{uid}"
    print(f"Deleting posts for user_id: {uid}...")
    res = requests.delete(url, headers=headers)
    if res.status_code in [200, 204]:
        print(f"  ✅ Success")
    else:
        print(f"  ⚠️ Error {res.status_code}: {res.text}")

print("🎉 Finished deleting seeded posts!")
