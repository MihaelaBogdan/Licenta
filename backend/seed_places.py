import os
import json
import requests
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Set your Supabase URL and SERVICE ROLE KEY (Loaded from .env)
SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_SERVICE_ROLE_KEY")

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
