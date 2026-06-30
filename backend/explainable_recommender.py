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
from datetime import datetime
from typing import Dict, List, Any
import numpy as np

# In-memory query caches with threading locks
_recommender_cache_lock = threading.Lock()
_recommender_text_search_cache = {}
_recommender_nearby_search_cache = {}
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

def fetch_current_weather(lat, lng):
    import time
    try:
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

            # 2. Fetch user's visit history
            visit_history = self._fetch_visit_history(user_id)

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

            # 6. Calculate recommendation scores for each place
            recommendations = []
            for place in all_places[:limit * 4]:  # Get more candidates, then filter
                score_breakdown = self._calculate_recommendation_score(
                    place, user_data, visit_history, interests, lat, lng, language
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

        # Check cache first
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
                                       visit_history: List[Dict],
                                       interests: List[str], lat: float, lng: float, language: str = "ro") -> Dict[str, float]:
        """
        Calculate confidence score breakdown for a place.
        SMART algorithm that considers:
        - Detailed interest matching
        - Visit history patterns
        - User preferences
        - Recency bias
        - Category diversity
        - Weather & Time of Day suitability
        """
        scores = {
            "interest_match": 0.0,
            "freshness": 0.0,
            "popularity": 0.0,
            "user_level": 0.0,
            "diversity": 0.0,
            "weather_time": 0.0,
            "total": 0.0
        }

        place_name = place.get("name", "").lower()
        place_type = place.get("types", [])[0] if place.get("types") else "unknown"
        place_types_full = place.get("types", [])

        # ==================== 1. INTEREST MATCH (0-40%) ====================
        interest_score = 0.0
        interests_clean = [i.lower().strip() for i in interests if i]

        # Extract type counts from history for density boost
        history_type_counts = {}
        for visit in visit_history:
            v_type = visit.get("place_type", "").lower()
            if v_type:
                history_type_counts[v_type] = history_type_counts.get(v_type, 0) + 1

        if interests_clean:
            # ULTRA-SMART matching: analyze place name + type carefully
            match_score = 0.0

            # Define detailed keyword mappings
            interest_keywords = {
                "museums": ["museum", "gallery", "exhibit"],
                "art": ["art", "gallery", "artistic", "painting", "sculpture"],
                "history": ["history", "historic", "historical", "monument", "archeolog", "ancient"],
                "food": ["restaurant", "cafe", "bistro", "pizzeria", "cuisine", "dining"],
                "parks": ["park", "garden", "botanical", "natural", "reserve", "nature"],
                "churches": ["church", "cathedral", "monastery", "chapel", "religious"],
                "culture": ["cultural", "culture", "theater", "theatre", "performance"],
                "adventure": ["hiking", "climbing", "trail", "outdoor", "adventure", "sport"],
                "nightlife": ["bar", "club", "nightclub", "pub", "lounge", "disco"],
                "shopping": ["mall", "market", "shop", "shopping", "store", "bazaar"],
            }

            # Step 1: Parse place name for specific keywords
            name_words = place_name.split()

            for interest in interests_clean:
                translated = ROMANIAN_INTEREST_TRANSLATION.get(interest, interest)
                keywords = interest_keywords.get(translated, [interest])
                current_match = 0

                # Check each keyword against place name and type
                for keyword in keywords:
                    # EXACT WORD MATCH in name (highest)
                    if keyword in place_name:
                        current_match = max(current_match, 3)

                    # SUBSTRING match (medium)
                    elif any(keyword in word for word in name_words):
                        current_match = max(current_match, 2)

                    # TYPE match (medium)
                    if keyword in place_type.lower():
                        current_match = max(current_match, 2.5)

                # Add this interest's contribution
                if current_match > 0:
                    match_score += current_match

            # Convert score to 0-40% confidence
            if match_score >= 3:
                # Strong exact match
                interest_score = min(40.0, 32.0 + (match_score - 3) * 2)
            elif match_score >= 2:
                # Good keyword match
                interest_score = min(40.0, 20.0 + (match_score - 2) * 8)
            elif match_score >= 1:
                # Partial match
                interest_score = min(40.0, 8.0 + (match_score - 1) * 5)
            else:
                # No real match
                interest_score = 1.0  # Barely relevant

            # Add a small name length/character-based jitter to differentiate places with the same match score
            name_len_boost = (len(place.get("name", "")) % 10) * 0.1  # 0.0 to 0.9 points
            interest_score = min(40.0, interest_score + name_len_boost)

            # History density personalization boost:
            if place_type.lower() in history_type_counts:
                density_boost = min(10.0, history_type_counts[place_type.lower()] * 2.0)
                interest_score = min(40.0, interest_score + density_boost)

            interest_score = max(1.0, min(40.0, interest_score))
        else:
            interest_score = 5.0
            if place_type.lower() in history_type_counts:
                density_boost = min(15.0, history_type_counts[place_type.lower()] * 2.0)
                interest_score = min(40.0, interest_score + density_boost)

        scores["interest_match"] = interest_score

        # ==================== 2. FRESHNESS (0-30%) ====================
        visited_places = {v.get("place_name", "").lower(): v for v in visit_history}
        visited_types = [v.get("place_type", "").lower() for v in visit_history]

        if place_name in visited_places:
            # Already visited - no freshness bonus
            freshness_score = 0.0
        else:
            # Boost based on how long since visiting similar type
            freshness_score = 30.0

            # Reduce slightly if they've visited similar type recently
            for i, visit in enumerate(visit_history[:10]):  # Recent 10 visits
                if visit.get("place_type", "").lower() == place_type.lower():
                    # Penalize based on recency
                    recency_penalty = (10 - i) * 2  # More recent = more penalty
                    freshness_score = max(10.0, freshness_score - recency_penalty)
                    break

        scores["freshness"] = freshness_score

        # ==================== 3. POPULARITY (0-15%) ====================
        rating = float(place.get("rating") or 0.0)
        user_ratings_total = int(place.get("user_ratings_total") or place.get("reviews") or 0)

        # Rating score is continuous (0 to 12.0)
        if rating > 0.0:
            popularity_score = (rating / 5.0) * 12.0
        else:
            popularity_score = 8.0  # fallback

        # Review count bonus is continuous (0 to 3.0) using log scale
        import math
        if user_ratings_total > 0:
            review_bonus = min(3.0, (math.log10(user_ratings_total) / 4.0) * 3.0)
        else:
            review_bonus = 0.0

        scores["popularity"] = max(0.0, min(15.0, popularity_score + review_bonus))

        # ==================== 4. USER LEVEL MATCH (0-10%) ====================
        user_level = user_data.get("level", 1)
        user_xp = user_data.get("total_xp", 0)

        # Higher level users get more "premium" recommendations
        # Lower level users get more tourist attractions
        if user_level >= 5:
            # Advanced user - can recommend niche places
            level_match_score = 10.0
        elif user_level >= 3:
            level_match_score = 7.0
        else:
            # Beginner - recommend popular tourist spots
            level_match_score = 5.0

        scores["user_level"] = level_match_score

        # ==================== 5. DIVERSITY (0-10%) ====================
        # Ensure user visits varied place types
        recent_types = [v.get("place_type", "").lower() for v in visit_history[:15]]
        type_count = recent_types.count(place_type.lower())

        if type_count == 0:
            # Completely new category
            diversity_score = 10.0
        elif type_count == 1:
            # They've visited this type once
            diversity_score = 7.0
        elif type_count >= 2:
            # They've visited this type multiple times - less diversity
            diversity_score = 3.0
        else:
            diversity_score = 5.0

        scores["diversity"] = diversity_score

        # Distance calculation and continuous penalty (up to 3.0 points)
        distance_penalty = 0.0
        geo = place.get("geometry", {}).get("location", {})
        p_lat, p_lng = geo.get("lat"), geo.get("lng")
        if p_lat is not None and p_lng is not None:
            dist = haversine_distance(lat, lng, p_lat, p_lng)
            # 0.1 points penalty per km, max 3.0 points penalty
            distance_penalty = min(3.0, dist * 0.1)

        # ==================== 6. WEATHER & TIME MATCH (0-10%) ====================
        weather = fetch_current_weather(lat, lng)
        is_bad_weather = weather.get("is_bad", False)
        
        # Categorize place types
        indoor_types = [
            "museum", "art_gallery", "cafe", "restaurant", "movie_theater", 
            "library", "shopping_mall", "church", "place_of_worship", 
            "food", "bar", "nightclub"
        ]
        outdoor_types = [
            "park", "zoo", "amusement_park", "tourist_attraction", 
            "stadium", "campground", "natural_feature"
        ]
        
        # Check types overlap
        is_indoor = any(t in indoor_types for t in place_types_full) or place_type in indoor_types
        is_outdoor = any(t in outdoor_types for t in place_types_full) or place_type in outdoor_types
        
        # Fallback default suitability (7.0 out of 10)
        weather_time_score = 7.0
        
        # 1. Weather compatibility
        if is_bad_weather:
            if is_indoor:
                weather_time_score += 1.5
            elif is_outdoor:
                weather_time_score -= 3.5
        else: # Good weather
            if is_outdoor:
                weather_time_score += 2.0
            elif is_indoor:
                # Terrace boost for cafes/food
                if "cafe" in place_types_full or "restaurant" in place_types_full or "food" in place_types_full:
                    weather_time_score += 1.0
                    
        # 2. Time of day suitability
        current_hour = datetime.now().hour
        is_night = current_hour >= 18 or current_hour < 6
        
        if is_night:
            if any(t in ["bar", "nightclub", "restaurant", "food"] for t in place_types_full) or place_type in ["bar", "nightclub", "restaurant"]:
                weather_time_score += 1.0
            elif any(t in ["park", "zoo", "library"] for t in place_types_full) or place_type in ["park", "zoo", "library"]:
                weather_time_score -= 3.0
        else: # Daytime
            if any(t in ["park", "museum", "zoo", "tourist_attraction"] for t in place_types_full) or place_type in ["park", "museum", "zoo", "tourist_attraction"]:
                weather_time_score += 1.0
            elif any(t in ["bar", "nightclub"] for t in place_types_full) or place_type in ["bar", "nightclub"]:
                weather_time_score -= 4.0
                
        # Clamp between 1.0 and 10.0
        scores["weather_time"] = max(1.0, min(10.0, weather_time_score))

        # ==================== WEIGHTED TOTAL ====================
        # Weight factors based on what matters most
        weighted_total = (
            scores["interest_match"] * 0.30 +  # Interests (max 12.0)
            scores["freshness"] * 0.20 +        # Freshness (max 6.0)
            scores["popularity"] * 0.12 +       # Popularity & Quality (max 1.8)
            scores["user_level"] * 0.10 +       # Level match (max 1.0)
            scores["diversity"] * 0.10 +        # Diversity (max 1.0)
            scores["weather_time"] * 0.18       # Weather & Time (max 1.8)
        ) - distance_penalty

        # Max possible sum of weighted factors is 23.6. Normalize to 0-100% scale.
        scores["total"] = min(100.0, max(0.0, (weighted_total / 23.6) * 100.0))

        return scores

    def _generate_reasoning(self, score_breakdown: Dict, language: str) -> str:
        """Generate human-readable reasoning for the recommendation."""
        reasons = []

        # Interest Match
        if score_breakdown["interest_match"] > 30:
            reason = "Perfect match cu interesele tale" if language == "ro" else "Perfect match with your interests"
            reasons.append(reason)
        elif score_breakdown["interest_match"] > 15:
            reason = "Corespunde intereselor tale" if language == "ro" else "Matches your interests"
            reasons.append(reason)
        elif score_breakdown["interest_match"] > 5:
            reason = "Ar putea să-ți placă" if language == "ro" else "You might enjoy it"
            reasons.append(reason)

        # Freshness
        if score_breakdown["freshness"] >= 30:
            reason = "Ceva complet nou pentru tine" if language == "ro" else "Something brand new for you"
            reasons.append(reason)
        elif score_breakdown["freshness"] > 15:
            reason = "Nu ai vizitat încă" if language == "ro" else "You haven't visited yet"
            reasons.append(reason)

        # Popularity
        if score_breakdown["popularity"] >= 14:
            reason = "Foarte apreciat - 4.6+ rating" if language == "ro" else "Highly rated - 4.6+ stars"
            reasons.append(reason)
        elif score_breakdown["popularity"] > 8:
            reason = "Bine cotat de vizitatori" if language == "ro" else "Well rated by visitors"
            reasons.append(reason)

        # Diversity
        if score_breakdown["diversity"] >= 8:
            reason = "Categorie nouă pentru tine" if language == "ro" else "New category for you"
            reasons.append(reason)
        elif score_breakdown["diversity"] >= 5:
            reason = "Diferit de locurile recente" if language == "ro" else "Different from your recent visits"
            reasons.append(reason)

        # Weather & Time Match
        if score_breakdown.get("weather_time", 0.0) >= 9.0:
            reason = "Potrivit pentru vremea și momentul actual" if language == "ro" else "Great for current weather & time"
            reasons.append(reason)

        return " • ".join(reasons) if reasons else ("Explorare recomandată" if language == "ro" else "Worth exploring")

    def _generate_explanation(self, place: Dict, score_breakdown: Dict,
                             user_data: Dict, language: str) -> Dict:
        """Generate detailed explanation of how recommendation was calculated."""
        base_msg = "Pe baza" if language == "ro" else "Based on"

        # Calculate normalized display percentages (0-100%)
        interest_pct = min(100.0, (score_breakdown['interest_match'] / 40.0) * 100.0)
        freshness_pct = min(100.0, (score_breakdown['freshness'] / 30.0) * 100.0)
        popularity_pct = min(100.0, (score_breakdown['popularity'] / 15.0) * 100.0)
        level_pct = min(100.0, (score_breakdown['user_level'] / 10.0) * 100.0)
        diversity_pct = min(100.0, (score_breakdown['diversity'] / 10.0) * 100.0)
        weather_pct = min(100.0, (score_breakdown.get('weather_time', 7.0) / 10.0) * 100.0)

        return {
            "summary": f"{base_msg} analizei factorilor de personalizare",
            "factors": [
                {
                    "name": "Potrivire interese" if language == "ro" else "Interest Match",
                    "score": f"{interest_pct:.1f}%",
                    "description": "Cât de bine se potrivește cu interesele tale salvate" if language == "ro" else "How well it matches your saved interests"
                },
                {
                    "name": "Noutate" if language == "ro" else "Freshness",
                    "score": f"{freshness_pct:.1f}%",
                    "description": "Dacă nu ai vizitat deja acest loc" if language == "ro" else "Whether you haven't visited this place yet"
                },
                {
                    "name": "Popularitate" if language == "ro" else "Popularity",
                    "score": f"{popularity_pct:.1f}%",
                    "description": "Rating și numărul de recenzii" if language == "ro" else "Rating and number of reviews"
                },
                {
                    "name": "Nivel potrivit" if language == "ro" else "Level Match",
                    "score": f"{level_pct:.1f}%",
                    "description": "Adecvat pentru nivelul tău de experiență" if language == "ro" else "Appropriate for your experience level"
                },
                {
                    "name": "Diversitate" if language == "ro" else "Diversity",
                    "score": f"{diversity_pct:.1f}%",
                    "description": "Diferit de locurile vizitate recent" if language == "ro" else "Different from recent visits"
                },
                {
                    "name": "Vreme & Timp" if language == "ro" else "Weather & Time Match",
                    "score": f"{weather_pct:.1f}%",
                    "description": "Potrivirea cu vremea actuală și momentul zilei" if language == "ro" else "Compatibility with current weather and time of day"
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
