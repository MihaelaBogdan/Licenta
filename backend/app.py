import sys
import os

# Force Python to find its local modules (chatbot.py)
current_dir = os.path.dirname(os.path.abspath(__file__))
if current_dir not in sys.path:
    sys.path.append(current_dir)

# Add user's site-packages for Torch/Flask (optional, usually handled by pip)
# user_site = '/Users/mihaela/Library/Python/3.9/lib/python/site-packages'
# if user_site not in sys.path:
#     sys.path.append(user_site)

from flask import Flask, render_template, request, jsonify
from flask_cors import CORS
from chatbot import get_response, get_response_with_details
import json
import os
import random
import requests
from dotenv import load_dotenv

# Load environment variables
env_path = os.path.join(current_dir, '.env')
load_dotenv(dotenv_path=env_path)

app = Flask(__name__)
CORS(app)

# ==================== WEATHER INTELLIGENCE ====================

def fetch_current_weather(lat, lng):
    """Fetches real-time weather to adapt recommendations (Mocked if no key)."""
    import time
    # Simulation: 20% chance of rain based on timestamp minutes
    is_raining = (int(time.time() / 60) % 5 == 0) 
    temp = 22 # Spring average
    
    status = "Plouă" if is_raining else "Senin"
    icon = "☁️" if is_raining else "☀️"
    
    return {
        "status": status,
        "is_bad": is_raining,
        "temp": f"{temp}°C",
        "icon": icon,
        "advice": "Sugestii indoor activate" if is_raining else "Vreme perfectă pentru explorare"
    }

def get_weather_adjusted_types(is_bad_weather):
    """Returns preferred place types based on current weather."""
    if is_bad_weather:
        return ["museum", "art_gallery", "cafe", "restaurant", "movie_theater", "library", "shopping_mall"]
    else:
        return ["park", "tourist_attraction", "zoo", "amusement_park", "stadium", "campground"]

@app.route("/")
def index_get():
    return render_template("base.html")

@app.route("/join")
def join_get():
    return render_template("join.html")

@app.post("/predict")
def predict():
    """Simple endpoint - legacy support."""
    data = request.get_json()
    text = data.get("message")
    language = data.get("language", "ro")
    user_id = data.get("user_id")
    lat = data.get("lat")
    lng = data.get("lng")
    city_name = data.get("city_name")
    
    from chatbot import get_response_with_rag
    result = get_response_with_rag(text, user_id, lat, lng, language, city_name)
    return jsonify({"answer": result["answer"]})

@app.post("/predict/detailed")
def predict_detailed():
    """Full RAG endpoint with intent, confidence, and context."""
    data = request.get_json()
    text = data.get("message")
    language = data.get("language", "ro")
    user_id = data.get("user_id")
    lat = data.get("lat")
    lng = data.get("lng")
    city_name = data.get("city_name")
    
    from chatbot import get_response_with_rag
    result = get_response_with_rag(text, user_id, lat, lng, language, city_name)
    return jsonify(result)

@app.get("/quests/daily")
def get_daily_quest():
    """Generates a personalized daily quest based on weather and interests."""
    user_id = request.args.get("user_id")
    lat = request.args.get("lat")
    lng = request.args.get("lng")
    interests = request.args.get("interests", "")
    language = request.args.get("language", "ro")
    
    # 1. Get Weather
    weather = fetch_current_weather(lat, lng) if lat and lng else {"status": "Senin", "is_bad": False, "temp": "20°C"}
    
    # 2. Generate with Gemini
    try:
        import google.generativeai as genai
        genai.configure(api_key=os.getenv("GOOGLE_API_KEY"))
        model = genai.GenerativeModel("gemini-flash-latest")
        
        prompt = (
            f"Ești CityScape AI Master. Generează o MISIUNE ZILNICĂ (Daily Quest) pentru un explorator urban. "
            f"DATE CONTEXTUALE: Interese: {interests}. Vreme: {weather['status']}, {weather['temp']}. "
            f"Limba: {'română' if language == 'ro' else 'engleză'}. "
            "CERINȚE: "
            "1. Titlu captivant (ex: 'Misiunea Gurmandului'). "
            "2. Obiectiv specific (ex: 'Vizitează 2 cafenele și postează o poză'). "
            "3. Recompensă (ex: '500 XP și insigna Coffee Lover'). "
            "4. Justificare (ex: 'Pentru că plouă, am ales activități indoor'). "
            "FORMAT JSON: {'title': '...', 'objective': '...', 'reward': '...', 'reason': '...'}"
        )
        
        response = model.generate_content(prompt)
        # Extract JSON from response
        import re
        json_match = re.search(r"\{.*\}", response.text, re.DOTALL)
        if json_match:
            quest_data = json.loads(json_match.group())
            return jsonify(quest_data)
    except Exception as e:
        print(f"Quest Error: {e}")
        
    # Fallback quest
    return jsonify({
        "title": "Explorator Urban",
        "objective": "Vizitează o locație nouă astăzi",
        "reward": "250 XP",
        "reason": "O zi perfectă pentru a descoperi orașul!"
    })

# ==================== REPORTING & ANALYTICS ====================

@app.get("/reports/user/<u_id>")
def get_user_activity_report(u_id):
    """Generates a summary of activity for a specific user."""
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    
    try:
        # 1. Profile data
        profile_res = requests.get(f"{SUPABASE_URL}/rest/v1/user_profiles?id=eq.{u_id}&select=*", headers=headers).json()
        if not profile_res:
            return jsonify({"error": "Profilul utilizatorului nu a fost găsit."}), 404
        profile = profile_res[0]
        
        # 2. Visit stats
        visits = requests.get(f"{SUPABASE_URL}/rest/v1/visited_places?user_id=eq.{u_id}&select=*", headers=headers).json()
        
        # Calculate favorite category
        categories = {}
        for v in visits:
            t = v.get("place_type", "General")
            categories[t] = categories.get(t, 0) + 1
        
        fav_cat = max(categories, key=categories.get) if categories else "N/A"
        
        # 3. Social stats
        posts = requests.get(f"{SUPABASE_URL}/rest/v1/feed_posts?user_id=eq.{u_id}&select=id", headers=headers).json()
        
        report = {
            "user_name": profile.get("name"),
            "level": profile.get("level", 1),
            "total_xp": profile.get("total_xp", 0),
            "places_visited_count": len(visits),
            "favorite_category": fav_cat,
            "posts_created": len(posts),
            "badges_count": profile.get("badges_earned", 0),
            "join_date": profile.get("created_at", "").split("T")[0],
            "recent_visits": [v["place_name"] for v in visits[:5]]
        }
        
        return jsonify(report)
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.get("/admin/stats")
def get_admin_stats():
    """Returns global system statistics for the admin dashboard."""
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    
    try:
        # Get counts from various tables
        users_count = len(requests.get(f"{SUPABASE_URL}/rest/v1/user_profiles?select=id", headers=headers).json())
        posts_count = len(requests.get(f"{SUPABASE_URL}/rest/v1/feed_posts?select=id", headers=headers).json())
        visits_count = len(requests.get(f"{SUPABASE_URL}/rest/v1/visited_places?select=id", headers=headers).json())
        reports_count = len(requests.get(f"{SUPABASE_URL}/rest/v1/content_reports?select=id", headers=headers).json())
        
        # Get pending reports
        pending_reports = requests.get(f"{SUPABASE_URL}/rest/v1/content_reports?status=eq.pending&select=*", headers=headers).json()
        
        stats = {
            "total_users": users_count,
            "total_posts": posts_count,
            "total_visits": visits_count,
            "total_reports": reports_count,
            "pending_reports_count": len(pending_reports),
            "system_health": "Optimal",
            "last_updated": "now"
        }
        
        return jsonify(stats)
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.get("/map/hype")
def get_hype_map():
    """Aggregates recent activity to show where the 'vibe' is, filtered by current location."""
    lat = request.args.get("lat")
    lng = request.args.get("lng")
    radius = 0.5 # approx 50km
    
    try:
        # Fetch visits
        url = f"{SUPABASE_URL}/rest/v1/visited_places?select=latitude,longitude,place_name&order=visited_at.desc&limit=100"
        headers = {"apikey": os.getenv("SUPABASE_KEY"), "Authorization": f"Bearer {os.getenv('SUPABASE_KEY')}"}
        res_v = requests.get(url, headers=headers).json()
        
        # Fetch posts
        url_p = f"{SUPABASE_URL}/rest/v1/feed_posts?select=latitude,longitude,place_name&order=created_at.desc&limit=100"
        res_p = requests.get(url_p, headers=headers).json()
        
        points = []
        def is_nearby(p_lat, p_lng):
            if not lat or not lng: return True # Show all if no center
            try:
                return abs(float(p_lat) - float(lat)) < radius and abs(float(p_lng) - float(lng)) < radius
            except: return False

        if isinstance(res_v, list):
            for p in res_v:
                if p.get("latitude") and p.get("longitude") and is_nearby(p["latitude"], p["longitude"]):
                    points.append({"lat": p["latitude"], "lng": p["longitude"], "weight": 1.0, "source": "visit"})
        
        if isinstance(res_p, list):
            for p in res_p:
                if p.get("latitude") and p.get("longitude") and is_nearby(p["latitude"], p["longitude"]):
                    points.append({"lat": p["latitude"], "lng": p["longitude"], "weight": 1.5, "source": "post"})
                    
        return jsonify(points[:50]) # Return top 50 relevant
    except Exception as e:
        print(f"Hype Map Error: {e}")
        return jsonify([])

import requests
import os

# API Keys - Loaded from environment variables (.env)
GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY")
MAPS_API_KEY = os.getenv("MAPS_API_KEY")
FOURSQUARE_API_KEY = os.getenv("FOURSQUARE_API_KEY")
SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_KEY")
OPENWEATHER_API_KEY = os.getenv("OPENWEATHER_API_KEY")
PREDICTHQ_API_KEY = os.getenv("PREDICTHQ_API_KEY")
TICKETMASTER_API_KEY = os.getenv("TICKETMASTER_API_KEY")

# Foursquare Category Mapping
FSQ_CATEGORIES = {
    "restaurant": "13065",
    "cafe": "13032",
    "park": "16032",
    "museum": "10027",
    "art_gallery": "10004",
    "bakery": "13002",
    "bar": "13003",
    "night_club": "10032",
    "movie_theater": "10024",
    "spa": "11044",
    "tourist_attraction": "16000",
    "zoo": "10051",
    "aquarium": "10001",
    "stadium": "10051", # Best match
    "shopping_mall": "17114",
    "landmark": "16026",
    "historic": "16026",
    "culture": "10027",
    "shopping": "17114",
    "cinema": "10024",
    "tenis": "18065",
    "bowling": "10006",
    "sport": "18000",
    "fitness": "11038",
    "karting": "10018",
    "biliard": "10006"
}

# Categories to ALWAYS exclude from UI results
BLACKLISTED_TYPES = {
    "lodging", "health", "dentist", "locality", "political", 
    "real_estate_agency", "lawyer", "accounting", "finance",
    "insurance_agency", "doctor", "hospital", "clinic",
    "neighborhood", "sublocality", "administrative_area_level_1",
    "administrative_area_level_2"
}

def google_text_search(query, lat=None, lng=None, radius=50000):
    """Searches Google Places Text Search API for high-relevance matches with strict location biasing."""
    url = "https://maps.googleapis.com/maps/api/place/textsearch/json"
    params = {
        "query": query,
        "key": MAPS_API_KEY,
        "language": "ro"
    }
    if lat and lng:
        params["location"] = f"{lat},{lng}"
        params["radius"] = str(radius)
        # Force strict location biasing to avoid Bucharest bias
        params["locationbias"] = f"circle:{radius}@{lat},{lng}"
    try:
        res = requests.get(url, params=params, timeout=10).json()
        results = res.get("results", [])
        
        # Double check results to filter out anything too far (sanity check)
        if lat and lng:
            filtered = []
            for r in results:
                loc = r.get("geometry", {}).get("location", {})
                r_lat, r_lng = loc.get("lat"), loc.get("lng")
                if r_lat and r_lng:
                    dist = abs(float(r_lat) - float(lat)) + abs(float(r_lng) - float(lng))
                    if dist < 0.6: # approx 60km
                        filtered.append(r)
            return filtered
        return results
    except Exception as e:
        print(f"Google Text Search Error: {e}")
        return []

