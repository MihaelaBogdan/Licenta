package com.cityscape.app.ai;

import android.content.Context;

import com.cityscape.app.database.AppDatabase;
import com.cityscape.app.database.entities.Place;
import com.cityscape.app.database.entities.UserPreference;
import com.cityscape.app.database.entities.Visit;
import com.cityscape.app.database.entities.Favorite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI-powered Recommendation Engine
 * Uses hybrid approach combining:
 * - Content-based filtering (user preferences matching)
 * - Collaborative filtering (similar users analysis)
 * - Popularity and trending scores
 * - Location proximity (for nearby recommendations)
 */
public class RecommendationEngine {

    private static final String TAG = "RecommendationEngine";

    // Weights for hybrid recommendation
    private static final float WEIGHT_CONTENT_BASED = 0.35f;
    private static final float WEIGHT_COLLABORATIVE = 0.25f;
    private static final float WEIGHT_POPULARITY = 0.20f;
    private static final float WEIGHT_TRENDING = 0.10f;
    private static final float WEIGHT_RATING = 0.10f;

    private final AppDatabase database;
    private final CompatibilityCalculator compatibilityCalculator;

    public RecommendationEngine(Context context) {
        this.database = AppDatabase.getDatabase(context);
        this.compatibilityCalculator = new CompatibilityCalculator(context);
    }

    /**
     * Get personalized recommendations for a user in a specific city
     * 
     * @param userId User ID
     * @param cityId City ID
     * @param limit  Maximum number of recommendations
     * @return List of recommended places with scores
     */
    public List<RecommendedPlace> getPersonalizedRecommendations(String userId, String cityId, int limit) {
        List<Place> allPlaces = database.placeDao().getPlacesByCitySync(cityId);
        List<UserPreference> userPrefs = database.userPreferenceDao().getPreferencesByUserSync(userId);
        List<Place> favoritePlaces = database.favoriteDao().getFavoritePlacesSync(userId);
        List<Visit> userVisits = database.visitDao().getVisitsByUserSync(userId);

        // Create set of already visited/favorited place IDs to potentially filter or
        // weight
        Set<String> visitedPlaceIds = new HashSet<>();
        for (Visit visit : userVisits) {
            visitedPlaceIds.add(visit.getPlaceId());
        }

        Set<String> favoritePlaceIds = new HashSet<>();
        for (Place place : favoritePlaces) {
            favoritePlaceIds.add(place.getId());
        }

        // Calculate scores for each place
        List<RecommendedPlace> recommendations = new ArrayList<>();

        for (Place place : allPlaces) {
            float score = calculateRecommendationScore(
                    place,
                    userPrefs,
                    visitedPlaceIds,
                    favoritePlaceIds,
                    userVisits);

            RecommendedPlace rec = new RecommendedPlace(place, score);
            rec.setCompatibilityScore(compatibilityCalculator.calculate(userId, place));
            recommendations.add(rec);
        }

        // Sort by score descending
        Collections.sort(recommendations, (a, b) -> Float.compare(b.getScore(), a.getScore()));

        // Return top N
        return recommendations.subList(0, Math.min(limit, recommendations.size()));
    }

    /**
     * Calculate recommendation score for a place
     */
    private float calculateRecommendationScore(Place place,
            List<UserPreference> userPrefs,
            Set<String> visitedPlaceIds,
            Set<String> favoritePlaceIds,
            List<Visit> userVisits) {

        float contentScore = calculateContentBasedScore(place, userPrefs);
        float collaborativeScore = calculateCollaborativeScore(place, userVisits, favoritePlaceIds);
        float popularityScore = place.getPopularityScore();
        float trendingScore = place.getTrendingScore();
        float ratingScore = place.getRating() / 5.0f; // Normalize to 0-1

        // Apply weights
        float totalScore = (contentScore * WEIGHT_CONTENT_BASED) +
                (collaborativeScore * WEIGHT_COLLABORATIVE) +
                (popularityScore * WEIGHT_POPULARITY) +
                (trendingScore * WEIGHT_TRENDING) +
                (ratingScore * WEIGHT_RATING);

        // Boost score slightly for unvisited places to encourage exploration
        if (!visitedPlaceIds.contains(place.getId())) {
            totalScore *= 1.1f;
        }

        return Math.min(1.0f, totalScore); // Cap at 1.0
    }

