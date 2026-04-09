package com.example.licenta.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
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