def google_nearby_search(lat, lng, place_type, radius=5000, keyword=None):
    """Searches Google Places API for nearby places."""
    url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
    params = {
        "location": f"{lat},{lng}",
        "radius": str(radius),
        "key": MAPS_API_KEY,
        "language": "ro"
    }
    if place_type:
        params["type"] = place_type

    if keyword:
        params["keyword"] = keyword
    try:
        res = requests.get(url, params=params, timeout=10).json()
        return res.get("results", [])
    except Exception as e:
        print(f"Google Places Error: {e}")
        return []

@app.get("/places/<place_id>/details")
def get_place_details(place_id):
    """Fetches high-detail info for a specific place, including reviews."""
    url = "https://maps.googleapis.com/maps/api/place/details/json"
    params = {
        "place_id": place_id,
        "key": MAPS_API_KEY,
        "language": "ro",
        "fields": "name,rating,formatted_address,photos,reviews,editorial_summary,opening_hours,geometry"
    }
    
    try:
        res = requests.get(url, params=params, timeout=10).json()
        result = res.get("result", {})
        
        # Extract reviews
        reviews = []
        for r in result.get("reviews", []):
            reviews.append({
                "author": r.get("author_name"),
                "text": r.get("text"),
                "rating": r.get("rating"),
                "time": r.get("relative_time_description")
            })
            
        # Format photo
        img_url = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800"
        if result.get("photos"):
            ref = result["photos"][0]["photo_reference"]
            img_url = f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference={ref}&key={MAPS_API_KEY}"

        # AI Review Summary (Premium Feature)
        ai_summary = "O locație interesantă care merită explorată pentru experiența sa unică."
        if reviews:
            try:
                import google.generativeai as genai
                # Check if already configured, if not, use environment
                try:
                    model = genai.GenerativeModel('gemini-flash-latest')
                except:
                    genai.configure(api_key=GOOGLE_API_KEY)
                    model = genai.GenerativeModel('gemini-flash-latest')
                
                reviews_text = "\n".join([f"- {r['text']}" for r in reviews[:5] if r['text']])
                if reviews_text:
                    prompt = (
                        f"Ești un critic urban inteligent. Rezumă aceste recenzii pentru '{result.get('name')}' "
                        "într-o singură frază ultra-scurtă, onestă și captivantă (max 25 cuvinte). "
                        "Folosește un ton modern, de tip 'TL;DR'. Menționează un punct forte și un punct slab dacă există. "
                        f"Recenzii:\n{reviews_text}"
                    )
                    ai_res = model.generate_content(prompt)
                    ai_summary = ai_res.text.strip().replace('"', '')
            except Exception as ae:
                print(f"⚠️ AI Summary Error: {ae}")

        return jsonify({
            "name": result.get("name"),
            "rating": result.get("rating"),
            "address": result.get("formatted_address"),
            "description": result.get("editorial_summary", {}).get("overview", "O locație excelentă de descoperit."),
            "imageUrl": img_url,
            "reviews": reviews,
            "ai_summary": ai_summary,
            "isOpen": result.get("opening_hours", {}).get("open_now")
        })
    except Exception as e:
        print(f"⚠️ Details Error: {e}")
        return jsonify({"error": "Failed to fetch details"}), 500

@app.get("/search")
def universal_search():
    """Universal search for places using Google Text Search."""
    query = request.args.get("query")
    lat = request.args.get("lat")
    lng = request.args.get("lng")
    
    if not query:
        return jsonify([])
        
    results = google_text_search(query, lat, lng)
    formatted = []
    for r in results:
        f = format_google_place(r, lat, lng)
        if f:
            formatted.append(f)
            
    return jsonify(formatted)

def google_autocomplete(query, lat=None, lng=None):
    """Fetches autocomplete suggestions from Google Places API."""
    url = "https://maps.googleapis.com/maps/api/place/autocomplete/json"
    params = {
        "input": query,
        "key": MAPS_API_KEY,
        "language": "ro",
        "types": "establishment"
    }
    if lat and lng:
        params["location"] = f"{lat},{lng}"
        params["radius"] = "20000"
    
    try:
        res = requests.get(url, params=params, timeout=5).json()
        return res.get("predictions", [])
    except Exception as e:
        print(f"Google Autocomplete Error: {e}")
        return []

def format_google_place(place, user_lat=None, user_lng=None):
    """Formats a Google Places result into our app's Place model."""
    # Photo URL
    img_url = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800"
    if place.get("photos"):
        ref = place["photos"][0]["photo_reference"]
        img_url = f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference={ref}&key={MAPS_API_KEY}"

    geo = place.get("geometry", {}).get("location", {})
    
    # Distance calculation
    dist_str = ""
    if user_lat and user_lng and geo.get("lat") and geo.get("lng"):
        try:
            from math import radians, cos, sin, asin, sqrt
            lon1, lat1, lon2, lat2 = map(radians, [float(user_lng), float(user_lat), float(geo["lng"]), float(geo["lat"])])
            d = 2 * 6371 * asin(sqrt(sin((lat2-lat1)/2)**2 + cos(lat1)*cos(lat2)*sin((lon2-lon1)/2)**2))
            dist_str = f" ({int(d*1000)}m)" if d < 1 else f" ({d:.1f}km)"
        except: pass

    # Blacklist check
    types = place.get("types", [])
    for t in types:
        if t in BLACKLISTED_TYPES:
            return None

    return {
        "id": place.get("place_id", ""),
        "name": place.get("name", "Locație"),
        "address": place.get("formatted_address", place.get("vicinity", "")),
        "rating": place.get("rating", 4.2),
        "imageUrl": img_url,
        "latitude": geo.get("lat"),
        "longitude": geo.get("lng"),
        "type": (types[0] if types else "").replace("_", " ").capitalize(),
        "reviewCount": place.get("user_ratings_total", 0),
        "priceLevel": place.get("price_level"),
        "_raw_types": types,   # full Google type list for scorer
        "relevance_score": 0
    }

# ==================== PERSONALIZATION ENGINE v2 ====================

import math
from datetime import datetime, timezone

# ---------- Interest synonym map ----------
# Maps raw interest tags → related Google place types
INTEREST_TYPE_MAP = {
    "restaurant": ["restaurant", "food", "meal_takeaway", "meal_delivery"],
    "cafe": ["cafe", "bakery", "coffee"],
    "park": ["park", "natural_feature", "campground"],
    "museum": ["museum", "art_gallery", "library"],
    "art_gallery": ["art_gallery", "museum"],
    "bar": ["bar", "night_club"],
    "night_club": ["night_club", "bar"],
    "shopping": ["shopping_mall", "store", "clothing_store"],
    "sport": ["gym", "stadium", "sports_complex", "bowling_alley"],
    "cinema": ["movie_theater"],
    "spa": ["spa", "beauty_salon"],
    "music": ["night_club", "bar", "concert_hall"],
    "history": ["museum", "church", "tourist_attraction"],
    "nature": ["park", "natural_feature", "campground", "zoo"],
    "food": ["restaurant", "cafe", "bakery", "meal_takeaway"],
    "culture": ["museum", "art_gallery", "library", "tourist_attraction"],
    "adventure": ["tourist_attraction", "amusement_park", "campground"],
    "wellness": ["spa", "gym", "park"],
}

# ---------- Time-of-day preference buckets ----------
# hour ranges → preferred place types
TIME_PREFERENCES = {
    "morning":   {"range": (6, 11),  "types": ["cafe", "bakery", "park"],
                  "weight": 18},
    "lunch":     {"range": (11, 14), "types": ["restaurant", "cafe", "food"],
                  "weight": 18},
    "afternoon": {"range": (14, 18), "types": ["tourist_attraction", "museum", "park", "art_gallery"],
                  "weight": 14},
    "evening":   {"range": (18, 22), "types": ["restaurant", "bar", "night_club", "movie_theater"],
                  "weight": 18},
    "night":     {"range": (22, 6),  "types": ["bar", "night_club"],
                  "weight": 20},
}

# ---------- Seasonal preference map ----------
SEASON_TYPE_BOOST = {
    "spring": ["park", "tourist_attraction", "cafe", "art_gallery"],  # Mar-May
    "summer": ["park", "campground", "zoo", "amusement_park", "beach"],  # Jun-Aug
    "autumn": ["museum", "cafe", "restaurant", "art_gallery"],  # Sep-Nov
    "winter": ["museum", "shopping_mall", "cafe", "movie_theater", "spa"],  # Dec-Feb
}

# ---------- Indoor / Outdoor classification ----------
INDOOR_TYPES = {"museum", "art_gallery", "cafe", "restaurant", "movie_theater",
                "shopping_mall", "spa", "library", "gym", "bar", "night_club", "bowling_alley"}
OUTDOOR_TYPES = {"park", "tourist_attraction", "campground", "zoo", "amusement_park",
                 "stadium", "natural_feature", "beach"}

def _get_season(month: int) -> str:
    if month in (3, 4, 5):   return "spring"
    if month in (6, 7, 8):   return "summer"
    if month in (9, 10, 11): return "autumn"
    return "winter"

def _get_time_bucket(hour: int) -> str:
    for bucket, data in TIME_PREFERENCES.items():
        lo, hi = data["range"]
        if lo < hi:
            if lo <= hour < hi:
                return bucket
        else:  # wraps midnight
            if hour >= lo or hour < hi:
                return bucket
    return "afternoon"

def _weather_prefers_indoor(lat, lng) -> bool:
    """Quick real weather check via Open-Meteo (no API key needed)."""
    try:
        url = (f"https://api.open-meteo.com/v1/forecast"
               f"?latitude={lat}&longitude={lng}&current=weather_code&timezone=auto")
        r = requests.get(url, timeout=4).json()
        code = r.get("current", {}).get("weather_code", 0)
        return code >= 51  # drizzle, rain, snow, thunderstorm
    except:
        return False

def _haversine_km(lat1, lon1, lat2, lon2) -> float:
    """Returns distance in km between two coordinates."""
    try:
        R = 6371.0
        dlat = math.radians(float(lat2) - float(lat1))
        dlon = math.radians(float(lon2) - float(lon1))
        a = (math.sin(dlat / 2) ** 2 +
             math.cos(math.radians(float(lat1))) *
             math.cos(math.radians(float(lat2))) *
             math.sin(dlon / 2) ** 2)
        return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    except:
        return 999.0

def get_user_context(user_id):
    """Fetches rich user context: interests, visit history with recency, price preference."""
    if not user_id:
        return {
            "interests": [], "history_weights": {}, "recency_weights": {},
            "visited_place_ids": set(), "avg_price_level": 2,
            "name": "Explorator", "level": 1,
        }

    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    profile_context = {
        "name": "Explorator", "interests": [], "level": 1,
        "xp": 0, "visited_count": 0, "badges_count": 0,
    }
    interests = []

    # 1. Profile
    try:
        res = requests.get(
            f"{SUPABASE_URL}/rest/v1/users?id=eq.{user_id}&select=*", headers=headers)
        if res.status_code == 200 and res.json():
            u = res.json()[0]
            interests_str = u.get("interests", "")
            interests = [i.strip().lower() for i in interests_str.split(",") if i.strip()]
            profile_context.update({
                "name": u.get("name", "Explorator"),
                "level": u.get("level", 1),
                "xp": u.get("total_xp", 0),
                "visited_count": u.get("places_visited", 0),
                "badges_count": u.get("badges_earned", 0),
                "interests": interests,
            })
    except Exception as e:
        print(f"⚠️ get_user_context profile error: {e}")

    # 2. Full visit history with timestamps for recency decay
    history_weights = {}   # type → raw count
    recency_weights = {}   # type → recency-decayed score
    visited_place_ids = set()
    price_levels = []

    try:
        res = requests.get(
            f"{SUPABASE_URL}/rest/v1/visited_places"
            f"?user_id=eq.{user_id}&select=place_type,google_place_id,place_id,visited_at,price_level"
            f"&order=visited_at.desc&limit=200",
            headers=headers)
        if res.status_code == 200:
            now_ts = datetime.now(timezone.utc).timestamp()
            for i, visit in enumerate(res.json()):
                ptype = visit.get("place_type", "").lower().strip()
                pid_g = visit.get("google_place_id", "")
                pid   = visit.get("place_id", "")
                visited_at_str = visit.get("visited_at", "")
                pl = visit.get("price_level")

                if pid_g: visited_place_ids.add(pid_g)
                if pid:   visited_place_ids.add(pid)
                if pl is not None:
                    try: price_levels.append(int(pl))
                    except: pass

                if not ptype:
                    continue

                history_weights[ptype] = history_weights.get(ptype, 0) + 1

                # Recency decay: half-life ≈ 30 days
                decay = 1.0
                if visited_at_str:
                    try:
                        # Handle both with/without timezone suffix
                        ts_str = visited_at_str.replace("Z", "+00:00")
                        visit_ts = datetime.fromisoformat(ts_str).timestamp()
                        age_days = (now_ts - visit_ts) / 86400.0
                        decay = math.exp(-0.693 * age_days / 30.0)  # half-life 30d
                    except:
                        decay = max(0.1, 1.0 - i * 0.05)

                recency_weights[ptype] = recency_weights.get(ptype, 0.0) + decay
    except Exception as e:
        print(f"⚠️ get_user_context history error: {e}")

    avg_price_level = round(sum(price_levels) / len(price_levels)) if price_levels else 2

    return {
        **profile_context,
        "history_weights": history_weights,
        "recency_weights": recency_weights,
        "visited_place_ids": visited_place_ids,
        "avg_price_level": avg_price_level,
    }


