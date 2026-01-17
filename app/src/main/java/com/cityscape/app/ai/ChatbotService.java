package com.cityscape.app.ai;

import android.content.Context;

import com.cityscape.app.database.AppDatabase;
import com.cityscape.app.database.entities.Place;
import com.cityscape.app.database.entities.City;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple chatbot service for providing recommendations through conversation
 * Parses user queries and returns relevant suggestions
 */
public class ChatbotService {

    private final AppDatabase database;
    private final RecommendationEngine recommendationEngine;
    private String currentCityId;
    private String currentUserId;

    // Intent patterns
    private static final Pattern PATTERN_FIND = Pattern.compile(
            "(?i)(find|show|recommend|suggest|where|get).*?(\\w+)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_CATEGORY = Pattern.compile(
            "(?i)(restaurant|cafe|coffee|bar|club|museum|park|shop|mall)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_ATMOSPHERE = Pattern.compile(
            "(?i)(romantic|quiet|cozy|modern|trendy|cheap|expensive|luxury|family)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_NEARBY = Pattern.compile(
            "(?i)(near|nearby|close|around|here)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_BEST = Pattern.compile(
            "(?i)(best|top|popular|trending|famous)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_RANDOM = Pattern.compile(
            "(?i)(random|surprise|anything|whatever)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_GREETING = Pattern.compile(
            "(?i)(hi|hello|hey|good morning|good evening|howdy)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_HELP = Pattern.compile(
            "(?i)(help|what can you|how do|assist)", Pattern.CASE_INSENSITIVE);

    public ChatbotService(Context context) {
        this.database = AppDatabase.getDatabase(context);
        this.recommendationEngine = new RecommendationEngine(context);
    }

    /**
     * Set current context (user and city)
     */
    public void setContext(String userId, String cityId) {
        this.currentUserId = userId;
        this.currentCityId = cityId;
    }

    /**
     * Process user message and return response
     */
    public ChatResponse processMessage(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return new ChatResponse("I didn't catch that. Could you please repeat?", null);
        }

        String message = userMessage.trim().toLowerCase();

        // Check for greeting
        if (PATTERN_GREETING.matcher(message).find()) {
            return handleGreeting();
        }

        // Check for help
        if (PATTERN_HELP.matcher(message).find()) {
            return handleHelp();
        }

        // Check for random/surprise request
        if (PATTERN_RANDOM.matcher(message).find()) {
            return handleRandomRequest();
        }

        // Check for category-specific request
        Matcher categoryMatcher = PATTERN_CATEGORY.matcher(message);
        if (categoryMatcher.find()) {
            String category = mapToCategory(categoryMatcher.group(1));
            boolean isBest = PATTERN_BEST.matcher(message).find();
            return handleCategoryRequest(category, isBest);
        }

        // Check for atmosphere-based request
        Matcher atmosphereMatcher = PATTERN_ATMOSPHERE.matcher(message);
        if (atmosphereMatcher.find()) {
            String atmosphere = atmosphereMatcher.group(1);
            return handleAtmosphereRequest(atmosphere);
        }

        // Check for nearby request
        if (PATTERN_NEARBY.matcher(message).find()) {
            return handleNearbyRequest();
        }

        // Check for best/top places
        if (PATTERN_BEST.matcher(message).find()) {
            return handleBestPlacesRequest();
        }

