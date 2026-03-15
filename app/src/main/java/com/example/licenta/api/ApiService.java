package com.example.licenta.api;

import com.example.licenta.model.Place;
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
            @retrofit2.http.Query("lng") double lng);
}
