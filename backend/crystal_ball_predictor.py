"""
Crystal Ball Predictor - AI-powered prediction of user's next interests.
Analyzes behavior patterns and predicts what they'll want to explore next.
"""

import os
from datetime import datetime, timedelta
from typing import Dict, List, Any
import requests

SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_KEY")
MAPS_API_KEY = os.getenv("MAPS_API_KEY")


TYPE_TRANSLATIONS = {
    "ro": {
        "museum": "muzee",
        "museums": "muzee",
        "restaurant": "restaurante",
        "restaurants": "restaurante",
        "cafe": "cafenele",
        "cafes": "cafenele",
        "park": "parcuri",
        "parks": "parcuri",
        "church": "biserici",
        "churches": "biserici",
        "shopping": "cumpărături",
        "theater": "teatre",
        "theaters": "teatre",
        "gallery": "galerii de artă",
        "galleries": "galerii de artă",
        "monument": "monumente",
        "monuments": "monumente",
        "market": "piețe",
        "markets": "piețe",
        "hotel": "hoteluri",
        "hotels": "hoteluri",
        "bar": "baruri",
        "bars": "baruri",
        "general": "atracții",
        "general attraction": "atracții generale",
        "tourist attraction": "atracții turistice",
        "tourist_attraction": "atracții turistice"
    },
    "en": {
        "museum": "museums",
        "museums": "museums",
        "restaurant": "restaurants",
        "restaurants": "restaurants",
        "cafe": "cafes",
        "cafes": "cafes",
        "park": "parks",
        "parks": "parks",
        "church": "churches",
        "churches": "churches",
        "shopping": "shopping",
        "theater": "theaters",
        "theaters": "theaters",
        "gallery": "galleries",
        "galleries": "galleries",
        "monument": "monuments",
        "monuments": "monuments",
        "market": "markets",
        "markets": "markets",
        "hotel": "hotels",
        "hotels": "hotels",
        "bar": "bars",
        "bars": "bars",
        "general": "attractions",
        "general attraction": "general attractions",
        "tourist attraction": "tourist attractions",
        "tourist_attraction": "tourist attractions"
    }
}

def translate_type(t: str, language: str) -> str:
    lang = "en" if language == "en" else "ro"
    t_lower = t.lower().strip()
    return TYPE_TRANSLATIONS[lang].get(t_lower, t)