def score_item(item, context, user_lat=None, user_lng=None,
               already_scored_types=None, use_weather=False):
    """
    Multi-signal recommendation score (0–100).

    Signals
    -------
    A. Explicit interest match          (0–25 pts)
    B. Implicit habit match w/ recency  (0–20 pts)
    C. Popularity  (rating × log(reviews))  (0–15 pts)
    D. Time-of-day context              (0–18 pts)
    E. Seasonal relevance               (0–8  pts)
    F. Weather (indoor/outdoor fit)     (0–8  pts)
    G. Novelty (not already visited)    (0–10 pts)
    H. Distance (closer = better)       (0–10 pts)
    I. Diversity bonus (type variety)   (0–8  pts)
    J. Day-of-week (weekend boost)      (0–5  pts)
    K. Price-level fit                  (0–5  pts)
    ─────────────────────────────────────────────
    Total raw max                      ≈ 132 pts  → normalized to 100
    """
    if already_scored_types is None:
        already_scored_types = {}

    score = 0.0
    title     = (item.get("name") or item.get("title") or "").lower()
    raw_type  = (item.get("type") or item.get("category") or "").lower().replace(" ", "_")
    g_types   = item.get("_raw_types", [raw_type])  # list of Google place types
    place_id  = item.get("id", "")

    interests       = context.get("interests", [])
    history_weights = context.get("history_weights", {})
    recency_weights = context.get("recency_weights", {})
    visited_ids     = context.get("visited_place_ids", set())
    avg_price       = context.get("avg_price_level", 2)

    now    = datetime.now()
    hour   = now.hour
    month  = now.month
    weekday = now.weekday()   # 0=Mon … 6=Sun
    season  = _get_season(month)
    bucket  = _get_time_bucket(hour)
    season_boost_types = SEASON_TYPE_BOOST.get(season, [])

    # ── A. Explicit interest match ──────────────────────────────────
    interest_score = 0.0
    for interest in interests:
        # Direct name/type match
        if interest in title:
            interest_score += 12
        # Type synonym expansion
        synonyms = INTEREST_TYPE_MAP.get(interest, [interest])
        if any(s in raw_type or raw_type in s for s in synonyms):
            interest_score += 13
        # Also check all raw Google types
        for gtype in g_types:
            if any(s == gtype for s in synonyms):
                interest_score += 8
                break
    score += min(interest_score, 25)

    # ── B. Implicit habit (recency-decayed) ─────────────────────────
    habit_score = 0.0
    for gtype in g_types + [raw_type]:
        if gtype in recency_weights:
            # Logarithmic: frequent visits matter but don't dominate
            habit_score += min(recency_weights[gtype] * 4.0, 20)
            break
    score += min(habit_score, 20)

    # ── C. Popularity (rating × log1p(review_count)) ────────────────
    rating      = float(item.get("rating") or 4.0)
    review_cnt  = int(item.get("reviewCount") or item.get("review_count") or 0)
    pop_score   = (rating / 5.0) * math.log1p(review_cnt) / math.log1p(5000) * 15
    score += min(pop_score, 15)

    # ── D. Time-of-day context ──────────────────────────────────────
    time_data  = TIME_PREFERENCES.get(bucket, {})
    time_types = time_data.get("types", [])
    time_w     = time_data.get("weight", 14)
    if any(tt in raw_type or raw_type in tt for tt in time_types):
        score += time_w
    elif any(any(tt in gtype for tt in time_types) for gtype in g_types):
        score += time_w * 0.6

    # ── E. Seasonal relevance ───────────────────────────────────────
    if raw_type in season_boost_types or any(st in raw_type for st in season_boost_types):
        score += 8
    elif any(st in gtype for gtype in g_types for st in season_boost_types):
        score += 4

    # ── F. Weather (indoor / outdoor fit) ──────────────────────────
    # use_weather flag avoids re-calling Open-Meteo per item (caller checks once)
    if use_weather:
        is_indoor_place = raw_type in INDOOR_TYPES or any(gt in INDOOR_TYPES for gt in g_types)
        is_outdoor_place = raw_type in OUTDOOR_TYPES or any(gt in OUTDOOR_TYPES for gt in g_types)
        weather_bad = item.get("_weather_bad", False)
        if weather_bad and is_indoor_place:
            score += 8
        elif not weather_bad and is_outdoor_place:
            score += 8
        elif weather_bad and is_outdoor_place:
            score -= 6   # penalize outdoor when raining

    # ── G. Novelty — prefer unvisited places ────────────────────────
    if place_id and place_id not in visited_ids:
        score += 10
    elif place_id in visited_ids:
        score -= 8   # already been there → deprioritize

    # ── H. Distance scoring ─────────────────────────────────────────
    if user_lat and user_lng and item.get("latitude") and item.get("longitude"):
        dist_km = _haversine_km(user_lat, user_lng, item["latitude"], item["longitude"])
        if dist_km <= 0.5:
            score += 10
        elif dist_km <= 1.5:
            score += 7
        elif dist_km <= 3.0:
            score += 4
        elif dist_km <= 8.0:
            score += 1
        else:
            score -= 2  # far away mild penalty

    # ── I. Diversity boost — penalize over-represented types ─────────
    type_count = already_scored_types.get(raw_type, 0)
    if type_count == 0:
        score += 8
    elif type_count == 1:
        score += 3
    elif type_count >= 3:
        score -= 5  # too many of same type already in list

    # ── J. Day-of-week ──────────────────────────────────────────────
    is_weekend = weekday >= 5
    if is_weekend and raw_type in {"park", "tourist_attraction", "museum", "restaurant", "cafe"}:
        score += 5
    elif not is_weekend and raw_type in {"cafe", "restaurant", "museum"}:
        score += 2

    # ── K. Price-level fit ──────────────────────────────────────────
    item_price = item.get("priceLevel") or item.get("price_level")
    if item_price is not None:
        try:
            diff = abs(int(item_price) - avg_price)
            score += max(0, 5 - diff * 2)
        except:
            pass

    # Normalize to 0-100 (raw max ≈ 132)
    normalized = max(0.0, min(score / 132.0 * 100.0, 100.0))
    return round(normalized, 2)


def rank_places(formatted_places, context, user_lat=None, user_lng=None,
                check_weather=True):
    """
    Applies score_item to every place with shared state for diversity tracking.
    Fetches weather once (not per item) when check_weather=True.
    Returns places sorted by relevance_score descending.
    """
    weather_bad = False
    if check_weather and user_lat and user_lng:
        weather_bad = _weather_prefers_indoor(user_lat, user_lng)

    type_counts = {}
    # First pass: score everything
    for p in formatted_places:
        raw_type = (p.get("type") or "").lower().replace(" ", "_")
        p["_weather_bad"] = weather_bad
        p["relevance_score"] = score_item(
            p, context,
            user_lat=user_lat, user_lng=user_lng,
            already_scored_types=type_counts,
            use_weather=check_weather,
        )
        type_counts[raw_type] = type_counts.get(raw_type, 0) + 1

    formatted_places.sort(key=lambda x: x["relevance_score"], reverse=True)

    # Second pass: re-score top-N with diversity now known to improve ordering
    type_counts2 = {}
    for p in formatted_places:
        raw_type = (p.get("type") or "").lower().replace(" ", "_")
        p["_weather_bad"] = weather_bad
        p["relevance_score"] = score_item(
            p, context,
            user_lat=user_lat, user_lng=user_lng,
            already_scored_types=type_counts2,
            use_weather=check_weather,
        )
        type_counts2[raw_type] = type_counts2.get(raw_type, 0) + 1

    # Clean temp keys
    for p in formatted_places:
        p.pop("_weather_bad", None)
        p.pop("_raw_types", None)

    formatted_places.sort(key=lambda x: x["relevance_score"], reverse=True)
    return formatted_places

# --------------------------------

@app.get("/nearby")
def get_nearby_realtime():
    """Nearby places within 2km with personalization."""
    lat = request.args.get("lat")
    lng = request.args.get("lng")
    user_id = request.args.get("user_id")
    if not lat or not lng:
        return jsonify({"error": "Missing coordinates"}), 400
        
    place_type = request.args.get("type", "All")
    
    # Translate custom types to Google/Foursquare standard types
    type_map = {
        "culture": "museum",
        "shopping": "shopping_mall",
        "mixed": None,
        "tourist_attraction": None,
        "All": None
    }
    actual_type = type_map.get(place_type, place_type)
        
    results = []
    if not actual_type or actual_type == "" or actual_type.lower() == "all":
        types_to_search = ["restaurant", "cafe", "tourist_attraction", "park", "museum", "art_gallery"]
        seen_ids = set()
        for t in types_to_search:
            res = google_nearby_search(lat, lng, t, radius=2000)
            if res:
                for r in res[:10]: # take top 10 from each to keep things fast and diverse
                    pid = r.get("place_id")
                    if pid and pid not in seen_ids:
                        results.append(r)
                        seen_ids.add(pid)
    else:
        results = google_nearby_search(lat, lng, actual_type, radius=2000)
    formatted = []
    for p in results:
        f = format_google_place(p, lat, lng)
        if f:
            # User request: No shops/malls in 'Nearby' GENERALLY.
            # Only allow if user EXPLICITLY filtered for 'shopping'.
            if actual_type != "shopping_mall":
                p_types = p.get("types", [])
                if "shopping_mall" in p_types or "store" in p_types:
                    continue
            formatted.append(f)
    
    if user_id:
        context = get_user_context(user_id)
        formatted = rank_places(formatted, context,
                                user_lat=float(lat), user_lng=float(lng),
                                check_weather=True)
    return jsonify(formatted[:15])

@app.get("/places/search")
def personalized_discovery():
    """Trending / Discovery search with personalization."""
    lat = request.args.get("lat")
    lng = request.args.get("lng")
    user_id = request.args.get("user_id")
    place_type = request.args.get("type", "All")

    if not lat or not lng:
        return jsonify({"error": "Missing coordinates"}), 400

    place_type = request.args.get("type", "All")
    
    # Translate custom types
    type_map = {
        "culture": "museum",
        "shopping": "shopping_mall",
        "mixed": None,
        "tourist_attraction": None,
        "All": None
    }
    actual_type = type_map.get(place_type, place_type)

    radius = request.args.get("radius", 15000)
    try:
        radius = int(radius)
    except:
        radius = 15000
    results = []
    if not actual_type or actual_type == "" or actual_type.lower() == "all":
        city = request.args.get("city", "București")
        
        # 1. Start with high-relevance Text Search for "atractii" and "restaurante"
        results.extend(google_text_search(f"Top atracții turistice și obiective în {city}", lat, lng, radius=radius))
        results.extend(google_text_search(f"Restaurante faimoase și cafenele populare în {city}", lat, lng, radius=radius))
        results.extend(google_text_search(f"Experiențe unice și locuri cool în {city}", lat, lng, radius=radius))
        
        # 2. Add some specific categories too for variety
        # Use more categories to ensure we get results
        categories = ["tourist_attraction", "museum", "park", "restaurant", "cafe", "night_club"]
        for cat in categories:
            results.extend(google_nearby_search(lat, lng, cat, radius=radius))
        
        # 3. Last resort if empty: broad attraction search
        if not results:
            results.extend(google_nearby_search(lat, lng, "point_of_interest", radius=radius))
    else:
        results = google_nearby_search(lat, lng, actual_type, radius=radius)
        if not results:
            # Fallback to general if specific type yields nothing
            results = google_nearby_search(lat, lng, "tourist_attraction", radius=radius, keyword=actual_type)

    formatted = []
    seen_ids = set()
    for p in results:
        pid = p.get("place_id")
        if pid and pid not in seen_ids:
            f = format_google_place(p, lat, lng)
            if f:
                formatted.append(f)
                seen_ids.add(pid)
    
    if user_id:
        context = get_user_context(user_id)
        formatted = rank_places(formatted, context,
                                user_lat=float(lat), user_lng=float(lng),
                                check_weather=True)
    else:
        # Guest users: sort by rating × log(reviews) with small jitter for freshness
        for p in formatted:
            rc = p.get("reviewCount") or 0
            p["_guest_score"] = float(p.get("rating") or 0) * math.log1p(rc) + random.uniform(0, 0.5)
        formatted.sort(key=lambda x: x.get("_guest_score", 0), reverse=True)
        for p in formatted:
            p.pop("_guest_score", None)
            p.pop("_raw_types", None)

    return jsonify(formatted[:60])

