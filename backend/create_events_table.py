#!/usr/bin/env python3
import requests
import os
from dotenv import load_dotenv

load_dotenv()

SUPABASE_URL = os.getenv("SUPABASE_URL")
SERVICE_KEY = os.getenv("SUPABASE_SERVICE_ROLE_KEY")

headers = {
    "apikey": SERVICE_KEY,
    "Authorization": f"Bearer {SERVICE_KEY}",
    "Content-Type": "application/json"
}

sql = """
CREATE TABLE IF NOT EXISTS scraped_events (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    title TEXT NOT NULL,
    location TEXT,
    date_str TEXT,
    image_url TEXT,
    event_url TEXT UNIQUE,
    source TEXT,
    category TEXT DEFAULT 'General',
    tags TEXT[],
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE scraped_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public read scraped_events" ON public.scraped_events FOR SELECT USING (true);
"""

rpc_url = f"{SUPABASE_URL}/rest/v1/rpc/exec_sql"

print("Creating scraped_events table...")
res = requests.post(rpc_url, headers=headers, json={"query": sql})
if res.status_code in [200, 201, 204]:
    print("✅ Table created.")
else:
    print(f"⚠️ Status {res.status_code}: {res.text}")