class CrystalBallPredictor:
    """Predicts user's next interests based on visit patterns."""

    def __init__(self):
        self.supabase_headers = {
            "apikey": SUPABASE_KEY,
            "Authorization": f"Bearer {SUPABASE_KEY}"
        }

    def predict_next_interests(self, user_id: str, language: str = "ro") -> Dict[str, Any]:
        """
        Predict what the user will be interested in next.
        Analyzes: frequency, recency, diversity, patterns.
        """
        try:
            # Fetch user's visit history
            url = f"{SUPABASE_URL}/rest/v1/visited_places?user_id=eq.{user_id}&select=*&order=visited_at.desc&limit=50"
            resp = requests.get(url, headers=self.supabase_headers, timeout=5)
            history = resp.json() if resp.status_code == 200 else []

            if not history:
                return self._get_generic_prediction(language)

            # Analyze patterns
            predictions = self._analyze_patterns(history, language)

            return {
                "status": "success",
                "user_id": user_id,
                "timestamp": datetime.now().isoformat(),
                "predictions": predictions
            }
        except Exception as e:
            print(f"❌ Crystal Ball error: {e}")
            return self._get_generic_prediction(language)

    def _analyze_patterns(self, history: List[Dict], language: str) -> List[Dict]:
        """Analyze visit history to predict next interests."""
        predictions = []

        # Count place types
        type_counts = {}
        recent_types = []

        for visit in history:
            place_type = visit.get("place_type", "general").lower()
            type_counts[place_type] = type_counts.get(place_type, 0) + 1

            # Track recent (last 10 visits)
            if len(recent_types) < 10:
                recent_types.append(place_type)

        # Identify patterns
        total_visits = len(history)

        # 1. TRENDING - What they visit most
        if type_counts:
            sorted_types = sorted(type_counts.items(), key=lambda x: x[1], reverse=True)

            # Top visited type
            if sorted_types:
                top_type = sorted_types[0][0]
                count = sorted_types[0][1]
                percentage = (count / total_visits) * 100

                predictions.append({
                    "category": "trending",
                    "type": translate_type(top_type, language),
                    "confidence": f"{min(100, 60 + percentage / 2):.1f}%",
                    "reason": f"Favoritul tău: {count} vizite la {translate_type(top_type, language)}" if language == "ro" else f"Your favorite: {count} visits to {translate_type(top_type, language)}",
                    "icon": "🔥",
                    "probability": min(100, 60 + percentage / 2)
                })

        # 2. VARIETY - What's missing
        missing_types = self._identify_missing_types(type_counts)
        for missing_type in missing_types[:3]:
            confidence = 50 + (10 - len(recent_types)) * 5
            predictions.append({
                "category": "variety",
                "type": translate_type(missing_type, language),
                "confidence": f"{min(100, confidence):.1f}%",
                "reason": f"Nu ai explorat prea mult {translate_type(missing_type, language)} în ultima vreme" if language == "ro" else f"You haven't explored {translate_type(missing_type, language)} much lately",
                "icon": "✨",
                "probability": min(100, confidence)
            })

        # 3. CYCLE - What comes next in pattern
        if len(recent_types) >= 3:
            next_prediction = self._predict_next_in_cycle(recent_types)
            if next_prediction:
                predictions.append({
                    "category": "cycle",
                    "type": translate_type(next_prediction, language),
                    "confidence": "72.5%",
                    "reason": "Tiparul sugerează că vei vizita asta în continuare" if language == "ro" else "Pattern suggests you'll visit this next",
                    "icon": "🎯",
                    "probability": 72.5
                })

        # 4. DISCOVERY - Random but matching interests
        if len(history) > 5:
            user_interests = self._extract_interests(history)
            if user_interests:
                predictions.append({
                    "category": "discovery",
                    "type": f"{translate_type(user_interests[0], language)} (nou)" if language == "ro" else f"{translate_type(user_interests[0], language)} (new style)",
                    "confidence": "55.0%",
                    "reason": "Ceva nou în categoria ta preferată" if language == "ro" else "Something new in your favorite category",
                    "icon": "🌟",
                    "probability": 55.0
                })

        # Sort by probability
        predictions.sort(key=lambda x: x.get("probability", 0), reverse=True)

        return predictions[:5]  # Top 5 predictions

    def _identify_missing_types(self, type_counts: Dict) -> List[str]:
        """Find place types user hasn't visited much."""
        common_types = [
            "museum", "restaurant", "cafe", "park",
            "church", "shopping", "theater", "gallery",
            "monument", "market", "hotel", "bar"
        ]

        missing = []
        for place_type in common_types:
            if place_type not in type_counts or type_counts[place_type] < 2:
                missing.append(place_type)

        return missing

    def _predict_next_in_cycle(self, recent_types: List[str]) -> str:
        """Predict next type based on cycling pattern."""
        if len(recent_types) < 3:
            return None

        # Check if there's a pattern
        # e.g., museum -> restaurant -> park -> museum (cycling)
        if recent_types[0] != recent_types[1]:
            # If last two are different, next might be something new
            return "restaurant" if "restaurant" not in recent_types else "park"

        return None

    def _extract_interests(self, history: List[Dict]) -> List[str]:
        """Extract main interests from visit history."""
        type_counts = {}
        for visit in history:
            place_type = visit.get("place_type", "").lower()
            type_counts[place_type] = type_counts.get(place_type, 0) + 1

        if type_counts:
            top_interests = sorted(type_counts.items(), key=lambda x: x[1], reverse=True)
            return [t[0] for t in top_interests[:3]]

        return []

    def _get_generic_prediction(self, language: str) -> Dict[str, Any]:
        """Generic predictions for new users."""
        if language == "en":
            predictions = [
                {
                    "category": "trending",
                    "type": "museums",
                    "confidence": "65.0%",
                    "reason": "Popular starting point",
                    "icon": "🏛️",
                    "probability": 65.0
                },
                {
                    "category": "variety",
                    "type": "parks",
                    "confidence": "55.0%",
                    "reason": "Balance your exploration",
                    "icon": "🌳",
                    "probability": 55.0
                },
                {
                    "category": "discovery",
                    "type": "restaurants",
                    "confidence": "60.0%",
                    "reason": "Everyone loves good food",
                    "icon": "🍽️",
                    "probability": 60.0
                }
            ]
            message = "Start exploring to unlock personalized predictions!"
        else:
            predictions = [
                {
                    "category": "trending",
                    "type": "muzee",
                    "confidence": "65.0%",
                    "reason": "Punct de plecare popular",
                    "icon": "🏛️",
                    "probability": 65.0
                },
                {
                    "category": "variety",
                    "type": "parcuri",
                    "confidence": "55.0%",
                    "reason": "Echilibrează-ți explorarea",
                    "icon": "🌳",
                    "probability": 55.0
                },
                {
                    "category": "discovery",
                    "type": "restaurante",
                    "confidence": "60.0%",
                    "reason": "Toată lumea iubește mâncarea bună",
                    "icon": "🍽️",
                    "probability": 60.0
                }
            ]
            message = "Începe să explorezi pentru a debloca predicții personalizate!"

        return {
            "status": "success",
            "predictions": predictions,
            "message": message
        }

    def get_crystal_ball_visualization(self, user_id: str, language: str = "ro") -> Dict[str, Any]:
        """
        Get data for crystal ball visualization.
        Returns: trends, predictions, confidence levels, animations.
        """
        predictions = self.predict_next_interests(user_id, language)

        if predictions.get("status") != "success":
            return predictions

        # Format for visualization
        viz_data = {
            "status": "success",
            "timestamp": datetime.now().isoformat(),
            "crystal_ball": {
                "inner_circle": [],  # Most likely (top prediction)
                "middle_ring": [],   # Probable (2nd-4th)
                "outer_ring": [],    # Possible (5th+)
                "glow_intensity": 0.0
            },
            "timeline": [],
            "confidence_gauge": 0.0
        }

        preds = predictions.get("predictions", [])

        if preds:
            # Inner circle: top prediction (brightest)
            top = preds[0]
            viz_data["crystal_ball"]["inner_circle"] = [top]
            viz_data["crystal_ball"]["glow_intensity"] = top.get("probability", 0) / 100

            # Middle ring: next 3
            viz_data["crystal_ball"]["middle_ring"] = preds[1:4]

            # Outer ring: rest
            viz_data["crystal_ball"]["outer_ring"] = preds[4:]

            # Timeline visualization
            for i, pred in enumerate(preds):
                viz_data["timeline"].append({
                    "position": i + 1,
                    "type": pred.get("type"),
                    "confidence": float(pred.get("confidence", "0").replace("%", "")),
                    "icon": pred.get("icon")
                })

            # Overall confidence
            avg_confidence = sum(
                float(p.get("confidence", "0").replace("%", ""))
                for p in preds
            ) / len(preds)
            viz_data["confidence_gauge"] = avg_confidence

        return viz_data


# Global instance
predictor = CrystalBallPredictor()