@app.get("/places/autocomplete")
def get_autocomplete():
    query = request.args.get("query")
    lat = request.args.get("lat")
    lng = request.args.get("lng")
    if not query:
        return jsonify([])
    
    results = google_autocomplete(query, lat, lng)
    # Simple formatting: just name and place_id
    formatted = []
    for r in results:
        formatted.append({
            "name": r.get("structured_formatting", {}).get("main_text"),
            "full_name": r.get("description"),
            "place_id": r.get("place_id")
        })
    return jsonify(formatted)

@app.post("/visit")
def record_visit():
    data = request.get_json()
    user_id = data.get("user_id")
    place_id = data.get("place_id")
    google_place_id = data.get("google_place_id")
    place_name = data.get("place_name")
    place_type = data.get("place_type", "General")

    if not user_id or not place_name:
        return jsonify({"error": "Missing user_id or place_name"}), 400

    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "return=minimal"
    }

    visit_data = {
        "user_id": user_id,
        "place_id": place_id,
        "google_place_id": google_place_id,
        "place_name": place_name,
        "place_type": place_type
    }

    url = f"{SUPABASE_URL}/rest/v1/visited_places"
    requests.post(url, headers=headers, json=visit_data)

    profile_url = f"{SUPABASE_URL}/rest/v1/user_profiles?id=eq.{user_id}&select=*"
    try:
        p_res = requests.get(profile_url, headers=headers).json()
        if p_res:
            profile = p_res[0]
            new_visited_count = profile.get("places_visited", 0) + 1
            new_xp = profile.get("current_xp", 0) + 20
            
            update_url = f"{SUPABASE_URL}/rest/v1/user_profiles?id=eq.{user_id}"
            requests.patch(update_url, headers=headers, json={
                "places_visited": new_visited_count,
                "current_xp": new_xp,
                "total_xp": profile.get("total_xp", 0) + 20
            })
            check_and_unlock_badges(user_id, new_visited_count, place_type)
    except Exception as e:
        print(f"Error updating profile: {e}")

    return jsonify({"status": "success", "message": "Vizită înregistrată!"})

@app.get("/visited")
def get_visited():
    user_id = request.args.get("user_id")
    if not user_id:
        return jsonify({"error": "Missing user_id"}), 400

    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}"
    }
    url = f"{SUPABASE_URL}/rest/v1/visited_places?user_id=eq.{user_id}&order=visited_at.desc&select=*"
    
    response = requests.get(url, headers=headers)
    if response.status_code == 200:
        return jsonify(response.json())
    return jsonify([])

def check_and_unlock_badges(user_id, total_visits, last_type):
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json"
    }
    
    badges_to_check = []
    if total_visits >= 1: badges_to_check.append({"id": "first_visit", "name": "Prima Aventură", "desc": "Ai vizitat prima ta locație!"})
    if total_visits >= 5: badges_to_check.append({"id": "explorer", "name": "Explorator", "desc": "Ai vizitat 5 locații diferite!"})
    if total_visits >= 10: badges_to_check.append({"id": "pro_traveler", "name": "Călător Pro", "desc": "Ești deja un expert al orașului!"})
    
    if last_type:
        lt = last_type.lower()
        if any(x in lt for x in ["rest", "mâncare", "food", "cafe"]):
            badges_to_check.append({"id": "foodie", "name": "Gourmet", "desc": "Pasionat de restaurante și cafenele."})
        elif any(x in lt for x in ["parc", "park", "nature", "grădină"]):
            badges_to_check.append({"id": "nature", "name": "Iubitor de Natură", "desc": "Îți plac parcurile și aerul curat!"})
        elif any(x in lt for x in ["muz", "mus", "art", "gal"]):
            badges_to_check.append({"id": "culture", "name": "Rafinat", "desc": "Apreciezi arta și cultura."})

    for b in badges_to_check:
        badge_data = {
            "user_id": user_id,
            "badge_id": b["id"],
            "name": b["name"],
            "description": b["desc"],
            "is_unlocked": True,
            "unlocked_at": "now()"
        }
        url = f"{SUPABASE_URL}/rest/v1/user_badges"
        requests.post(url, headers=headers, json=badge_data)

@app.get("/places")
def get_places():
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json"
    }
    url = f"{SUPABASE_URL}/rest/v1/places?select=*"
    
    response = requests.get(url, headers=headers)
    if response.status_code == 200:
        places = response.json()
        print(f"✅ [GET /places] Successfully fetched {len(places)} places from Supabase.")
        for p in places:
            if not p.get("imageUrl") or "placeholder" in p.get("imageUrl"):
                p["imageUrl"] = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800"
        return jsonify(places)
    else:
        return jsonify({"error": "Failed to fetch from Supabase"}), 500


# ==================== GAMIFICATION 2.0 ====================

@app.get("/leaderboard")
def get_leaderboard():
    """Returns top users based on XP/Visited places."""
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    url = f"{SUPABASE_URL}/rest/v1/user_profiles?order=total_xp.desc&limit=10&select=name,total_xp,places_visited"
    
    try:
        res = requests.get(url, headers=headers)
        if res.status_code == 200:
            return jsonify(res.json())
        return jsonify([])
    except:
        # Mock data for demonstration if DB fails
        return jsonify([
            {"name": "Andrei Popa", "total_xp": 1250, "places_visited": 42},
            {"name": "Elena Ionescu", "total_xp": 1100, "places_visited": 38},
            {"name": "Mihai Radu", "total_xp": 950, "places_visited": 31},
            {"name": "Ana Maria", "total_xp": 800, "places_visited": 25}
        ])

@app.get("/challenges")
def get_challenges():
    """Active challenges for the user."""
    return jsonify([
        {
            "id": "weekend_cafe",
            "title": "Weekend-ul Cafenelelor",
            "description": "Vizitează 3 cafenele noi sâmbăta și duminica.",
            "reward_xp": 150,
            "progress": 1,
            "target": 3,
            "icon": "☕"
        },
        {
            "id": "museum_marathon",
            "title": "Maratonul Muzeelor",
            "description": "Descoperă 2 muzee în 24 de ore.",
            "reward_xp": 300,
            "progress": 0,
            "target": 2,
            "icon": "🏛️"
        },
        {
            "id": "night_owl",
            "title": "Bufniță de Noapte",
            "description": "Vizitează o locație după ora 22:00.",
            "reward_xp": 100,
            "progress": 0,
            "target": 1,
            "icon": "🦉"
        }
    ])

# ==================== WEATHER RECOVERY ====================

@app.get("/weather/plan-b")
def get_weather_plan_b():
    """Provides indoor alternatives if the weather is bad."""
    lat = request.args.get("lat")
    lng = request.args.get("lng")
    if not lat or not lng:
        return jsonify({"error": "Missing coordinates"}), 400
        
    # Check real weather
    w_url = f"https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lng}&current=weather_code"
    is_raining = False
    try:
        w_res = requests.get(w_url).json()
        code = w_res.get("current", {}).get("weather_code", 0)
        if code >= 51: # Drizzle, Rain, Snow, etc.
            is_raining = True
    except: pass
    
    if not is_raining:
        return jsonify({"status": "fine", "message": "Vremea e perfectă pentru explorare afară!"})
        
    # Fetch indoor places
    indoor_types = ["museum", "art_gallery", "movie_theater", "shopping_mall", "cafe"]
    results = []
    for t in indoor_types[:3]:
        res = google_nearby_search(lat, lng, t, radius=5000)
        if res:
            results.extend(res[:3])
            
    formatted = []
    for r in results:
        f = format_google_place(r, lat, lng)
        if f: formatted.append(f)
        
    return jsonify({
        "status": "bad",
        "message": "Văd că a început ploaia! Ce zici de aceste alternative indoor?",
        "alternatives": formatted[:6]
    })

# ==================== COMMUNITY GUIDES ====================

@app.post("/itineraries/share")
def share_itinerary():
    """Saves a generated itinerary to the public gallery."""
    data = request.get_json()
    user_id = data.get("user_id")
    user_name = data.get("user_name", "Explorator")
    title = data.get("title", "Traseu Urban")
    city = data.get("city", "București")
    items = data.get("items", []) # List of places
    
    if not user_id or not items:
        return jsonify({"error": "Missing data"}), 400
        
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}", "Content-Type": "application/json"}
    payload = {
        "user_id": user_id,
        "user_name": user_name,
        "title": title,
        "city": city,
        "items": items,
        "likes": 0
    }
    
    try:
        # Note: This expects a table 'public_itineraries' to exist
        res = requests.post(f"{SUPABASE_URL}/rest/v1/public_itineraries", headers=headers, json=payload)
        return jsonify({"status": "success", "message": "Itinerariul a fost publicat!"})
    except:
        return jsonify({"error": "Failed to share"}), 500

@app.get("/itineraries/public")
def get_public_itineraries():
    """Fetches shared itineraries from the community."""
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    try:
        res = requests.get(f"{SUPABASE_URL}/rest/v1/public_itineraries?order=likes.desc&limit=10", headers=headers)
        if res.status_code == 200:
            return jsonify(res.json())
        return jsonify([])
    except:
        # Mock data
        return jsonify([
            {
                "id": 1,
                "user_name": "Maria Popescu",
                "title": "Bucureștiul Istoric în 4 ore",
                "city": "București",
                "likes": 45,
                "image_url": "https://images.unsplash.com/photo-1549443105-098522300b0e?w=800"
            },
            {
                "id": 2,
                "user_name": "Alex Ghid",
                "title": "Turul Cafenelelor de Specialitate",
                "city": "București",
                "likes": 128,
                "image_url": "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=800"
            }
        ])


