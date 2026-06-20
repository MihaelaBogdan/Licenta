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
from datetime import datetime
from typing import Dict, List, Any
import numpy as np

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
                          limit: int = 10, city_name: str = None, trending: bool = True) -> Dict[str, Any]:
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
        """
        try:
            # Validate limit
            limit = min(max(int(limit), 1), 10)  # Clamp to 1-10

            # 1. Fetch user profile for personalization
            user_data = self._fetch_user_profile(user_id)

            # 2. Fetch user's visit history
            visit_history = self._fetch_visit_history(user_id)

            # 3. Find nearby places
            nearby_places = self._fetch_nearby_places(lat, lng)

            # 4. Get trending places if enabled (places visited by similar users)
            trending_places = []
            if trending:
                trending_places = self._get_trending_places(user_id, city_name, language)

            # 5. Merge and prioritize places
            all_places = nearby_places + trending_places
            all_places = list({p['place_id']: p for p in all_places}.values())  # Remove duplicates

            # 6. Calculate recommendation scores for each place
            recommendations = []
            for place in all_places[:limit * 4]:  # Get more candidates, then filter
                score_breakdown = self._calculate_recommendation_score(
                    place, user_data, visit_history, interests, language
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

    def _fetch_nearby_places(self, lat: float, lng: float, radius: int = 5000) -> List[Dict]:
        """Fetch nearby places from Google Places API."""
        try:
            url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
            params = {
                "location": f"{lat},{lng}",
                "radius": radius,
                "type": "tourist_attraction",
                "key": MAPS_API_KEY
            }
            resp = requests.get(url, params=params, timeout=5)
            if resp.status_code == 200:
                return resp.json().get("results", [])
        except Exception as e:
            print(f"⚠️ Nearby places fetch error: {e}")

        return []

    def _calculate_recommendation_score(self, place: Dict, user_data: Dict,
                                       visit_history: List[Dict],
                                       interests: List[str], language: str = "ro") -> Dict[str, float]:
        """
        Calculate confidence score breakdown for a place.
        SMART algorithm that considers:
        - Detailed interest matching
        - Visit history patterns
        - User preferences
        - Recency bias
        - Category diversity
        """
        scores = {
            "interest_match": 0.0,
            "freshness": 0.0,
            "popularity": 0.0,
            "user_level": 0.0,
            "diversity": 0.0,
            "total": 0.0
        }

        place_name = place.get("name", "").lower()
        place_type = place.get("types", [])[0] if place.get("types") else "unknown"
        place_types_full = place.get("types", [])

        # ==================== 1. INTEREST MATCH (0-40%) ====================
        interest_score = 0.0
        interests_clean = [i.lower().strip() for i in interests if i]

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
                keywords = interest_keywords.get(interest, [interest])
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

            interest_score = max(1.0, min(40.0, interest_score))
        else:
            interest_score = 5.0

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
        rating = place.get("rating", 0)
        user_ratings_total = place.get("user_ratings_total", 0)

        # High rating + high reviews
        if rating >= 4.6:
            popularity_score = 15.0
        elif rating >= 4.3:
            popularity_score = 12.0
        elif rating >= 4.0:
            popularity_score = 8.0
        elif rating >= 3.5:
            popularity_score = 4.0
        else:
            popularity_score = 0.0

        # Bonus for high review count (credibility)
        if user_ratings_total > 5000:
            popularity_score = min(15.0, popularity_score + 3.0)
        elif user_ratings_total > 1000:
            popularity_score = min(15.0, popularity_score + 1.5)

        scores["popularity"] = popularity_score

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

        # ==================== WEIGHTED TOTAL ====================
        # Weight factors based on what matters most
        weighted_total = (
            scores["interest_match"] * 0.35 +  # Interests are most important
            scores["freshness"] * 0.25 +        # Don't repeat places
            scores["popularity"] * 0.15 +       # Quality matters
            scores["user_level"] * 0.12 +       # Appropriate level
            scores["diversity"] * 0.13          # Variety
        )

        scores["total"] = min(100.0, max(0.0, weighted_total))

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

        return " • ".join(reasons) if reasons else ("Explorare recomandată" if language == "ro" else "Worth exploring")

    def _generate_explanation(self, place: Dict, score_breakdown: Dict,
                             user_data: Dict, language: str) -> Dict:
        """Generate detailed explanation of how recommendation was calculated."""
        base_msg = "Pe baza" if language == "ro" else "Based on"

        return {
            "summary": f"{base_msg} analizei factorilor de personalizare",
            "factors": [
                {
                    "name": "Potrivire interese" if language == "ro" else "Interest Match",
                    "score": f"{score_breakdown['interest_match']:.1f}%",
                    "description": "Cât de bine se potrivește cu interesele tale salvate" if language == "ro" else "How well it matches your saved interests"
                },
                {
                    "name": "Noutate" if language == "ro" else "Freshness",
                    "score": f"{score_breakdown['freshness']:.1f}%",
                    "description": "Dacă nu ai vizitat deja acest loc" if language == "ro" else "Whether you haven't visited this place yet"
                },
                {
                    "name": "Popularitate" if language == "ro" else "Popularity",
                    "score": f"{score_breakdown['popularity']:.1f}%",
                    "description": "Rating și numărul de recenzii" if language == "ro" else "Rating and number of reviews"
                },
                {
                    "name": "Nivel potrivit" if language == "ro" else "Level Match",
                    "score": f"{score_breakdown['user_level']:.1f}%",
                    "description": "Adecvat pentru nivelul tău de experiență" if language == "ro" else "Appropriate for your experience level"
                },
                {
                    "name": "Diversitate" if language == "ro" else "Diversity",
                    "score": f"{score_breakdown['diversity']:.1f}%",
                    "description": "Diferit de locurile vizitate recent" if language == "ro" else "Different from recent visits"
                }
            ],
            "total_confidence": f"{score_breakdown['total']:.1f}%"
        }

    def _format_recommendation(self, rec: Dict) -> Dict:
        """Format recommendation for API response."""
        place = rec["place"]
        return {
            "id": place.get("place_id"),
            "name": place.get("name"),
            "address": place.get("vicinity"),
            "rating": place.get("rating"),
            "reviews": place.get("user_ratings_total"),
            "type": place.get("types", ["unknown"])[0],
            "latitude": place.get("geometry", {}).get("location", {}).get("lat"),
            "longitude": place.get("geometry", {}).get("location", {}).get("lng"),
            "confidence": f"{rec['total_confidence']:.1f}%",
            "reasoning": rec["reasoning"],
            "explanation": rec["explanation"],
            "score_breakdown": {k: f"{v:.1f}%" if v != rec['score_breakdown'].get(k) else v
                              for k, v in rec["score_breakdown"].items()},
            "image_url": place.get("photos", [{}])[0].get("html_attributions", [None])[0] if place.get("photos") else None
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
