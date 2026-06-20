#!/usr/bin/env python3
"""Test scraping real Romanian events from iabilet.ro"""
import requests, re, json
from datetime import datetime

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
    "Accept-Language": "ro-RO,ro;q=0.9",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
}

CITY_URLS = {
    "București": "https://www.iabilet.ro/bilete-bucuresti/",
    "Cluj-Napoca": "https://www.iabilet.ro/bilete-cluj-napoca/",
    "Timișoara": "https://www.iabilet.ro/bilete-timisoara/",
    "Iași": "https://www.iabilet.ro/bilete-iasi/",
    "Brașov": "https://www.iabilet.ro/bilete-brasov/",
}

CAT_MAP = {
    "concert": "Muzică", "muzica": "Muzică", "jazz": "Muzică", "rock": "Muzică",
    "teatru": "Teatru", "spectacol": "Teatru", "opera": "Teatru",
    "festival": "Festival", "film": "Film", "expo": "Expoziție", "art": "Expoziție",
    "sport": "Sport", "standup": "Recreativ", "comedy": "Recreativ",
    "curs": "Educație", "master": "Educație",
}

def guess_category(title):
    t = title.lower()
    for kw, cat in CAT_MAP.items():
        if kw in t:
            return cat
    return "Recreativ"

def get_image_for_category(cat):
    images = {
        "Muzică": "https://images.unsplash.com/photo-1514525253344-f81f3f746522?w=800",
        "Teatru": "https://images.unsplash.com/photo-1507679799987-c73779587ccf?w=800",
        "Festival": "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=800",
        "Film": "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800",
        "Expoziție": "https://images.unsplash.com/photo-1579783902614-a3fb3927b6a5?w=800",
        "Sport": "https://images.unsplash.com/photo-1476480862126-209bfaa8edc8?w=800",
        "Recreativ": "https://images.unsplash.com/photo-1517457373958-b7bdd4587205?w=800",
        "Educație": "https://images.unsplash.com/photo-1524178232363-1fb2b075b655?w=800",
    }
    return images.get(cat, images["Recreativ"])

def format_date(date_str):
    """Format ISO date to human-readable Romanian."""
    try:
        dt = datetime.fromisoformat(date_str[:10])
        months = ["Ian", "Feb", "Mar", "Apr", "Mai", "Iun",
                  "Iul", "Aug", "Sep", "Oct", "Nov", "Dec"]
        days = ["Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă", "Duminică"]
        return f"{days[dt.weekday()]}, {dt.day} {months[dt.month-1]} {dt.year}"
    except:
        return date_str

def scrape_iabilet(city_name):
    url = CITY_URLS.get(city_name, CITY_URLS["București"])
    try:
        r = requests.get(url, headers=HEADERS, timeout=8)
        content = r.text
    except Exception as e:
        print(f"Failed to fetch {url}: {e}")
        return []

    # Extract microdata: name+date+location+url patterns
    names = re.findall(r'"name":\s*"([^"]{5,80})"', content)
    dates = re.findall(r'"startDate":\s*"([^"]+)"', content)
    locations = re.findall(r'"addressLocality":\s*"([^"]+)"', content)
    streets = re.findall(r'"streetAddress":\s*"([^"]+)"', content)
    event_urls = re.findall(r'"url":\s*"(https://www\.iabilet\.ro/bilete/[^"]+)"', content)

    # Filter out venue names (they appear as alternating name/venue pairs)
    # Events have dates; venues don't. Pair them.
    events = []
    today = datetime.now().date()

    seen = set()
    for i, (name, date_str) in enumerate(zip(names, dates)):
        if name in seen:
            continue
        # Skip if date is in the past
        try:
            event_date = datetime.fromisoformat(date_str[:10]).date()
            if event_date < today:
                continue
        except:
            pass

        # Skip venue-only names (they're usually short location names)
        if len(name) < 8 or name in ["Sala Palatului", "Ateneul Român"]:
            continue

        seen.add(name)
        cat = guess_category(name)
        location = streets[i//2] if i//2 < len(streets) else (locations[i//2] if i//2 < len(locations) else city_name)
        event_url = event_urls[i//2] if i//2 < len(event_urls) else url

        events.append({
            "title": name,
            "category": cat,
            "date_str": format_date(date_str),
            "location": location or city_name,
            "image_url": get_image_for_category(cat),
            "event_url": event_url,
            "source": "iabilet",
        })

    return events

if __name__ == "__main__":
    for city in ["București", "Cluj-Napoca", "Timișoara"]:
        print(f"\n=== {city} ===")
        events = scrape_iabilet(city)
        print(f"Found {len(events)} real events")
        for e in events[:5]:
            print(f"  ✅ {e['title']} | {e['date_str']} | {e['category']}")
