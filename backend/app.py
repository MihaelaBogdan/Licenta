import sys
import os

# Force Python to find its local modules (chatbot.py)
current_dir = os.path.dirname(os.path.abspath(__file__))
if current_dir not in sys.path:
    sys.path.append(current_dir)

# Add user's site-packages for Torch/Flask
user_site = '/Users/mihaela/Library/Python/3.9/lib/python/site-packages'
if user_site not in sys.path:
    sys.path.append(user_site)

from flask import Flask, render_template, request, jsonify
from flask_cors import CORS
from chatbot import get_response, get_response_with_details
import json
import os
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
    "historic": "16026"
}

def google_nearby_search(lat, lng, place_type, radius=5000, keyword=None):
    """Searches Google Places API for nearby places."""
    url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
    params = {
        "location": f"{lat},{lng}",
        "radius": str(radius),
        "type": place_type,
        "key": MAPS_API_KEY,
        "language": "ro"
    }
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

    return {
        "id": place.get("place_id", ""),
        "name": place.get("name", "Locație"),
        "address": place.get("vicinity", "București") + dist_str,
        "rating": place.get("rating", 4.2),
        "imageUrl": img_url,
        "latitude": geo.get("lat"),
        "longitude": geo.get("lng"),
        "type": (place.get("types", [""])[0] or "").replace("_", " ").capitalize()
    }

# --------------------------------

@app.get("/nearby")
def get_nearby_realtime():
    """Nearby places within 2km using Google Places API."""
    lat = request.args.get("lat")
    lng = request.args.get("lng")
    place_type = request.args.get("type", "restaurant")

    if not lat or not lng:
        return jsonify({"error": "Missing coordinates"}), 400

    print(f"🔍 [GET /nearby] Google Places 2KM for {place_type} at {lat},{lng}")
    results = google_nearby_search(lat, lng, place_type, radius=2000)
    formatted = [format_google_place(p, lat, lng) for p in results if p.get("name")]
    return jsonify(formatted[:15])

@app.get("/places/search")
def search_places():
    """Trending / Discovery search using Google Places API (wider radius)."""
    lat = request.args.get("lat")
    lng = request.args.get("lng")
    place_type = request.args.get("type", "restaurant")

    if not lat or not lng:
        return jsonify({"error": "Missing coordinates"}), 400

    print(f"📈 [GET /places/search] Google Places 15KM for {place_type} at {lat},{lng}")
    results = google_nearby_search(lat, lng, place_type, radius=15000)
    # Sort by rating for trending
    results.sort(key=lambda x: x.get("rating", 0), reverse=True)
    formatted = [format_google_place(p, lat, lng) for p in results if p.get("name")]
    return jsonify(formatted[:25])

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
    """Generates a day plan using Google Places API."""
    lat_str = request.args.get("lat")
    lng_str = request.args.get("lng")
    if not lat_str or not lng_str:
        return jsonify([])

    lat = float(lat_str)
    lng = float(lng_str)
    style = request.args.get("type", "exploration").lower()
    duration = int(request.args.get("duration", 6))
    points_count = int(request.args.get("points", 4))

    import random
    from datetime import datetime, timedelta

    # Patterns for different styles
    if "cultural" in style:
        pattern = [
            {"type": "cafe", "label": "Mic Dejun"},
            {"type": "museum", "label": "Activitate Culturală"},
            {"type": "restaurant", "label": "Prânz"},
            {"type": "museum", "label": "Explorare Istorică"},
            {"type": "cafe", "label": "Pauză de Ceai"},
            {"type": "museum", "label": "Muzeu Seară"}
        ]
    elif "relax" in style:
        pattern = [
            {"type": "cafe", "label": "Mic Dejun"},
            {"type": "park", "label": "Moment de Relaxare"},
            {"type": "restaurant", "label": "Prânz"},
            {"type": "park", "label": "Plimbare Liniștită"},
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
    elif "night" in style or "nocturn" in style:
        pattern = [
            {"type": "restaurant", "label": "Cină Elegantă"},
            {"type": "bar", "label": "Distracție de Noapte"},
            {"type": "night_club", "label": "Cocktail Bar"},
            {"type": "bar", "label": "After-party"},
            {"type": "night_club", "label": "Clubbing"},
            {"type": "bakery", "label": "Mic Dejun de Dimineață"}
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
    # Add jitter so each request starts from a slightly different spot
    current_lat = lat + random.uniform(-0.008, 0.008)
    current_lng = lng + random.uniform(-0.008, 0.008)
    current_time = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)
    slot_duration_mins = (duration * 60) // len(slots) if slots else 60

    used_place_ids = set()  # Prevent duplicates across slots

    for slot in slots:
        try:
            results = google_nearby_search(current_lat, current_lng, slot["type"], radius=5000)
            # Shuffle and filter out already-used places
            random.shuffle(results)
            available = [r for r in results if r.get("place_id") not in used_place_ids and r.get("name")]
            if not available:
                available = results
            if available:
                best = available[0]  # Already shuffled, so first = random
                used_place_ids.add(best.get("place_id"))
                
                img_url = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800"
                if best.get("photos"):
                    ref = best["photos"][0]["photo_reference"]
                    img_url = f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference={ref}&key={MAPS_API_KEY}"

                level = best.get("price_level", 2)
                price_map = {0: 0, 1: 30, 2: 70, 3: 150, 4: 350}
                est_cost = price_map.get(level, 70)
                if slot["type"] == "park": est_cost = 0
                if slot["type"] == "museum": est_cost = 30

                t_start = current_time.strftime("%H:%M")
                current_time += timedelta(minutes=slot_duration_mins)
                t_end = current_time.strftime("%H:%M")

                geo = best.get("geometry", {}).get("location", {})
                plan.append({
                    "slot": slot["label"],
                    "name": best["name"],
                    "address": best.get("vicinity", "București"),
                    "imageUrl": img_url,
                    "latitude": geo.get("lat"),
                    "longitude": geo.get("lng"),
                    "estimatedCost": est_cost,
                    "placeId": best.get("place_id"),
                    "type": slot["type"],
                    "time": f"{t_start} - {t_end}",
                    "is_open": True
                })
                current_lat = geo.get("lat", current_lat)
                current_lng = geo.get("lng", current_lng)
        except Exception as e:
            print(f"⚠️ Itinerary Error: {e}")
            continue

    return jsonify(plan)

# ==================== SOCIAL FEED ====================

@app.get("/feed")
def get_feed():
    """Returns all posts for the social feed, sorted by newest first."""
    user_id = request.args.get("user_id", "")
    
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}"
    }
    
    # Logic: Get posts and enrich with interactions
    url = f"{SUPABASE_URL}/rest/v1/feed_posts?select=*&order=created_at.desc&limit=30"
    
    try:
        res = requests.get(url, headers=headers)
        if res.status_code != 200:
            return jsonify([])
            
        posts = res.json()
        for post in posts:
            p_id = post.get("id")
            # Get comment count
            c_res = requests.get(f"{SUPABASE_URL}/rest/v1/feed_comments?post_id=eq.{p_id}&select=id", headers=headers)
            post["comments_count"] = len(c_res.json()) if c_res.status_code == 200 else 0
            # Get likes and user participation
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

    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
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
        print(f"❌ Supabase Insert Error: {res.text}")
        return jsonify({"error": "Failed to create post"}), 500
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

if __name__ == "__main__":
    app.run(host='0.0.0.0', port=5000, debug=True)
