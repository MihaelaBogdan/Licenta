package com.cityscape.app.model;

import com.google.gson.annotations.SerializedName;

public class Event {
    public String title;
    public String time;
    public String location;
    
    @SerializedName("image_url")
    public String imageUrl;
    
    @SerializedName("event_url")
    public String url;
    
    public double latitude;
    public double longitude;
    public String musicType;
    public String eventType;
    public String price; 

    public String date_str;
    public String source;
    public String category;
    
    @SerializedName("relevance_score")
    public int relevance_score;

    public Event() {}

    public Event(String title, String date_str, String location, String imageUrl, String url) {
        this.title = title;
        this.date_str = date_str;
        this.location = location;
        this.imageUrl = imageUrl;
        this.url = url;
    }
}
