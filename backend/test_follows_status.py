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

check_url = f"{SUPABASE_URL}/rest/v1/user_follows?select=id,status&limit=1"
res = requests.get(check_url, headers=headers)
print(res.text)
