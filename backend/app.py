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
load_dotenv()

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
SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_KEY")
OPENWEATHER_API_KEY = os.getenv("OPENWEATHER_API_KEY")
PREDICTHQ_API_KEY = os.getenv("PREDICTHQ_API_KEY")
TICKETMASTER_API_KEY = os.getenv("TICKETMASTER_API_KEY")

def get_fallback_events():
    return [
        {"title": "Concert Live: Rock în Parc", "location": "Parcul Central", "time": "Mâine 19:00", "imageUrl": "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800", "url": "https://google.com"},
        {"title": "Festival Gastronomic", "location": "Piața Victoriei", "time": "Azi 12:00", "imageUrl": "https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=800", "url": "https://google.com"},
        {"title": "Expoziție de Artă", "location": "Muzeul Național", "time": "Zilnic 10:00", "imageUrl": "https://images.unsplash.com/photo-1518998053502-53cc8de431d8?w=800", "url": "https://google.com"}
    ]

def get_fallback_nearby(lat, lng):
    return [
        {"id": "fb1", "name": "Grădina Eden", "address": "Calea Victoriei 107", "rating": 4.5, "imageUrl": "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800", "latitude": float(lat), "longitude": float(lng), "type": "Grădină"},
        {"id": "fb2", "name": "The Artist", "address": "Calea Victoriei 147", "rating": 4.9, "imageUrl": "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=800", "latitude": float(lat)+0.001, "longitude": float(lng)+0.001, "type": "Restaurant"},
        {"id": "fb3", "name": "M60 Cafe", "address": "Strada Mendeleev 2", "rating": 4.7, "imageUrl": "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=800", "latitude": float(lat)-0.001, "longitude": float(lng)-0.001, "type": "Cafenea"}
    ]

# --------------------------------