        // Default response
        return handleDefaultResponse(message);
    }

    /**
     * Handle greeting
     */
    private ChatResponse handleGreeting() {
        City city = database.cityDao().getCityByIdSync(currentCityId);
        String cityName = city != null ? city.getName() : "your city";

        String greeting = TrendAnalyzer.getTimeBasedGreeting();
        String response = String.format(
                "%s! 👋 I'm your CityScape assistant. " +
                        "I can help you discover amazing places in %s. " +
                        "Try asking me for restaurant recommendations, trendy cafes, or somewhere romantic!",
                greeting, cityName);

        return new ChatResponse(response, null);
    }

    /**
     * Handle help request
     */
    private ChatResponse handleHelp() {
        String response = "Here's what I can help you with:\n\n" +
                "🍽️ **Restaurants** - \"Find a good restaurant\"\n" +
                "☕ **Cafes** - \"Suggest a cozy cafe\"\n" +
                "🍺 **Bars** - \"Where can I get drinks?\"\n" +
                "🎭 **Culture** - \"Show me museums\"\n" +
                "🌳 **Nature** - \"Recommend a park\"\n" +
                "💑 **Romantic** - \"Something romantic for a date\"\n" +
                "✨ **Best places** - \"What's the best restaurant?\"\n" +
                "🎲 **Surprise me** - \"Pick something random!\"\n\n" +
                "Just type naturally and I'll understand!";

        return new ChatResponse(response, null);
    }

    /**
     * Handle category-specific request
     */
    private ChatResponse handleCategoryRequest(String category, boolean best) {
        List<Place> places;

        if (best) {
            places = new ArrayList<>();
            List<Place> allPlaces = database.placeDao().getPlacesByCitySync(currentCityId);
            for (Place p : allPlaces) {
                if (p.getCategory().equals(category)) {
                    places.add(p);
                }
            }
            // Sort by rating
            places.sort((a, b) -> Float.compare(b.getRating(), a.getRating()));
            places = places.subList(0, Math.min(3, places.size()));
        } else {
            // Get personalized recommendations for this category
            List<RecommendationEngine.RecommendedPlace> recommendations = recommendationEngine
                    .getPersonalizedRecommendations(currentUserId, currentCityId, 10);

            places = new ArrayList<>();
            for (RecommendationEngine.RecommendedPlace rec : recommendations) {
                if (rec.getPlace().getCategory().equals(category)) {
                    places.add(rec.getPlace());
                    if (places.size() >= 3)
                        break;
                }
            }
        }

        if (places.isEmpty()) {
            return new ChatResponse(
                    "I couldn't find any " + getCategoryDisplayName(category) + " in this city. " +
                            "Would you like to try a different category?",
                    null);
        }

        String categoryName = getCategoryDisplayName(category);
        String response = String.format(
                "Here are some %s %s I found for you:\n\n",
                best ? "top" : "great",
                categoryName);

        for (int i = 0; i < places.size(); i++) {
            Place place = places.get(i);
            response += String.format(
                    "%d. **%s** ⭐ %.1f\n   %s\n\n",
                    i + 1,
                    place.getName(),
                    place.getRating(),
                    place.getAddress() != null ? place.getAddress() : "See details for location");
        }

        response += "Would you like more details about any of these?";

        return new ChatResponse(response, places);
    }

    /**
     * Handle atmosphere-based request
     */
    private ChatResponse handleAtmosphereRequest(String atmosphere) {
        List<Place> places = database.placeDao().getPlacesByAtmosphere(currentCityId, atmosphere);

        if (places == null || places.isEmpty()) {
            return new ChatResponse(
                    "I couldn't find places with a " + atmosphere + " vibe in this city. " +
                            "Try a different atmosphere like cozy, modern, or trendy!",
                    null);
        }

        // Sort by rating and limit to 3
        places.sort((a, b) -> Float.compare(b.getRating(), a.getRating()));
        List<Place> topPlaces = places.subList(0, Math.min(3, places.size()));

        String response = String.format(
                "Looking for something %s? Here are my picks:\n\n",
                atmosphere);

        for (int i = 0; i < topPlaces.size(); i++) {
            Place place = topPlaces.get(i);
            response += String.format(
                    "%d. **%s** ⭐ %.1f\n   %s • %s\n\n",
                    i + 1,
                    place.getName(),
                    place.getRating(),
                    getCategoryDisplayName(place.getCategory()),
                    place.getPriceLevelString());
        }

        return new ChatResponse(response, topPlaces);
    }

    /**
     * Handle nearby request
     */
    private ChatResponse handleNearbyRequest() {
        String response = "To show you nearby places, please enable location services " +
                "and tap the 'Discover Nearby' button on the map. " +
                "I'll be able to give you personalized recommendations based on your location!";

        return new ChatResponse(response, null);
    }

    /**
     * Handle best places request
     */
    private ChatResponse handleBestPlacesRequest() {
        List<Place> places = database.placeDao().getPlacesByCitySync(currentCityId);
        places.sort((a, b) -> Float.compare(b.getRating(), a.getRating()));
        List<Place> topPlaces = places.subList(0, Math.min(5, places.size()));

        City city = database.cityDao().getCityByIdSync(currentCityId);
        String cityName = city != null ? city.getName() : "this city";

        String response = String.format(
                "🏆 Here are the top-rated places in %s:\n\n",
                cityName);

        for (int i = 0; i < topPlaces.size(); i++) {
            Place place = topPlaces.get(i);
            response += String.format(
                    "%d. **%s** ⭐ %.1f\n   %s\n\n",
                    i + 1,
                    place.getName(),
                    place.getRating(),
                    getCategoryDisplayName(place.getCategory()));
        }

        return new ChatResponse(response, topPlaces);
    }

    /**
     * Handle random/surprise request
     */
    private ChatResponse handleRandomRequest() {
        RecommendationEngine.RecommendedPlace recommendation = recommendationEngine
                .getRandomRecommendation(currentUserId, currentCityId);

        if (recommendation == null) {
            return new ChatResponse(
                    "Oops! I couldn't find a surprise for you right now. " +
                            "Try searching for a specific category instead!",
                    null);
        }

        Place place = recommendation.getPlace();

        String response = String.format(
                "🎲 Surprise! How about **%s**?\n\n" +
                        "⭐ Rating: %.1f\n" +
                        "📍 Category: %s\n" +
                        "💰 Price: %s\n" +
                        "🎯 Compatibility: %d%%\n\n" +
                        "Sounds good? Tap to see more details!",
                place.getName(),
                place.getRating(),
                getCategoryDisplayName(place.getCategory()),
                place.getPriceLevelString(),
                recommendation.getCompatibilityScore());

        List<Place> places = new ArrayList<>();
        places.add(place);

        return new ChatResponse(response, places);
    }

    /**
     * Handle default/unknown request
     */
    private ChatResponse handleDefaultResponse(String message) {
        // Try to give a helpful response based on context
        String response = "I'm not sure what you're looking for. " +
                "Try asking me to:\n" +
                "• Find a restaurant\n" +
                "• Suggest a cozy cafe\n" +
                "• Show the best bars\n" +
                "• Surprise you with something random\n\n" +
                "Or type 'help' for more options!";

        return new ChatResponse(response, null);
    }

    /**
     * Map user input to standard category
     */
    private String mapToCategory(String input) {
        if (input == null)
            return Place.CATEGORY_RESTAURANT;

        switch (input.toLowerCase()) {
            case "restaurant":
            case "food":
            case "eat":
                return Place.CATEGORY_RESTAURANT;
            case "cafe":
            case "coffee":
                return Place.CATEGORY_CAFE;
            case "bar":
            case "pub":
            case "drinks":
                return Place.CATEGORY_BAR;
            case "club":
            case "nightclub":
            case "party":
                return Place.CATEGORY_NIGHTLIFE;
            case "museum":
            case "gallery":
            case "art":
                return Place.CATEGORY_CULTURE;
            case "park":
            case "garden":
            case "nature":
                return Place.CATEGORY_NATURE;
            case "shop":
            case "mall":
            case "shopping":
                return Place.CATEGORY_SHOPPING;
            default:
                return Place.CATEGORY_RESTAURANT;
        }
    }

    /**
     * Get display name for category
     */
    private String getCategoryDisplayName(String category) {
        if (category == null)
            return "places";

        switch (category) {
            case Place.CATEGORY_RESTAURANT:
                return "restaurants";
            case Place.CATEGORY_CAFE:
                return "cafes";
            case Place.CATEGORY_BAR:
                return "bars";
            case Place.CATEGORY_NIGHTLIFE:
                return "nightlife spots";
            case Place.CATEGORY_CULTURE:
                return "cultural attractions";
            case Place.CATEGORY_NATURE:
                return "parks & nature spots";
            case Place.CATEGORY_SHOPPING:
                return "shopping destinations";
            case Place.CATEGORY_ENTERTAINMENT:
                return "entertainment venues";
            case Place.CATEGORY_WELLNESS:
                return "wellness centers";
            default:
                return "places";
        }
    }

    /**
     * Chat response data class
     */
    public static class ChatResponse {
        private final String message;
        private final List<Place> places;

        public ChatResponse(String message, List<Place> places) {
            this.message = message;
            this.places = places;
        }

        public String getMessage() {
            return message;
        }

        public List<Place> getPlaces() {
            return places;
        }

        public boolean hasPlaces() {
            return places != null && !places.isEmpty();
        }
    }
}
