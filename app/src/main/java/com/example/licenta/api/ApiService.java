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

    @POST("predict")
    Call<ChatResponse> chat(@Body ChatRequest request);
}
