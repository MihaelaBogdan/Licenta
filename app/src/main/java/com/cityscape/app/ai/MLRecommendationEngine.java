package com.cityscape.app.ai;

import android.content.Context;
import android.util.Log;

import com.cityscape.app.database.AppDatabase;
import com.cityscape.app.database.entities.Place;
import com.cityscape.app.database.entities.UserPreference;
import com.cityscape.app.database.entities.Visit;
import com.cityscape.app.database.entities.Review;
import com.cityscape.app.database.entities.Favorite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Machine Learning-based Recommendation Engine
 * 
 * Uses feature vectors and cosine similarity to calculate compatibility
 * between users and places based on:
 * - User preferences (explicit)
 * - User behavior (visits, favorites, reviews - implicit)
 * - Place attributes (category, atmosphere, price, cuisine)
 * 
 * Algorithm:
 * 1. Build user feature vector from preferences and behavior
 * 2. Build place feature vector from attributes
 * 3. Calculate cosine similarity between vectors
 * 4. Apply learning weights from user history
 */
public class MLRecommendationEngine {

    private static final String TAG = "MLRecommendationEngine";

    private final AppDatabase database;

    // Feature dimensions
    private static final int DIM_CATEGORIES = 9; // restaurant, cafe, bar, nightlife, culture, nature, shopping,
                                                 // entertainment, wellness
    private static final int DIM_ATMOSPHERE = 8; // romantic, quiet, lively, modern, traditional, family, cozy, trendy
    private static final int DIM_PRICE = 4; // 1-4 ($-$$$$)
    private static final int DIM_CUISINE = 10; // italian, asian, local, mexican, american, etc.

    private static final int TOTAL_FEATURES = DIM_CATEGORIES + DIM_ATMOSPHERE + DIM_PRICE + DIM_CUISINE;

    // Category indices
    private static final Map<String, Integer> CATEGORY_INDEX = new HashMap<>();
    private static final Map<String, Integer> ATMOSPHERE_INDEX = new HashMap<>();
    private static final Map<String, Integer> CUISINE_INDEX = new HashMap<>();

    static {
        // Categories
        CATEGORY_INDEX.put("restaurant", 0);
        CATEGORY_INDEX.put("cafe", 1);
        CATEGORY_INDEX.put("bar", 2);
        CATEGORY_INDEX.put("nightlife", 3);
        CATEGORY_INDEX.put("culture", 4);
        CATEGORY_INDEX.put("nature", 5);
        CATEGORY_INDEX.put("shopping", 6);
        CATEGORY_INDEX.put("entertainment", 7);
        CATEGORY_INDEX.put("wellness", 8);

        // Atmospheres
        ATMOSPHERE_INDEX.put("romantic", 0);
        ATMOSPHERE_INDEX.put("quiet", 1);
        ATMOSPHERE_INDEX.put("lively", 2);
        ATMOSPHERE_INDEX.put("modern", 3);
        ATMOSPHERE_INDEX.put("traditional", 4);
        ATMOSPHERE_INDEX.put("family", 5);
        ATMOSPHERE_INDEX.put("cozy", 6);
        ATMOSPHERE_INDEX.put("trendy", 7);

        // Cuisines
        CUISINE_INDEX.put("italian", 0);
        CUISINE_INDEX.put("asian", 1);
        CUISINE_INDEX.put("local", 2);
        CUISINE_INDEX.put("mexican", 3);
        CUISINE_INDEX.put("american", 4);
        CUISINE_INDEX.put("french", 5);
        CUISINE_INDEX.put("mediterranean", 6);
        CUISINE_INDEX.put("indian", 7);
        CUISINE_INDEX.put("japanese", 8);
        CUISINE_INDEX.put("international", 9);
    }

    public MLRecommendationEngine(Context context) {
        this.database = AppDatabase.getDatabase(context);
    }