    /**
     * Calculate content-based score using user preferences
     */
    private float calculateContentBasedScore(Place place, List<UserPreference> preferences) {
        if (preferences == null || preferences.isEmpty()) {
            return 0.5f; // Neutral score if no preferences
        }

        float totalScore = 0;
        float totalWeight = 0;

        for (UserPreference pref : preferences) {
            float matchScore = 0;

            switch (pref.getPreferenceType()) {
                case UserPreference.TYPE_CATEGORY:
                    if (place.getCategory() != null &&
                            place.getCategory().equalsIgnoreCase(pref.getPreferenceValue())) {
                        matchScore = 1.0f;
                    }
                    break;

                case UserPreference.TYPE_ATMOSPHERE:
                    if (place.getAtmosphereTags() != null &&
                            place.getAtmosphereTags().toLowerCase()
                                    .contains(pref.getPreferenceValue().toLowerCase())) {
                        matchScore = 1.0f;
                    }
                    break;

                case UserPreference.TYPE_PRICE_RANGE:
                    int prefPrice = parsePriceRange(pref.getPreferenceValue());
                    if (prefPrice == place.getPriceLevel()) {
                        matchScore = 1.0f;
                    } else if (Math.abs(prefPrice - place.getPriceLevel()) == 1) {
                        matchScore = 0.5f;
                    }
                    break;

                case UserPreference.TYPE_CUISINE:
                    if (place.getCuisineTags() != null &&
                            place.getCuisineTags().toLowerCase()
                                    .contains(pref.getPreferenceValue().toLowerCase())) {
                        matchScore = 1.0f;
                    }
                    break;

                case UserPreference.TYPE_VIBE:
                    if (place.getAtmosphereTags() != null &&
                            place.getAtmosphereTags().toLowerCase()
                                    .contains(pref.getPreferenceValue().toLowerCase())) {
                        matchScore = 0.8f;
                    }
                    break;
            }

            totalScore += matchScore * pref.getWeight();
            totalWeight += pref.getWeight();
        }

        return totalWeight > 0 ? totalScore / totalWeight : 0.5f;
    }

    /**
     * Calculate collaborative filtering score
     * Based on what similar users have liked
     */
    private float calculateCollaborativeScore(Place place,
            List<Visit> userVisits,
            Set<String> favoritePlaceIds) {
        // Simple collaborative scoring:
        // Higher score if the place is popular among users with similar visit history

        // For MVP, use a simplified version based on place popularity
        // In a full implementation, this would analyze user similarity

        float score = 0;

        // Boost places in same category as user's favorites
        for (String favId : favoritePlaceIds) {
            Place favPlace = database.placeDao().getPlaceByIdSync(favId);
            if (favPlace != null && favPlace.getCategory() != null &&
                    favPlace.getCategory().equals(place.getCategory())) {
                score += 0.2f;
            }
        }

        // Boost places near user's visited places
        for (Visit visit : userVisits) {
            Place visitedPlace = database.placeDao().getPlaceByIdSync(visit.getPlaceId());
            if (visitedPlace != null) {
                double distance = calculateDistance(
                        place.getLatitude(), place.getLongitude(),
                        visitedPlace.getLatitude(), visitedPlace.getLongitude());
                if (distance < 1.0) { // Within 1 km
                    score += 0.1f;
                }
            }
        }

        return Math.min(1.0f, score);
    }