@app.get("/events")
def get_events():
    lat = request.args.get("lat")
    lng = request.args.get("lng")
    interests = request.args.get("interests", "")
    if not lat or not lng:
        return jsonify({"error": "Missing coordinates"}), 400

    print(f"🎭 Fetching Ticketmaster events near: {lat}, {lng}. Interests: {interests}")

    # Map user interests to Ticketmaster keywords/segments dynamically
    keyword = ""
    if interests:
        mapping = []
        interests_lower = interests.lower()
        if "muzic" in interests_lower or "music" in interests_lower or "concert" in interests_lower: mapping.append("Music")
        if "arta" in interests_lower or "art" in interests_lower or "cultur" in interests_lower: mapping.append("Arts")
        if "sport" in interests_lower: mapping.append("Sports")
        if "film" in interests_lower: mapping.append("Film")
        
        if mapping:
            keyword = mapping[0] # Pick primary mapped interest

    # --- PREMIUM TICKETMASTER STRATEGY ---
    url = "https://app.ticketmaster.com/discovery/v2/events.json"
    
    # Discovery segments
    music_seg = "KZFzniwnSyZfZ7v7n1"
    arts_seg = "KZFzniwnSyZfZ7v7na"
    sports_seg = "KZFzniwnSyZfZ7v7nE"

    # 1. Build optimized params
    params = {
        "apikey": TICKETMASTER_API_KEY,
        "latlong": f"{lat},{lng}",
        "radius": "300",
        "unit": "km",
        "countryCode": "RO",
        "sort": "relevance,desc", # Relevance first for better quality
        "size": 30
    }
    
    # Multi-category discovery if interests are missing
    if not interests:
        params["segmentId"] = f"{music_seg},{arts_seg},{sports_seg}"
    else:
        if keyword: params["keyword"] = keyword

    try:
        response = requests.get(url, params=params)
        data = response.json()
        
        # --- 1. PREMIUM MANUAL DISCOVERY (Bucharest 2026) ---
        premium_events = [
            {"title": "Metallica: M72 World Tour", "location": "National Arena, București", "time": "2026-05-13", "imageUrl": "https://images.unsplash.com/photo-1540039155733-5bb30b53aa14?w=800", "url": "https://www.metallica.com", "lat": 44.4372, "lng": 26.1511},
            {"title": "Iron Maiden: Run for Your Lives", "location": "National Arena, București", "time": "2026-05-28", "imageUrl": "https://images.unsplash.com/photo-1493225255756-d9584f8606e9?w=800", "url": "https://www.ironmaiden.com", "lat": 44.4372, "lng": 26.1511},
            {"title": "Summer Well Festival", "location": "Domeniul Știrbey, Buftea", "time": "2026-08-07", "imageUrl": "https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?w=800", "url": "https://summerwell.ro", "lat": 44.5644, "lng": 25.9522},
            {"title": "Saga Festival 2026", "location": "Romaero, București", "time": "2026-06-25", "imageUrl": "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800", "url": "https://sagafestival.com", "lat": 44.4936, "lng": 26.1039},
            {"title": "Eros Ramazzotti Live", "location": "Laminor Arena, București", "time": "2026-04-22", "imageUrl": "https://images.unsplash.com/photo-1501612780327-45045538702b?w=800", "url": "https://ramazzotti.com", "lat": 44.4172, "lng": 26.1755},
            {"title": "Skillet: Dominion Tour", "location": "Arenele Romane, București", "time": "2026-05-12", "imageUrl": "https://images.unsplash.com/photo-1459749411177-042180ce673c?w=800", "url": "https://skillet.com", "lat": 44.4116, "lng": 26.0956},
            {"title": "Neversea Kapital", "location": "National Arena, București", "time": "2026-07-03", "imageUrl": "https://images.unsplash.com/photo-1516280440614-37939bbacd81?w=800", "url": "https://neversea.com", "lat": 44.4372, "lng": 26.1511},
            {"title": "George Enescu Festival", "location": "Sala Palatului, București", "time": "2026-08-23", "imageUrl": "https://images.unsplash.com/photo-1507838153414-b4b713384a76?w=800", "url": "https://festivalenescu.ro", "lat": 44.4398, "lng": 26.0958},
            {"title": "Russel Crowe: Indoor Garden Party", "location": "Sala Palatului, București", "time": "2026-07-04", "imageUrl": "https://images.unsplash.com/photo-1521334885634-95b42c053b01?w=800", "url": "https://bilete.ro", "lat": 44.4398, "lng": 26.0958}
        ]

        formatted = []
        
        # Add premium events if in Bucharest (lat/lng approx)
        try:
            if 44.3 <= float(lat) <= 44.6 and 25.9 <= float(lng) <= 26.3:
                for pe in premium_events:
                    formatted.append({
                        "title": pe["title"],
                        "location": pe["location"],
                        "time": pe["time"],
                        "imageUrl": pe["imageUrl"],
                        "url": pe["url"],
                        "latitude": pe["lat"],
                        "longitude": pe["lng"],
                        "source": "Local Discovery"
                    })
        except: pass

        # --- 2. TICKETMASTER DISCOVERY ---
        tm_events = data.get("_embedded", {}).get("events", [])
        for e in tm_events:
            images = sorted(e.get("images", []), key=lambda i: i.get('width', 0), reverse=True)
            img_url = images[0]["url"] if images else "https://images.unsplash.com/photo-1501281668745-f7f57925c3b4?w=800"
            venues = e.get("_embedded", {}).get("venues", [])
            venue_name = venues[0].get("name", "Locație") if venues else "România"
            display_location = f"{venue_name}, {venues[0].get('city', {}).get('name', '')}".strip(", ")
            ev_lat = float(venues[0]["location"].get("latitude", 0.0)) if venues and venues[0].get("location") else 0.0
            ev_lng = float(venues[0]["location"].get("longitude", 0.0)) if venues and venues[0].get("location") else 0.0

            formatted.append({
                "title": e.get("name", "Premium Event"),
                "location": display_location,
                "time": e.get("dates", {}).get("start", {}).get("localDate", "Vom anunța"),
                "imageUrl": img_url,
                "url": e.get("url", ""),
                "latitude": ev_lat,
                "longitude": ev_lng,
                "source": "Ticketmaster"
            })

        # --- 3. SEATGEEK DISCOVERY (Global Fallback) ---
        if len(formatted) < 15:
            try:
                sg_url = f"https://api.seatgeek.com/2/events?lat={lat}&lon={lng}&range=50mi&client_id=MzExNjEwMzh8MTY3MTkxNjY0Mi4zOTY3NTg5"
                sg_res = requests.get(sg_url).json()
                for sg_e in sg_res.get("events", []):
                    img_url = "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=800"
                    if sg_e.get("performers"):
                        img_url = sg_e["performers"][0].get("image", img_url)
                    
                    formatted.append({
                        "title": sg_e.get("short_title", sg_e.get("title", "Event")),
                        "location": sg_e.get("venue", {}).get("name", "Venue"),
                        "time": sg_e.get("datetime_local", "").split("T")[0],
                        "imageUrl": img_url,
                        "url": sg_e.get("url", ""),
                        "latitude": sg_e.get("venue", {}).get("location", {}).get("lat", 0.0),
                        "longitude": sg_e.get("venue", {}).get("location", {}).get("lon", 0.0),
                        "source": "SeatGeek"
                    })
            except: pass

        # --- 4. PREDICTHQ (Premium Events & Festivals) ---
        if PREDICTHQ_API_KEY and len(formatted) < 25:
            try:
                phq_url = "https://api.predicthq.com/v1/events/"
                ph_headers = {"Authorization": f"Bearer {PREDICTHQ_API_KEY}", "Accept": "application/json"}
                ph_params = {
                    "location_around.origin": f"{lat},{lng}",
                    "location_around.scale": "10km",
                    "category": "festivals,concerts,sports",
                    "limit": 10
                }
                ph_res = requests.get(phq_url, headers=ph_headers, params=ph_params).json()
                for ph_e in ph_res.get("results", []):
                    formatted.append({
                        "title": ph_e.get("title", "Premium Event"),
                        "location": ph_e.get("location", [0,0]), # PHQ returns [lng, lat] usually or has a dedicated field
                        "time": ph_e.get("start", "").split("T")[0],
                        "imageUrl": "https://images.unsplash.com/photo-1514525253361-bee8a816d9db?w=800",
                        "url": "https://www.predicthq.com",
                        "latitude": ph_e.get("location", [0,0])[1],
                        "longitude": ph_e.get("location", [0,0])[0],
                        "source": "PredictHQ"
                    })
            except: pass

        return jsonify(formatted[:40])

    except Exception as ex:
        print(f"⚠️ Event Discovery Failure: {ex}")
        return jsonify([])