    /**
     * Calculate ML-based compatibility score between user and place
     * 
     * @return Score from 0-100 representing compatibility
     */
    public MLCompatibilityResult calculateCompatibility(String userId, Place place) {
        // Build feature vectors
        float[] userVector = buildUserFeatureVector(userId);
        float[] placeVector = buildPlaceFeatureVector(place);

        // Calculate cosine similarity
        float similarity = cosineSimilarity(userVector, placeVector);

        // Apply learning weights from user history
        float learningBoost = calculateLearningBoost(userId, place);

        // Combine similarity with rating and popularity
        float ratingFactor = place.getRating() / 5.0f;
        float popularityFactor = place.getPopularityScore();

        // Weighted combination
        float finalScore = (similarity * 0.50f) +
                (learningBoost * 0.25f) +
                (ratingFactor * 0.15f) +
                (popularityFactor * 0.10f);

        // Convert to 0-100 scale
        int compatibilityScore = Math.round(finalScore * 100);
        compatibilityScore = Math.max(0, Math.min(100, compatibilityScore));

        // Create result with breakdown
        MLCompatibilityResult result = new MLCompatibilityResult();
        result.overallScore = compatibilityScore;
        result.similarityScore = Math.round(similarity * 100);
        result.learningScore = Math.round(learningBoost * 100);
        result.matchedFeatures = getMatchedFeatures(userVector, placeVector);
        result.recommendation = getRecommendationText(compatibilityScore);

        return result;
    }

    /**
     * Build user feature vector from preferences and behavior
     */
    private float[] buildUserFeatureVector(String userId) {
        float[] vector = new float[TOTAL_FEATURES];
        Arrays.fill(vector, 0.0f);

        // 1. Get explicit preferences
        List<UserPreference> preferences = database.userPreferenceDao().getPreferencesByUserSync(userId);

        if (preferences != null) {
            for (UserPreference pref : preferences) {
                applyPreferenceToVector(vector, pref);
            }
        }

        // 2. Learn from favorites (implicit preferences)
        List<Place> favorites = database.favoriteDao().getFavoritePlacesSync(userId);
        if (favorites != null) {
            for (Place favPlace : favorites) {
                float[] placeVec = buildPlaceFeatureVector(favPlace);
                // Add 30% of place vector to user vector (implicit learning)
                for (int i = 0; i < TOTAL_FEATURES; i++) {
                    vector[i] += placeVec[i] * 0.3f;
                }
            }
        }

        // 3. Learn from visits (implicit preferences with recency weighting)
        List<Visit> visits = database.visitDao().getVisitsByUserSync(userId);
        if (visits != null) {
            long now = System.currentTimeMillis();
            for (Visit visit : visits) {
                Place visitedPlace = database.placeDao().getPlaceByIdSync(visit.getPlaceId());
                if (visitedPlace != null) {
                    float[] placeVec = buildPlaceFeatureVector(visitedPlace);

                    // Calculate recency weight (more recent = higher weight)
                    long daysSinceVisit = (now - visit.getVisitedAt()) / (1000 * 60 * 60 * 24);
                    float recencyWeight = (float) Math.exp(-daysSinceVisit / 30.0); // Decay over 30 days

                    // Add to user vector
                    for (int i = 0; i < TOTAL_FEATURES; i++) {
                        vector[i] += placeVec[i] * 0.2f * recencyWeight;
                    }
                }
            }
        }

        // Normalize vector
        normalizeVector(vector);

        return vector;
    }

