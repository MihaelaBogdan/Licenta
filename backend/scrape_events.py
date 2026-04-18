import requests
from bs4 import BeautifulSoup
import os
import time
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_KEY")

CATEGORIES = {
    "Muzică": ["concert", "live", "tribut", "dj", "party", "festival", "jazz", "rock", "pop"],
    "Teatru": ["teatru", "piesa", "spectacol", "comedie", "drama"],
    "Sport": ["meci", "fotbal", "tenis", "maraton", "alergare", "baschet"],
    "Artă": ["expozitie", "muzeu", "galerie", "vernisaj", "pictura"],
    "Food": ["degustare", "vin", "food", "festivalul", "bucatarie"]
}

# Cuvinte cheie pe care vrem să le evităm (pentru filtrare)
EXCLUDED_KEYWORDS = ["curs", "workshop", "atelier", "seminar", "webinar", "clasa", "invata"]

def clean_text(text):
    if not text: return ""
    return " ".join(text.split())

def detect_category(title):
    title_lower = title.lower()
    for cat, keywords in CATEGORIES.items():
        if any(kw in title_lower for kw in keywords):
            return cat
    return "Social"

import json

def scrape_iabilet():
    print("🔍 Scraping iabilet.ro using JSON-LD...")
    url = "https://www.iabilet.ro/bilete-bucuresti/"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
    }
    
    try:
        response = requests.get(url, headers=headers, timeout=15)
        if response.status_code != 200:
            return []
            
        soup = BeautifulSoup(response.text, 'html.parser')
        scripts = soup.find_all('script', type='application/ld+json')
        print(f"📊 Found {len(scripts)} JSON-LD blocks.")
        
        scraped_data = []
        for script in scripts:
            try:
                content = script.get_text().strip()
                if not content: continue
                print(f"📄 Content preview: {content[:50]}...")
                data = json.loads(content)
                items = data if isinstance(data, list) else [data]
                
                for item in items:
                    # Debug print
                    typ = item.get("@type", "???")
                    print(f"DEBUG: Item type is {typ}")
                    if typ != "Event":
                        continue
                        
                    title = item.get("name", "")
                    print(f"📌 Found event: {title}")
                    if not title or any(kw in title.lower() for kw in EXCLUDED_KEYWORDS):
                        continue
                        
                    event_url = item.get("url", "")
                    if not event_url.startswith("http"):
                        event_url = "https://www.iabilet.ro" + event_url
                        
                    location_obj = item.get("location", {})
                    location_name = location_obj.get("name", "București") if isinstance(location_obj, dict) else "București"
                    
                    date_str = item.get("startDate", "")
                    image_url = item.get("image", "")
                    
                    scraped_data.append({
                        "title": title,
                        "location": location_name,
                        "date_str": date_str,
                        "image_url": image_url,
                        "event_url": event_url,
                        "source": "iabilet",
                        "category": detect_category(title)
                    })
            except Exception:
                continue
                
        # Remove duplicates based on URL
        unique_events = {e['event_url']: e for e in scraped_data}.values()
        return list(unique_events)
    except Exception as e:
        print(f"❌ Error during JSON-LD scrape: {e}")
        return []

def scrape_eventbook():
    # Placeholder for another source to show scalability
    print("🔍 Scraping eventbook.ro (Mocked for now)...")
    return []

def upsert_to_supabase(events):
    if not events: return
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "resolution=merge-duplicates"
    }
    url = f"{SUPABASE_URL}/rest/v1/scraped_events"
    requests.post(url, headers=headers, json=events)
    print(f"✅ Sync complete: {len(events)} events processed.")

def main():
    all_events = []
    all_events.extend(scrape_iabilet())
    all_events.extend(scrape_eventbook())
    
    if all_events:
        upsert_to_supabase(all_events)
    else:
        print("🤷 No new events.")

if __name__ == "__main__":
    main()
