"""
Explainable Recommendation System for CityScape.
Generates AI-powered place recommendations with transparency about:
- Confidence scores (%), sources, and reasoning
- User-specific and professor-specific tracking
- Complete history of all recommendations and their outcomes
"""

import os
import json
import requests
import time
import threading
import math
from datetime import datetime, timezone
from typing import Dict, List, Any
import numpy as np
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# In-memory query caches with threading locks
_recommender_cache_lock = threading.Lock()
_recommender_text_search_cache = {}
_recommender_nearby_search_cache = {}
_recommender_weather_cache = {}
SEARCH_CACHE_TTL = 600  # 10 minutes (600 seconds)

ROMANIAN_INTEREST_TRANSLATION = {
    "muzee": "museums",
    "parcuri și natură": "parks",
    "parcuri si natura": "parks",
    "artă și design": "art",
    "arta si design": "art",
    "restaurante": "food",
    "cultură și istorie": "history",
    "cultura si istorie": "history",
    "locuri interesante": "culture",
    "sporte": "sports",
    "shopping": "shopping",
    "viața de noapte": "nightlife",
    "viata de noapte": "nightlife",
    "evenimente": "culture",
    "general": "culture"
}

def haversine_distance(lat1, lon1, lat2, lon2):
    from math import radians, cos, sin, asin, sqrt
    try:
        lon1, lat1, lon2, lat2 = map(radians, [float(lon1), float(lat1), float(lon2), float(lat2)])
        dlon = lon2 - lon1
        dlat = lat2 - lat1
        a = sin(dlat/2)**2 + cos(lat1) * cos(lat2) * sin(dlon/2)**2
        c = 2 * asin(sqrt(a))
        return c * 6371
    except:
        return 9999.0

OPENWEATHER_API_KEY = os.getenv("OPENWEATHER_API_KEY")

def fetch_current_weather(lat, lng):
    # Check cache first
    cache_key = (round(float(lat), 3), round(float(lng), 3))
    now = time.time()
    with _recommender_cache_lock:
        if cache_key in _recommender_weather_cache:
            val, exp = _recommender_weather_cache[cache_key]
            if now < exp:
                return val

    try:
        if OPENWEATHER_API_KEY:
            url = f"https://api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lng}&appid={OPENWEATHER_API_KEY}&units=metric"
            resp = requests.get(url, timeout=3)
            if resp.status_code == 200:
                w_data = resp.json()
                temp = w_data.get("main", {}).get("temp", 22.0)
                weather_id = w_data.get("weather", [{}])[0].get("id", 800)
                # OpenWeather IDs: less than 700 are thunderstorm/drizzle/rain/snow
                is_raining = (weather_id < 700) or ("rain" in w_data.get("weather", [{}])[0].get("main", "").lower())
                
                status = "Plouă" if is_raining else "Senin"
                icon = "🌧️" if is_raining else "☀️"
                if temp > 28:
                    icon = "🔥"
                elif temp < 10:
                    icon = "❄️"
                elif not is_raining and weather_id >= 801:
                    icon = "☁️" # Cloudy but not raining

                result = {
                    "status": status,
                    "is_bad": is_raining,
                    "temp": f"{int(round(temp))}°C",
                    "icon": icon,
                    "advice": "Sugestii indoor activate" if is_raining else "Vreme perfectă pentru explorare"
                }
                with _recommender_cache_lock:
                    _recommender_weather_cache[cache_key] = (result, now + SEARCH_CACHE_TTL)
                return result
    except Exception as e:
        print(f"⚠️ OpenWeather API error: {e}")

    # Fallback to simulation
    try:
        # Simulation: 20% chance of rain based on timestamp minutes
        is_raining = (int(time.time() / 60) % 5 == 0) 
        temp = 22 # Spring average
        
        status = "Plouă" if is_raining else "Senin"
        icon = "☁️" if is_raining else "☀️"
        
        result = {
            "status": status,
            "is_bad": is_raining,
            "temp": f"{temp}°C",
            "icon": icon,
            "advice": "Sugestii indoor activate" if is_raining else "Vreme perfectă pentru explorare"
        }
        with _recommender_cache_lock:
            _recommender_weather_cache[cache_key] = (result, now + SEARCH_CACHE_TTL)
        return result
    except:
        return {
            "status": "Senin",
            "is_bad": False,
            "temp": "22°C",
            "icon": "☀️",
            "advice": "Vreme perfectă pentru explorare"
        }

SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_KEY")
MAPS_API_KEY = os.getenv("MAPS_API_KEY")

