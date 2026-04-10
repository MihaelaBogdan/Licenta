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
    """Simple endpoint - returns just the answer text. Supports language parameter."""
    data = request.get_json()
    text = data.get("message")
    language = data.get("language", "ro")  # Default to Romanian
    response = get_response(text, language)
    message = {"answer": response}
    return jsonify(message)

@app.post("/predict/detailed")
def predict_detailed():
    """
    Full endpoint - returns answer, intent, confidence, and suggestions.
    Used by the Android app for conversational flow with quick replies.
    Supports language parameter for bilingual responses.
    
    Response format:
    {
        "answer": "Hai să-ți planificăm ziua! 🌟 Ce ai chef să faci?",
        "intent": "what_to_do",
        "confidence": 0.95,
        "suggestions": ["🍽️ Să mănânc ceva", "🌃 Să ies în oraș", ...]
    }
    """
    data = request.get_json()
    text = data.get("message")
    language = data.get("language", "ro")  # Default to Romanian
    result = get_response_with_details(text, language)
    return jsonify(result)

import requests
import os

# API Keys - Loaded from environment variables (.env)
# GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY")
GOOGLE_API_KEY = None  # Comentat temporar pentru a evita costurile
SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_KEY")
OPENWEATHER_API_KEY = os.getenv("OPENWEATHER_API_KEY")
TICKETMASTER_API_KEY = os.getenv("TICKETMASTER_API_KEY")
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

    url = "https://app.ticketmaster.com/discovery/v2/events.json"
    params = {
        "apikey": TICKETMASTER_API_KEY,
        "latlong": f"{lat},{lng}",
        "radius": "50",
        "unit": "km",
        "sort": "date,asc",
        "size": 15
    }
    
    if keyword:
        params["keyword"] = keyword

    try:
        response = requests.get(url, params=params)
        if response.status_code == 200:
            data = response.json()
            events = data.get("_embedded", {}).get("events", [])
            
            formatted = []
            for e in events:
                # Extract best image
                images = e.get("images", [])
                img_url = images[0]["url"] if images else "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=800&q=80"
                
                # Extract venue/location
                venues = e.get("_embedded", {}).get("venues", [])
                location_name = venues[0].get("name", "Locație necunoscută") if venues else "Locație necunoscută"
                
                # Format time
                dates = e.get("dates", {}).get("start", {})
                local_date = dates.get("localDate", "")
                local_time = dates.get("localTime", "")
                time_str = f"{local_date} {local_time}".strip()
                
                # Extract coordinates
                lat_eve = 0.0
                lng_eve = 0.0
                if venues:
                    loc = venues[0].get("location", {})
                    lat_eve = float(loc.get("latitude", 0.0))
                    lng_eve = float(loc.get("longitude", 0.0))
                
                formatted.append({
                    "title": e.get("name", "Eveniment"),
                    "location": location_name,
                    "time": time_str if time_str else "În curând",
                    "imageUrl": img_url,
                    "url": e.get("url", ""),
                    "latitude": lat_eve,
                    "longitude": lng_eve
                })
            
            # If we requested a keyword but got NO results, fallback to general events
            if not formatted and keyword:
                print(f"⚠️ No events found for '{keyword}', falling back to general events")
                del params["keyword"]
                fallback_response = requests.get(url, params=params)
                if fallback_response.status_code == 200:
                    data = fallback_response.json()
                    events = data.get("_embedded", {}).get("events", [])
                    for e in events:
                        images = e.get("images", [])
                        img_url = images[0]["url"] if images else "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=800&q=80"
                        venues = e.get("_embedded", {}).get("venues", [])
                        location_name = venues[0].get("name", "Locație necunoscută") if venues else "Locație necunoscută"
                        dates = e.get("dates", {}).get("start", {})
                        local_date = dates.get("localDate", "")
                        local_time = dates.get("localTime", "")
                        time_str = f"{local_date} {local_time}".strip()
                        
                        lat_eve_fb = 0.0
                        lng_eve_fb = 0.0
                        if venues:
                            loc = venues[0].get("location", {})
                            lat_eve_fb = float(loc.get("latitude", 0.0))
                            lng_eve_fb = float(loc.get("longitude", 0.0))

                        formatted.append({
                            "title": e.get("name", "Eveniment"),
                            "location": location_name,
                            "time": time_str if time_str else "În curând",
                            "imageUrl": img_url,
                            "url": e.get("url", ""),
                            "latitude": lat_eve_fb,
                            "longitude": lng_eve_fb
                        })
            
            return jsonify(formatted[:10])
    except Exception as ex:
        print(f"⚠️ Ticketmaster error: {ex}")

    # Fallback to Google Maps if Ticketmaster completely fails or gets rate limited
    if not GOOGLE_API_KEY:
        return jsonify([])

    try:
        url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
        params = {
            "location": f"{lat},{lng}",
            "radius": "10000",
            "keyword": f"events festivals concert show {keyword}".strip(),
            "key": GOOGLE_API_KEY
        }
        res = requests.get(url, params=params).json()
        results = res.get("results", [])
        
        events = []
        for p in results[:10]:
            photo_ref = p.get("photos", [{}])[0].get("photo_reference", "")
            img_url = f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference={photo_ref}&key={GOOGLE_API_KEY}" if photo_ref else "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=800&q=80"
            
            events.append({
                "title": p.get("name"),
                "location": p.get("vicinity"),
                "time": "Azi / În curând",
                "imageUrl": img_url,
                "url": f"https://www.google.com/maps/search/?api=1&query={p.get('geometry', {}).get('location', {}).get('lat')},{p.get('geometry', {}).get('location', {}).get('lng')}",
                "latitude": float(p.get('geometry', {}).get('location', {}).get('lat', 0.0)),
                "longitude": float(p.get('geometry', {}).get('location', {}).get('lng', 0.0))
            })
        return jsonify(events)
    except Exception as ex:
        print(f"⚠️ Google Maps fallback error: {ex}")

    return jsonify([])

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
        # Fallback images for Supabase places that might have broken links
        for p in places:
            if not p.get("imageUrl") or "placeholder" in p.get("imageUrl"):
                p["imageUrl"] = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=800&q=80"
        return jsonify(places)
    else:
        return jsonify({"error": "Failed to fetch from Supabase"}), 500

