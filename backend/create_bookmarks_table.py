#!/usr/bin/env python3
"""Creates the feed_bookmarks table in Supabase."""
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
CREATE TABLE IF NOT EXISTS public.feed_bookmarks (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    post_id UUID REFERENCES public.feed_posts(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.user_profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(post_id, user_id)
);

ALTER TABLE public.feed_bookmarks ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Users manage own bookmarks') THEN
        CREATE POLICY "Users manage own bookmarks" ON public.feed_bookmarks USING (auth.uid() = user_id);
    END IF;
END
$$;
"""

rpc_url = f"{SUPABASE_URL}/rest/v1/rpc/exec_sql"

print("Creating table feed_bookmarks...")
res = requests.post(rpc_url, headers=headers, json={"query": sql})
if res.status_code in [200, 201, 204]:
    print("✅ Table feed_bookmarks created successfully")
else:
    print(f"⚠️ Status {res.status_code}: {res.text}")
    print("\nPlease create this table manually in Supabase SQL Editor if needed:")
    print(sql)
