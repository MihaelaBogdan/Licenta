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
}
