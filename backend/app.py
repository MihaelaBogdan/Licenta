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
    
    from chatbot import get_response_with_rag
    result = get_response_with_rag(text, user_id, lat, lng, language)
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
    
    from chatbot import get_response_with_rag
    result = get_response_with_rag(text, user_id, lat, lng, language)
    return jsonify(result)

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
    "shopping": "17114"
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
    """Searches Google Places Text Search API for high-relevance matches."""
    url = "https://maps.googleapis.com/maps/api/place/textsearch/json"
    params = {
        "query": query,
        "key": MAPS_API_KEY,
        "language": "ro"
    }
    if lat and lng:
        params["location"] = f"{lat},{lng}"
        params["radius"] = str(radius)
    try:
        res = requests.get(url, params=params, timeout=15).json()
        return res.get("results", [])
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
        "address": place.get("vicinity", "București") + dist_str,
        "rating": place.get("rating", 4.2),
        "imageUrl": img_url,
        "latitude": geo.get("lat"),
        "longitude": geo.get("lng"),
        "type": (types[0] if types else "").replace("_", " ").capitalize(),
        "reviewCount": place.get("user_ratings_total", 0),
        "relevance_score": 0 
    }

# ==================== PERSONALIZATION ENGINE ====================

def get_user_context(user_id):
    """Fetches user interests and history from Supabase to provide context for recommendations."""
    if not user_id:
        return {"interests": [], "history_weights": {}}
        
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    
    # 1. Fetch interests from user profile
    # Table name might be 'users' based on User.java @Entity
    user_url = f"{SUPABASE_URL}/rest/v1/users?id=eq.{user_id}&select=interests"
    interests = []
    try:
        res = requests.get(user_url, headers=headers)
        if res.status_code == 200 and res.json():
            interests_str = res.json()[0].get("interests", "")
            interests = [i.strip().lower() for i in interests_str.split(",") if i.strip()]
    except: pass
    
    # 2. Fetch history to detect preferred categories
    history_weights = {}
    history_url = f"{SUPABASE_URL}/rest/v1/visited_places?user_id=eq.{user_id}&select=place_type"
    try:
        res = requests.get(history_url, headers=headers)
        if res.status_code == 200:
            for visit in res.json():
                ptype = visit.get("place_type", "").lower()
                if ptype:
                    history_weights[ptype] = history_weights.get(ptype, 0) + 1
    except: pass
    
    return {"interests": interests, "history_weights": history_weights}

def score_item(item, context):
    """Calculates a compatibility score (0-100) based on title, type, and user context."""
    score = 0
    title = item.get("name", item.get("title", "")).lower()
    item_type = item.get("type", item.get("category", "")).lower()
    
    # 1. Interest Matching (High priority)
    for interest in context["interests"]:
        if interest in title or interest in item_type:
            score += 25
            
    # 2. History Matching (Reflects habits)
    history_weight = context["history_weights"].get(item_type, 0)
    score += min(history_weight * 5, 30) # Max 30 points for habits
    
    # 3. Quality Factor
    rating = float(item.get("rating", 4.0))
    score += (rating - 3.0) * 5 # E.g. 5.0 rating gives 10 points
    
    return int(min(score, 100))

# --------------------------------

@app.get("/nearby")
def get_nearby_realtime():
    """Nearby places within 2km with personalization."""
    lat = request.args.get("lat")
    lng = request.args.get("lng")
    user_id = request.args.get("user_id")
    if not lat or not lng:
        return jsonify({"error": "Missing coordinates"}), 400
        
    place_type = request.args.get("type", "tourist_attraction")
    
    # Translate custom types to Google/Foursquare standard types
    type_map = {
        "culture": "museum",
        "shopping": "shopping_mall",
        "mixed": None,
        "tourist_attraction": None,
        "All": None
    }
    actual_type = type_map.get(place_type, place_type)
        
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
        for p in formatted:
            p["relevance_score"] = score_item(p, context)
        formatted.sort(key=lambda x: x["relevance_score"], reverse=True)
            
    return jsonify(formatted[:15])

@app.get("/places/search")
def search_places():
    """Trending / Discovery search with personalization."""
    lat = request.args.get("lat")
    lng = request.args.get("lng")
    user_id = request.args.get("user_id")
    place_type = request.args.get("type", "tourist_attraction")

    if not lat or not lng:
        return jsonify({"error": "Missing coordinates"}), 400

    place_type = request.args.get("type", "tourist_attraction")
    
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
    if not actual_type or actual_type == "":
        # 1. Start with high-relevance Text Search for "atractii" and "restaurante"
        results.extend(google_text_search("Top atracții turistice și obiective", lat, lng, radius=radius))
        results.extend(google_text_search("Restaurante faimoase și cafenele populare", lat, lng, radius=radius))
        
        # 2. Add some specific categories too for variety
        categories = ["museum", "park"]
        for cat in categories:
            results.extend(google_nearby_search(lat, lng, cat, radius=radius))
    else:
        results = google_nearby_search(lat, lng, actual_type, radius=radius)

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
        for p in formatted:
            # Refresh feel: add random jitter to score
            jitter = random.uniform(0, 2.0)
            p["relevance_score"] = score_item(p, context) + jitter
        formatted.sort(key=lambda x: (x["relevance_score"], x["rating"]), reverse=True)
    else:
        for p in formatted:
            p["jitter_score"] = p.get("rating", 0) + random.uniform(0, 1.0)
        formatted.sort(key=lambda x: x["jitter_score"], reverse=True)
        
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