@app.get("/nearby")
def get_nearby_realtime():
    """Fetches real-time recommendations from Google Places based on user coordinates."""
    if not GOOGLE_API_KEY:
        return jsonify([])

    lat = request.args.get("lat")
    lng = request.args.get("lng")
    place_type = request.args.get("type", "restaurant")
    
    print(f"🔍 Nearby Search request for: {lat}, {lng} (Type: {place_type})")
    
    if not lat or not lng:
        return jsonify({"error": "Missing coordinates"}), 400

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
            "key": GOOGLE_API_KEY
        }
        
        try:
            response = requests.get(url, params=params)
            data = response.json()
            results = data.get("results", [])
            for p in results:
                p_id = p.get("place_id")
                if p_id not in seen_ids:
                    # Inject the actual type found
                    p["detected_type"] = t.replace("_", " ").capitalize()
                    all_results.append(p)
                    seen_ids.add(p_id)
        except Exception as e:
            print(f"⚠️ Error fetching type {t}: {e}")

    # Format results
    formatted = []
    for p in all_results:
        photo_ref = p.get("photos", [{}])[0].get("photo_reference", "")
        img_url = f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference={photo_ref}&key={GOOGLE_API_KEY}" if photo_ref else "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=800&q=80"
        
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
    
    # Sort by rating or shuffle? Let's sort by rating but keep top 20
    formatted.sort(key=lambda x: x['rating'], reverse=True)
            
    return jsonify(formatted[:40])

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
    if not GOOGLE_API_KEY:
        return jsonify([])

    lat = float(request.args.get("lat"))
    lng = float(request.args.get("lng"))
    style = request.args.get("type", "exploration").lower()
    budget = int(request.args.get("budget", 250))
    interests = request.args.get("interests", "").lower()
    
    import random
    
    # Randomly offset the center slightly for variety between calls
    # This ensures that "Plan 1", "Plan 2", "Plan 3" don't always pick the same "closest" place
    lat += random.uniform(-0.005, 0.005)
    lng += random.uniform(-0.005, 0.005)

    # Calculate max allowed price level based on budget
    max_price = 4
    if budget < 100: max_price = 1
    elif budget < 300: max_price = 2
    
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

    # Build targeted slots based on style
    if "cultural" in style:
        slots = [
            {"type": "cafe", "label": "Mic Dejun"},
            {"type": random.choice(culture_pool), "label": "Activitate Culturală"},
            {"type": "restaurant", "label": "Prânz"},
            {"type": random.choice(culture_pool), "label": "Explorare Istorică"}
        ]
    elif "relax" in style:
        slots = [
            {"type": "cafe", "label": "Mic Dejun"},
            {"type": random.choice(relax_pool), "label": "Moment de Relaxare"},
            {"type": "restaurant", "label": "Prânz"},
            {"type": "park", "label": "Plimbare Liniștită"}
        ]
    elif "gastronomic" in style or "foodie" in style:
        slots = [
            {"type": "bakery", "label": "Mic Dejun & Patiserie"},
            {"type": "cafe", "label": "Degustare Cafea"},
            {"type": "restaurant", "label": "Prânz Gourmet"},
            {"type": "bar", "label": "Aperitiv de Seară"}
        ]
    elif "night" in style or "nocturn" in style:
        night_pool = ["bar", "night_club", "casino", "movie_theater"]
        slots = [
            {"type": "restaurant", "label": "Cină Elegantă"},
            {"type": random.choice(night_pool), "label": "Distracție de Noapte"},
            {"type": "bar", "label": "Cocktail Bar"},
            {"type": random.choice(night_pool), "label": "After-party"}
        ]
    else: # Default: Explorare
        slots = [
            {"type": "cafe", "label": "Mic Dejun"},
            {"type": random.choice(explore_pool), "label": "Aventură Urbană"},
            {"type": "restaurant", "label": "Prânz"},
            {"type": random.choice(relax_pool), "label": "Relaxare"}
        ]
    
    # Preference Injection: If user likes art but chose "Relaxation", swap one slot
    if ("art" in interests or "cultur" in interests) and len(slots) > 1:
        slots[1] = {"type": random.choice(culture_pool), "label": "Preferința Ta: Cultură"}
    
    plan = []
    current_lat, current_lng = lat, lng

    for slot in slots:
        url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
        params = {
            "location": f"{current_lat},{current_lng}",
            "radius": "3000",
            "type": slot["type"],
            "key": GOOGLE_API_KEY,
            "maxprice": max_price
        }
        try:
            print(f"📍 Fetching {slot['type']} near {current_lat}, {current_lng}")
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
                
                img_url = f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference={photo_ref}&key={GOOGLE_API_KEY}" if photo_ref else "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=800&q=80"
                
                # Estimate cost
                price_level = best.get("price_level", (1 if slot["type"] in ["cafe", "park"] else 2))
                price_map = {0: 0, 1: 35, 2: 85, 3: 175, 4: 400}
                est_cost = price_map.get(price_level, 85)
                
                if slot["type"] == "park": est_cost = 0
                if slot["type"] == "museum": est_cost = 30 # Entry fee

                plan.append({
                    "slot": slot["label"],
                    "name": best["name"],
                    "address": best.get("vicinity"),
                    "imageUrl": img_url,
                    "latitude": best["geometry"]["location"]["lat"],
                    "longitude": best["geometry"]["location"]["lng"],
                    "estimatedCost": est_cost,
                    "placeId": best.get("place_id"),
                    "type": slot["type"]
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
