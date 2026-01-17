package com.cityscape.app.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * User entity representing app users
 * Stores authentication and profile information
 */
@Entity(tableName = "users")
public class User {

    @PrimaryKey
    @NonNull
    private String id;

    private String email;
    private String name;
    private String password; // User password for authentication
    private String profileImageUrl;
    private String selectedCityId;
    private long createdAt;
    private long lastLoginAt;
    private int totalPoints;
    private int level;
    private boolean isDarkMode;
    private String preferredLanguage;

    // Constructor
    public User(@NonNull String id, String email, String name) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.createdAt = System.currentTimeMillis();
        this.lastLoginAt = System.currentTimeMillis();
        this.totalPoints = 0;
        this.level = 1;
        this.isDarkMode = true;
        this.preferredLanguage = "en";
    }

    // Getters and Setters
    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getSelectedCityId() {
        return selectedCityId;
    }

    public void setSelectedCityId(String selectedCityId) {
        this.selectedCityId = selectedCityId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(long lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public int getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(int totalPoints) {
        this.totalPoints = totalPoints;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public boolean isDarkMode() {
        return isDarkMode;
    }

    public void setDarkMode(boolean darkMode) {
        isDarkMode = darkMode;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    // Add points and check for level up
    public void addPoints(int points) {
        this.totalPoints += points;
        // Level up every 100 points
        this.level = (this.totalPoints / 100) + 1;
    }
}
