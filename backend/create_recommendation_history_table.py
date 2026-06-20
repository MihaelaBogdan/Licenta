"""
Database migration script to create recommendation_history table in Supabase.
This table tracks all recommendations made to users for analytics and learning.

Run this script once to set up the table:
    python3 create_recommendation_history_table.py
"""

import os
import requests
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_KEY")

def create_recommendation_history_table():
    """Create the recommendation_history table with proper schema."""

    sql = """
    CREATE TABLE IF NOT EXISTS recommendation_history (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
        place_name VARCHAR(255) NOT NULL,
        place_id VARCHAR(255),
        place_type VARCHAR(100),
        confidence FLOAT CHECK (confidence >= 0 AND confidence <= 100),
        reasoning TEXT,
        factors JSONB,
        recommended_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
        status VARCHAR(50) DEFAULT 'pending' CHECK (status IN ('pending', 'visited', 'accepted', 'rejected')),
        visited_at TIMESTAMP WITH TIME ZONE,
        user_feedback TEXT,
        updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
        created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
        CONSTRAINT valid_status CHECK (status IN ('pending', 'visited', 'accepted', 'rejected'))
    );

    CREATE INDEX idx_recommendation_user ON recommendation_history(user_id);
    CREATE INDEX idx_recommendation_status ON recommendation_history(status);
    CREATE INDEX idx_recommendation_date ON recommendation_history(recommended_at DESC);
    CREATE INDEX idx_recommendation_user_date ON recommendation_history(user_id, recommended_at DESC);
    """

    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json"
    }

    try:
        # Use Supabase SQL API to execute raw SQL
        url = f"{SUPABASE_URL}/rest/v1/rpc/exec_sql"

        # Note: This approach might not work with standard REST API
        # Instead, use the Supabase SQL Editor or psql directly

        print("⚠️ Note: Use Supabase SQL Editor to run this SQL directly:")
        print("\n" + sql)
        print("\n✅ Or run via psql if you have direct database access")

        return True

    except Exception as e:
        print(f"❌ Error: {e}")
        return False


def create_via_supabase_console():
    """
    Instructions for creating the table via Supabase Console.
    """

    print("""
    ╔════════════════════════════════════════════════════════════════╗
    ║     SETUP RECOMMENDATION HISTORY TABLE                          ║
    ║     Follow these steps in Supabase Console:                    ║
    ╚════════════════════════════════════════════════════════════════╝

    1. Go to: https://app.supabase.com
    2. Select your project
    3. Navigate to: SQL Editor
    4. Click "New Query"
    5. Copy and paste this SQL:

    ───────────────────────────────────────────────────────────────

    CREATE TABLE IF NOT EXISTS recommendation_history (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
        place_name VARCHAR(255) NOT NULL,
        place_id VARCHAR(255),
        place_type VARCHAR(100),
        confidence FLOAT CHECK (confidence >= 0 AND confidence <= 100),
        reasoning TEXT,
        factors JSONB,
        recommended_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
        status VARCHAR(50) DEFAULT 'pending' CHECK (status IN ('pending', 'visited', 'accepted', 'rejected')),
        visited_at TIMESTAMP WITH TIME ZONE,
        user_feedback TEXT,
        updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
        created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
        CONSTRAINT valid_status CHECK (status IN ('pending', 'visited', 'accepted', 'rejected'))
    );

    CREATE INDEX idx_recommendation_user ON recommendation_history(user_id);
    CREATE INDEX idx_recommendation_status ON recommendation_history(status);
    CREATE INDEX idx_recommendation_date ON recommendation_history(recommended_at DESC);
    CREATE INDEX idx_recommendation_user_date ON recommendation_history(user_id, recommended_at DESC);

    ───────────────────────────────────────────────────────────────

    6. Click "Run" button
    7. Verify table appears in Table Editor
    8. Done! ✅

    ════════════════════════════════════════════════════════════════
    """)


if __name__ == "__main__":
    create_via_supabase_console()
    create_recommendation_history_table()