    /**
     * Build place feature vector from attributes
     */
    private float[] buildPlaceFeatureVector(Place place) {
        float[] vector = new float[TOTAL_FEATURES];
        Arrays.fill(vector, 0.0f);

        int offset = 0;

        // 1. Category features (one-hot encoding)
        if (place.getCategory() != null) {
            Integer catIdx = CATEGORY_INDEX.get(place.getCategory().toLowerCase());
            if (catIdx != null) {
                vector[offset + catIdx] = 1.0f;
            }
        }
        offset += DIM_CATEGORIES;

        // 2. Atmosphere features (multi-hot encoding)
        if (place.getAtmosphereTags() != null) {
            String[] tags = place.getAtmosphereTags().toLowerCase().split(",");
            for (String tag : tags) {
                tag = tag.trim();
                Integer atmIdx = ATMOSPHERE_INDEX.get(tag);
                if (atmIdx != null) {
                    vector[offset + atmIdx] = 1.0f;
                }
            }
        }
        offset += DIM_ATMOSPHERE;

        // 3. Price level (one-hot encoding)
        int priceLevel = Math.max(1, Math.min(4, place.getPriceLevel()));
        vector[offset + priceLevel - 1] = 1.0f;
        offset += DIM_PRICE;

        // 4. Cuisine features (multi-hot encoding)
        if (place.getCuisineTags() != null) {
            String[] cuisines = place.getCuisineTags().toLowerCase().split(",");
            for (String cuisine : cuisines) {
                cuisine = cuisine.trim();
                Integer cuisineIdx = CUISINE_INDEX.get(cuisine);
                if (cuisineIdx != null) {
                    vector[offset + cuisineIdx] = 1.0f;
                }
            }
        }

        // Normalize vector
        normalizeVector(vector);

        return vector;
    }

    /**
     * Apply user preference to feature vector
     */
    private void applyPreferenceToVector(float[] vector, UserPreference pref) {
        int offset = 0;
        String value = pref.getPreferenceValue().toLowerCase();
        float weight = pref.getWeight();

        switch (pref.getPreferenceType()) {
            case UserPreference.TYPE_CATEGORY:
                Integer catIdx = CATEGORY_INDEX.get(value);
                if (catIdx != null) {
                    vector[offset + catIdx] += weight;
                }
                break;

            case UserPreference.TYPE_ATMOSPHERE:
            case UserPreference.TYPE_VIBE:
                offset = DIM_CATEGORIES;
                Integer atmIdx = ATMOSPHERE_INDEX.get(value);
                if (atmIdx != null) {
                    vector[offset + atmIdx] += weight;
                }
                break;

            case UserPreference.TYPE_PRICE_RANGE:
                offset = DIM_CATEGORIES + DIM_ATMOSPHERE;
                int priceIdx = parsePriceLevel(value) - 1;
                if (priceIdx >= 0 && priceIdx < DIM_PRICE) {
                    vector[offset + priceIdx] += weight;
                }
                break;

            case UserPreference.TYPE_CUISINE:
                offset = DIM_CATEGORIES + DIM_ATMOSPHERE + DIM_PRICE;
                Integer cuisineIdx = CUISINE_INDEX.get(value);
                if (cuisineIdx != null) {
                    vector[offset + cuisineIdx] += weight;
                }
                break;
        }
    }

