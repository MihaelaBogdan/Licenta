package com.cityscape.app.api;

import com.cityscape.app.model.Place;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {
        @GET("places")
        Call<List<Place>> getPlaces();

        @GET("nearby")
        Call<List<Place>> getNearby(
                @retrofit2.http.Query("lat") double lat,
                @retrofit2.http.Query("lng") double lng,
                @retrofit2.http.Query("type") String type,
                @retrofit2.http.Query("user_id") String userId);

        @GET("places/{placeId}/details")
        Call<Place> getPlaceDetails(@retrofit2.http.Path("placeId") String placeId);


        @GET("places/search")
        Call<List<Place>> getPlacesSearch(
                        @retrofit2.http.Query("lat") double lat,
                        @retrofit2.http.Query("lng") double lng,
                        @retrofit2.http.Query("query") String query,
                        @retrofit2.http.Query("type") String type,
                        @retrofit2.http.Query("radius") Integer radius,
                        @retrofit2.http.Query("user_id") String userId,
                        @retrofit2.http.Query("city") String city);

        @GET("places/autocomplete")
        Call<List<java.util.Map<String, String>>> getAutocomplete(
                        @retrofit2.http.Query("query") String query,
                        @retrofit2.http.Query("lat") Double lat,
                        @retrofit2.http.Query("lng") Double lng);

        @POST("predict")
        Call<ChatResponse> chat(@Body ChatRequest request);

        @GET("hype/battles")
        Call<com.google.gson.JsonObject> getHypeBattle(@retrofit2.http.Query("user_id") String userId);

        @POST("hype/vote")
        Call<com.google.gson.JsonObject> castHypeVote(@Body Map<String, Object> body);

        @GET("weather")
        Call<WeatherResponse> getWeather(
                        @retrofit2.http.Query("lat") double lat,
                        @retrofit2.http.Query("lng") double lng);

        @GET("itinerary")
        Call<List<ItineraryItem>> getItinerary(
                        @retrofit2.http.Query("lat") double lat,
                        @retrofit2.http.Query("lng") double lng,
                        @retrofit2.http.Query("scope") String scope,
                        @retrofit2.http.Query("radius") Integer radius,
                        @retrofit2.http.Query("type") String type,
                        @retrofit2.http.Query("budget") Integer budget,
                        @retrofit2.http.Query("interests") String interests,
                        @retrofit2.http.Query("duration") Integer duration,
                        @retrofit2.http.Query("points") Integer points,
                        @retrofit2.http.Query("user_id") String userId,
                        @retrofit2.http.Query("travel_mode") String travelMode,
                        @retrofit2.http.Query("start_hour") Integer startHour,
                        @retrofit2.http.Query("companion") String companion,
                        @retrofit2.http.Query("avoid_crowds") Boolean avoidCrowds);

        @GET("itinerary/replace")
        Call<ItineraryItem> replaceItinerarySlot(
                        @retrofit2.http.Query("lat") double lat,
                        @retrofit2.http.Query("lng") double lng,
                        @retrofit2.http.Query("type") String type,
                        @retrofit2.http.Query("label") String label,
                        @retrofit2.http.Query("time") String time,
                        @retrofit2.http.Query("budget_per_slot") double budgetPerSlot,
                        @retrofit2.http.Query("used_ids") String usedIds);

        @GET("itinerary/enhanced")
        Call<com.google.gson.JsonObject> getEnhancedItinerary(
                        @retrofit2.http.Query("lat") double lat,
                        @retrofit2.http.Query("lng") double lng,
                        @retrofit2.http.Query("user_id") String userId,
                        @retrofit2.http.Query("type") String type,
                        @retrofit2.http.Query("duration") int duration,
                        @retrofit2.http.Query("points") int points,
                        @retrofit2.http.Query("optimize") boolean optimize,
                        @retrofit2.http.Query("budget") Integer budget);

        @GET("analytics/personal/{user_id}")
        Call<com.google.gson.JsonObject> getPersonalAnalytics(
                        @retrofit2.http.Path("user_id") String userId);

        @GET("events")
        Call<List<com.cityscape.app.model.Event>> getEvents(
                        @retrofit2.http.Query("lat") double lat,
                        @retrofit2.http.Query("lng") double lng,
                        @retrofit2.http.Query("radius") int radius,
                        @retrofit2.http.Query("interests") String interests,
                        @retrofit2.http.Query("user_id") String userId);


        @POST("visit")
        Call<Void> recordVisit(@Body VisitRequest request);

        @GET("visited")
        Call<List<Place>> getVisited(@retrofit2.http.Query("user_id") String userId);

        @GET("feed")
        Call<List<com.cityscape.app.model.FeedPost>> getFeed(
                        @retrofit2.http.Query("type") String type,
                        @retrofit2.http.Query("user_id") String userId,
                        @retrofit2.http.Query("lat") Double lat,
                        @retrofit2.http.Query("lng") Double lng);

        @GET("social/trending")
        Call<com.google.gson.JsonObject> getSocialTrending();

        @GET("trending/stories")
        Call<List<Place>> getTrendingStories(@retrofit2.http.Query("city") String city);

        @GET("groups/{groupId}/recommendations")
        Call<List<java.util.Map<String, String>>> getGroupRecommendations(
                @retrofit2.http.Path("groupId") String groupId,
                @retrofit2.http.Query("lat") double lat,
                @retrofit2.http.Query("lng") double lng);

        @GET("users/{userId}/posts")
        Call<List<com.cityscape.app.model.FeedPost>> getUserPosts(
                        @retrofit2.http.Path("userId") String userId);

        @POST("feed")
        Call<com.google.gson.JsonObject> createPost(@Body java.util.Map<String, Object> post);

        @GET("feed/{postId}/comments")
        Call<List<com.cityscape.app.model.FeedComment>> getComments(
                        @retrofit2.http.Path("postId") String postId);

        @POST("feed/{postId}/comments")
        Call<com.google.gson.JsonObject> addComment(
                        @retrofit2.http.Path("postId") String postId,
                        @Body java.util.Map<String, String> comment);

        @POST("feed/{postId}/like")
        Call<com.google.gson.JsonObject> toggleLike(
                        @retrofit2.http.Path("postId") String postId,
                        @Body java.util.Map<String, String> data);

        @GET("feed/by_ids")
        Call<List<com.cityscape.app.model.FeedPost>> getFeedByIds(
                        @retrofit2.http.Query("ids") String ids,
                        @retrofit2.http.Query("user_id") String userId);

        @GET("users/{userId}/liked_posts")
        Call<List<com.cityscape.app.model.FeedPost>> getLikedPosts(
                        @retrofit2.http.Path("userId") String userId);

        // ===== USERS & SOCIAL =====
        @GET("users/search")
        Call<List<com.cityscape.app.model.User>> searchUsers(
                        @retrofit2.http.Query("query") String query,
                        @retrofit2.http.Query("current_user_id") String currentUserId);

        @POST("users/follow")
        Call<com.google.gson.JsonObject> followUser(@Body java.util.Map<String, String> data);

        @GET("users/{userId}/following")
        Call<List<com.cityscape.app.model.User>> getFollowing(
                        @retrofit2.http.Path("userId") String userId);

        @GET("users/recommended")
        Call<List<com.cityscape.app.model.User>> getRecommendedUsers(
                        @retrofit2.http.Query("user_id") String userId);

        @GET("recommendation/magic")
        Call<com.google.gson.JsonObject> getMagicRecommendation(
                        @retrofit2.http.Query("lat") double lat,
                        @retrofit2.http.Query("lng") double lng,
                        @retrofit2.http.Query("user_id") String userId,
                        @retrofit2.http.Query("type") String type,
                        @retrofit2.http.Query("interests") String interests);

        @GET("recommendations/personalized")
        Call<List<Place>> getPersonalizedRecommendations(
                        @retrofit2.http.Query("lat") double lat,
                        @retrofit2.http.Query("lng") double lng,
                        @retrofit2.http.Query("user_id") String userId,
                        @retrofit2.http.Query("query") String query,
                        @retrofit2.http.Query("type") String type,
                        @retrofit2.http.Query("city") String city,
                        @retrofit2.http.Query("interests") String interests);

        @GET("quests/daily")
        Call<com.google.gson.JsonObject> getDailyQuest(
                        @retrofit2.http.Query("user_id") String userId,
                        @retrofit2.http.Query("lat") double lat,
                        @retrofit2.http.Query("lng") double lng,
                        @retrofit2.http.Query("interests") String interests,
                        @retrofit2.http.Query("language") String language);

        @GET("map/hype")
        Call<List<java.util.Map<String, Object>>> getHypeMap(
                        @retrofit2.http.Query("lat") double lat,
                        @retrofit2.http.Query("lng") double lng);

        @POST("report")
        Call<com.google.gson.JsonObject> reportContent(@Body java.util.Map<String, Object> data);

        @GET("reports/user/{userId}")
        Call<com.google.gson.JsonObject> getUserReport(@retrofit2.http.Path("userId") String userId);

        @GET("admin/stats")
        Call<com.google.gson.JsonObject> getAdminStats();

        @GET("weather/plan-b")
        Call<com.google.gson.JsonObject> getWeatherPlanB(
                        @retrofit2.http.Query("lat") double lat,
                        @retrofit2.http.Query("lng") double lng);

        @POST("travel-story")
        Call<com.google.gson.JsonObject> generateTravelStory(@retrofit2.http.Body java.util.Map<String, Object> body);

        @GET("crystal-ball/timeline/{user_id}")
        Call<com.google.gson.JsonObject> getCrystalBallTimeline(
                        @retrofit2.http.Path("user_id") String userId);
}
