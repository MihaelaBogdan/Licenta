package com.cityscape.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

@Entity(tableName = "places")
public class Place implements Serializable {
    @PrimaryKey(autoGenerate = false)
    @NonNull
    public String id;

    public String googlePlaceId;
    public String name;
    public String description;
    public float rating;
    public String imageUrl;
    public double latitude;
    public double longitude;
    public String type;
    public String address;
    public boolean isFavorite;
    public int priceLevel; 
    public int reviewCount; 
    public String aiSuggestion; 
    
    @SerializedName("match_history_pct")
    public float matchHistoryPct;

    @SerializedName("match_prefs_pct")
    public float matchPrefsPct;

    @SerializedName("freshness_pct")
    public float freshnessPct;

    @SerializedName("popularity_pct")
    public float popularityPct;

    @SerializedName("user_level_pct")
    public float userLevelPct;

    @SerializedName("diversity_pct")
    public float diversityPct;

    @SerializedName("weather_match_pct")
    public float weatherMatchPct;

    @SerializedName("confidence")
    public float confidence; 
    public String ai_summary; 

    @androidx.room.Ignore
    public java.util.List<Review> reviews;

    @androidx.room.Ignore
    public java.util.List<Photo> photos;

    @SerializedName("is_open")
    public Boolean isOpen;

    public String website;
    public String photo;

    public static class Review implements java.io.Serializable {
        public String author;
        public String text;
        public float rating;
        public String time;
        public String source;
    }

    public static class Photo implements java.io.Serializable {
        public String url;
        public String source;
    }

    public Place() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    public Place(String name, String description, float rating, String imageUrl, double latitude, double longitude,
            String type, String address) {
        this.id = java.util.UUID.randomUUID().toString();
        this.name = name;
        this.description = description;
        this.rating = rating;
        this.imageUrl = imageUrl;
        this.latitude = latitude;
        this.longitude = longitude;
        this.type = type;
        this.address = address;
        this.isFavorite = false;
    }
}
