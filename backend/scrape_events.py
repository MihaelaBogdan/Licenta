import requests
from bs4 import BeautifulSoup
import os
import time
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_KEY")

# Cuvinte cheie pe care vrem să le evităm (pentru filtrare)
EXCLUDED_KEYWORDS = ["curs", "workshop", "atelier", "seminar", "webinar", "clasa", "invata"]

def clean_text(text):
    if not text: return ""
    return " ".join(text.split())

def scrape_iabilet():
    print("🔍 Scraping iabilet.ro for Bucharest events...")
    url = "https://www.iabilet.ro/bilete-bucuresti/"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
    }
    
    try:
        response = requests.get(url, headers=headers, timeout=10)
        if response.status_code != 200:
            print(f"❌ Error accessing iabilet: {response.status_code}")
            return []
            
        soup = BeautifulSoup(response.text, 'html.parser')
        raw_events = soup.select('.event-list-item') # Selector specific pentru iabilet
        
        scraped_data = []
        for item in raw_events:
            try:
                title = clean_text(item.select_one('.event-title').text)
                
                # --- FILTRARE ---
                # 1. Verificăm cuvinte cheie nedorite
                if any(kw in title.lower() for kw in EXCLUDED_KEYWORDS):
                    print(f"⏩ Skipping (Filtered): {title}")
                    continue
                
                link_tag = item.select_one('a')
                if not link_tag: continue
                link = "https://www.iabilet.ro" + link_tag['href']
                
                venue = clean_text(item.select_one('.event-venue').text)
                date_str = clean_text(item.select_one('.event-date').text)
                
                # Imagine (iabilet foloseste adesea lazy loading sau imagini in stil)
                img_tag = item.select_one('img')
                img_url = img_tag['src'] if img_tag and 'src' in img_tag.attrs else "https://images.unsplash.com/photo-1501281668745-f7f57925c3b4?w=800"
                if img_url.startswith('//'): img_url = "https:" + img_url

                scraped_data.append({
                    "title": title,
                    "location": venue,
                    "date_str": date_str,
                    "image_url": img_url,
                    "event_url": link,
                    "source": "iabilet"
                })
            except Exception as e:
                print(f"⚠️ Error parsing item: {e}")
                continue
                
        return scraped_data
    except Exception as e:
        print(f"❌ Scraping failure: {e}")
        return []

def upsert_to_supabase(events):
    if not events:
        print("ℹ️ No events to save.")
        return

    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "resolution=merge-duplicates"
    }
    
    url = f"{SUPABASE_URL}/rest/v1/scraped_events"
    
    # Supabase upsert requires the unique key (event_url)
    response = requests.post(url, headers=headers, json=events)
    
    if response.status_code in [201, 204]:
        print(f"✅ Successfully saved {len(events)} events to Supabase!")
    else:
        print(f"❌ Failed to save: {response.status_code} - {response.text}")

def main():
    events = scrape_iabilet()
    if events:
        print(f"✨ Found {len(events)} valid events after filtering.")
        upsert_to_supabase(events)
    else:
        print("🤷 No events found.")

if __name__ == "__main__":
    main()
