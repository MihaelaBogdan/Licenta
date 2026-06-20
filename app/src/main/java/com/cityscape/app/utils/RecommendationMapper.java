package com.cityscape.app.utils;

import com.cityscape.app.model.Place;
import org.json.JSONObject;
import org.json.JSONArray;

public class RecommendationMapper {

    public static Place mapRecommendationToPlace(JSONObject recJson) {
        try {
            Place place = new Place();

            place.id = recJson.optString("id", "");
            place.googlePlaceId = recJson.optString("id");
            place.name = recJson.optString("name", "Unknown Place");
            place.address = recJson.optString("address", "");
            place.type = recJson.optString("type", "");
            place.rating = (float) recJson.optDouble("rating", 0);
            place.reviewCount = recJson.optInt("reviews", 0);
            place.latitude = recJson.optDouble("latitude", 0);
            place.longitude = recJson.optDouble("longitude", 0);
            place.imageUrl = recJson.optString("imageUrl", recJson.optString("image_url", ""));

            String aiReason = recJson.optString("ai_reason", recJson.optString("aiSuggestion", ""));
            place.aiSuggestion = aiReason;

            int matchHistoryPct = recJson.optInt("match_history_pct", 50);
            int matchPrefsPct = recJson.optInt("match_prefs_pct", 50);
            int totalConfidence = recJson.optInt("confidence", matchHistoryPct + matchPrefsPct) / 2;

            place.matchHistoryPct = matchHistoryPct;
            place.matchPrefsPct = matchPrefsPct;

            if (recJson.has("explanation")) {
                try {
                    JSONObject explanation = recJson.getJSONObject("explanation");
                    if (explanation.has("factors")) {
                        JSONArray factors = explanation.getJSONArray("factors");
                        if (factors.length() > 0) {
                            for (int i = 0; i < factors.length(); i++) {
                                JSONObject factor = factors.getJSONObject(i);
                                String name = factor.optString("name", "");
                                String score = factor.optString("score", "0%");

                                if (name.toLowerCase().contains("interes")) {
                                    place.matchPrefsPct = extractPercentage(score);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    //Fallback already set
                }
            }

            return place;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Place mapRecommendationFromResponse(JSONObject recJson) {
        if (recJson == null) {
            return null;
        }

        try {
            Place place = new Place();

            place.id = recJson.optString("id", "");
            place.googlePlaceId = recJson.optString("id");
            place.name = recJson.optString("name", "");
            place.address = recJson.optString("address", "");
            place.type = recJson.optString("type", "");
            place.rating = (float) recJson.optDouble("rating", 3.5);
            place.reviewCount = recJson.optInt("user_ratings_total", 0);
            place.latitude = recJson.optDouble("latitude", 0);
            place.longitude = recJson.optDouble("longitude", 0);
            place.imageUrl = recJson.optString("imageUrl", "");

            String aiReason = recJson.optString("ai_reason", "");
            place.aiSuggestion = aiReason;

            int matchHistoryPct = recJson.optInt("match_history_pct", 50);
            int matchPrefsPct = recJson.optInt("match_prefs_pct", 50);

            place.matchHistoryPct = matchHistoryPct;
            place.matchPrefsPct = matchPrefsPct;
            place.confidence = (matchHistoryPct + matchPrefsPct) / 2;

            return place;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static int extractPercentage(String percentStr) {
        try {
            String numStr = percentStr.replace("%", "").replace(",", ".").trim();
            float value = Float.parseFloat(numStr);
            return Math.round(value);
        } catch (Exception e) {
            return 0;
        }
    }

    public static java.util.List<Place> mapRecommendationsToPlaces(JSONArray recommendations) {
        java.util.List<Place> places = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < recommendations.length(); i++) {
                JSONObject obj = recommendations.getJSONObject(i);
                Place place = mapRecommendationFromResponse(obj);
                if (place != null && !place.name.isEmpty()) {
                    places.add(place);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return places;
    }

    public static java.util.List<Place> mapToPlaceList(JSONArray jsonArray) {
        return mapRecommendationsToPlaces(jsonArray);
    }
}
