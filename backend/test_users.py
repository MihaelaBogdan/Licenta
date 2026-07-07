import requests
import os
from dotenv import load_dotenv

load_dotenv(dotenv_path=".env")
SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_SERVICE_ROLE_KEY")

headers = {
    "apikey": SUPABASE_KEY,
    "Authorization": f"Bearer {SUPABASE_KEY}"
}

query = "Miha"
url = f"{SUPABASE_URL}/rest/v1/user_profiles?or=(name.ilike.*{query}*,email.ilike.*{query}*)&limit=20"
res = requests.get(url, headers=headers)
print(f"Status: {res.status_code}")
print(res.text)
