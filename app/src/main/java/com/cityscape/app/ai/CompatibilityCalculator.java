package com.cityscape.app.ai;

import android.content.Context;

import com.cityscape.app.database.AppDatabase;
import com.cityscape.app.database.entities.Place;
import com.cityscape.app.database.entities.UserPreference;
import com.cityscape.app.database.entities.Visit;
import com.cityscape.app.database.entities.Favorite;

import java.util.List;

/**
 * Calculates compatibility score between a user and a place
 * Returns a score from 0-100 representing how well the place matches user
 * preferences
 */
public class CompatibilityCalculator {

    private final AppDatabase database;

    // Weight factors for different compatibility aspects
    private static final float WEIGHT_CATEGORY = 0.30f;
    private static final float WEIGHT_ATMOSPHERE = 0.25f;
    private static final float WEIGHT_PRICE = 0.20f;
    private static final float WEIGHT_RATING = 0.15f;
    private static final float WEIGHT_POPULARITY = 0.10f;

    public CompatibilityCalculator(Context context) {
        this.database = AppDatabase.getDatabase(context);
    }

    /**
     * Calculate compatibility score between user and place
     * 
     * @param userId User ID
     * @param place  Place to evaluate
     * @return Score from 0-100
     */
    public int calculate(String userId, Place place) {
        List<UserPreference> preferences = database.userPreferenceDao()
                .getPreferencesByUserSync(userId);

        if (preferences == null || preferences.isEmpty()) {
            // No preferences: return score based on rating and popularity
            return (int) ((place.getRating() / 5.0f) * 70 +
                    place.getPopularityScore() * 30);
        }

        float categoryScore = calculateCategoryMatch(place, preferences);
        float atmosphereScore = calculateAtmosphereMatch(place, preferences);
        float priceScore = calculatePriceMatch(place, preferences);
        float ratingScore = place.getRating() / 5.0f;
        float popularityScore = place.getPopularityScore();

        float totalScore = (categoryScore * WEIGHT_CATEGORY) +
                (atmosphereScore * WEIGHT_ATMOSPHERE) +
                (priceScore * WEIGHT_PRICE) +
                (ratingScore * WEIGHT_RATING) +
                (popularityScore * WEIGHT_POPULARITY);

        // Convert to 0-100 scale
        int score = Math.round(totalScore * 100);
        return Math.max(0, Math.min(100, score));
    }

    /**
     * Calculate category match score
     */
    private float calculateCategoryMatch(Place place, List<UserPreference> preferences) {
        String placeCategory = place.getCategory();
        if (placeCategory == null)
            return 0.5f;

        for (UserPreference pref : preferences) {
            if (pref.getPreferenceType().equals(UserPreference.TYPE_CATEGORY)) {
                if (placeCategory.equalsIgnoreCase(pref.getPreferenceValue())) {
                    return 1.0f * pref.getWeight();
                }
            }
        }

        return 0.3f; // Partial score for non-matching categories
    }

    /**
     * Calculate atmosphere match score
     */
    private float calculateAtmosphereMatch(Place place, List<UserPreference> preferences) {
        String atmosphereTags = place.getAtmosphereTags();
        if (atmosphereTags == null)
            return 0.5f;

        float matchScore = 0;
        int atmospherePrefs = 0;

        for (UserPreference pref : preferences) {
            if (pref.getPreferenceType().equals(UserPreference.TYPE_ATMOSPHERE) ||
                    pref.getPreferenceType().equals(UserPreference.TYPE_VIBE)) {
                atmospherePrefs++;
                if (atmosphereTags.toLowerCase()
                        .contains(pref.getPreferenceValue().toLowerCase())) {
                    matchScore += pref.getWeight();
                }
            }
        }

        if (atmospherePrefs == 0)
            return 0.5f;
        return matchScore / atmospherePrefs;
    }

    /**
     * Calculate price match score
     */
    private float calculatePriceMatch(Place place, List<UserPreference> preferences) {
        int placePrice = place.getPriceLevel();

        for (UserPreference pref : preferences) {
            if (pref.getPreferenceType().equals(UserPreference.TYPE_PRICE_RANGE)) {
                int prefPrice = parsePriceLevel(pref.getPreferenceValue());

                if (prefPrice == placePrice) {
                    return 1.0f;
                } else if (Math.abs(prefPrice - placePrice) == 1) {
                    return 0.7f;
                } else if (Math.abs(prefPrice - placePrice) == 2) {
                    return 0.4f;
                } else {
                    return 0.1f;
                }
            }
        }

        return 0.5f; // No price preference
    }

    /**
     * Parse price level from string
     */
    private int parsePriceLevel(String priceValue) {
        switch (priceValue.toLowerCase()) {
            case "budget":
            case "cheap":
            case "low":
            case "$":
                return 1;
            case "moderate":
            case "medium":
            case "$$":
                return 2;
            case "expensive":
            case "high":
            case "$$$":
                return 3;
            case "luxury":
            case "premium":
            case "very high":
            case "$$$$":
                return 4;
            default:
                return 2;
        }
    }

    /**
     * Get compatibility label based on score
     */
    public static String getCompatibilityLabel(int score) {
        if (score >= 90)
            return "Perfect Match";
        if (score >= 75)
            return "Great Match";
        if (score >= 60)
            return "Good Match";
        if (score >= 40)
            return "Moderate Match";
        return "Low Match";
    }

    /**
     * Get color resource for compatibility score
     */
    public static int getCompatibilityColor(int score) {
        if (score >= 75)
            return android.graphics.Color.parseColor("#4CAF50"); // Green
        if (score >= 50)
            return android.graphics.Color.parseColor("#FFB74D"); // Orange
        return android.graphics.Color.parseColor("#FF5252"); // Red
    }
}