@app.get("/weather")
def get_weather():
    lat = request.args.get("lat")
    lng = request.args.get("lng")
    if not lat or not lng:
        return jsonify({"error": "Missing coordinates"}), 400
    
    url = f"https://api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lng}&appid={OPENWEATHER_API_KEY}&units=metric"
    try:
        response = requests.get(url)
        data = response.json()
        if response.status_code == 200:
            return jsonify({
                "temp": data["main"]["temp"],
                "condition": data["weather"][0]["main"],
                "description": data["weather"][0]["description"],
                "icon": data["weather"][0]["icon"]
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
        url = f"{SUPABASE_URL}/rest/v1/feed_posts?select=*&order=created_at.desc&limit=30"
    
    try:
        res = requests.get(url, headers=headers)
        if res.status_code != 200:
            return jsonify([])
            
        posts = res.json()
        for post in posts:
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

@app.post("/feed")
def create_post():
    """Creates a new feed post with UUID validation."""
    data = request.get_json()
    
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
    
    comment_data = {
        "post_id": post_id,
        "user_id": data.get("user_id"),
        "user_name": data.get("user_name", "Explorer"),
        "comment_text": data.get("comment_text", "")
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
    
    # Tier 1: Global Ticketmaster (Concerts, Sports)
    events = fetch_ticketmaster_events(lat, lng, radius)
    
    # Tier 2: Local Scraped (Specialty Bucharest)
    # 50km radius check roughly... (each degree lat is 111km)
    dist_to_bucu = ((float(lat) - 44.4268)**2 + (float(lng) - 26.1025)**2)**0.5
    if dist_to_bucu < (radius / 100.0): # 50km ~ 0.5 deg
        headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
        url = f"{SUPABASE_URL}/rest/v1/scraped_events?order=created_at.desc&limit=30"
        try:
            res = requests.get(url, headers=headers)
            if res.status_code == 200:
                events.extend(res.json())
        except: pass

    # Tier 3: Universal Fallback (Foursquare Trending)
    # If we have very few events, fill the gaps with popular places
    if len(events) < 5:
        trending = fetch_foursquare_trending(lat, lng)
        events.extend(trending)

    # Personalization & Deduplication
    seen_titles = set()
    unique_events = []
    interest_list = [i.strip() for i in interests.split(",") if i.strip()] if interests else []
    
    for event in events:
        title = event.get('title')
        if not title or title in seen_titles: continue
        seen_titles.add(title)
        
        # Scoring
        score = 0
        title_lower = title.lower()
        cat_lower = event.get('category', '').lower()
        for interest in interest_list:
            if interest in title_lower or interest in cat_lower:
                score += 30
        
        # Source priority: iabilet > ticketmaster > foursquare
        source_score = 0
        if event.get('source') == 'iabilet': source_score = 10
        elif event.get('source') == 'ticketmaster': source_score = 5
        
        event['relevance_score'] = score + source_score
        unique_events.append(event)
    
    unique_events.sort(key=lambda x: x.get('relevance_score', 0), reverse=True)
    return jsonify(unique_events[:40])

@app.get("/users/search")
def search_users():
    query = request.args.get("query", "")
    current_user_id = request.args.get("current_user_id", "")
    
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}"
    }
    
    # Search for users by name or email
    url = f"{SUPABASE_URL}/rest/v1/user_profiles?or=(name.ilike.*{query}*,email.ilike.*{query}*)&limit=20"
    
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

    if not lat_str or not lng_str:
        return jsonify({"error": "Missing location"}), 400

    lat = float(lat_str)
    lng = float(lng_str)
    
    # 1. Fetch some real places using google_nearby_search (multiple categories for diversity)
    candidates = []
    seen_ids = set()
    for p_type in ["tourist_attraction", "museum", "park", "restaurant", "cafe"]:
        res = google_nearby_search(lat, lng, p_type, radius=15000)
        random.shuffle(res)
        for c in res[:5]:
            if c['place_id'] not in seen_ids:
                candidates.append(c)
                seen_ids.add(c['place_id'])
    
    if not candidates:
        return jsonify({"error": "No candidates found"}), 404

    random.shuffle(candidates)
    subset = candidates[:15] # Give Gemini a good list to pick from

    # 2. Use Gemini to pick the BEST one and generate activities
    import google.generativeai as genai
    from os import getenv
    model = genai.GenerativeModel('gemini-flash-latest')
    
    candidates_data = [{"id": c['place_id'], "name": c['name'], "types": c.get('types', []), "rating": c.get('rating', 0)} for c in subset]
    
    prompt = (
        f"Ești expertul 'MysticMinds' din CityScape. Din următoarele locații reale dintr-un oraș: {json.dumps(candidates_data)}, "
        "alege EXACT UNA care ar fi cea mai interesantă recomandare de tip 'Destin' pentru un utilizator acum. "
        "Pentru această locație, gândește-te la 3 activități specifice și creative pe care utilizatorul le poate face acolo. "
        "RĂSPUNS: Un obiect JSON (fără explicații) cu formatul: "
        '{"place_id": "...", "name": "...", "reason": "De ce am ales asta (max 20 cuvinte)", '
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
        # Final fallback - pick first candidate
        c = subset[0]
        geo = c.get("geometry", {}).get("location", {})
        return jsonify({
            "place_id": c['place_id'],
            "name": c['name'],
            "reason": "O destinație plină de surprize pe care trebuie să o descoperi!",
            "activities": ["Explorează arhitectura", "Fă fotografii", "Relaxează-te"],
            "latitude": geo.get("lat"),
            "longitude": geo.get("lng"),
            "imageUrl": "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800"
        })

if __name__ == "__main__":
    app.run(host='0.0.0.0', port=5000, debug=True)
