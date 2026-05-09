#!/usr/bin/env python3
"""Creates the content_reports table in Supabase."""
import requests
import os
from dotenv import load_dotenv

# Load environment variables from the backend directory
load_dotenv()

SUPABASE_URL = os.getenv("SUPABASE_URL")
SERVICE_KEY = os.getenv("SUPABASE_SERVICE_ROLE_KEY") or os.getenv("SUPABASE_KEY")

headers = {
    "apikey": SERVICE_KEY,
    "Authorization": f"Bearer {SERVICE_KEY}",
    "Content-Type": "application/json",
    "Prefer": "return=minimal"
}

sql = """
CREATE TABLE IF NOT EXISTS content_reports (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    reporter_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    post_id UUID REFERENCES feed_posts(id) ON DELETE CASCADE,
    comment_id UUID REFERENCES feed_comments(id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    status TEXT DEFAULT 'pending', -- pending, reviewed, dismissed, removed
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Enable RLS
ALTER TABLE content_reports ENABLE ROW LEVEL SECURITY;

-- Policies
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'Users can create reports') THEN
        CREATE POLICY "Users can create reports" ON public.content_reports FOR INSERT WITH CHECK (auth.uid() = reporter_id);
        CREATE POLICY "Users can view their own reports" ON public.content_reports FOR SELECT USING (auth.uid() = reporter_id);
        CREATE POLICY "Admins can view all reports" ON public.content_reports FOR SELECT USING (true); -- Simplified for now
    END IF;
END
$$;
"""

rpc_url = f"{SUPABASE_URL}/rest/v1/rpc/exec_sql"

print("Creating table content_reports...")
res = requests.post(rpc_url, headers=headers, json={"query": sql})
if res.status_code in [200, 201, 204]:
    print("✅ Table content_reports created successfully")
else:
    print(f"⚠️ Status {res.status_code}: {res.text}")
    print("\nPlease create this table manually in Supabase SQL Editor if needed:")
    print(sql)
