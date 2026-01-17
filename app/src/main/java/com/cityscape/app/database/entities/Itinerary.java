package com.cityscape.app.database.entities;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Itinerary entity for auto-generated or custom itineraries
 * "A Perfect Day in [City]" feature
 */
@Entity(tableName = "itineraries", foreignKeys = @ForeignKey(entity = City.class, parentColumns = "id", childColumns = "cityId", onDelete = ForeignKey.CASCADE), indices = {
        @Index("cityId") })
public class Itinerary {

    @PrimaryKey
    @NonNull
    private String id;

    @NonNull
    private String cityId;

    private String title;
    private String description;
    private String imageUrl;
    private int durationHours;
    private int estimatedBudget;
    private String theme; // romantic, adventure, culture, foodie, nightlife
    private String placeIds; // Comma-separated place IDs in order
    private String schedule; // JSON with time slots
    private float rating;
    private int usageCount;
    private boolean isGenerated; // AI generated or curated
    private boolean isPublic;
    private String createdBy; // User ID or "system"
    private long createdAt;

    // Constructor
    public Itinerary(@NonNull String id, @NonNull String cityId, String title) {
        this.id = id;
        this.cityId = cityId;
        this.title = title;
        this.durationHours = 8;
        this.estimatedBudget = 100;
        this.rating = 0;
        this.usageCount = 0;
        this.isGenerated = false;
        this.isPublic = true;
        this.createdAt = System.currentTimeMillis();
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getDurationHours() {
        return durationHours;
    }

    public void setDurationHours(int durationHours) {
        this.durationHours = durationHours;
    }

    public int getEstimatedBudget() {
        return estimatedBudget;
    }

    public void setEstimatedBudget(int estimatedBudget) {
        this.estimatedBudget = estimatedBudget;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getPlaceIds() {
        return placeIds;
    }

    public void setPlaceIds(String placeIds) {
        this.placeIds = placeIds;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(int usageCount) {
        this.usageCount = usageCount;
    }

    public boolean isGenerated() {
        return isGenerated;
    }

    public void setGenerated(boolean generated) {
        isGenerated = generated;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    // Theme constants
    public static final String THEME_ROMANTIC = "romantic";
    public static final String THEME_ADVENTURE = "adventure";
    public static final String THEME_CULTURE = "culture";
    public static final String THEME_FOODIE = "foodie";
    public static final String THEME_NIGHTLIFE = "nightlife";
    public static final String THEME_FAMILY = "family";
    public static final String THEME_BUDGET = "budget";
    public static final String THEME_LUXURY = "luxury";
}
