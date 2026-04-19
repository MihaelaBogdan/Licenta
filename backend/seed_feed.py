import requests
import os
from dotenv import load_dotenv
import time

# Load env from backend directory
backend_dir = os.path.dirname(os.path.abspath(__file__))
load_dotenv(os.path.join(backend_dir, '.env'))

SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_KEY")

if not SUPABASE_URL or not SUPABASE_KEY:
    print("❌ Critical: SUPABASE_URL or SUPABASE_KEY missing in .env")
    exit(1)

headers = {
    "apikey": SUPABASE_KEY,
    "Authorization": f"Bearer {SUPABASE_KEY}",
    "Content-Type": "application/json",
    "Prefer": "return=minimal"
}

# Test users (using IDs that match the ones I seeded in Room if possible, 
# but for Supabase TEXT user_id is used, so we can use readable names)
test_posts = [
    {
        "user_id": "admin_uuid",
        "user_name": "Administrator",
        "place_name": "Caru' cu Bere",
        "image_url": "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=800",
        "caption": "Cea mai bună mâncare tradițională din București! 🍺🥘 #Bucuresti #Tradiție",
        "rating": 5.0,
        "latitude": 44.4323,
        "longitude": 26.0984
    },
    {
        "user_id": "test_user_uuid",
        "user_name": "Test User",
        "place_name": "Cărturești Carusel",
        "image_url": "https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?w=800",
        "caption": "O după-amiază liniștită între cărți. Arhitectura este absolut superbă! 📚✨",
        "rating": 4.8,
        "latitude": 44.4319,
        "longitude": 26.1023
    },
    {
        "user_id": "mihaela_uuid",
        "user_name": "Mihaela Bogdan",
        "place_name": "Parcul Herăstrău",
        "image_url": "https://images.unsplash.com/photo-1519331379826-f10be5486c6f?w=800",
        "caption": "Plimbare de primăvară pe malul lacului. 🌸🚣‍♂️",
        "rating": 4.5,
        "latitude": 44.4716,
        "longitude": 26.0824
    },
    {
        "user_id": "admin_uuid",
        "user_name": "Administrator",
        "place_name": "Palatul Parlamentului",
        "image_url": "https://images.unsplash.com/photo-1588668214407-6ea9a6d8c272?w=800",
        "caption": "Impresionantă clădire! Merită vizitată măcar o dată în viață. 🏛️🇷🇴",
        "rating": 4.0,
        "latitude": 44.4275,
        "longitude": 26.0877
    }
]

url = f"{SUPABASE_URL}/rest/v1/feed_posts"

print(f"🚀 Seeding {len(test_posts)} posts to Supabase...")

for post in test_posts:
    print(f"Adding post for {post['place_name']}...")
    res = requests.post(url, headers=headers, json=post)
    if res.status_code in [200, 201, 204]:
        print(f"  ✅ Success")
    else:
        print(f"  ⚠️ Error {res.status_code}: {res.text}")
    time.sleep(0.5)

print("\n🎉 Seeding complete!")