    /**
     * Calculate cosine similarity between two vectors
     */
    private float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length)
            return 0;

        float dotProduct = 0;
        float normA = 0;
        float normB = 0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0)
            return 0;

        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Normalize vector to unit length
     */
    private void normalizeVector(float[] vector) {
        float norm = 0;
        for (float v : vector) {
            norm += v * v;
        }

        if (norm > 0) {
            norm = (float) Math.sqrt(norm);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }

    /**
     * Calculate learning boost from user's historical interactions with similar
     * places
     */
    private float calculateLearningBoost(String userId, Place place) {
        float boost = 0;

        // Check if user has visited similar places
        List<Visit> visits = database.visitDao().getVisitsByUserSync(userId);
        if (visits != null) {
            for (Visit visit : visits) {
                Place visitedPlace = database.placeDao().getPlaceByIdSync(visit.getPlaceId());
                if (visitedPlace != null) {
                    // Same category boost
                    if (visitedPlace.getCategory() != null &&
                            visitedPlace.getCategory().equals(place.getCategory())) {
                        boost += 0.2f;
                    }
                }
            }
        }

        // Cap boost at 1.0
        return Math.min(1.0f, boost);
    }

    /**
     * Get list of matched features between user and place
     */
    private List<String> getMatchedFeatures(float[] userVector, float[] placeVector) {
        List<String> matched = new ArrayList<>();

        int offset = 0;

        // Check categories
        for (Map.Entry<String, Integer> entry : CATEGORY_INDEX.entrySet()) {
            int idx = offset + entry.getValue();
            if (userVector[idx] > 0.1f && placeVector[idx] > 0.1f) {
                matched.add("🏷️ " + capitalizeFirst(entry.getKey()));
            }
        }
        offset += DIM_CATEGORIES;

        // Check atmospheres
        for (Map.Entry<String, Integer> entry : ATMOSPHERE_INDEX.entrySet()) {
            int idx = offset + entry.getValue();
            if (userVector[idx] > 0.1f && placeVector[idx] > 0.1f) {
                matched.add("✨ " + capitalizeFirst(entry.getKey()));
            }
        }
        offset += DIM_ATMOSPHERE;

        // Check price
        for (int i = 0; i < DIM_PRICE; i++) {
            int idx = offset + i;
            if (userVector[idx] > 0.1f && placeVector[idx] > 0.1f) {
                matched.add("💰 " + getPriceLevelLabel(i + 1));
            }
        }

        return matched;
    }

    private String capitalizeFirst(String s) {
        if (s == null || s.isEmpty())
            return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private String getPriceLevelLabel(int level) {
        switch (level) {
            case 1:
                return "Budget friendly";
            case 2:
                return "Moderate";
            case 3:
                return "Premium";
            case 4:
                return "Luxury";
            default:
                return "Moderate";
        }
    }

    private int parsePriceLevel(String value) {
        switch (value.toLowerCase()) {
            case "budget":
            case "cheap":
            case "$":
                return 1;
            case "moderate":
            case "medium":
            case "$$":
                return 2;
            case "expensive":
            case "premium":
            case "$$$":
                return 3;
            case "luxury":
            case "$$$$":
                return 4;
            default:
                return 2;
        }
    }

    private String getRecommendationText(int score) {
        if (score >= 90)
            return "🌟 Potrivire perfectă pentru tine!";
        if (score >= 75)
            return "💚 Foarte recomandat";
        if (score >= 60)
            return "👍 O alegere bună";
        if (score >= 40)
            return "🤔 Potrivire moderată";
        return "⚠️ S-ar putea să nu fie pe gustul tău";
    }

    /**
     * Get top ML recommendations for a user
     */
    public List<MLRecommendation> getMLRecommendations(String userId, String cityId, int limit) {
        List<Place> allPlaces = database.placeDao().getPlacesByCitySync(cityId);
        List<MLRecommendation> recommendations = new ArrayList<>();

        for (Place place : allPlaces) {
            MLCompatibilityResult result = calculateCompatibility(userId, place);
            recommendations.add(new MLRecommendation(place, result));
        }

        // Sort by overall score descending
        recommendations.sort((a, b) -> Integer.compare(b.compatibility.overallScore, a.compatibility.overallScore));

        // Return top N
        return recommendations.subList(0, Math.min(limit, recommendations.size()));
    }

    /**
     * Result class containing compatibility breakdown
     */
    public static class MLCompatibilityResult {
        public int overallScore; // 0-100
        public int similarityScore; // Cosine similarity score
        public int learningScore; // Learning boost score
        public List<String> matchedFeatures;
        public String recommendation;
    }

    /**
     * Recommendation with place and compatibility
     */
    public static class MLRecommendation {
        public final Place place;
        public final MLCompatibilityResult compatibility;

        public MLRecommendation(Place place, MLCompatibilityResult compatibility) {
            this.place = place;
            this.compatibility = compatibility;
        }
    }
}
