import os
import requests
import json
import time
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Configurations - Hardcoded for environment stability
# Configurations - Loaded from environment variables
# GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY")
GOOGLE_API_KEY = None  # Comentat temporar pentru a evita costurile
SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_KEY")

# Categories to fetch
CATEGORIES = {
    "restaurant": "Restaurante",
    "cafe": "Cafenele",
    "park": "Parcuri",
    "museum": "Muzee"
}

def fetch_places_from_google(query, place_type):
    url = "https://maps.googleapis.com/maps/api/place/textsearch/json"
    params = {
        "query": query,
        "type": place_type,
        "key": GOOGLE_API_KEY,
        "location": "44.4268,26.1025", # Bucharest center
        "radius": "10000"
    }
    
    response = requests.get(url, params=params)
    if response.status_code == 200:
        return response.json().get("results", [])
    else:
        print(f"Error fetching from Google: {response.status_code}")
        return []

def get_photo_url(photo_reference):
    if not photo_reference:
        return "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=800&q=80"
    return f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference={photo_reference}&key={GOOGLE_API_KEY}"

def upsert_to_supabase(places, category_name):
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "resolution=merge-duplicates"
    }
    
    formatted_places = []
    for p in places:
        photo_ref = p.get("photos", [{}])[0].get("photo_reference")
        
        formatted_places.append({
            "name": p.get("name"),
            "description": f"A popular {category_name.lower()} in Bucharest.",
            "rating": p.get("rating", 0.0),
            "image_url": get_photo_url(photo_ref),
            "latitude": p.get("geometry", {}).get("location", {}).get("lat"),
            "longitude": p.get("geometry", {}).get("location", {}).get("lng"),
            "type": category_name,
            "address": p.get("formatted_address", "Bucharest, Romania")
        })

    url = f"{SUPABASE_URL}/rest/v1/places"
    response = requests.post(url, headers=headers, json=formatted_places)
    
    if response.status_code in [201, 204]:
        print(f"Successfully added {len(formatted_places)} {category_name} to Supabase!")
    else:
        print(f"Failed to add {category_name}: {response.status_code}")
        print(response.text)

def main():
    print("🚀 Starting Google Places Data Fetcher...")
    
    for g_type, cat_name in CATEGORIES.items():
        print(f"🔍 Fetching {cat_name}...")
        query = f"top {g_type}s in Bucharest"
        results = fetch_places_from_google(query, g_type)
        if results:
            upsert_to_supabase(results[:10], cat_name) # Take top 10 per category
        time.sleep(1) # Be nice to API

    print("✅ All categories processed!")

if __name__ == "__main__":
    main()