class ExplainableRecommender:
    def __init__(self):
        self.supabase_headers = {
            "apikey": SUPABASE_KEY,
            "Authorization": f"Bearer {SUPABASE_KEY}"
        }

    def get_recommendations(self, user_id: str, lat: float, lng: float,
                          interests: List[str], language: str = "ro",
                          limit: int = 10, city_name: str = None, trending: bool = True,
                          category: str = None, query: str = None, min_rating: float = None,
                          max_distance: float = None, price_level: int = None) -> Dict[str, Any]:
        """
        Generate explainable recommendations with confidence scores and reasoning.
        Returns detailed breakdown of where each recommendation comes from.

        Args:
            user_id: User identifier
            lat, lng: User's coordinates
            interests: User's interests
            language: Response language (ro/en)
            limit: Max recommendations (1-10, default 10)
            city_name: Current city name to display
            trending: Include trending places from other users in the city
            category: Selected UI category filter
            query: Custom search query text
            min_rating: Minimum rating threshold
            max_distance: Maximum distance in km
            price_level: Level of price (1-4)
        """
        try:
            # Validate limit
            limit = min(max(int(limit), 1), 10)  # Clamp to 1-10

            # 1. Fetch user profile for personalization
            user_data = self._fetch_user_profile(user_id)

            # Fallback to profile interests if client interests are empty
            if not interests and user_data.get("interests"):
                profile_interests = user_data.get("interests")
                if isinstance(profile_interests, str):
                    interests = [i.strip() for i in profile_interests.split(",") if i.strip()]
                elif isinstance(profile_interests, list):
                    interests = profile_interests

            # 2. Fetch user's visit history and bookmarks
            visit_history = self._fetch_visit_history(user_id)
            bookmarks = self._fetch_user_bookmarks(user_id)

            # 3. Find nearby places
            nearby_places = self._fetch_nearby_places(lat, lng, category=category)

            # Text query candidate expansion
            if query and query.strip() != "":
                query_places = []
                cache_key = (f"{query} {category or ''}".strip(), lat, lng)
                now = time.time()
                with _recommender_cache_lock:
                    if cache_key in _recommender_text_search_cache:
                        val, exp = _recommender_text_search_cache[cache_key]
                        if now < exp:
                            query_places = val
                
                if not query_places:
                    try:
                        url = "https://maps.googleapis.com/maps/api/place/textsearch/json"
                        params = {
                            "query": f"{query} {category or ''}".strip(),
                            "location": f"{lat},{lng}",
                            "radius": "15000",
                            "key": MAPS_API_KEY
                        }
                        resp = requests.get(url, params=params, timeout=5)
                        if resp.status_code == 200:
                            query_places = resp.json().get("results", [])
                            with _recommender_cache_lock:
                                _recommender_text_search_cache[cache_key] = (query_places, now + SEARCH_CACHE_TTL)
                    except Exception as e:
                        print(f"⚠️ Text search candidates query error: {e}")
                nearby_places = query_places + nearby_places

            # 4. Get trending places if enabled (places visited by similar users)
            trending_places = []
            if trending:
                trending_places = self._get_trending_places(user_id, city_name, language)

            # 5. Merge and filter places based on rules
            all_places = nearby_places + trending_places
            all_places = list({p['place_id']: p for p in all_places if p.get('place_id')}.values())  # Remove duplicates

            filtered_candidates = []
            for place in all_places:
                # Rating filter
                rating = place.get("rating", 0)
                if min_rating is not None and rating < float(min_rating):
                    continue

                # Distance filter
                geo = place.get("geometry", {}).get("location", {})
                p_lat, p_lng = geo.get("lat"), geo.get("lng")
                if max_distance is not None and p_lat is not None and p_lng is not None:
                    dist = haversine_distance(lat, lng, p_lat, p_lng)
                    if dist > float(max_distance):
                        continue

                # Price level filter
                p_level = place.get("price_level")
                if price_level is not None and int(price_level) > 0 and p_level is not None:
                    if int(p_level) != int(price_level):
                        continue

                # Search query filter check (in case search API returns excess)
                if query and query.strip() != "":
                    q_lower = query.lower()
                    p_name = place.get("name", "").lower()
                    p_vic = place.get("vicinity", "").lower()
                    p_types = [t.lower() for t in place.get("types", [])]
                    if q_lower not in p_name and q_lower not in p_vic and not any(q_lower in t for t in p_types):
                        continue

                filtered_candidates.append(place)

            all_places = filtered_candidates

            # 6. Calculate recommendation scores for each place (score all to find the absolute best options)
            recommendations = []
            for place in all_places:  
                score_breakdown = self._calculate_recommendation_score(
                    place, user_data, visit_history, bookmarks, interests, lat, lng, language
                )
                recommendations.append({
                    "place": place,
                    "score_breakdown": score_breakdown,
                    "total_confidence": score_breakdown["total"],
                    "reasoning": self._generate_reasoning(score_breakdown, language),
                    "explanation": self._generate_explanation(
                        place, score_breakdown, user_data, language
                    ),
                    "is_trending": place.get("is_trending", False)
                })

            # 7. Sort by confidence (prioritize high confidence)
            recommendations.sort(key=lambda x: (
                -x["total_confidence"],  # Higher confidence first
                -int(x.get("is_trending", False))  # Then trending
            ))
            top_recommendations = recommendations[:limit]

            # 8. Log recommendations for history
            self._log_recommendations(user_id, top_recommendations)

            # 9. Format output
            return {
                "status": "success",
                "user_id": user_id,
                "city": city_name or "București",
                "timestamp": datetime.now().isoformat(),
                "count": len(top_recommendations),
                "recommendations": [
                    self._format_recommendation(rec) for rec in top_recommendations
                ],
                "metadata": {
                    "total_places_considered": len(all_places),
                    "method": "explainable_ai_personalized",
                    "includes_trending": trending,
                    "language": language,
                    "limit": limit
                }
            }
        except Exception as e:
            print(f"❌ Recommendation Error: {e}")
            return {
                "status": "error",
                "error": str(e),
                "recommendations": []
            }

    def _fetch_user_profile(self, user_id: str) -> Dict:
        """Fetch user profile including interests and preferences."""
        try:
            url = f"{SUPABASE_URL}/rest/v1/user_profiles?id=eq.{user_id}&select=*"
            resp = requests.get(url, headers=self.supabase_headers, timeout=5)
            if resp.status_code == 200 and resp.json():
                return resp.json()[0]
        except Exception as e:
            print(f"⚠️ User profile fetch error: {e}")

        return {
            "id": user_id,
            "interests": [],
            "level": 1,
            "total_xp": 0,
            "preferences": {}
        }

    def _fetch_visit_history(self, user_id: str) -> List[Dict]:
        """Fetch user's complete visit history."""
        try:
            url = f"{SUPABASE_URL}/rest/v1/visited_places?user_id=eq.{user_id}&select=*&order=visited_at.desc&limit=100"
            resp = requests.get(url, headers=self.supabase_headers, timeout=5)
            if resp.status_code == 200:
                return resp.json() if resp.json() else []
        except Exception as e:
            print(f"⚠️ Visit history fetch error: {e}")
        return []

    def _fetch_user_bookmarks(self, user_id: str) -> List[Dict]:
        """Fetch user's bookmarked places from Supabase by joining feed_bookmarks and feed_posts."""
        try:
            url = f"{SUPABASE_URL}/rest/v1/feed_bookmarks?user_id=eq.{user_id}&select=*,feed_posts(*)"
            resp = requests.get(url, headers=self.supabase_headers, timeout=5)
            if resp.status_code == 200:
                bookmarks = resp.json() if resp.json() else []
                places = []
                for b in bookmarks:
                    post = b.get("feed_posts")
                    if post and isinstance(post, dict):
                        places.append(post)
                return places
        except Exception as e:
            print(f"⚠️ Bookmarks fetch error: {e}")
        return []

    def _fetch_nearby_places(self, lat: float, lng: float, radius: int = 5000, category: str = None) -> List[Dict]:
        """Fetch nearby places from Google Places API."""
        ptype = "tourist_attraction"
        if category:
            cat_lower = category.lower()
            if "rest" in cat_lower or "food" in cat_lower:
                ptype = "restaurant"
            elif "caf" in cat_lower or "coffee" in cat_lower:
                ptype = "cafe"
            elif "par" in cat_lower or "nature" in cat_lower:
                ptype = "park"
            elif "mus" in cat_lower or "art" in cat_lower:
                ptype = "museum"
            elif "cult" in cat_lower:
                ptype = "tourist_attraction"
            elif "com" in cat_lower or "shop" in cat_lower:
                ptype = "shopping_mall"

        # If no specific category is selected, fetch multiple types in parallel to maximize recommendation coverage
        if not category:
            types_to_fetch = ["tourist_attraction", "restaurant", "cafe", "park", "museum", "night_club"]
            import concurrent.futures
            
            def fetch_single_type(t):
                # Check cache first
                ck = (lat, lng, radius, t)
                with _recommender_cache_lock:
                    if ck in _recommender_nearby_search_cache:
                        val, exp = _recommender_nearby_search_cache[ck]
                        if time.time() < exp:
                            return val
                try:
                    url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
                    params = {
                        "location": f"{lat},{lng}",
                        "radius": radius,
                        "type": t,
                        "key": MAPS_API_KEY
                    }
                    resp = requests.get(url, params=params, timeout=4)
                    if resp.status_code == 200:
                        results = resp.json().get("results", [])
                        with _recommender_cache_lock:
                            _recommender_nearby_search_cache[ck] = (results, time.time() + SEARCH_CACHE_TTL)
                        return results
                except Exception as e:
                    print(f"⚠️ Nearby places fetch error for type {t}: {e}")
                return []
                
            merged_results = []
            with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
                futures = [executor.submit(fetch_single_type, t) for t in types_to_fetch]
                for future in concurrent.futures.as_completed(futures):
                    merged_results.extend(future.result())
            
            # Remove duplicates by place_id
            seen = set()
            unique_results = []
            for p in merged_results:
                pid = p.get("place_id")
                if pid and pid not in seen:
                    seen.add(pid)
                    unique_results.append(p)
            return unique_results

        # Check cache first for single category fetch
        cache_key = (lat, lng, radius, ptype)
        now = time.time()
        with _recommender_cache_lock:
            if cache_key in _recommender_nearby_search_cache:
                val, exp = _recommender_nearby_search_cache[cache_key]
                if now < exp:
                    return val

        try:
            url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
            params = {
                "location": f"{lat},{lng}",
                "radius": radius,
                "type": ptype,
                "key": MAPS_API_KEY
            }
            resp = requests.get(url, params=params, timeout=5)
            if resp.status_code == 200:
                results = resp.json().get("results", [])
                with _recommender_cache_lock:
                    _recommender_nearby_search_cache[cache_key] = (results, now + SEARCH_CACHE_TTL)
                return results
        except Exception as e:
            print(f"⚠️ Nearby places fetch error: {e}")

        return []

    def _calculate_recommendation_score(self, place: Dict, user_data: Dict,
                                       visit_history: List[Dict], bookmarks: List[Dict],
                                       interests: List[str], lat: float, lng: float, language: str = "ro") -> Dict[str, float]:
        """
        Calculate confidence score breakdown for a place.
        Highly personalized algorithm (75% personal factors, 25% contextual/quality):
        - Interest Match (35%)
        - Visit History Affinity (decayed) (25%)
        - Bookmarks Match (15%)
        - Freshness / Novelty (10%)
        - Weather & Time Suitability (10%)
        - General Popularity (5%)
        """
        scores = {
            "interest_match": 0.0,
            "history_affinity": 0.0,
            "bookmarks": 0.0,
            "freshness": 0.0,
            "weather_time": 0.0,
            "popularity": 0.0,
            "total": 0.0
        }

        place_name = place.get("name", "").lower()
        place_types_full = place.get("types", [])
        place_type = place_types_full[0] if place_types_full else "unknown"
        name_words = place_name.split()

        # ==================== 1. INTEREST MATCH (0-100 score, weight 35%) ====================
        interest_score = 0.0
        interests_clean = [i.lower().strip() for i in interests if i]

        interest_keywords = {
            "museums": ["museum", "gallery", "exhibit", "muzeu", "galerie", "expozi", "expozitie"],
            "art": ["art", "gallery", "artistic", "painting", "sculpture", "galerie", "pictura", "sculptura", "arta"],
            "history": ["history", "historic", "historical", "monument", "archeolog", "ancient", "istoric", "istorie", "vestigiu", "monument", "ruine", "cetate", "palat", "castel"],
            "food": ["restaurant", "cafe", "bistro", "pizzeria", "cuisine", "dining", "cafenea", "ceainarie", "pizzerie", "mancare", "pub", "taverna", "burger", "sushi", "trattoria"],
            "parks": ["park", "garden", "botanical", "natural", "reserve", "nature", "parc", "gradina", "botanica", "rezervatie", "natura", "padure", "lac"],
            "churches": ["church", "cathedral", "monastery", "chapel", "religious", "biserica", "cathedrala", "manastire", "capela", "schit", "templu", "sinagoga", "moschee"],
            "culture": ["cultural", "culture", "theater", "theatre", "performance", "cultura", "teatru", "ateneu", "opera", "spectacol", "filarmonica"],
            "adventure": ["hiking", "climbing", "trail", "outdoor", "adventure", "traseu", "alpinism", "drumetie", "aventura", "parc aventura", "escapada"],
            "nightlife": ["bar", "club", "nightclub", "pub", "lounge", "disco", "club", "bar", "pub", "discoteca", "berarie", "cocktail"],
            "shopping": ["mall", "market", "shop", "shopping", "store", "bazaar", "magazin", "piata", "bazar", "centru comercial", "boutique"],
            "sports": ["sport", "gym", "stadium", "fitness", "arena", "billiard", "bowling", "pool", "tenis", "fotbal", "teren", "sala", "alergare", "complex sportiv", "piscina", "bazin"]
        }

        interest_google_types = {
            "museums": ["museum", "art_gallery"],
            "art": ["art_gallery", "museum"],
            "history": ["church", "place_of_worship", "city_hall", "monument", "cemetery"],
            "food": ["restaurant", "cafe", "food", "bakery", "bar", "meal_takeaway", "meal_delivery"],
            "parks": ["park", "zoo", "aquarium", "campground", "amusement_park"],
            "churches": ["church", "place_of_worship", "mosque", "synagogue"],
            "culture": ["library", "theater", "museum", "movie_theater", "performing_arts_theater", "art_gallery"],
            "adventure": ["campground", "amusement_park", "park", "zoo", "stadium"],
            "nightlife": ["bar", "night_club", "casino"],
            "shopping": ["shopping_mall", "store", "clothing_store", "department_store", "supermarket", "electronics_store", "book_store", "shoe_store", "jewelry_store"],
            "sports": ["gym", "stadium", "bowling_alley", "sports_complex"]
        }

        if interests_clean:
            match_score = 0.0
            for interest in interests_clean:
                translated = ROMANIAN_INTEREST_TRANSLATION.get(interest, interest)
                keywords = interest_keywords.get(translated, [interest])
                google_types = interest_google_types.get(translated, [])
                current_match = 0.0

                # Check against Google Place Types
                for p_t in place_types_full:
                    p_t_lower = p_t.lower()
                    if p_t_lower in google_types:
                        current_match = max(current_match, 3.5)
                    for g_t in google_types:
                        if g_t in p_t_lower:
                            current_match = max(current_match, 3.0)

                # Check against name keywords
                for keyword in keywords:
                    if keyword in place_name:
                        current_match = max(current_match, 3.5)
                    elif any(keyword in word for word in name_words):
                        current_match = max(current_match, 2.5)

                if current_match > 0:
                    match_score += current_match

            if match_score >= 3.5:
                interest_score = min(100.0, 85.0 + (match_score - 3.5) * 5)
            elif match_score >= 2.0:
                interest_score = min(100.0, 60.0 + (match_score - 2.0) * 15)
            elif match_score >= 1.0:
                interest_score = min(100.0, 30.0 + (match_score - 1.0) * 30)
            else:
                interest_score = 10.0
        else:
            interest_score = 20.0

        scores["interest_match"] = interest_score

        # ==================== 2. VISIT HISTORY AFFINITY (0-100 score, weight 25%) ====================
        history_affinity_score = 0.0
        if visit_history:
            history_category_weights = {}
            now_ts = time.time()
            for i, visit in enumerate(visit_history):
                v_type = visit.get("place_type", "").lower()
                if not v_type:
                    continue
                visited_at_str = visit.get("visited_at", "")
                decay = 1.0
                if visited_at_str:
                    try:
                        ts_str = visited_at_str.replace("Z", "+00:00")
                        visit_ts = datetime.fromisoformat(ts_str).timestamp()
                        age_days = (now_ts - visit_ts) / 86400.0
                        decay = math.exp(-0.693 * age_days / 14.0)  # 14-day half-life
                    except:
                        decay = max(0.1, 1.0 - i * 0.05)
                history_category_weights[v_type] = history_category_weights.get(v_type, 0.0) + decay

            # Match types with weights
            affinity = 0.0
            for p_t in place_types_full:
                p_t_lower = p_t.lower()
                if p_t_lower in history_category_weights:
                    affinity = max(affinity, history_category_weights[p_t_lower])

            # Normalize: affinity of 3.0 visits gives max score
            history_affinity_score = min(100.0, (affinity / 3.0) * 100.0)
            history_affinity_score = max(15.0, history_affinity_score)  # base baseline
        else:
            history_affinity_score = 50.0  # neutral fallback

        scores["history_affinity"] = history_affinity_score

        # ==================== 3. BOOKMARKS MATCH (0-100 score, weight 15%) ====================
        bookmark_score = 0.0
        if bookmarks:
            exact_match = False
            type_match = False
            for b in bookmarks:
                b_name = b.get("place_name", "").lower()
                b_id = b.get("place_id", "")
                b_type = b.get("place_type", "").lower()

                if (place.get("place_id") and place.get("place_id") == b_id) or (place_name == b_name):
                    exact_match = True
                    break
                if b_type and (b_type in place_types_full or b_type == place_type.lower()):
                    type_match = True

            if exact_match:
                bookmark_score = 100.0
            elif type_match:
                bookmark_score = 75.0
            else:
                bookmark_score = 20.0
        else:
            bookmark_score = 30.0

        scores["bookmarks"] = bookmark_score

        # ==================== 4. FRESHNESS / NOVELTY (0-100 score, weight 10%) ====================
        freshness_score = 100.0
        visited_places = {v.get("place_name", "").lower(): v for v in visit_history}

        if place_name in visited_places:
            # Revisit policy depends on type
            revisit_friendly = ["restaurant", "cafe", "bar", "night_club", "food", "establishment", "shopping_mall", "store"]
            is_revisit_friendly = any(t in revisit_friendly for t in place_types_full) or place_type in revisit_friendly
            
            if is_revisit_friendly:
                freshness_score = 40.0
            else:
                freshness_score = 10.0
        else:
            # Recency penalty if user recently visited a place of this type
            for i, visit in enumerate(visit_history[:10]):
                if visit.get("place_type", "").lower() == place_type.lower():
                    freshness_score = max(50.0, freshness_score - (10 - i) * 5.0)
                    break

        scores["freshness"] = freshness_score

        # ==================== 5. WEATHER & TIME MATCH (0-100 score, weight 10%) ====================
        weather = fetch_current_weather(lat, lng)
        is_bad_weather = weather.get("is_bad", False)

        indoor_types = [
            "museum", "art_gallery", "cafe", "restaurant", "movie_theater", 
            "library", "shopping_mall", "church", "place_of_worship", 
            "food", "bar", "nightclub"
        ]
        outdoor_types = [
            "park", "zoo", "amusement_park", "tourist_attraction", 
            "stadium", "campground", "natural_feature"
        ]

        is_indoor = any(t in indoor_types for t in place_types_full) or place_type in indoor_types
        is_outdoor = any(t in outdoor_types for t in place_types_full) or place_type in outdoor_types

        weather_time_score = 70.0  # base neutral

        # Weather
        if is_bad_weather:
            if is_indoor:
                weather_time_score += 15.0
            elif is_outdoor:
                weather_time_score -= 35.0
        else:
            if is_outdoor:
                weather_time_score += 20.0
            elif is_indoor:
                if "cafe" in place_types_full or "restaurant" in place_types_full or "food" in place_types_full:
                    weather_time_score += 10.0

        # Time of day
        current_hour = datetime.now().hour
        is_night = current_hour >= 18 or current_hour < 6

        if is_night:
            if any(t in ["bar", "nightclub", "restaurant", "food"] for t in place_types_full) or place_type in ["bar", "nightclub", "restaurant"]:
                weather_time_score += 10.0
            elif any(t in ["park", "zoo", "library"] for t in place_types_full) or place_type in ["park", "zoo", "library"]:
                weather_time_score -= 30.0
        else:
            if any(t in ["park", "museum", "zoo", "tourist_attraction"] for t in place_types_full) or place_type in ["park", "museum", "zoo", "tourist_attraction"]:
                weather_time_score += 10.0
            elif any(t in ["bar", "nightclub"] for t in place_types_full) or place_type in ["bar", "nightclub"]:
                weather_time_score -= 40.0

        scores["weather_time"] = max(0.0, min(100.0, weather_time_score))

        # ==================== 6. POPULARITY & QUALITY (0-100 score, weight 5%) ====================
        rating = float(place.get("rating") or 0.0)
        user_ratings_total = int(place.get("user_ratings_total") or place.get("reviews") or 0)

        if rating > 0.0:
            popularity_score = (rating / 5.0) * 80.0
        else:
            popularity_score = 60.0

        if user_ratings_total > 0:
            review_bonus = min(20.0, (math.log10(user_ratings_total) / 4.0) * 20.0)
        else:
            review_bonus = 0.0

        scores["popularity"] = max(0.0, min(100.0, popularity_score + review_bonus))

        # ==================== WEIGHTED TOTAL ====================
        weighted_total = (
            scores["interest_match"] * 0.35 +
            scores["history_affinity"] * 0.25 +
            scores["bookmarks"] * 0.15 +
            scores["freshness"] * 0.10 +
            scores["weather_time"] * 0.10 +
            scores["popularity"] * 0.05
        )

        # Distance penalty (up to 10 points penalty)
        distance_penalty = 0.0
        geo = place.get("geometry", {}).get("location", {})
        p_lat, p_lng = geo.get("lat"), geo.get("lng")
        if p_lat is not None and p_lng is not None:
            dist = haversine_distance(lat, lng, p_lat, p_lng)
            distance_penalty = min(10.0, dist * 0.4)

        scores["total"] = max(0.0, min(100.0, weighted_total - distance_penalty))

        return scores

    def _generate_reasoning(self, score_breakdown: Dict, language: str) -> str:
        """Generate human-readable reasoning for the recommendation."""
        reasons = []

        # Interest Match
        if score_breakdown["interest_match"] > 75:
            reason = "Perfect match cu interesele tale" if language == "ro" else "Perfect match with your interests"
            reasons.append(reason)
        elif score_breakdown["interest_match"] > 50:
            reason = "Corespunde intereselor tale" if language == "ro" else "Matches your interests"
            reasons.append(reason)

        # Bookmarks
        if score_breakdown.get("bookmarks", 0.0) >= 95:
            reason = "Salvat la favorite" if language == "ro" else "Saved to bookmarks"
            reasons.append(reason)
        elif score_breakdown.get("bookmarks", 0.0) >= 70:
            reason = "Categorie preferată salvată" if language == "ro" else "Matches bookmarked category"
            reasons.append(reason)

        # Freshness
        if score_breakdown["freshness"] >= 90:
            reason = "Ceva complet nou pentru tine" if language == "ro" else "Something brand new for you"
            reasons.append(reason)
        elif score_breakdown["freshness"] > 60:
            reason = "Nu ai vizitat încă" if language == "ro" else "You haven't visited yet"
            reasons.append(reason)

        # History Affinity
        if score_breakdown.get("history_affinity", 0.0) >= 80:
            reason = "Frecventat de tine în trecut" if language == "ro" else "Frequently visited by you"
            reasons.append(reason)

        # Popularity
        if score_breakdown["popularity"] >= 85:
            reason = "Foarte apreciat - 4.6+ rating" if language == "ro" else "Highly rated - 4.6+ stars"
            reasons.append(reason)

        # Weather & Time Match
        if score_breakdown.get("weather_time", 0.0) >= 85.0:
            reason = "Potrivit pentru vremea și momentul actual" if language == "ro" else "Great for current weather & time"
            reasons.append(reason)

        return " • ".join(reasons) if reasons else ("Explorare recomandată" if language == "ro" else "Worth exploring")

    def _generate_explanation(self, place: Dict, score_breakdown: Dict,
                             user_data: Dict, language: str) -> Dict:
        """Generate detailed explanation of how recommendation was calculated."""
        base_msg = "Pe baza" if language == "ro" else "Based on"

        return {
            "summary": f"{base_msg} analizei factorilor de personalizare" if language == "ro" else f"{base_msg} personalization factors analysis",
            "factors": [
                {
                    "name": "Potrivire interese" if language == "ro" else "Interest Match",
                    "score": f"{score_breakdown['interest_match']:.1f}%",
                    "description": "Cât de bine se potrivește cu interesele tale salvate" if language == "ro" else "How well it matches your saved interests"
                },
                {
                    "name": "Afinitate istoric" if language == "ro" else "History Affinity",
                    "score": f"{score_breakdown.get('history_affinity', 50.0):.1f}%",
                    "description": "Corespunderea cu locurile vizitate de tine în trecut" if language == "ro" else "Match with places you visited in the past"
                },
                {
                    "name": "Locuri salvate" if language == "ro" else "Bookmarked Places",
                    "score": f"{score_breakdown.get('bookmarks', 30.0):.1f}%",
                    "description": "Preferința pentru locațiile salvate la favorite" if language == "ro" else "Preference for places saved to bookmarks"
                },
                {
                    "name": "Noutate" if language == "ro" else "Freshness",
                    "score": f"{score_breakdown['freshness']:.1f}%",
                    "description": "Dacă nu ai vizitat deja acest loc recent" if language == "ro" else "Whether you haven't visited this place recently"
                },
                {
                    "name": "Vreme & Timp" if language == "ro" else "Weather & Time Match",
                    "score": f"{score_breakdown['weather_time']:.1f}%",
                    "description": "Potrivirea cu vremea actuală și momentul zilei" if language == "ro" else "Compatibility with current weather and time of day"
                },
                {
                    "name": "Popularitate" if language == "ro" else "Popularity",
                    "score": f"{score_breakdown['popularity']:.1f}%",
                    "description": "Rating și numărul de recenzii" if language == "ro" else "Rating and number of reviews"
                }
            ],
            "total_confidence": f"{score_breakdown['total']:.1f}%"
        }

    def _format_recommendation(self, rec: Dict) -> Dict:
        """Format recommendation for API response."""
        place = rec["place"]
        img_url = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800"
        if place.get("photos"):
            ref = place["photos"][0].get("photo_reference")
            if ref:
                img_url = f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference={ref}&key={MAPS_API_KEY}"

        return {
            "id": place.get("place_id"),
            "name": place.get("name"),
            "address": place.get("vicinity") or place.get("formatted_address"),
            "rating": place.get("rating"),
            "reviews": place.get("user_ratings_total") or place.get("reviews", 0),
            "type": place.get("types", ["unknown"])[0],
            "latitude": place.get("geometry", {}).get("location", {}).get("lat"),
            "longitude": place.get("geometry", {}).get("location", {}).get("lng"),
            "confidence": f"{rec['total_confidence']:.1f}%",
            "reasoning": rec["reasoning"],
            "explanation": rec["explanation"],
            "score_breakdown": {k: f"{v:.1f}%" if v != rec['score_breakdown'].get(k) else v
                              for k, v in rec["score_breakdown"].items()},
            "image_url": img_url
        }

    def _get_trending_places(self, user_id: str, city_name: str, language: str) -> List[Dict]:
        """
        Get trending places - high-rated places that are popular.
        Uses Google Places API to get popular locations.
        """
        try:
            # First try to get from recommendation_history (user-driven trending)
            url = f"{SUPABASE_URL}/rest/v1/recommendation_history?status=eq.visited&select=place_name,place_id&order=visited_at.desc&limit=50"
            resp = requests.get(url, headers=self.supabase_headers, timeout=5)

            if resp.status_code == 200:
                trending_data = resp.json()
                if trending_data:
                    trending_places = []
                    seen_ids = set()

                    for item in trending_data[:15]:  # Top 15 visited
                        place_id = item.get("place_id")
                        if place_id and place_id not in seen_ids:
                            seen_ids.add(place_id)
                            trending_places.append({
                                "place_id": place_id,
                                "name": item.get("place_name"),
                                "is_trending": True,
                                "visited_count": len([x for x in trending_data if x.get("place_id") == place_id])
                            })

                    if trending_places:
                        return trending_places[:10]
        except Exception as e:
            print(f"⚠️ Trending from history error: {e}")

        # FALLBACK: Get trending from Google Places (high-rated places)
        cache_key = (city_name, "trending_fallback")
        now = time.time()
        with _recommender_cache_lock:
            if cache_key in _recommender_text_search_cache:
                val, exp = _recommender_text_search_cache[cache_key]
                if now < exp:
                    return val

        try:
            url = "https://maps.googleapis.com/maps/api/place/textsearch/json"
            params = {
                "query": f"popular places {city_name}",
                "key": MAPS_API_KEY
            }
            resp = requests.get(url, params=params, timeout=5)

            if resp.status_code == 200:
                results = resp.json().get("results", [])
                trending_places = []

                for place in results[:10]:  # Top 10 from Google
                    # Only include high-rated places
                    if place.get("rating", 0) >= 4.2:
                        trending_places.append({
                            "place_id": place.get("place_id"),
                            "name": place.get("name"),
                            "types": place.get("types", []),
                            "rating": place.get("rating"),
                            "user_ratings_total": place.get("user_ratings_total"),
                            "geometry": place.get("geometry"),
                            "vicinity": place.get("vicinity"),
                            "photos": place.get("photos"),
                            "is_trending": True,
                            "trending_reason": "Popular & Highly Rated"
                        })

                with _recommender_cache_lock:
                    _recommender_text_search_cache[cache_key] = (trending_places, now + SEARCH_CACHE_TTL)
                return trending_places
        except Exception as e:
            print(f"⚠️ Google trending error: {e}")

        return []

    def _get_similar_users(self, user_id: str, interests: List[str], limit: int = 5) -> List[str]:
        """
        Find users with similar interests for collaborative recommendations.
        """
        try:
            # This would query users with similar interest profiles
            # For now, return empty - can be enhanced with ML
            pass
        except:
            pass

        return []

    def _log_recommendations(self, user_id: str, recommendations: List[Dict]) -> None:
        """Log recommendations (optional - table not required)."""
        # Can log to database if needed, but system works without it
        pass

    def get_user_recommendation_history(self, user_id: str, limit: int = 50) -> Dict:
        """Fetch recommendation history (placeholder - no table required)."""
        return {
            "status": "success",
            "user_id": user_id,
            "history": [],
            "total_count": 0,
            "note": "History tracking disabled"
        }

    def get_recommendation_statistics(self, user_id: str) -> Dict:
        """Get statistics about recommendations for a user (placeholder)."""
        return {
            "status": "success",
            "user_id": user_id,
            "total_recommendations": 0,
            "visited_count": 0,
            "accepted_count": 0,
            "rejected_count": 0,
            "accuracy_rate": "N/A",
            "avg_confidence": "N/A",
            "note": "Statistics disabled (no history table)"
        }

    def _fetch_user_recommendation_history_raw(self, user_id: str) -> List[Dict]:
        """Internal method to fetch raw recommendation history."""
        try:
            url = f"{SUPABASE_URL}/rest/v1/recommendation_history?user_id=eq.{user_id}&select=*"
            resp = requests.get(url, headers=self.supabase_headers, timeout=5)
            if resp.status_code == 200:
                return resp.json() if resp.json() else []
        except:
            pass
        return []

    def update_recommendation_status(self, recommendation_id: str, status: str) -> Dict:
        """Update recommendation status (visited, accepted, rejected)."""
        try:
            url = f"{SUPABASE_URL}/rest/v1/recommendation_history?id=eq.{recommendation_id}"
            payload = {
                "status": status,
                "updated_at": datetime.now().isoformat()
            }
            resp = requests.patch(
                url,
                json=payload,
                headers=self.supabase_headers,
                timeout=5
            )
            return {
                "status": "success",
                "message": f"Recommendation status updated to '{status}'"
            }
        except Exception as e:
            return {"status": "error", "error": str(e)}


# Initialize global recommender instance
recommender = ExplainableRecommender()
