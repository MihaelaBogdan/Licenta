package com.cityscape.app.api;

public class ChatRequest {
    public String message;
    public String language;
    public String user_id;
    public Double lat;
    public Double lng;
    public String city_name;
    public String interests;
    public Integer user_xp;
    public Integer user_level;
    public Integer places_visited;

    public ChatRequest(String message) {
        this.message = message;
        this.language = "ro";
    }

    public ChatRequest(String message, String user_id, Double lat, Double lng, String city_name) {
        this.message = message;
        this.user_id = user_id;
        this.lat = lat;
        this.lng = lng;
        this.city_name = city_name;
        this.language = "ro";
    }

    public ChatRequest(String message, String user_id, Double lat, Double lng, String city_name,
                       String interests, Integer user_xp, Integer user_level, Integer places_visited) {
        this.message = message;
        this.user_id = user_id;
        this.lat = lat;
        this.lng = lng;
        this.city_name = city_name;
        this.interests = interests;
        this.user_xp = user_xp;
        this.user_level = user_level;
        this.places_visited = places_visited;
        this.language = "ro";
    }
}
