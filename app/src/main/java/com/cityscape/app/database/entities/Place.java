package com.cityscape.app.database.entities;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import java.util.List;

/**
 * Place entity representing restaurants, cafes, bars, attractions, etc.
 * Core entity for the recommendation system
 */
@Entity(tableName = "places", foreignKeys = @ForeignKey(entity = City.class, parentColumns = "id", childColumns = "cityId", onDelete = ForeignKey.CASCADE), indices = {
        @Index("cityId"), @Index("category"), @Index("rating") })
public class Place {

    @PrimaryKey
    @NonNull
    private String id;

    @NonNull
    private String cityId;

    private String name;
    private String description;
    private String category; // restaurant, cafe, bar, culture, nature, shopping
    private String subcategory;
    private String address;
    private double latitude;
    private double longitude;
    private float rating; // 0-5
    private int reviewCount;
    private int priceLevel; // 1-4 ($, $$, $$$, $$$$)
    private String phoneNumber;
    private String website;
    private String imageUrl;

    // Opening hours stored as JSON string
    private String openingHours;

    // Atmosphere tags (comma-separated): "romantic,quiet,modern"
    private String atmosphereTags;

    // Cuisine/type tags for restaurants
    private String cuisineTags;

    // For AI recommendations
    private float popularityScore;
    private float trendingScore;

    // Google Places ID for integration
    private String googlePlaceId;

    private boolean isVerified;
    private boolean isSponsored;
    private long createdAt;
    private long updatedAt;

    // Constructor
    public Place(@NonNull String id, @NonNull String cityId, String name,
            String category, double latitude, double longitude) {
        this.id = id;
        this.cityId = cityId;
        this.name = name;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.rating = 0;
        this.reviewCount = 0;
        this.priceLevel = 2;
        this.popularityScore = 0;
        this.trendingScore = 0;
        this.isVerified = false;
        this.isSponsored = false;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    // Getters and Setters
    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public String getCityId() {
        return cityId;
    }

    public void setCityId(@NonNull String cityId) {
        this.cityId = cityId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
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

    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(int reviewCount) {
        this.reviewCount = reviewCount;
    }

    public int getPriceLevel() {
        return priceLevel;
    }

    public void setPriceLevel(int priceLevel) {
        this.priceLevel = priceLevel;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public void setOpeningHours(String openingHours) {
        this.openingHours = openingHours;
    }

    public String getAtmosphereTags() {
        return atmosphereTags;
    }

    public void setAtmosphereTags(String atmosphereTags) {
        this.atmosphereTags = atmosphereTags;
    }

    public String getCuisineTags() {
        return cuisineTags;
    }

    public void setCuisineTags(String cuisineTags) {
        this.cuisineTags = cuisineTags;
    }

    public float getPopularityScore() {
        return popularityScore;
    }

    public void setPopularityScore(float popularityScore) {
        this.popularityScore = popularityScore;
    }

    public float getTrendingScore() {
        return trendingScore;
    }

    public void setTrendingScore(float trendingScore) {
        this.trendingScore = trendingScore;
    }

    public String getGooglePlaceId() {
        return googlePlaceId;
    }

    public void setGooglePlaceId(String googlePlaceId) {
        this.googlePlaceId = googlePlaceId;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
    }

    public boolean isSponsored() {
        return isSponsored;
    }

    public void setSponsored(boolean sponsored) {
        isSponsored = sponsored;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Utility methods
    public String getPriceLevelString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < priceLevel; i++) {
            sb.append("$");
        }
        return sb.toString();
    }

    // Category constants
    public static final String CATEGORY_RESTAURANT = "restaurant";
    public static final String CATEGORY_CAFE = "cafe";
    public static final String CATEGORY_BAR = "bar";
    public static final String CATEGORY_NIGHTLIFE = "nightlife";
    public static final String CATEGORY_CULTURE = "culture";
    public static final String CATEGORY_NATURE = "nature";
    public static final String CATEGORY_SHOPPING = "shopping";
    public static final String CATEGORY_ENTERTAINMENT = "entertainment";
    public static final String CATEGORY_WELLNESS = "wellness";

    public String getPhotoUrl() {
        return imageUrl;
    }
}
