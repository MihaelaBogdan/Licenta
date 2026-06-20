import requests
import os
from dotenv import load_dotenv

backend_dir = os.path.dirname(os.path.abspath(__file__))
load_dotenv(os.path.join(backend_dir, '.env'))

SUPABASE_URL = os.getenv("SUPABASE_URL")
SERVICE_KEY = os.getenv("SUPABASE_SERVICE_ROLE_KEY")

admin_headers = {
    "apikey": SERVICE_KEY,
    "Authorization": f"Bearer {SERVICE_KEY}",
    "Content-Type": "application/json"
}

profile_headers = {
    "apikey": SERVICE_KEY,
    "Authorization": f"Bearer {SERVICE_KEY}",
    "Content-Type": "application/json",
    "Prefer": "return=representation"
}

users = [
    {"name": "Alexandru Ionescu", "email": "alex.ionescu@test.com",     "password": "Test1234!", "level": 5,  "current_xp": 320, "total_xp": 1320, "places_visited": 12, "badges_earned": 3},
    {"name": "Maria Popescu",     "email": "maria.popescu@test.com",    "password": "Test1234!", "level": 3,  "current_xp": 150, "total_xp": 650,  "places_visited": 7,  "badges_earned": 2},
    {"name": "Andrei Constantin", "email": "andrei.constantin@test.com","password": "Test1234!", "level": 7,  "current_xp": 480, "total_xp": 2480, "places_visited": 25, "badges_earned": 6},
    {"name": "Elena Dumitrescu",  "email": "elena.dumitrescu@test.com", "password": "Test1234!", "level": 2,  "current_xp": 80,  "total_xp": 280,  "places_visited": 3,  "badges_earned": 1},
    {"name": "Mihai Stanescu",    "email": "mihai.stanescu@test.com",   "password": "Test1234!", "level": 10, "current_xp": 900, "total_xp": 4900, "places_visited": 50, "badges_earned": 10},
]

for u in users:
    # 1. Create auth user
    res = requests.post(
        f"{SUPABASE_URL}/auth/v1/admin/users",
        headers=admin_headers,
        json={"email": u["email"], "password": u["password"], "email_confirm": True}
    )
    if res.status_code not in (200, 201):
        # User already exists — find ID from profile table
        profile_lookup = requests.get(
            f"{SUPABASE_URL}/rest/v1/user_profiles?email=eq.{u['email']}&select=id",
            headers=profile_headers
        )
        rows = profile_lookup.json()
        if not rows:
            print(f"❌ Nu am găsit profileul pentru {u['email']}")
            continue
        user_id = rows[0]["id"]
        print(f"ℹ️  User existent: {u['email']} (id: {user_id})")
    else:
        user_id = res.json()["id"]
        print(f"✅ Auth creat: {u['email']} (id: {user_id})")

    # 2. Upsert profile
    profile_res = requests.patch(
        f"{SUPABASE_URL}/rest/v1/user_profiles?id=eq.{user_id}",
        headers=profile_headers,
        json={
            "name": u["name"],
            "email": u["email"],
            "level": u["level"],
            "current_xp": u["current_xp"],
            "total_xp": u["total_xp"],
            "places_visited": u["places_visited"],
            "badges_earned": u["badges_earned"]
        }
    )
    if profile_res.status_code in (200, 201, 204):
        print(f"   ✅ Profil actualizat pentru {u['name']}")
    else:
        print(f"   ❌ Profil eșuat pentru {u['name']}: {profile_res.text}")

print("\nGata!")