@app.get("/weather")
def get_weather():
    lat = request.args.get("lat")
    lng = request.args.get("lng")
    if not lat or not lng:
        return jsonify({"error": "Missing coordinates"}), 400
    
    # Open-Meteo is free and doesn't need a key
    url = f"https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lng}&current=temperature_2m,weather_code&timezone=auto"
    try:
        response = requests.get(url)
        data = response.json()
        if response.status_code == 200:
            current = data.get("current", {})
            code = current.get("weather_code", 0)
            
            # WMO Weather interpretation
            condition_map = {
                0: "Senin",
                1: "Parțial Senin", 2: "Noros", 3: "Înnorat",
                45: "Ceață", 48: "Ceață înghețată",
                51: "Burniță", 53: "Burniță", 55: "Burniță",
                61: "Ploaie slabă", 63: "Ploaie", 65: "Ploaie torențială",
                71: "Zăpadă slabă", 73: "Zăpadă", 75: "Zăpadă puternică",
                80: "Averse de ploaie", 81: "Averse", 82: "Averse violente",
                95: "Furtună", 96: "Furtună cu grindină", 99: "Furtună severă"
            }
            
            condition = condition_map.get(code, "Stabil")
            
            return jsonify({
                "temp": current.get("temperature_2m"),
                "condition": condition,
                "description": condition.lower(),
                "icon": "01d" # Fallback for UI if needed
            })
        return jsonify({"error": "Weather fetch failed"}), response.status_code
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.get("/itinerary")
def get_itinerary():
    """Generates a day plan using personalized sorting with high entropy."""
    lat_str = request.args.get("lat")
    lng_str = request.args.get("lng")
    user_id = request.args.get("user_id")
    scope = request.args.get("scope", "nearby").lower()
    
    if not lat_str or not lng_str:
        return jsonify([])

    lat = float(lat_str)
    lng = float(lng_str)
    style = request.args.get("type", "exploration").lower()
    duration = int(request.args.get("duration", 6))
    points_count = int(request.args.get("points", 4))

    context = get_user_context(user_id) if user_id else {"interests": [], "history_weights": {}}
    personalized = request.args.get("personalized", "false").lower() == "true"
    user_query = request.args.get("query", "")

    if personalized:
        from chatbot import generate_personalized_itinerary
        plan = generate_personalized_itinerary(lat, lng, style, duration, points_count, context, user_query)
        return jsonify(plan)

    import random
    from datetime import datetime, timedelta

    # Dynamic radius based on scope
    search_radius = 5000 if scope == "nearby" else 30000

    # Patterns for different styles
    if "cultural" in style:
        pattern = [
            {"type": "cafe", "label": "Mic Dejun"},
            {"type": "museum", "label": "Activitate Culturală"},
            {"type": "restaurant", "label": "Prânz"},
            {"type": "art_gallery", "label": "Explorare Istorică"},
            {"type": "library", "label": "Pauză de Ceai"},
            {"type": "museum", "label": "Muzeu Seară"}
        ]
    elif "relax" in style:
        pattern = [
            {"type": "park", "label": "Mic Dejun în Natură"},
            {"type": "park", "label": "Moment de Relaxare"},
            {"type": "restaurant", "label": "Prânz"},
            {"type": "spa", "label": "Plimbare Liniștită"},
            {"type": "spa", "label": "Răsfăț"},
            {"type": "restaurant", "label": "Cină Relaxantă"}
        ]
    elif "gastronomic" in style or "foodie" in style:
        pattern = [
            {"type": "bakery", "label": "Mic Dejun & Patiserie"},
            {"type": "cafe", "label": "Degustare Cafea"},
            {"type": "restaurant", "label": "Prânz Gourmet"},
            {"type": "bar", "label": "Aperitiv de Seară"},
            {"type": "restaurant", "label": "Cină Tasting"},
            {"type": "cafe", "label": "Desert Târziu"}
        ]
    elif "cinema" in style:
        pattern = [
            {"type": "cafe", "label": "Socializare pre-film"},
            {"type": "movie_theater", "label": "Vizionare Film"},
            {"type": "restaurant", "label": "Cină post-film"},
            {"type": "movie_theater", "label": "Avanpremieră"},
            {"type": "bar", "label": "Cocktail & Movie Talk"}
        ]
    elif "sport" in style:
        pattern = [
            {"type": "cafe", "label": "Mic dejun energetic"},
            {"type": "gym", "label": "Antrenament / Tenis"},
            {"type": "restaurant", "label": "Prânz proteic"},
            {"type": "park", "label": "Alergare / Yoga"},
            {"type": "stadium", "label": "Eveniment Sportiv"}
        ]
    else:
        pattern = [
            {"type": "cafe", "label": "Mic Dejun"},
            {"type": "tourist_attraction", "label": "Aventură Urbană"},
            {"type": "restaurant", "label": "Prânz"},
            {"type": "park", "label": "Relaxare în Natură"},
            {"type": "tourist_attraction", "label": "Punct de Interes"},
            {"type": "restaurant", "label": "Cină Locală"}
        ]

    slots = []
    for i in range(points_count):
        item = pattern[i % len(pattern)]
        label = item["label"]
        if i >= len(pattern):
            label += f" {(i // len(pattern)) + 1}"
        slots.append({"type": item["type"], "label": label})

    plan = []
    # SHAKE UP the start to prevent samey paths
    current_lat = lat + random.uniform(-0.015, 0.015)
    current_lng = lng + random.uniform(-0.015, 0.015)
    current_time = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
    slot_duration_mins = (duration * 60) // len(slots) if slots else 60

    used_place_ids = set()

    for slot in slots:
        try:
            # Randomize category slightly for fun
            search_type = slot["type"]
            results = google_nearby_search(current_lat, current_lng, search_type, radius=search_radius)
            available = [r for r in results if r.get("place_id") not in used_place_ids and r.get("name")]
            
            # Shuffle results globally for randomness
            random.shuffle(available)
            
            if not available:
                # Fallback to general attraction if specific fails
                results = google_nearby_search(current_lat, current_lng, "tourist_attraction", radius=search_radius)
                available = [r for r in results if r.get("place_id") not in used_place_ids]
                random.shuffle(available)
            
            if available:
                # Score them but keep weight on randomness
                for r in available:
                    temp_item = {"name": r.get("name"), "type": search_type, "rating": r.get("rating", 4.0)}
                    r["_score"] = score_item(temp_item, context) + random.uniform(0, 2) # Add Luck factor
                
                # Sort and pick from a LARGER pool for more diversity (Top 8)
                available.sort(key=lambda x: x.get("_score", 0), reverse=True)
                top_candidates = available[:8]
                best = random.choice(top_candidates)
                
                used_place_ids.add(best.get("place_id"))
                
                img_url = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800"
                if best.get("photos"):
                    ref = best["photos"][0]["photo_reference"]
                    img_url = f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference={ref}&key={MAPS_API_KEY}"

                level = best.get("price_level", 2)
                price_map = {0: 0, 1: 30, 2: 70, 3: 150, 4: 350}
                est_cost = price_map.get(level, 70)
                if search_type in ["park", "natural_feature", "place_of_worship"]: est_cost = 0
                if search_type in ["museum", "art_gallery"]: est_cost = 40

                t_start = current_time.strftime("%H:%M")
                current_time += timedelta(minutes=slot_duration_mins)
                t_end = current_time.strftime("%H:%M")

                geo = best.get("geometry", {}).get("location", {})
                plan.append({
                    "slot": slot["label"],
                    "name": best["name"],
                    "address": best.get("vicinity", "Locație oraș"),
                    "imageUrl": img_url,
                    "latitude": geo.get("lat"),
                    "longitude": geo.get("lng"),
                    "estimatedCost": est_cost,
                    "placeId": best.get("place_id"),
                    "type": search_type,
                    "time": f"{t_start} - {t_end}",
                    "is_open": True
                })
                # Move slightly towards result but keep some center bias for better city coverage
                next_lat = geo.get("lat", current_lat)
                next_lng = geo.get("lng", current_lng)
                current_lat = (current_lat + next_lat) / 2
                current_lng = (current_lng + next_lng) / 2
                
        except Exception as e:
            print(f"⚠️ Itinerary Error: {e}")
            continue

    return jsonify(plan)

@app.get("/users/<u_id>/posts")
def get_user_posts(u_id):
    """Returns posts created by a specific user."""
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    url = f"{SUPABASE_URL}/rest/v1/feed_posts?user_id=eq.{u_id}&order=created_at.desc"
    
    try:
        res = requests.get(url, headers=headers)
        if res.status_code == 200:
            posts = res.json()
            # Minimal enrichment for profile view
            for p in posts:
                p_id = p.get("id")
                # Add basic count if needed
                p["comments_count"] = 0 
                p["likes_count"] = 0
            return jsonify(posts)
        return jsonify([])
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# ==================== SOCIAL FEED ====================

@app.get("/feed")
def get_feed():
    """Returns posts for the social feed with community/friends filtering."""
    user_id = request.args.get("user_id", "")
    feed_type = request.args.get("type", "foryou")
    
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    
    # 1. Determine which posts to fetch
    if feed_type == "friends" and user_id:
        # Get list of following IDs
        f_url = f"{SUPABASE_URL}/rest/v1/user_follows?follower_id=eq.{user_id}&select=following_id"
        f_res = requests.get(f_url, headers=headers)
        if f_res.status_code == 200:
            following_ids = [f["following_id"] for f in f_res.json()]
            if not following_ids:
                return jsonify([]) # No friends, empty feed
            # Filter posts by these IDs
            ids_str = ",".join(following_ids)
            url = f"{SUPABASE_URL}/rest/v1/feed_posts?user_id=in.({ids_str})&order=created_at.desc&limit=30"
        else:
            return jsonify([])
    else:
        # Community (foryou) - all posts
        url = f"{SUPABASE_URL}/rest/v1/feed_posts?select=*&order=created_at.desc&limit=100"
    
    try:
        res = requests.get(url, headers=headers)
        if res.status_code != 200:
            return jsonify([])
            
        posts = res.json()
        
        # 2. Filter by location if lat/lng provided
        lat_req = request.args.get("lat")
        lng_req = request.args.get("lng")
        
        if lat_req and lng_req:
            try:
                from math import geodesic
            except:
                # Manual distance approximation if geopy not available
                import math
                def distance(lat1, lon1, lat2, lon2):
                    R = 6371 # Radius of earth in km
                    dLat = math.radians(lat2 - lat1)
                    dLon = math.radians(lon2 - lon1)
                    a = math.sin(dLat/2) * math.sin(dLat/2) + \
                        math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * \
                        math.sin(dLon/2) * math.sin(dLon/2)
                    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))
                    return R * c
                
                user_lat = float(lat_req)
                user_lng = float(lng_req)
                # Keep posts within 50km
                local_posts = [p for p in posts if p.get('latitude') and p.get('longitude') and 
                         distance(user_lat, user_lng, float(p['latitude']), float(p['longitude'])) <= 50]
                
                # Fallback: if no local posts, show all but maybe a bit more than usual to ensure content
                if not local_posts:
                    posts = posts[:40]
                else:
                    posts = local_posts
            else:
                user_lat = float(lat_req)
                user_lng = float(lng_req)
                local_posts = [p for p in posts if p.get('latitude') and p.get('longitude') and 
                         geodesic((user_lat, user_lng), (float(p['latitude']), float(p['longitude']))).km <= 50]
                
                if not local_posts:
                    posts = posts[:40]
                else:
                    posts = local_posts

        # 3. Post-processing and enrichment
        for post in posts[:30]: # Limit to 30 after filtering
            p_id = post.get("id")
            # Enrichment
            c_res = requests.get(f"{SUPABASE_URL}/rest/v1/feed_comments?post_id=eq.{p_id}&select=id", headers=headers)
            post["comments_count"] = len(c_res.json()) if c_res.status_code == 200 else 0
            
            l_res = requests.get(f"{SUPABASE_URL}/rest/v1/feed_likes?post_id=eq.{p_id}&select=user_id", headers=headers)
            likes = l_res.json() if l_res.status_code == 200 else []
            post["likes_count"] = len(likes)
            post["is_liked"] = any(l.get("user_id") == user_id for l in likes)
            
        return jsonify(posts)

    except Exception as e:
        print(f"❌ Feed Error: {e}")
        return jsonify([])

def is_content_safe(text):
    """Uses Gemini AI to check if content is appropriate for the community."""
    if not text or len(text.strip()) < 2:
        return True, ""
        
    try:
        import google.generativeai as genai
        # Reuse existing configuration or configure
        try:
            model = genai.GenerativeModel("gemini-flash-latest")
        except:
            genai.configure(api_key=GOOGLE_API_KEY)
            model = genai.GenerativeModel("gemini-flash-latest")
            
        prompt = (
            "Ești un moderator de conținut pentru o aplicație socială de explorare urbană numită CityScape. "
            "Analizează următorul text și decide dacă este ADECVAT sau NEADECVAT. "
            "NEADECVAT înseamnă: limbaj licențios, ură, hărțuire, conținut sexual explicit, violență sau spam. "
            "Răspunde DOAR în format JSON: {\"safe\": true/false, \"reason\": \"motivul scurt în română dacă e unsafe\"}. "
            f"Text de analizat: \"{text}\""
        )
        
        response = model.generate_content(prompt)
        import re
        json_match = re.search(r"\{.*\}", response.text, re.DOTALL)
        if json_match:
            result = json.loads(json_match.group())
            return result.get("safe", True), result.get("reason", "")
    except Exception as e:
        print(f"⚠️ AI Moderation Error: {e}")
        
    return True, "" # Fallback to safe if AI fails

