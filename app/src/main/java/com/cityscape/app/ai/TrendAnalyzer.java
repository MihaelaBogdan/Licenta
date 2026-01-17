package com.cityscape.app.ai;

import android.content.Context;

import com.cityscape.app.database.AppDatabase;
import com.cityscape.app.database.entities.Place;
import com.cityscape.app.database.entities.Review;
import com.cityscape.app.database.entities.Visit;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Analyzes trends for places based on recent activity
 * Updates trending and popularity scores
 */
public class TrendAnalyzer {

    private final AppDatabase database;

    // Time windows for trend analysis
    private static final long TREND_WINDOW_MS = TimeUnit.DAYS.toMillis(7); // Last 7 days
    private static final long POPULARITY_WINDOW_MS = TimeUnit.DAYS.toMillis(30); // Last 30 days

    // Weight factors
    private static final float WEIGHT_RECENT_VISITS = 0.4f;
    private static final float WEIGHT_RECENT_REVIEWS = 0.3f;
    private static final float WEIGHT_FAVORITES = 0.2f;
    private static final float WEIGHT_SEASONAL = 0.1f;

    public TrendAnalyzer(Context context) {
        this.database = AppDatabase.getDatabase(context);
    }

    /**
     * Update trending scores for all places in a city
     */
    public void updateTrendingScores(String cityId) {
        List<Place> places = database.placeDao().getPlacesByCitySync(cityId);
        long now = System.currentTimeMillis();
        long trendWindowStart = now - TREND_WINDOW_MS;

        for (Place place : places) {
            float trendingScore = calculateTrendingScore(place, trendWindowStart, now);
            database.placeDao().updateTrendingScore(place.getId(), trendingScore);
        }
    }

    /**
     * Update popularity scores for all places in a city
     */
    public void updatePopularityScores(String cityId) {
        List<Place> places = database.placeDao().getPlacesByCitySync(cityId);
        long now = System.currentTimeMillis();
        long popularityWindowStart = now - POPULARITY_WINDOW_MS;

        for (Place place : places) {
            float popularityScore = calculatePopularityScore(place, popularityWindowStart, now);
            database.placeDao().updatePopularityScore(place.getId(), popularityScore);
        }
    }

    /**
     * Calculate trending score for a place
     */
    private float calculateTrendingScore(Place place, long windowStart, long now) {
        // Get recent reviews
        List<Review> recentReviews = database.reviewDao().getReviewsByPlaceSync(place.getId());
        int reviewsInWindow = 0;
        float avgRecentRating = 0;

        for (Review review : recentReviews) {
            if (review.getCreatedAt() >= windowStart) {
                reviewsInWindow++;
                avgRecentRating += review.getRating();
            }
        }

        if (reviewsInWindow > 0) {
            avgRecentRating /= reviewsInWindow;
        }

        // Calculate review velocity (reviews per day)
        float reviewVelocity = reviewsInWindow / 7.0f;

        // Normalize scores
        float reviewVelocityScore = Math.min(1.0f, reviewVelocity / 2.0f); // 2+ reviews/day = max
        float ratingBoost = avgRecentRating / 5.0f;

        // Seasonal boost based on current season
        float seasonalBoost = getSeasonalBoost(place.getCategory());

        // Combine factors
        float trendingScore = (reviewVelocityScore * 0.5f) +
                (ratingBoost * 0.3f) +
                (seasonalBoost * 0.2f);

        return Math.min(1.0f, trendingScore);
    }

    /**
     * Calculate popularity score for a place
     */
    private float calculatePopularityScore(Place place, long windowStart, long now) {
        // Get total review count
        int reviewCount = place.getReviewCount();
        float rating = place.getRating();

        // Calculate favorite count (total, not window-based)
        // In a real implementation, this would be tracked in the database

        // Normalize review count (100+ reviews = max score)
        float reviewCountScore = Math.min(1.0f, reviewCount / 100.0f);

        // Rating component
        float ratingScore = rating / 5.0f;

        // Combine factors
        float popularityScore = (reviewCountScore * 0.4f) + (ratingScore * 0.6f);

        return Math.min(1.0f, popularityScore);
    }

    /**
     * Get seasonal boost for category
     */
    private float getSeasonalBoost(String category) {
        if (category == null)
            return 0.5f;

        Calendar calendar = Calendar.getInstance();
        int month = calendar.get(Calendar.MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        // Summer (June-August): boost outdoor/nature
        if (month >= 5 && month <= 7) {
            if (category.equals(Place.CATEGORY_NATURE))
                return 1.0f;
            if (category.equals(Place.CATEGORY_ENTERTAINMENT))
                return 0.8f;
        }

        // Winter (December-February): boost indoor/cozy places
        if (month == 11 || month <= 1) {
            if (category.equals(Place.CATEGORY_CAFE))
                return 1.0f;
            if (category.equals(Place.CATEGORY_CULTURE))
                return 0.8f;
        }

        // Evening hours: boost nightlife
        if (hour >= 18 || hour <= 2) {
            if (category.equals(Place.CATEGORY_BAR) ||
                    category.equals(Place.CATEGORY_NIGHTLIFE))
                return 1.0f;
        }

        // Meal times: boost restaurants
        if ((hour >= 11 && hour <= 14) || (hour >= 18 && hour <= 21)) {
            if (category.equals(Place.CATEGORY_RESTAURANT))
                return 0.9f;
        }

        // Morning: boost cafes
        if (hour >= 7 && hour <= 11) {
            if (category.equals(Place.CATEGORY_CAFE))
                return 0.9f;
        }

        return 0.5f;
    }

    /**
     * Get recommended places for current time/season
     */
    public String getSuggestedCategory() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int month = calendar.get(Calendar.MONTH);

        // Time-based suggestions
        if (hour >= 7 && hour <= 10) {
            return Place.CATEGORY_CAFE;
        } else if (hour >= 11 && hour <= 14) {
            return Place.CATEGORY_RESTAURANT;
        } else if (hour >= 14 && hour <= 17) {
            if (month >= 5 && month <= 7) {
                return Place.CATEGORY_NATURE;
            }
            return Place.CATEGORY_CAFE;
        } else if (hour >= 18 && hour <= 21) {
            return Place.CATEGORY_RESTAURANT;
        } else if (hour >= 21 || hour <= 2) {
            return Place.CATEGORY_BAR;
        }

        return Place.CATEGORY_CAFE; // Default
    }

    /**
     * Get time-appropriate greeting
     */
    public static String getTimeBasedGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        if (hour >= 5 && hour < 12) {
            return "Good morning";
        } else if (hour >= 12 && hour < 17) {
            return "Good afternoon";
        } else if (hour >= 17 && hour < 21) {
            return "Good evening";
        } else {
            return "Good night";
        }
    }
}
