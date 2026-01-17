package com.cityscape.app.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * City entity representing cities from Romania and worldwide
 * Used for multi-city support
 */
@Entity(tableName = "cities")
public class City {

    @PrimaryKey
    @NonNull
    private String id;

    private String name;
    private String country;
    private String countryCode;
    private double latitude;
    private double longitude;
    private String imageUrl;
    private String description;
    private String timezone;
    private boolean isPopular;
    private int placeCount;

    // Constructor
    public City(@NonNull String id, String name, String country,
            double latitude, double longitude) {
        this.id = id;
        this.name = name;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isPopular = false;
        this.placeCount = 0;
    }

    // Getters and Setters
    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public boolean isPopular() {
        return isPopular;
    }

    public void setPopular(boolean popular) {
        isPopular = popular;
    }

    public int getPlaceCount() {
        return placeCount;
    }

    public void setPlaceCount(int placeCount) {
        this.placeCount = placeCount;
    }

    // Display name with country
    public String getDisplayName() {
        return name + ", " + country;
    }
}
