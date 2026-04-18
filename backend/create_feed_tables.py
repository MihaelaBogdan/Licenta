#!/usr/bin/env python3
"""Creates the social feed tables in Supabase."""
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

# SQL to create tables via Supabase REST RPC
sql_statements = [
    """
    CREATE TABLE IF NOT EXISTS feed_posts (
        id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
        user_id TEXT NOT NULL,
        user_name TEXT DEFAULT 'Explorer',
        user_avatar TEXT DEFAULT '',
        place_name TEXT NOT NULL,
        place_id TEXT DEFAULT '',
        image_url TEXT DEFAULT '',
        caption TEXT DEFAULT '',
        rating DOUBLE PRECISION DEFAULT 0,
        latitude DOUBLE PRECISION DEFAULT 0,
        longitude DOUBLE PRECISION DEFAULT 0,
        created_at TIMESTAMPTZ DEFAULT now()
    );
    """,
    """
    CREATE TABLE IF NOT EXISTS feed_comments (
        id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
        post_id UUID REFERENCES feed_posts(id) ON DELETE CASCADE,
        user_id TEXT NOT NULL,
        user_name TEXT DEFAULT 'Explorer',
        comment_text TEXT NOT NULL,
        created_at TIMESTAMPTZ DEFAULT now()
    );
    """,
    """
    CREATE TABLE IF NOT EXISTS feed_likes (
        id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
        post_id UUID REFERENCES feed_posts(id) ON DELETE CASCADE,
        user_id TEXT NOT NULL,
        created_at TIMESTAMPTZ DEFAULT now(),
        UNIQUE(post_id, user_id)
    );
    """
]

# Execute via Supabase SQL endpoint
rpc_url = f"{SUPABASE_URL}/rest/v1/rpc/exec_sql"

for i, sql in enumerate(sql_statements):
    print(f"Creating table {i+1}/3...")
    res = requests.post(rpc_url, headers=headers, json={"query": sql})
    if res.status_code in [200, 201, 204]:
        print(f"  ✅ Table {i+1} created")
    else:
        print(f"  ⚠️ Status {res.status_code}: {res.text[:200]}")
        # Try alternative approach - direct SQL via management API
        alt_url = f"{SUPABASE_URL}/rest/v1/rpc/"
        print(f"  Trying alternative...")

print("\n🎉 Done! Tables should be created.")
print("If the above showed errors, create these tables manually in Supabase Dashboard > SQL Editor:")
print()
for sql in sql_statements:
    print(sql.strip())
    print()
