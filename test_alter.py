import requests
import os
from dotenv import load_dotenv

load_dotenv(dotenv_path="backend/.env")
SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_SERVICE_ROLE_KEY")

headers = {
    "apikey": SUPABASE_KEY,
    "Authorization": f"Bearer {SUPABASE_KEY}",
    "Content-Type": "application/json"
}
sql = "ALTER TABLE user_follows ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'pending';"
res = requests.post(f"{SUPABASE_URL}/rest/v1/rpc/exec_sql", headers=headers, json={"query": sql})
print(res.status_code, res.text)
