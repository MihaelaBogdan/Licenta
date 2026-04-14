package com.cityscape.app.api;

public class ChatRequest {
    public String message;
    public String language;
    public String user_id;
    public Double lat;
    public Double lng;

    public ChatRequest(String message) {
        this.message = message;
        this.language = "ro";
    }

    public ChatRequest(String message, String user_id, Double lat, Double lng) {
        this.message = message;
        this.user_id = user_id;
        this.lat = lat;
        this.lng = lng;
        this.language = "ro";
    }
}