    /**
     * Get nearby recommendations based on location
     */
    public List<RecommendedPlace> getNearbyRecommendations(String userId,
            double latitude,
            double longitude,
            double radiusKm,
            int limit) {
        // Calculate bounding box
        double latDelta = radiusKm / 111.0; // Approximate km per degree latitude
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(latitude)));

        List<Place> nearbyPlaces = database.placeDao().getNearbyPlaces(
                latitude - latDelta, latitude + latDelta,
                longitude - lngDelta, longitude + lngDelta,
                limit * 2 // Get more to filter
        );

        List<UserPreference> userPrefs = database.userPreferenceDao().getPreferencesByUserSync(userId);

        List<RecommendedPlace> recommendations = new ArrayList<>();

        for (Place place : nearbyPlaces) {
            double distance = calculateDistance(latitude, longitude,
                    place.getLatitude(), place.getLongitude());
            if (distance <= radiusKm) {
                float contentScore = calculateContentBasedScore(place, userPrefs);
                float distanceScore = 1.0f - (float) (distance / radiusKm);
                float ratingScore = place.getRating() / 5.0f;

                float totalScore = (contentScore * 0.4f) + (distanceScore * 0.3f) + (ratingScore * 0.3f);

                RecommendedPlace rec = new RecommendedPlace(place, totalScore);
                rec.setDistanceKm(distance);
                rec.setCompatibilityScore(compatibilityCalculator.calculate(userId, place));
                recommendations.add(rec);
            }
        }

        Collections.sort(recommendations, (a, b) -> Float.compare(b.getScore(), a.getScore()));

        return recommendations.subList(0, Math.min(limit, recommendations.size()));
    }

    /**
     * Get trending places in a city
     */
    public List<RecommendedPlace> getTrendingPlaces(String cityId, int limit) {
        List<Place> places = database.placeDao().getPlacesByCitySync(cityId);

        List<RecommendedPlace> trending = new ArrayList<>();
        for (Place place : places) {
            float score = (place.getTrendingScore() * 0.6f) + (place.getRating() / 5.0f * 0.4f);
            trending.add(new RecommendedPlace(place, score));
        }

        Collections.sort(trending, (a, b) -> Float.compare(b.getScore(), a.getScore()));

        return trending.subList(0, Math.min(limit, trending.size()));
    }

    /**
     * Get random recommendation based on user preferences
     */
    public RecommendedPlace getRandomRecommendation(String userId, String cityId) {
        List<String> userCategories = database.userPreferenceDao().getUserCategories(userId);

        if (userCategories == null || userCategories.isEmpty()) {
            // Use all categories if no preferences
            userCategories = new ArrayList<>();
            userCategories.add(Place.CATEGORY_RESTAURANT);
            userCategories.add(Place.CATEGORY_CAFE);
            userCategories.add(Place.CATEGORY_BAR);
        }

        Place randomPlace = database.placeDao().getRandomPlace(cityId, userCategories);

        if (randomPlace != null) {
            RecommendedPlace rec = new RecommendedPlace(randomPlace, 1.0f);
            rec.setCompatibilityScore(compatibilityCalculator.calculate(userId, randomPlace));
            return rec;
        }

        return null;
    }

    /**
     * Calculate distance between two coordinates using Haversine formula
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371; // Earth's radius in km

        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * Parse price range string to integer
     */
    private int parsePriceRange(String priceRange) {
        switch (priceRange.toLowerCase()) {
            case "budget":
            case "cheap":
            case "$":
                return 1;
            case "moderate":
            case "$$":
                return 2;
            case "expensive":
            case "$$$":
                return 3;
            case "luxury":
            case "premium":
            case "$$$$":
                return 4;
            default:
                return 2;
        }
    }

    /**
     * Data class for recommended place with score
     */
    public static class RecommendedPlace {
        private final Place place;
        private final float score;
        private int compatibilityScore; // 0-100
        private double distanceKm;

        public RecommendedPlace(Place place, float score) {
            this.place = place;
            this.score = score;
        }

        public Place getPlace() {
            return place;
        }

        public float getScore() {
            return score;
        }

        public int getCompatibilityScore() {
            return compatibilityScore;
        }

        public void setCompatibilityScore(int compatibilityScore) {
            this.compatibilityScore = compatibilityScore;
        }

        public double getDistanceKm() {
            return distanceKm;
        }

        public void setDistanceKm(double distanceKm) {
            this.distanceKm = distanceKm;
        }
    }
}
