package com.cityscape.app.database.entities;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * User preferences entity for personalized recommendations
 * Stores user preferences like categories, atmospheres, price ranges
 */
@Entity(tableName = "user_preferences", foreignKeys = @ForeignKey(entity = User.class, parentColumns = "id", childColumns = "userId", onDelete = ForeignKey.CASCADE), indices = {
        @Index("userId") })
public class UserPreference {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String userId;

    // Preference types: CATEGORY, ATMOSPHERE, PRICE_RANGE, CUISINE, VIBE
    private String preferenceType;

    // The value of the preference (e.g., "coffee", "romantic", "budget")
    private String preferenceValue;

    // Weight for recommendation algorithm (0.0 to 1.0)
    private float weight;

    // Constructor
    public UserPreference(@NonNull String userId, String preferenceType,
            String preferenceValue, float weight) {
        this.userId = userId;
        this.preferenceType = preferenceType;
        this.preferenceValue = preferenceValue;
        this.weight = weight;
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getUserId() {
        return userId;
    }

    public void setUserId(@NonNull String userId) {
        this.userId = userId;
    }

    public String getPreferenceType() {
        return preferenceType;
    }

    public void setPreferenceType(String preferenceType) {
        this.preferenceType = preferenceType;
    }

    public String getPreferenceValue() {
        return preferenceValue;
    }

    public void setPreferenceValue(String preferenceValue) {
        this.preferenceValue = preferenceValue;
    }

    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        this.weight = weight;
    }

    // Preference type constants
    public static final String TYPE_CATEGORY = "CATEGORY";
    public static final String TYPE_ATMOSPHERE = "ATMOSPHERE";
    public static final String TYPE_PRICE_RANGE = "PRICE_RANGE";
    public static final String TYPE_CUISINE = "CUISINE";
    public static final String TYPE_VIBE = "VIBE";
}
