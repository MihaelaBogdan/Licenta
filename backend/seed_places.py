import os
import json
import requests

# Set your Supabase URL and SERVICE ROLE KEY (not anon key) here
SUPABASE_URL = "https://zbixeueymyxcimueobig.supabase.co"
SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpiaXhldWV5bXl4Y2ltdWVvYmlnIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc3MzQ3MTYxNiwiZXhwIjoyMDg5MDQ3NjE2fQ.FGBtvpiWDx-34dvJzTPWw7lh1EIymNfPC8fVAbUOOQQ"

def seed_places():
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "return=minimal"
    }

    with open('data/places.json', 'r', encoding='utf-8') as f:
        places_data = json.load(f)

    # Format data for Supabase (make sure keys match the SQL table columns)
    formatted_places = []
    for p in places_data:
        formatted_places.append({
            "id": p["id"],
            "name": p["name"],
            "description": p["description"],
            "rating": p["rating"],
            "image_url": p["imageUrl"], # Mapping from JSON to SQL
            "latitude": p["latitude"],
            "longitude": p["longitude"],
            "type": p["type"],
            "address": p["address"]
        })

    print(f"Uploading {len(formatted_places)} places to Supabase...")
    
    url = f"{SUPABASE_URL}/rest/v1/places"
    response = requests.post(url, headers=headers, json=formatted_places)
    
    if response.status_code in [201, 204]:
        print("Successfully seeded places!")
    else:
        print(f"Failed to seed places: {response.status_code}")
        print(response.text)

if __name__ == "__main__":
    seed_places()
