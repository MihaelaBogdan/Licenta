package com.cityscape.app.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Badge entity for gamification system
 */
@Entity(tableName = "badges")
public class Badge {

    @PrimaryKey
    @NonNull
    private String id;

    private String name;
    private String description;
    private String iconName; // Resource name for icon
    private String requirement; // Description of how to earn
    private int requiredValue; // e.g., 10 for "Visit 10 places"
    private String requirementType; // VISITS, REVIEWS, CITIES, PHOTOS, etc.
    private int pointsAwarded;
    private String tier; // bronze, silver, gold, platinum

    // Constructor
    public Badge(@NonNull String id, String name, String description,
            String requirementType, int requiredValue) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.requirementType = requirementType;
        this.requiredValue = requiredValue;
        this.pointsAwarded = requiredValue * 10;
        this.tier = "bronze";
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIconName() {
        return iconName;
    }

    public void setIconName(String iconName) {
        this.iconName = iconName;
    }

    public String getRequirement() {
        return requirement;
    }

    public void setRequirement(String requirement) {
        this.requirement = requirement;
    }

    public int getRequiredValue() {
        return requiredValue;
    }

    public void setRequiredValue(int requiredValue) {
        this.requiredValue = requiredValue;
    }

    public String getRequirementType() {
        return requirementType;
    }

    public void setRequirementType(String requirementType) {
        this.requirementType = requirementType;
    }

    public int getPointsAwarded() {
        return pointsAwarded;
    }

    public void setPointsAwarded(int pointsAwarded) {
        this.pointsAwarded = pointsAwarded;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    // Requirement type constants
    public static final String TYPE_VISITS = "VISITS";
    public static final String TYPE_REVIEWS = "REVIEWS";
    public static final String TYPE_CITIES = "CITIES";
    public static final String TYPE_PHOTOS = "PHOTOS";
    public static final String TYPE_FAVORITES = "FAVORITES";
    public static final String TYPE_ITINERARIES = "ITINERARIES";
    public static final String TYPE_FIRST_REVIEW = "FIRST_REVIEW"; // First to review a new place

    // Tier constants
    public static final String TIER_BRONZE = "bronze";
    public static final String TIER_SILVER = "silver";
    public static final String TIER_GOLD = "gold";
    public static final String TIER_PLATINUM = "platinum";
}
