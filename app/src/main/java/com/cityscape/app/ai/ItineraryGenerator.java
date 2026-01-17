package com.cityscape.app.ai;

import android.content.Context;

import com.cityscape.app.database.AppDatabase;
import com.cityscape.app.database.entities.Place;
import com.cityscape.app.database.entities.City;
import com.cityscape.app.database.entities.Itinerary;
import com.cityscape.app.database.entities.UserPreference;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates automatic itineraries for a day in a city
 * "A Perfect Day in [City]" feature
 */
public class ItineraryGenerator {

    private final AppDatabase database;
    private final RecommendationEngine recommendationEngine;

    // Default time slots for a day
    private static final String[] DEFAULT_TIME_SLOTS = {
            "09:00", "10:30", "12:00", "14:00", "16:00", "18:30", "20:30"
    };

    // Default activity types for time slots
    private static final String[] SLOT_CATEGORIES = {
            Place.CATEGORY_CAFE, // Morning coffee
            Place.CATEGORY_CULTURE, // Mid-morning activity
            Place.CATEGORY_RESTAURANT, // Lunch
            Place.CATEGORY_NATURE, // Afternoon relax
            Place.CATEGORY_SHOPPING, // Late afternoon
            Place.CATEGORY_RESTAURANT, // Dinner
            Place.CATEGORY_BAR // Evening drinks
    };

    public ItineraryGenerator(Context context) {
        this.database = AppDatabase.getDatabase(context);
        this.recommendationEngine = new RecommendationEngine(context);
    }

    /**
     * Generate a personalized day itinerary
     * 
     * @param userId    User ID for personalization
     * @param cityId    City ID
     * @param theme     Optional theme (romantic, adventure, culture, etc.)
     * @param maxBudget Maximum budget (0 for no limit)
     * @return Generated itinerary
     */
    public Itinerary generateDayItinerary(String userId, String cityId,
            String theme, int maxBudget) {
        City city = database.cityDao().getCityByIdSync(cityId);
        if (city == null)
            return null;

        List<UserPreference> preferences = database.userPreferenceDao()
                .getPreferencesByUserSync(userId);

        // Create itinerary object
        String itineraryId = UUID.randomUUID().toString();
        String title = "A Perfect Day in " + city.getName();

        Itinerary itinerary = new Itinerary(itineraryId, cityId, title);
        itinerary.setTheme(theme != null ? theme : "general");
        itinerary.setDurationHours(12);
        itinerary.setGenerated(true);
        itinerary.setCreatedBy("system");

        // Adjust categories based on theme
        String[] categories = getThemeCategories(theme);

        // Get places for each time slot
        List<String> selectedPlaceIds = new ArrayList<>();
        List<ScheduleItem> schedule = new ArrayList<>();
        int totalBudget = 0;

        for (int i = 0; i < DEFAULT_TIME_SLOTS.length && i < categories.length; i++) {
            String category = categories[i];

            // Get best place for this slot
            List<Place> candidates = database.placeDao()
                    .getPlacesByCitySync(cityId);

            Place bestPlace = null;
            float bestScore = -1;

            for (Place place : candidates) {
                // Skip already selected places
                if (selectedPlaceIds.contains(place.getId()))
                    continue;

                // Skip if not matching category
                if (!place.getCategory().equals(category))
                    continue;

                // Check budget
                int estimatedCost = getEstimatedCost(place);
                if (maxBudget > 0 && totalBudget + estimatedCost > maxBudget)
                    continue;

                // Calculate score
                float score = calculatePlaceScore(place, preferences, theme);
                if (score > bestScore) {
                    bestScore = score;
                    bestPlace = place;
                }
            }

            if (bestPlace != null) {
                selectedPlaceIds.add(bestPlace.getId());
                totalBudget += getEstimatedCost(bestPlace);

                ScheduleItem item = new ScheduleItem();
                item.time = DEFAULT_TIME_SLOTS[i];
                item.placeId = bestPlace.getId();
                item.placeName = bestPlace.getName();
                item.category = category;
                item.duration = getEstimatedDuration(category);
                schedule.add(item);
            }
        }

        // Set itinerary properties
        itinerary.setPlaceIds(String.join(",", selectedPlaceIds));
        itinerary.setEstimatedBudget(totalBudget);
        itinerary.setSchedule(scheduleToJson(schedule));
        itinerary.setDescription(generateDescription(city.getName(), theme, schedule.size()));

        return itinerary;
    }