@app.get("/nearby")
def get_nearby_realtime():
    """Fetches real-time recommendations from Google Places based on user coordinates."""
    lat = request.args.get("lat")
    lng = request.args.get("lng")
    place_type = request.args.get("type", "restaurant")
    
    if not lat or not lng:
        return jsonify({"error": "Missing coordinates"}), 400

    if not MAPS_API_KEY or MAPS_API_KEY == "None":
        print(f"⚠️ [GET /nearby] No Google API Key. Returning 3 fallback places.")
        return jsonify(get_fallback_nearby(lat, lng))

    print(f"🔍 [GET /nearby] Searching via Google Maps for {lat}, {lng}")

    types_to_fetch = [place_type]
    if place_type == "mixed" or place_type == "tourist_attraction":
        types_to_fetch = ["restaurant", "cafe", "park", "museum", "tourist_attraction"]

    all_results = []
    seen_ids = set()

    for t in types_to_fetch:
        url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
        params = {
            "location": f"{lat},{lng}",
            "radius": "5000",
            "type": t,
            "key": MAPS_API_KEY
        }
        
        try:
            response = requests.get(url, params=params)
            data = response.json()
            
            if data.get("status") != "OK" and data.get("status") != "ZERO_RESULTS":
                print(f"❌ Google API Error for type {t}: {data.get('status')} - {data.get('error_message', 'No message')}")
            
            results = data.get("results", [])
            print(f"📍 Found {len(results)} results for type {t}")
            for p in results:
                p_id = p.get("place_id")
                if p_id not in seen_ids:
                    p["detected_type"] = t.replace("_", " ").capitalize()
                    all_results.append(p)
                    seen_ids.add(p_id)
        except Exception as e:
            print(f"⚠️ Error fetching type {t}: {e}")

    formatted = []
    for p in all_results:
        photo_ref = p.get("photos", [{}])[0].get("photo_reference", "")
        img_url = f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference={photo_ref}&key={MAPS_API_KEY}" if photo_ref else "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=800&q=80"
        
        formatted.append({
            "id": p.get("place_id"),
            "name": p.get("name"),
            "address": p.get("vicinity"),
            "rating": p.get("rating", 0.0),
            "imageUrl": img_url,
            "latitude": p.get("geometry", {}).get("location", {}).get("lat"),
            "longitude": p.get("geometry", {}).get("location", {}).get("lng"),
            "type": p.get("detected_type", place_type.capitalize())
        })
    
    formatted.sort(key=lambda x: x.get('rating', 0), reverse=True)
    
    if not formatted:
        print(f"⚠️ [GET /nearby] Google returned ZERO results for {lat}, {lng}. Returning fallbacks.")
        return jsonify(get_fallback_nearby(lat, lng))

    return jsonify(formatted[:40])

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
        # Fallback images for Supabase places that might have broken links
        for p in places:
            if not p.get("imageUrl") or "placeholder" in p.get("imageUrl"):
                p["imageUrl"] = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=800&q=80"
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
                "condition": data["weather"][0]["main"], # Rain, Clouds, Clear
                "description": data["weather"][0]["description"],
                "icon": data["weather"][0]["icon"]
            })
        return jsonify({"error": "Weather fetch failed"}), response.status_code
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.get("/itinerary")
def get_itinerary():
    if not MAPS_API_KEY:
        return jsonify([])

    lat = float(request.args.get("lat"))
    lng = float(request.args.get("lng"))
    style = request.args.get("type", "exploration").lower()
    budget = int(request.args.get("budget", 250))
    interests = request.args.get("interests", "").lower()
    duration = int(request.args.get("duration", 6))
    points_count = int(request.args.get("points", 4))
    
    import random
    
    # Randomly offset the center slightly for variety between calls
    # This ensures that "Plan 1", "Plan 2", "Plan 3" don't always pick the same "closest" place
    lat += random.uniform(-0.005, 0.005)
    lng += random.uniform(-0.005, 0.005)

    # Calculate max allowed price level based on budget
    max_price = 4
    if budget < 150: max_price = 1
    elif budget < 400: max_price = 2
    elif budget < 800: max_price = 3
    
    # Category pool
    culture_pool = ["museum", "art_gallery", "church", "library", "university"]
    explore_pool = ["tourist_attraction", "zoo", "aquarium", "stadium", "city_hall"]
    relax_pool = ["park", "shopping_mall", "movie_theater", "bowling_alley", "book_store", "spa"]
    food_pool = ["restaurant", "cafe", "bakery", "bar", "meal_takeaway"]

    # Inject preferences into pools if they match
    if "art" in interests or "cultur" in interests:
        culture_pool *= 2 # Increase probability
    if "natur" in interests:
        relax_pool = ["park", "park", "garden"] + relax_pool
    if "food" in interests or "mancare" in interests:
        food_pool = ["restaurant", "restaurant"] + food_pool

    # Build dynamic slots based on points_count and style
    # We define a "Cycle" for each style and repeat it until we reach points_count
    
    pattern = []
    if "cultural" in style:
        pattern = [
            {"type": "cafe", "label": "Mic Dejun"},
            {"type": random.choice(culture_pool), "label": "Activitate Culturală"},
            {"type": "restaurant", "label": "Prânz"},
            {"type": random.choice(culture_pool), "label": "Explorare Istorică"},
            {"type": "cafe", "label": "Pauză de Ceai"},
            {"type": "museum", "label": "Muzeu Seară"}
        ]
    elif "relax" in style:
        pattern = [
            {"type": "cafe", "label": "Mic Dejun"},
            {"type": random.choice(relax_pool), "label": "Moment de Relaxare"},
            {"type": "restaurant", "label": "Prânz"},
            {"type": "park", "label": "Plimbare Liniștită"},
            {"type": "spa", "label": "Răsfăț"},
            {"type": "movie_theater", "label": "Seară la Film"}
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
        night_pool = ["bar", "night_club", "casino", "movie_theater"]
        pattern = [
            {"type": "restaurant", "label": "Cină Elegantă"},
            {"type": random.choice(night_pool), "label": "Distracție de Noapte"},
            {"type": "bar", "label": "Cocktail Bar"},
            {"type": random.choice(night_pool), "label": "After-party"},
            {"type": "night_club", "label": "Clubbing"},
            {"type": "bakery", "label": "Mic Dejun de Dimineață"}
        ]
    else: # Default: Explorare
        pattern = [
            {"type": "cafe", "label": "Mic Dejun"},
            {"type": random.choice(explore_pool), "label": "Aventură Urbană"},
            {"type": "restaurant", "label": "Prânz"},
            {"type": "park", "label": "Relaxare în Natură"},
            {"type": "tourist_attraction", "label": "Punct de Interes"},
            {"type": "restaurant", "label": "Cină Locală"}
        ]

    # Construct the final slots list by repeating the pattern to reach points_count
    slots = []
    for i in range(points_count):
        item_template = pattern[i % len(pattern)]
        # Add index to label if repeating
        label = item_template["label"]
        if i >= len(pattern):
            label += f" { (i // len(pattern)) + 1 }"
        
        slots.append({"type": item_template["type"], "label": label})
    
    # Preference Injection: If user likes art but chose "Relaxation", swap one slot
    if ("art" in interests or "cultur" in interests) and len(slots) > 1:
        slots[1] = {"type": random.choice(culture_pool), "label": "Preferința Ta: Cultură"}
    
    plan = []
    current_lat, current_lng = lat, lng
    
    # Time calculation
    from datetime import datetime, timedelta
    start_hour = 9 # Start the day at 9 AM
    current_time = datetime.now().replace(hour=start_hour, minute=0, second=0, microsecond=0)
    slot_duration_mins = (duration * 60) // len(slots) if len(slots) > 0 else 60

    for slot in slots:
        url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
        params = {
            "location": f"{current_lat},{current_lng}",
            "radius": "5000",
            "type": slot["type"],
            "key": MAPS_API_KEY,
            "maxprice": max_price
        }
        try:
            print(f"📍 Fetching {slot['type']} near {current_lat}, {current_lng} (Max Price: {max_price})")
            res = requests.get(url, params=params).json()
            results = res.get("results", [])
            
            # Fallback: If no results found with price constraint, try without it
            if not results and max_price < 4:
                print(f"🔄 Retrying {slot['type']} without price constraint...")
                params["radius"] = "5000" # Also expand radius on retry
                del params["maxprice"]
                res = requests.get(url, params=params).json()
                results = res.get("results", [])

            if results:
                # Shuffle ALL results to ensure maximum diversity across calls
                random.shuffle(results)
                # Pick the first one from the shuffled list
                best = results[0]
                # Safer photo extraction
                photos = best.get("photos", [])
                photo_ref = photos[0].get("photo_reference", "") if photos else ""
                
                img_url = f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference={photo_ref}&key={MAPS_API_KEY}" if photo_ref else "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=800&q=80"
                
                # Estimate cost
                price_level = best.get("price_level", (1 if slot["type"] in ["cafe", "park"] else 2))
                price_map = {0: 0, 1: 35, 2: 85, 3: 175, 4: 400}
                est_cost = price_map.get(price_level, 85)
                
                if slot["type"] == "park": est_cost = 0
                if slot["type"] == "museum": est_cost = 30 # Entry fee

                # Calculate time string
                time_start = current_time.strftime("%H:%M")
                current_time += timedelta(minutes=slot_duration_mins)
                time_end = current_time.strftime("%H:%M")
                time_str = f"{time_start} - {time_end}"

                opening_info = best.get("opening_hours", {})
                is_open = opening_info.get("open_now", True) # Default to true if not provided

                plan.append({
                    "slot": slot["label"],
                    "name": best["name"],
                    "address": best.get("vicinity"),
                    "imageUrl": img_url,
                    "latitude": best["geometry"]["location"]["lat"],
                    "longitude": best["geometry"]["location"]["lng"],
                    "estimatedCost": est_cost,
                    "placeId": best.get("place_id"),
                    "type": slot["type"],
                    "time": time_str,
                    "is_open": is_open
                })
                # Update next center
                current_lat = best["geometry"]["location"]["lat"]
                current_lng = best["geometry"]["location"]["lng"]
                print(f"✅ Found: {best['name']} (Est. Cost: {est_cost} RON)")
            else:
                print(f"❌ No results for {slot['type']}")
        except Exception as e:
            print(f"⚠️ Error in itinerary loop: {e}")
            continue

    return jsonify(plan)

if __name__ == "__main__":
    app.run(host='0.0.0.0', port=5000, debug=True)
