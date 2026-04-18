package com.cityscape.app.api;

import com.cityscape.app.model.Place;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface ApiService {
        @GET("places")
        Call<List<Place>> getPlaces();

        @GET("nearby")
        Call<List<Place>> getNearby(
                        @retrofit2.http.Query("lat") double lat,
                        @retrofit2.http.Query("lng") double lng,
                        @retrofit2.http.Query("type") String type);

        @GET("places/search")
        Call<List<Place>> getPlacesSearch(
                        @retrofit2.http.Query("lat") double lat,
                        @retrofit2.http.Query("lng") double lng,
                        @retrofit2.http.Query("query") String query,
                        @retrofit2.http.Query("type") String type,
                        @retrofit2.http.Query("radius") Integer radius);

        @GET("places/autocomplete")
        Call<List<java.util.Map<String, String>>> getAutocomplete(
                        @retrofit2.http.Query("query") String query,
                        @retrofit2.http.Query("lat") Double lat,
                        @retrofit2.http.Query("lng") Double lng);

        @POST("predict")
        Call<ChatResponse> chat(@Body ChatRequest request);

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
                        @retrofit2.http.Query("points") Integer points);

        @GET("events")
        Call<List<com.cityscape.app.model.Event>> getEvents(
                        @retrofit2.http.Query("lat") double lat,
                        @retrofit2.http.Query("lng") double lng,
                        @retrofit2.http.Query("interests") String interests);

        @POST("visit")
        Call<Void> recordVisit(@Body VisitRequest request);

        @GET("visited")
        Call<List<Place>> getVisited(@retrofit2.http.Query("user_id") String userId);

        // ===== SOCIAL FEED =====
        @GET("feed")
        Call<List<com.cityscape.app.model.FeedPost>> getFeed(
                        @retrofit2.http.Query("type") String type,
                        @retrofit2.http.Query("user_id") String userId);

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
}