@app.post("/feed")
def create_post():
    """Creates a new feed post with AI moderation and UUID validation."""
    data = request.get_json()
    caption = data.get("caption", "")
    
    # 1. AI Content Moderation
    is_safe, reason = is_content_safe(caption)
    if not is_safe:
        return jsonify({
            "status": "rejected", 
            "error": "Conținut neadecvat detectat", 
            "reason": reason
        }), 400

    
    # Validation for UUID: if it's not a valid UUID, use a fallback
    # to avoid Supabase NOT NULL UUID constraint violation
    user_id = data.get("user_id")
    import uuid
    try:
        uuid.UUID(str(user_id))
    except:
        # Fallback to a fixed user ID if invalid
        user_id = "00000000-0000-0000-0000-000000000000"

    # Use Service Role Key if available to bypass RLS for backend operations
    key_to_use = os.getenv("SUPABASE_SERVICE_ROLE_KEY") or SUPABASE_KEY
    
    headers = {
        "apikey": key_to_use,
        "Authorization": f"Bearer {key_to_use}",
        "Content-Type": "application/json",
        "Prefer": "return=representation"
    }
    
    post_data = {
        "user_id": user_id,
        "user_name": data.get("user_name", "Explorer"),
        "user_avatar": data.get("user_avatar", ""),
        "place_name": data.get("place_name", ""),
        "image_url": data.get("image_url", ""),
        "caption": data.get("caption", ""),
        "rating": data.get("rating", 0),
        "latitude": data.get("latitude", 0),
        "longitude": data.get("longitude", 0)
    }
    
    url = f"{SUPABASE_URL}/rest/v1/feed_posts"
    try:
        res = requests.post(url, headers=headers, json=post_data)
        if res.status_code in [200, 201]:
            return jsonify({"status": "success", "post": res.json()})
        
        # Log the error for the developer
        print(f"❌ Supabase Insert Error ({res.status_code}): {res.text}")
        return jsonify({
            "error": "Failed to create post", 
            "details": res.json() if res.status_code != 404 else "Table not found. Please run create_feed_tables.py"
        }), res.status_code

    except Exception as e:
        print(f"❌ Create Post Error: {e}")
        return jsonify({"error": str(e)}), 500

@app.get("/feed/<post_id>/comments")
def get_comments(post_id):
    """Returns all comments for a given post."""
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}"
    }
    url = f"{SUPABASE_URL}/rest/v1/feed_comments?post_id=eq.{post_id}&select=*&order=created_at.asc"
    res = requests.get(url, headers=headers)
    return jsonify(res.json() if res.status_code == 200 else [])

@app.post("/feed/<post_id>/comments")
def add_comment(post_id):
    """Adds a comment to a post."""
    data = request.get_json()
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "return=representation"
    }
    
    comment_text = data.get("comment_text", "")
    
    # 1. AI Content Moderation
    is_safe, reason = is_content_safe(comment_text)
    if not is_safe:
        return jsonify({
            "status": "rejected", 
            "error": "Comentariul conține limbaj neadecvat", 
            "reason": reason
        }), 400

    comment_data = {
        "post_id": post_id,
        "user_id": data.get("user_id"),
        "user_name": data.get("user_name", "Explorer"),
        "comment_text": comment_text
    }
    
    url = f"{SUPABASE_URL}/rest/v1/feed_comments"
    res = requests.post(url, headers=headers, json=comment_data)
    
    if res.status_code in [200, 201]:
        return jsonify({"status": "success"})
    return jsonify({"error": "Failed to add comment"}), 500

@app.post("/feed/<post_id>/like")
def toggle_like(post_id):
    """Toggles like on a post for the given user."""
    data = request.get_json()
    user_id = data.get("user_id")
    
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json"
    }
    
    # Check if already liked
    check_url = f"{SUPABASE_URL}/rest/v1/feed_likes?post_id=eq.{post_id}&user_id=eq.{user_id}&select=id"
    existing = requests.get(check_url, headers=headers).json()
    
    if existing:
        # Unlike - delete
        del_url = f"{SUPABASE_URL}/rest/v1/feed_likes?post_id=eq.{post_id}&user_id=eq.{user_id}"
        requests.delete(del_url, headers=headers)
        return jsonify({"status": "unliked"})
    else:
        # Like - insert
        like_data = {"post_id": post_id, "user_id": user_id}
        ins_url = f"{SUPABASE_URL}/rest/v1/feed_likes"
        requests.post(ins_url, headers=headers, json=like_data)
        return jsonify({"status": "liked"})

@app.post("/report")
def report_content():
    """Handles manual reporting of posts or comments."""
    data = request.get_json()
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json"
    }
    
    report_data = {
        "reporter_id": data.get("user_id"),
        "post_id": data.get("post_id"),
        "comment_id": data.get("comment_id"),
        "reason": data.get("reason", "Conținut neadecvat")
    }
    
    url = f"{SUPABASE_URL}/rest/v1/content_reports"
    res = requests.post(url, headers=headers, json=report_data)
    
    if res.status_code in [200, 201]:
        return jsonify({"status": "success", "message": "Raport trimis cu succes. Mulțumim!"})
    return jsonify({"error": "Eroare la trimiterea raportului"}), 500

# ==================== USERS & SOCIAL ====================

def fetch_ticketmaster_events(lat, lng, radius):
    """Fetches global events from Ticketmaster API based on coordinates."""
    if not TICKETMASTER_API_KEY:
        return []
    
    # Radius in KM
    url = f"https://app.ticketmaster.com/discovery/v2/events.json"
    params = {
        "apikey": TICKETMASTER_API_KEY,
        "latlong": f"{lat},{lng}",
        "radius": str(radius),
        "unit": "km",
        "size": 25,
        "sort": "date,asc"
    }
    
    try:
        res = requests.get(url, params=params, timeout=10)
        if res.status_code != 200:
            return []
            
        data = res.json()
        raw_events = data.get("_embedded", {}).get("events", [])
        
        formatted_events = []
        for e in raw_events:
            img = e.get("images", [{}])[0].get("url", "")
            venue = e.get("_embedded", {}).get("venues", [{}])[0]
            
            formatted_events.append({
                "title": e.get("name"),
                "location": venue.get("name", "Various"),
                "date_str": e.get("dates", {}).get("start", {}).get("localDate"),
                "image_url": img,
                "event_url": e.get("url"),
                "source": "ticketmaster",
                "category": e.get("classifications", [{}])[0].get("segment", {}).get("name", "Social")
            })
        return formatted_events
    except Exception as e:
        print(f"⚠️ Ticketmaster Error: {e}")
        return []

def fetch_foursquare_trending(lat, lng):
    """Fallback: Finds popular venues and 'events' using Foursquare Trending/Popularity."""
    if not FOURSQUARE_API_KEY:
        return []
        
    url = "https://api.foursquare.com/v3/places/search"
    # Categories: Arts & Ent (10000), Music Venue (10039), Nightlife (10032), Festival (10010)
    params = {
        "ll": f"{lat},{lng}",
        "categories": "10000,10010,10032,10039",
        "sort": "POPULARITY",
        "limit": 15
    }
    headers = {
        "Accept": "application/json",
        "Authorization": FOURSQUARE_API_KEY
    }
    
    try:
        res = requests.get(url, params=params, headers=headers, timeout=10)
        if res.status_code != 200:
            return []
            
        places = res.json().get("results", [])
        formatted = []
        for p in places:
            # Map place to event-like structure
            cat = p.get("categories", [{}])[0].get("name", "Social")
            img = "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=800" # Event placeholder
            
            formatted.append({
                "title": f"Spotlight: {p.get('name')}",
                "location": p.get("location", {}).get("address", "Nearby"),
                "date_str": "Trending Now",
                "image_url": img,
                "event_url": f"https://foursquare.com/v/{p.get('fsq_id')}",
                "source": "foursquare",
                "category": cat
            })
        return formatted
    except Exception as e:
        print(f"⚠️ Foursquare Fallback Error: {e}")
        return []

@app.get("/events")
def get_events():
    """Returns events for ANY location, balancing local scraping, global API, and trending spots."""
    lat = request.args.get("lat", 44.4268)
    lng = request.args.get("lng", 26.1025)
    radius = int(request.args.get("radius", 50))
    interests = request.args.get("interests", "").lower()
    
    # 0. Weather Check
    weather = fetch_current_weather(lat, lng)
    
    # Tier 1: Global Ticketmaster (Concerts, Sports)
    events = fetch_ticketmaster_events(lat, lng, radius)
    
    # Tier 2: Deep Category Discovery (Universal fallback)
    # If we have few, search for specific types to ensure 'Anything' coverage
    discovery_categories = ["festivaluri", "concerte", "expoziții artă", "târguri tradiționale", "evenimente culturale"]
    if len(events) < 10:
        for cat in discovery_categories:
            try:
                search_query = f"{cat} în apropiere de {lat}, {lng}"
                extra = google_text_search(search_query, lat, lng, radius=radius*1000)
                for s in extra[:8]:
                    events.append({
                        "title": s.get("name"),
                        "category": cat.capitalize(),
                        "date_str": "Program variat / În curând",
                        "location": s.get("vicinity", s.get("address", "Zonă locală")),
                        "image_url": s.get("imageUrl", "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=800"),
                        "source": "deep_discovery",
                        "latitude": s.get("latitude"),
                        "longitude": s.get("longitude")
                    })
            except: continue

    # Tier 3: Local Scraped (Universal Radius Discovery)
    # Automatically finds scraped events within the requested radius anywhere in the world
    try:
        headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
        # Fetch a batch of recent events to filter locally by distance
        res = requests.get(f"{SUPABASE_URL}/rest/v1/scraped_events?limit=100", headers=headers)
        if res.status_code == 200:
            all_scraped = res.json()
            for se in all_scraped:
                try:
                    s_lat = se.get("latitude")
                    s_lng = se.get("longitude")
                    if s_lat is not None and s_lng is not None:
                        u_lat, u_lng = float(lat), float(lng)
                        s_lat_v, s_lng_v = float(s_lat), float(s_lng)
                        # Basic Haversine approximation
                        dist = ((u_lat - s_lat_v)**2 + (u_lng - s_lng_v)**2)**0.5
                        if dist < (radius / 111.0): 
                            events.append(se)
                except (ValueError, TypeError):
                    continue
    except Exception as e:
        print(f"⚠️ Scraped Discovery Error: {e}")

    # Tier 4: Universal Fallback (Foursquare Trending)
    # If we still have very few, fill with trending spots
    if len(events) < 5:
        trending = fetch_foursquare_trending(lat, lng)
        events.extend(trending)

    # Tier 4: DAILY CHALLENGE ENGINE (Injects a pro-active task)
    daily_challenges = [
        {"title": "Maratonul Muzeelor", "category": "Challenge", "date_str": "Azi (Bonus 500 XP)", "source": "challenge", "description": "Vizitează 2 muzee în 4 ore!", "image_url": "https://images.unsplash.com/photo-1518998053502-53cc8de401b2?w=800"},
        {"title": "Turul Cafenelelor", "category": "Challenge", "date_str": "Azi (Bonus 300 XP)", "source": "challenge", "description": "Descoperă 3 locații noi de cafea!", "image_url": "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=800"},
        {"title": "Explorator de Noapte", "category": "Challenge", "date_str": "Azi (Bonus 750 XP)", "source": "challenge", "description": "Vizitează 2 puncte panoramice după ora 20!", "image_url": "https://images.unsplash.com/photo-1514525253344-f81f3f746522?w=800"}
    ]
    
    # Select one challenge for the user and put it at the very top
    import random
    active_challenge = random.choice(daily_challenges)
    
    # Personalization & Deduplication
    seen_titles = set()
    unique_events = [active_challenge] # Challenge is always first
    interest_list = [i.strip() for i in interests.split(",") if i.strip()] if interests else []
    
    for event in events:
        title = event.get('title')
        if not title or title in seen_titles: continue
        seen_titles.add(title)
        
        # Smart Vibe Tagging & Additional Pro Metadata
        vibe_tags = ["Popular", "Recomandat"]
        if "concert" in title.lower() or "party" in title.lower(): vibe_tags = ["Energy", "Social"]
        elif "muzeu" in title.lower() or "art" in title.lower(): vibe_tags = ["Culture", "Chill"]
        elif "parc" in title.lower() or "gradina" in title.lower(): vibe_tags = ["Nature", "Peaceful"]
        
        event['smart_tags'] = vibe_tags
        event['expected_crowding'] = random.choice(["Scăzut", "Moderat", "Vârf de audiență"])
        
        # Scoring
        score = 0
        title_lower = title.lower()
        cat_lower = event.get('category', '').lower()
        for interest in interest_list:
            if interest in title_lower or interest in cat_lower:
                score += 30
        
        # Source priority
        source_score = 0
        if event.get('source') == 'iabilet': source_score = 10
        elif event.get('source') == 'ticketmaster': source_score = 5
        
        event['relevance_score'] = score + source_score
        unique_events.append(event)
    
    # Sort remaining (skip the challenge at index 0)
    sorted_others = sorted(unique_events[1:], key=lambda x: x.get('relevance_score', 0), reverse=True)
    return jsonify([unique_events[0]] + sorted_others[:40])

