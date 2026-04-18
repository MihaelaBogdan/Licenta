#!/usr/bin/env python3
"""Creates the user_follows table in Supabase."""
import requests
import os
from dotenv import load_dotenv

load_dotenv()

SUPABASE_URL = os.getenv("SUPABASE_URL")
SERVICE_KEY = os.getenv("SUPABASE_SERVICE_ROLE_KEY")

headers = {
    "apikey": SERVICE_KEY,
    "Authorization": f"Bearer {SERVICE_KEY}",
    "Content-Type": "application/json",
    "Prefer": "return=minimal"
}

sql = """
CREATE TABLE IF NOT EXISTS user_follows (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    follower_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    following_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(follower_id, following_id)
);

ALTER TABLE user_follows ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Anyone can view follow counts') THEN
        CREATE POLICY "Anyone can view follow counts" ON public.user_follows FOR SELECT USING (true);
        CREATE POLICY "Users manage own follows" ON public.user_follows USING (auth.uid() = follower_id);
    END IF;
END
$$;
"""

rpc_url = f"{SUPABASE_URL}/rest/v1/rpc/exec_sql"

print("Creating table user_follows...")
res = requests.post(rpc_url, headers=headers, json={"query": sql})
if res.status_code in [200, 201, 204]:
    print("✅ Table created successfully")
else:
    print(f"⚠️ Status {res.status_code}: {res.text}")
    print("\nPlease create this table manually in Supabase SQL Editor if needed:")
    print(sql)
