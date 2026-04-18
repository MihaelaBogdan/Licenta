import os
import requests
import json
import time
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

FOURSQUARE_API_KEY = os.getenv("FOURSQUARE_API_KEY")
SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_KEY")

# Categories to fetch (Foursquare IDs)
# 13065: Restaurant, 13032: Cafe, 16032: Park, 10027: Museum
CATEGORIES = {
    "13065": "Restaurante",
    "13032": "Cafenele",
    "16032": "Parcuri",
    "10027": "Muzee"
}

def fetch_places_from_foursquare(category_id):
    url = "https://api.foursquare.com/v3/places/search"
    params = {
        "categories": category_id,
        "ll": "44.4268,26.1025", # Bucharest center
        "radius": "10000",
        "limit": "20",
        "fields": "fsq_id,name,rating,location,geocodes,photos,description"
    }
    
    headers = {
        "Accept": "application/json",
        "Authorization": FOURSQUARE_API_KEY
    }
    
    try:
        response = requests.get(url, params=params, headers=headers)
        if response.status_code == 200:
            return response.json().get("results", [])
        else:
            print(f"Error fetching from Foursquare: {response.status_code} - {response.text}")
            return []
    except Exception as e:
        print(f"Exception fetching from Foursquare: {e}")
        return []

def get_photo_url(photos):
    if not photos:
        return "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=800&q=80"
    
    # Foursquare photo format: prefix + size + suffix
    p = photos[0]
    return f"{p.get('prefix')}800x600{p.get('suffix')}"

def upsert_to_supabase(places, category_name):
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "resolution=merge-duplicates"
    }
    
    formatted_places = []
    for p in places:
        location = p.get("location", {})
        geocodes = p.get("geocodes", {}).get("main", {})
        
        formatted_places.append({
            "name": p.get("name"),
            "description": p.get("description") or f"A popular {category_name.lower()} in Bucharest.",
            "rating": p.get("rating", 0.0) / 2.0 if p.get("rating") else 0.0, # Foursquare ratings are 0-10, we want 0-5
            "image_url": get_photo_url(p.get("photos", [])),
            "latitude": geocodes.get("latitude"),
            "longitude": geocodes.get("longitude"),
            "type": category_name,
            "address": location.get("formatted_address") or location.get("address") or "Bucharest, Romania"
        })

    url = f"{SUPABASE_URL}/rest/v1/places"
    response = requests.post(url, headers=headers, json=formatted_places)
    
    if response.status_code in [201, 204]:
        print(f"Successfully added {len(formatted_places)} {category_name} to Supabase!")
    else:
        print(f"Failed to add {category_name}: {response.status_code}")
        print(response.text)

def main():
    if not FOURSQUARE_API_KEY:
        print("❌ Error: FOURSQUARE_API_KEY not found in .env")
        return

    print("🚀 Starting Foursquare Places Data Fetcher...")
    
    for cat_id, cat_name in CATEGORIES.items():
        print(f"🔍 Fetching {cat_name} (ID: {cat_id})...")
        results = fetch_places_from_foursquare(cat_id)
        if results:
            upsert_to_supabase(results, cat_name)
        time.sleep(1) # Be nice to API

    print("✅ All categories processed!")

if __name__ == "__main__":
    main()