@app.get("/users/search")
def search_users():
    query = request.args.get("query", "")
    current_user_id = request.args.get("current_user_id", "")
    
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}"
    }
    
    # Search for users by name, email or unique username
    url = f"{SUPABASE_URL}/rest/v1/user_profiles?or=(name.ilike.*{query}*,email.ilike.*{query}*,username.ilike.*{query}*)&limit=20"
    
    try:
        res = requests.get(url, headers=headers)
        if res.status_code != 200:
            return jsonify([])
        
        users = res.json()
        # Enrich with follow status
        for user in users:
            u_id = user.get("id")
            if u_id == current_user_id:
                user["is_me"] = True
                continue
                
            check_url = f"{SUPABASE_URL}/rest/v1/user_follows?follower_id=eq.{current_user_id}&following_id=eq.{u_id}&select=id"
            follow_res = requests.get(check_url, headers=headers).json()
            user["is_following"] = len(follow_res) > 0
            
        return jsonify(users)
    except Exception as e:
        print(f"❌ User Search Error: {e}")
        return jsonify([])

@app.post("/users/follow")
def toggle_follow():
    data = request.get_json()
    f_id = data.get("follower_id")
    t_id = data.get("following_id")
    
    if not f_id or not t_id:
        return jsonify({"error": "Missing IDs"}), 400
        
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json"
    }
    
    # Check if exists
    check_url = f"{SUPABASE_URL}/rest/v1/user_follows?follower_id=eq.{f_id}&following_id=eq.{t_id}&select=id"
    existing = requests.get(check_url, headers=headers).json()
    
    if existing:
        # Unfollow
        del_url = f"{SUPABASE_URL}/rest/v1/user_follows?follower_id=eq.{f_id}&following_id=eq.{t_id}"
        requests.delete(del_url, headers=headers)
        return jsonify({"status": "unfollowed"})
    else:
        # Follow
        ins_data = {"follower_id": f_id, "following_id": t_id}
        url = f"{SUPABASE_URL}/rest/v1/user_follows"
        requests.post(url, headers=headers, json=ins_data)
        return jsonify({"status": "followed"})

@app.get("/users/<user_id>/following")
def get_following(user_id):
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}"
    }
    
    # Get all user IDs the subject follows
    url = f"{SUPABASE_URL}/rest/v1/user_follows?follower_id=eq.{user_id}&select=following_id"
    try:
        res = requests.get(url, headers=headers)
        if res.status_code != 200:
            return jsonify([])
            
        following_ids = [r.get("following_id") for r in res.json()]
        if not following_ids:
            return jsonify([])
            
        # Bulk fetch profiles
        ids_str = ",".join(following_ids)
        profiles_url = f"{SUPABASE_URL}/rest/v1/user_profiles?id=in.({ids_str})"
        profiles_res = requests.get(profiles_url, headers=headers)
        
        profiles = profiles_res.json() if profiles_res.status_code == 200 else []
        for p in profiles:
            p["is_following"] = True # By definition in this list
            
        return jsonify(profiles)
    except Exception as e:
        print(f"❌ Get Following Error: {e}")
        return jsonify([])

@app.get("/users/recommended")
def get_recommended_users():
    user_id = request.args.get("user_id")
    # For now, just return some other users from the mock database
    # In a real app, this would be a complex ML recommendation or mutual friends logic
    exclude = [user_id]
    
    # Get following to exclude them too
    following_ids = [f["following_id"] for f in MOCK_DATA["follows"] if f["user_id"] == user_id]
    exclude.extend(following_ids)
    
    recommended = [u for u in MOCK_DATA["users"] if u["id"] not in exclude]
    random.shuffle(recommended)
    
    return jsonify(recommended[:10])

@app.get("/recommendation/magic")
def get_magic_recommendation():
    """Generates a high-quality AI recommendation for a place with specific activities."""
    lat_str = request.args.get("lat")
    lng_str = request.args.get("lng")
    user_id = request.args.get("user_id")
    requested_type = request.args.get("type", "general")

    if not lat_str or not lng_str:
        return jsonify({"error": "Missing location"}), 400

    lat = float(lat_str)
    lng = float(lng_str)
    
    # 0. User context for personalization
    user_context = "preferințe generale"
    u_interests = ""
    if user_id:
        try:
            headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
            p_res = requests.get(f"{SUPABASE_URL}/rest/v1/user_profiles?id=eq.{user_id}&select=*", headers=headers).json()
            if p_res:
                p = p_res[0]
                u_interests = p.get('interests', '')
                user_context = f"Nume: {p.get('full_name')}, Interese: {u_interests}, Buget: {p.get('budget_range')}"
        except: pass
    
    # Map friendly type to Google Places types
    type_map = {
        "restaurant": ["restaurant", "food", "cafe", "steakhouse", "sushi"],
        "entertainment": ["amusement_park", "movie_theater", "night_club", "bowling_alley"],
        "park": ["park", "natural_feature", "zoo", "campground"],
        "museum": ["museum", "art_gallery", "library", "church", "hindu_temple"]
    }
    
    target_types = type_map.get(requested_type, ["tourist_attraction", "museum", "park", "restaurant", "cafe"])
    
    # 1. Fetch real places based on requested type
    candidates = []
    seen_ids = set()
    for p_type in target_types:
        res = google_nearby_search(lat, lng, p_type, radius=15000)
        for c in res:
            if c['place_id'] not in seen_ids:
                # Add a "match_score" based on user interests
                score = c.get('rating', 0) * 10
                if u_interests:
                    for interest in u_interests.split(','):
                        if interest.strip().lower() in c['name'].lower() or any(interest.strip().lower() in t.lower() for t in c.get('types', [])):
                            score += 20
                
                c['personal_score'] = score
                candidates.append(c)
                seen_ids.add(c['place_id'])
    
    if not candidates:
        return jsonify({"error": "No candidates found"}), 404

    # Sort by personal score and pick some to give Gemini options
    candidates.sort(key=lambda x: x.get('personal_score', 0), reverse=True)
    subset = candidates[:15]
    random.shuffle(subset) # Shuffle top 15 to keep it "magic"

    # 2. Use Gemini to pick the BEST one
    import google.generativeai as genai
    model = genai.GenerativeModel('gemini-flash-latest')
    
    candidates_data = [{"id": c['place_id'], "name": c['name'], "types": c.get('types', []), "rating": c.get('rating', 0)} for c in subset]
    
    prompt = (
        f"Ești expertul 'CityScape AI'. Din următoarele locații: {json.dumps(candidates_data)}, "
        f"alege EXACT UNA care se potrivește cel mai bine cu categoria cerută '{requested_type}' "
        f"și cu profilul utilizatorului: {user_context}. "
        "Ține cont de interesele lui dar adaugă un gram de mister și destin. "
        "Pentru această locație, generează 3 activități specifice și creative. "
        "RĂSPUNS: Un obiect JSON (fără explicații) cu formatul: "
        '{"place_id": "...", "name": "...", "reason": "De ce am ales asta pentru TINE bazat pe interesele tale (max 15 cuvinte)", '
        '"activities": ["Activitate 1", "Activitate 2", "Activitate 3"]}'
    )

    try:
        response = model.generate_content(prompt)
        text = response.text
        if "```json" in text: text = text.split("```json")[1].split("```")[0].strip()
        elif "```" in text: text = text.split("```")[1].split("```")[0].strip()
        
        result = json.loads(text)
        
        # Enrich with real data (lat, lng, image)
        matched = next((c for c in subset if c['place_id'] == result.get('place_id')), subset[0])
        geo = matched.get("geometry", {}).get("location", {})
        
        img_url = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800"
        if matched.get("photos"):
            ref = matched["photos"][0]["photo_reference"]
            img_url = f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference={ref}&key={MAPS_API_KEY}"

        result.update({
            "latitude": geo.get("lat"),
            "longitude": geo.get("lng"),
            "address": matched.get("vicinity", "Locație oraș"),
            "imageUrl": img_url,
            "rating": matched.get("rating", 4.0),
            "type": matched.get("types", ["place"])[0]
        })
        
        return jsonify(result)
    except Exception as e:
        print(f"⚠️ Magic Rec Error: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/recommendations/personalized', methods=['GET'])
def get_personalized_recommendations():
    lat_str = request.args.get('lat')
    lng_str = request.args.get('lng')
    user_id = request.args.get('user_id')
    query = request.args.get('query', '')
    type_filter = request.args.get('type', '')
    city = request.args.get('city', 'București')
    
    if not lat_str or not lng_str:
        return jsonify({"error": "Location missing"}), 400
        
    lat, lng = float(lat_str), float(lng_str)
    
    # 1. Fetch User Context (Profile + History)
    user_context = "generale"
    history_names = []
    if user_id:
        try:
            headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
            # Profile
            p_res = requests.get(f"{SUPABASE_URL}/rest/v1/user_profiles?id=eq.{user_id}&select=*", headers=headers).json()
            profile_info = ""
            if p_res:
                p = p_res[0]
                profile_info = f"Preferințe: {p.get('interests')}, Buget: {p.get('budget_range')}."
            
            # Real history from DB
            v_res = requests.get(f"{SUPABASE_URL}/rest/v1/visited_places?user_id=eq.{user_id}&limit=8&order=visited_at.desc", headers=headers).json()
            history_names = [v.get("place_name") for v in v_res if v.get("place_name")]
            user_context = f"{profile_info} Locații vizitate recent: {', '.join(history_names)}."
        except Exception as e:
            print(f"⚠️ Context Fetch Error: {e}")

    # 2. Gather candidates (Respect filters if present)
    candidates = []
    search_terms = []
    if query: search_terms.append(query)
    if type_filter and type_filter.lower() not in ["toate", "all", "categoria"]: search_terms.append(type_filter)
    final_query = " ".join(search_terms)
    
    seen_ids = set()
    if final_query:
        # Search specifically for what the user wants
        candidates.extend(google_text_search(f"{final_query} în {city}", lat, lng, radius=20000))
    else:
        # Broad discovery using city name
        queries = [f"atracții de top în {city}", f"restaurante apreciate în {city}", f"locuri faimoase în {city}"]
        for q in queries:
            res = google_text_search(q, lat, lng, radius=15000)
            for c in res:
                if c['place_id'] not in seen_ids:
                    candidates.append(c)
                    seen_ids.add(c['place_id'])
    
    if not candidates:
        return jsonify([])

    # 3. AI Selection & Reasoning
    import google.generativeai as genai
    model = genai.GenerativeModel('gemini-flash-latest')
    
    candidates_simple = [{"id": c['place_id'], "name": c['name'], "types": c.get('types', []), "rating": c.get('rating', 0), "vicinity": c.get('vicinity', '')} for c in candidates[:15]]
    
    prompt = (
        f"Ești 'CityScape AI'. Recomandă cele mai bune 15 locații din această listă: {json.dumps(candidates_simple)} "
        f"pentru un utilizator cu contextul: {user_context} "
        f"și filtrul activ: '{final_query if final_query else 'descoperire generală'}'. "
        "IMPORTANT: Nu recomanda ceva ce a vizitat recent decât dacă e o legătură mistică. "
        "Răspunde DOAR cu un JSON (lista de obiecte): "
        '[{"place_id": "...", "ai_reason": "De ce e pentru tine, bazat pe profil/istoric (scurt, 10 cuvinte)"}, ...]'
    )

    try:
        response = model.generate_content(prompt)
        text = response.text
        if "```json" in text: text = text.split("```json")[1].split("```")[0].strip()
        elif "```" in text: text = text.split("```")[1].split("```")[0].strip()
        
        recs = json.loads(text)
        final_list = []
        for r in recs:
            matched = next((c for c in candidates if c['place_id'] == r.get('place_id')), None)
            if matched:
                geo = matched.get("geometry", {}).get("location", {})
                img_url = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800"
                if matched.get("photos"):
                    img_url = f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference={matched['photos'][0]['photo_reference']}&key={MAPS_API_KEY}"
                
                final_list.append({
                    "id": matched['place_id'],
                    "name": matched['name'],
                    "address": matched.get("vicinity", ""),
                    "rating": matched.get("rating", 4.0),
                    "imageUrl": img_url,
                    "latitude": geo.get("lat"),
                    "longitude": geo.get("lng"),
                    "ai_reason": r.get("ai_reason", "Alegere bazată pe profil"),
                    "type": matched.get("types", ["place"])[0],
                    "aiSuggestion": r.get("ai_reason") # Ensure both fields are set for adapter compatibility
                })
        return jsonify(final_list)
    except Exception as e:
        print(f"⚠️ Personalized AI Error: {e}")
        return jsonify([])

# ==================== GROUP COLLABORATION ENGINE ====================

@app.post("/groups/create")
def create_group():
    """Creates a new collaboration group."""
    data = request.get_json()
    name = data.get("name", "Grup Nou")
    creator_id = data.get("creator_id")
    
    if not creator_id:
        return jsonify({"error": "Missing creator_id"}), 400
        
    invite_code = ''.join(random.choices("ABCDEFGHJKLMNPQRSTUVWXYZ23456789", k=6))
    
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}", "Content-Type": "application/json"}
    group_data = {"name": name, "creator_id": creator_id, "invite_code": invite_code}
    
    try:
        res = requests.post(f"{SUPABASE_URL}/rest/v1/groups?select=*", headers=headers, json=group_data)
        if res.status_code == 201:
            group = res.json()[0]
            # Add creator as member
            requests.post(f"{SUPABASE_URL}/rest/v1/group_members", headers=headers, json={"group_id": group['id'], "user_id": creator_id})
            return jsonify(group)
        return jsonify({"error": "Failed to create group"}), res.status_code
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.post("/groups/join")
def join_group():
    data = request.get_json()
    code = data.get("invite_code")
    user_id = data.get("user_id")
    
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    try:
        g_res = requests.get(f"{SUPABASE_URL}/rest/v1/groups?invite_code=eq.{code}&select=*", headers=headers).json()
        if not g_res: return jsonify({"error": "Cod invalid"}), 404
        
        group = g_res[0]
        requests.post(f"{SUPABASE_URL}/rest/v1/group_members", headers=headers, json={"group_id": group['id'], "user_id": user_id})
        return jsonify(group)
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.post("/groups/propose")
def propose_activity():
    """Submit a proposal (activity or itinerary) to the group."""
    data = request.get_json()
    group_id = data.get("group_id")
    user_id = data.get("user_id")
    p_type = data.get("type", "activity") # activity or itinerary
    p_data = data.get("proposal_data") # JSON string or object
    location_name = data.get("location_name", "Locație propusă")
    
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}", "Content-Type": "application/json"}
    proposal = {
        "group_id": group_id,
        "user_id": user_id,
        "type": p_type,
        "data": p_data,
        "location_name": location_name,
        "votes": 0,
        "created_at": "now()"
    }
    
    try:
        res = requests.post(f"{SUPABASE_URL}/rest/v1/group_proposals", headers=headers, json=proposal)
        return jsonify({"status": "proposed"}), 201
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.get("/groups/<group_id>/proposals")
def get_proposals(group_id):
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    try:
        res = requests.get(f"{SUPABASE_URL}/rest/v1/group_proposals?group_id=eq.{group_id}&order=created_at.desc", headers=headers).json()
        return jsonify(res)
    except:
        return jsonify([])

