package com.cityscape.app.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

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
    public String date;
    public String source;
    public String category;

    @SerializedName("relevance_score")
    public int relevance_score;

    @SerializedName("confidence")
    public int confidence;

    @SerializedName("ai_reason")
    public String aiReason;

    @SerializedName("ai_factors")
    public AiFactors aiFactors;

    public static class AiFactors {
        @SerializedName("interest_match") public int interestMatch;
        @SerializedName("novelty")        public int novelty;
        @SerializedName("history_match")  public int historyMatch;
        @SerializedName("info_quality")   public int infoQuality;
    }

    // New enriched fields
    public String description;
    public List<Photo> photos;
    public List<Review> reviews;

    @SerializedName("google_rating")
    public double googleRating;

    @SerializedName("review_count")
    public int reviewCount;

    public String website;
    public Boolean is_open;

    public Event() {}

    public Event(String title, String date_str, String location, String imageUrl, String url) {
        this.title = title;
        this.date_str = date_str;
        this.location = location;
        this.imageUrl = imageUrl;
        this.url = url;
    }

    // Inner classes for photos and reviews
    public static class Photo {
        public String url;
        public String source;
    }

    public static class Review {
        public String author;
        public int rating;
        public String text;
        public String source;
        public String time;
    }
}