    /**
     * Get categories based on theme
     */
    private String[] getThemeCategories(String theme) {
        if (theme == null)
            return SLOT_CATEGORIES;

        switch (theme.toLowerCase()) {
            case "romantic":
                return new String[] {
                        Place.CATEGORY_CAFE, Place.CATEGORY_NATURE,
                        Place.CATEGORY_RESTAURANT, Place.CATEGORY_CULTURE,
                        Place.CATEGORY_SHOPPING, Place.CATEGORY_RESTAURANT,
                        Place.CATEGORY_BAR
                };

            case "adventure":
                return new String[] {
                        Place.CATEGORY_CAFE, Place.CATEGORY_NATURE,
                        Place.CATEGORY_RESTAURANT, Place.CATEGORY_ENTERTAINMENT,
                        Place.CATEGORY_NATURE, Place.CATEGORY_RESTAURANT,
                        Place.CATEGORY_NIGHTLIFE
                };

            case "culture":
                return new String[] {
                        Place.CATEGORY_CAFE, Place.CATEGORY_CULTURE,
                        Place.CATEGORY_RESTAURANT, Place.CATEGORY_CULTURE,
                        Place.CATEGORY_CAFE, Place.CATEGORY_RESTAURANT,
                        Place.CATEGORY_CULTURE
                };

            case "foodie":
                return new String[] {
                        Place.CATEGORY_CAFE, Place.CATEGORY_RESTAURANT,
                        Place.CATEGORY_CAFE, Place.CATEGORY_RESTAURANT,
                        Place.CATEGORY_CAFE, Place.CATEGORY_RESTAURANT,
                        Place.CATEGORY_BAR
                };

            case "nightlife":
                return new String[] {
                        Place.CATEGORY_CAFE, Place.CATEGORY_SHOPPING,
                        Place.CATEGORY_RESTAURANT, Place.CATEGORY_CAFE,
                        Place.CATEGORY_ENTERTAINMENT, Place.CATEGORY_RESTAURANT,
                        Place.CATEGORY_NIGHTLIFE
                };

            case "budget":
                return new String[] {
                        Place.CATEGORY_CAFE, Place.CATEGORY_NATURE,
                        Place.CATEGORY_RESTAURANT, Place.CATEGORY_NATURE,
                        Place.CATEGORY_CAFE, Place.CATEGORY_RESTAURANT,
                        Place.CATEGORY_BAR
                };

            default:
                return SLOT_CATEGORIES;
        }
    }

    /**
     * Calculate score for a place in itinerary context
     */
    private float calculatePlaceScore(Place place, List<UserPreference> preferences, String theme) {
        float score = 0;

        // Base score from rating
        score += (place.getRating() / 5.0f) * 0.3f;

        // Popularity
        score += place.getPopularityScore() * 0.2f;

        // Theme match
        if (theme != null && place.getAtmosphereTags() != null) {
            if (place.getAtmosphereTags().toLowerCase().contains(theme.toLowerCase())) {
                score += 0.2f;
            }
        }

        // User preference match
        if (preferences != null) {
            for (UserPreference pref : preferences) {
                if (pref.getPreferenceType().equals(UserPreference.TYPE_ATMOSPHERE)) {
                    if (place.getAtmosphereTags() != null &&
                            place.getAtmosphereTags().toLowerCase()
                                    .contains(pref.getPreferenceValue().toLowerCase())) {
                        score += 0.1f * pref.getWeight();
                    }
                }
            }
        }

        return Math.min(1.0f, score);
    }

    /**
     * Get estimated cost for a place
     */
    private int getEstimatedCost(Place place) {
        int priceLevel = place.getPriceLevel();
        String category = place.getCategory();

        int baseCost = switch (category) {
            case Place.CATEGORY_CAFE -> 15;
            case Place.CATEGORY_RESTAURANT -> 40;
            case Place.CATEGORY_BAR -> 25;
            case Place.CATEGORY_CULTURE -> 20;
            case Place.CATEGORY_ENTERTAINMENT -> 30;
            case Place.CATEGORY_SHOPPING -> 50;
            default -> 20;
        };

        // Adjust by price level
        return baseCost * priceLevel / 2;
    }

    /**
     * Get estimated duration for activity type
     */
    private int getEstimatedDuration(String category) {
        return switch (category) {
            case Place.CATEGORY_CAFE -> 45;
            case Place.CATEGORY_RESTAURANT -> 90;
            case Place.CATEGORY_BAR -> 90;
            case Place.CATEGORY_CULTURE -> 120;
            case Place.CATEGORY_NATURE -> 90;
            case Place.CATEGORY_ENTERTAINMENT -> 120;
            case Place.CATEGORY_SHOPPING -> 60;
            default -> 60;
        };
    }

    /**
     * Generate description for itinerary
     */
    private String generateDescription(String cityName, String theme, int stops) {
        String themeDescription = theme != null ? theme : "perfect";
        return String.format(
                "Discover %s with this %s day itinerary featuring %d carefully selected stops. " +
                        "From morning coffee to evening entertainment, experience the best the city has to offer.",
                cityName, themeDescription, stops);
    }

    /**
     * Convert schedule to JSON string
     */
    private String scheduleToJson(List<ScheduleItem> schedule) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < schedule.size(); i++) {
            ScheduleItem item = schedule.get(i);
            sb.append(String.format(
                    "{\"time\":\"%s\",\"placeId\":\"%s\",\"placeName\":\"%s\",\"category\":\"%s\",\"duration\":%d}",
                    item.time, item.placeId, item.placeName, item.category, item.duration));
            if (i < schedule.size() - 1)
                sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public Itinerary generateForTheme(String userId, String cityId, String theme) {
        // Delegate to main method with no budget limit
        return generateDayItinerary(userId, cityId, theme, 0);
    }

    /**
     * Schedule item data class
     */
    private static class ScheduleItem {
        String time;
        String placeId;
        String placeName;
        String category;
        int duration;
    }
}