@app.post("/groups/vote")
def vote_proposal():
    data = request.get_json()
    proposal_id = data.get("proposal_id")
    # Simple increment in logic or DB
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    try:
        p = requests.get(f"{SUPABASE_URL}/rest/v1/group_proposals?id=eq.{proposal_id}&select=votes", headers=headers).json()[0]
        new_votes = p['votes'] + 1
        requests.patch(f"{SUPABASE_URL}/rest/v1/group_proposals?id=eq.{proposal_id}", headers=headers, json={"votes": new_votes})
        return jsonify({"votes": new_votes})
    except:
        return jsonify({"error": "Failed to vote"}), 500

@app.post("/groups/memories")
def post_memory():
    """Post a memory or observation to the group wall."""
    data = request.get_json()
    group_id = data.get("group_id")
    user_id = data.get("user_id")
    content = data.get("content", "")
    image_url = data.get("image_url", "")
    
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}", "Content-Type": "application/json"}
    memory = {
        "group_id": group_id,
        "user_id": user_id,
        "content": content,
        "image_url": image_url,
        "created_at": "now()"
    }
    
    try:
        res = requests.post(f"{SUPABASE_URL}/rest/v1/group_memories", headers=headers, json=memory)
        return jsonify({"status": "memory_shared"}), 201
    except Exception as e:
        return jsonify({"error": str(e)}), 500
# ==================== AI ITINERARY OPTIMIZER ====================

@app.post("/itinerary/optimize")
def optimize_itinerary():
    """Reorders itinerary steps to minimize travel distance and respect time constraints."""
    data = request.get_json()
    steps = data.get("steps", [])
    if len(steps) < 2: return jsonify(steps)
    
    # 1. Separate Food from Non-Food
    food_steps = []
    other_steps = []
    for s in steps:
        cat = s.get("type", "").lower() or s.get("category", "").lower()
        if any(f in cat for f in ["rest", "cafe", "food", "mananc"]):
            food_steps.append(s)
        else:
            other_steps.append(s)
            
    # 2. Basic Greedy Distance Optimization
    optimized = []
    # Start with the first non-food step or the very first step
    current = other_steps.pop(0) if other_steps else food_steps.pop(0)
    optimized.append(current)
    
    while other_steps:
        # Find nearest from current
        from math import radians, cos, sin, asin, sqrt
        def dist(p1, p2):
            lat1, lon1 = radians(p1['latitude']), radians(p1['longitude'])
            lat2, lon2 = radians(p2['latitude']), radians(p2['longitude'])
            return 2 * 6371 * asin(sqrt(sin((lat2-lat1)/2)**2 + cos(lat1)*cos(lat2)*sin((lon2-lon1)/2)**2))

        nearest_idx = 0
        min_d = 99999
        for i, candidate in enumerate(other_steps):
            d = dist(current, candidate)
            if d < min_d:
                min_d = d
                nearest_idx = i
        
        current = other_steps.pop(nearest_idx)
        optimized.append(current)
        
        # Insert a food break if we have enough steps and it's 'lunch time' (middle of day)
        if len(optimized) == len(steps) // 2 and food_steps:
            optimized.append(food_steps.pop(0))

    # Add remaining food steps at the end
    optimized.extend(food_steps)
    
    return jsonify(optimized)

# ==================== SMART COLLECTIONS ENGINE ====================

@app.post("/collections/create")
def create_collection():
    """Creates a new themed collection."""
    data = request.get_json()
    user_id = data.get("user_id")
    name = data.get("name", "Colecție Nouă")
    desc = data.get("description", "")
    is_public = data.get("is_public", True)
    
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}", "Content-Type": "application/json"}
    col_data = {"user_id": user_id, "name": name, "description": desc, "is_public": is_public}
    
    try:
        res = requests.post(f"{SUPABASE_URL}/rest/v1/collections?select=*", headers=headers, json=col_data)
        return jsonify(res.json()[0])
    except:
        return jsonify({"error": "Failed to create collection"}), 500

@app.post("/collections/add")
def add_to_collection():
    """Adds a place to a collection."""
    data = request.get_json()
    col_id = data.get("collection_id")
    place_id = data.get("place_id")
    name = data.get("name")
    address = data.get("address")
    img = data.get("image_url")
    
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}", "Content-Type": "application/json"}
    item = {"collection_id": col_id, "place_id": place_id, "name": name, "address": address, "image_url": img}
    
    try:
        requests.post(f"{SUPABASE_URL}/rest/v1/collection_places", headers=headers, json=item)
        return jsonify({"status": "added"})
    except:
        return jsonify({"error": "Failed to add to collection"}), 500

@app.get("/collections/user/<user_id>")
def get_user_collections(user_id):
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    res = requests.get(f"{SUPABASE_URL}/rest/v1/collections?user_id=eq.{user_id}", headers=headers).json()
    return jsonify(res)

@app.get("/collections/public")
def get_public_collections():
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    res = requests.get(f"{SUPABASE_URL}/rest/v1/collections?is_public=eq.true&limit=20", headers=headers).json()
    return jsonify(res)

@app.get("/collections/<col_id>/places")
def get_collection_places(col_id):
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    res = requests.get(f"{SUPABASE_URL}/rest/v1/collection_places?collection_id=eq.{col_id}", headers=headers).json()
    return jsonify(res)

# ==================== GROUP SYNERGY RECOMMENDATIONS ====================

@app.get("/recommendations/group/<group_id>")
def get_group_recommendations(group_id):
    """Generates recommendations that satisfy all members of a group."""
    lat = request.args.get("lat")
    lng = request.args.get("lng")
    
    if not lat or not lng: return jsonify({"error": "Missing location"}), 400
    
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    
    try:
        # 1. Fetch all members and their profiles
        m_res = requests.get(f"{SUPABASE_URL}/rest/v1/group_members?group_id=eq.{group_id}&select=user_id", headers=headers).json()
        member_ids = [m['user_id'] for m in m_res]
        
        if not member_ids: return jsonify([])
        
        profiles = []
        for uid in member_ids:
            p = requests.get(f"{SUPABASE_URL}/rest/v1/user_profiles?id=eq.{uid}&select=*", headers=headers).json()
            if p: profiles.append(p[0])
            
        # 2. Extract collective interests
        all_interests = []
        for p in profiles:
            ints = [i.strip().lower() for i in p.get("interests", "").split(",") if i.strip()]
            all_interests.extend(ints)
            
        group_context = f"Grup de {len(profiles)} persoane. Interese combinate: {', '.join(set(all_interests))}."
        
        # 3. Fetch candidates
        candidates = []
        seen_ids = set()
        for p_type in ["tourist_attraction", "museum", "park", "restaurant", "cafe"]:
            res = google_nearby_search(float(lat), float(lng), p_type, radius=10000)
            for c in res[:8]:
                if c['place_id'] not in seen_ids:
                    candidates.append(c)
                    seen_ids.add(c['place_id'])
                    
        # 4. AI Orchestration (Gemini)
        import google.generativeai as genai
        model = genai.GenerativeModel('gemini-flash-latest')
        
        subset = [{"id": c['place_id'], "name": c['name'], "types": c.get('types', [])} for c in candidates[:20]]
        
        prompt = (
            f"Ești expertul 'CityScape Synergy'. Primești un grup cu aceste interese: {group_context}. "
            f"Din lista de locații: {json.dumps(subset)}, alege 5 care reprezintă CEL MAI BUN COMPROMIS. "
            "Dacă unul vrea artă și altul vrea mâncare, caută o 'cafenea-galerie' sau un muzeu cu restaurant de top. "
            "RĂSPUNS: Un JSON valid (listă de obiecte): "
            '[{"place_id": "...", "synergy_score": "8/10", "reason": "De ce e bine pentru TOȚI (max 12 cuvinte)"}]'
        )
        
        response = model.generate_content(prompt)
        text = response.text
        if "```json" in text: text = text.split("```json")[1].split("```")[0].strip()
        elif "```" in text: text = text.split("```")[1].split("```")[0].strip()
        
        recs = json.loads(text)
        final_list = []
        for r in recs:
            matched = next((c for c in candidates if c['place_id'] == r.get('place_id')), None)
            if matched:
                geo = matched.get("geometry", {}).get("location", {})
                img_url = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800"
                if matched.get("photos"):
                    img_url = f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference={matched['photos'][0]['photo_reference']}&key={MAPS_API_KEY}"
                
                final_list.append({
                    "id": matched['place_id'],
                    "name": matched['name'],
                    "address": matched.get("vicinity", ""),
                    "imageUrl": img_url,
                    "synergy_score": r.get("synergy_score"),
                    "reason": r.get("reason"),
                    "latitude": geo.get("lat"),
                    "longitude": geo.get("lng")
                })
        return jsonify(final_list)
    except Exception as e:
        print(f"⚠️ Group Synergy Error: {e}")
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5001))
    debug = os.environ.get('FLASK_ENV', 'production') != 'production'
    app.run(host='0.0.0.0', port=port, debug=debug)
