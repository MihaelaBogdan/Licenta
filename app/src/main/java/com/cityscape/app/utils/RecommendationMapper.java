package com.cityscape.app.utils;

import com.cityscape.app.model.Place;
import org.json.JSONObject;
import org.json.JSONArray;

public class RecommendationMapper {

    public static Place mapRecommendationToPlace(JSONObject recJson) {
        return mapRecommendationFromResponse(recJson);
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
            place.reviewCount = recJson.optInt("reviews", recJson.optInt("user_ratings_total", 0));
            place.latitude = recJson.optDouble("latitude", 0);
            place.longitude = recJson.optDouble("longitude", 0);
            place.imageUrl = recJson.optString("image_url", recJson.optString("imageUrl", ""));

            if (recJson.has("reviews")) {
                try {
                    JSONArray reviewsArray = recJson.optJSONArray("reviews");
                    if (reviewsArray != null) {
                        java.util.List<Place.Review> reviewsList = new java.util.ArrayList<>();
                        for (int i = 0; i < reviewsArray.length(); i++) {
                            JSONObject revObj = reviewsArray.getJSONObject(i);
                            Place.Review review = new Place.Review();
                            review.author = revObj.optString("author", revObj.optString("author_name", "Anonymous"));
                            review.text = revObj.optString("text", "");
                            review.rating = (float) revObj.optDouble("rating", 5.0);
                            review.time = revObj.optString("time", revObj.optString("relative_time_description", ""));
                            review.source = revObj.optString("source", "Google Maps");
                            reviewsList.add(review);
                        }
                        place.reviews = reviewsList;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (recJson.has("photos")) {
                try {
                    JSONArray photosArray = recJson.optJSONArray("photos");
                    if (photosArray != null) {
                        java.util.List<Place.Photo> photosList = new java.util.ArrayList<>();
                        for (int i = 0; i < photosArray.length(); i++) {
                            JSONObject photoObj = photosArray.getJSONObject(i);
                            Place.Photo photo = new Place.Photo();
                            photo.url = photoObj.optString("url", "");
                            photo.source = photoObj.optString("source", "");
                            photosList.add(photo);
                        }
                        place.photos = photosList;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            place.aiSuggestion = recJson.optString("reasoning", "");

            // Default percentages
            place.matchHistoryPct = 50f;
            place.matchPrefsPct = 50f;
            place.freshnessPct = 50f;
            place.popularityPct = 50f;
            place.userLevelPct = 50f;
            place.diversityPct = 50f;
            place.weatherMatchPct = 50f;

            String confStr = recJson.optString("confidence", "50%");
            place.confidence = extractPercentage(confStr);

            // Parse detailed explanations if available
            if (recJson.has("explanation")) {
                try {
                    JSONObject explanation = recJson.getJSONObject("explanation");
                    if (explanation.has("factors")) {
                        JSONArray factors = explanation.getJSONArray("factors");
                        for (int i = 0; i < factors.length(); i++) {
                            JSONObject factor = factors.getJSONObject(i);
                            String name = factor.optString("name", "");
                            String scoreStr = factor.optString("score", "0%");
                            float score = extractPercentage(scoreStr);

                            if (name.toLowerCase().contains("interes")) {
                                place.matchPrefsPct = score;
                            } else if (name.toLowerCase().contains("noutate") || name.toLowerCase().contains("fresh")) {
                                place.freshnessPct = score;
                                place.matchHistoryPct = score; // Sync history percentage with freshness
                            } else if (name.toLowerCase().contains("popular") || name.toLowerCase().contains("rating")) {
                                place.popularityPct = score;
                            } else if (name.toLowerCase().contains("nivel") || name.toLowerCase().contains("level")) {
                                place.userLevelPct = score;
                            } else if (name.toLowerCase().contains("diversit") || name.toLowerCase().contains("diver")) {
                                place.diversityPct = score;
                            } else if (name.toLowerCase().contains("vreme") || name.toLowerCase().contains("weather") || name.toLowerCase().contains("timp")) {
                                place.weatherMatchPct = score;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return place;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static float extractPercentage(String percentStr) {
        try {
            String numStr = percentStr.replace("%", "").replace(",", ".").trim();
            return Float.parseFloat(numStr);
        } catch (Exception e) {
            return 0.0f;
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
