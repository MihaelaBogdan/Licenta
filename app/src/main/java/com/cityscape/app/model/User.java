package com.cityscape.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "users")
public class User {
    @PrimaryKey(autoGenerate = false)
    @NonNull
    public String id;

    public String name;
    public String email;
    public String password;
    public int level;
    public int currentXp;
    public int totalXp;
    public int placesVisited;
    public int badgesEarned;
    public String interests;
    public String avatar;
    public boolean isFollowing;

    public User() {
        this.id = java.util.UUID.randomUUID().toString();
        this.level = 1;
        this.currentXp = 0;
        this.totalXp = 0;
        this.placesVisited = 0;
        this.badgesEarned = 0;
    }

    public User(String name, String email, String password) {
        this.id = java.util.UUID.randomUUID().toString();
        this.name = name;
        this.email = email;
        this.password = password;
        this.level = 1;
        this.currentXp = 0;
        this.totalXp = 0;
        this.placesVisited = 0;
        this.badgesEarned = 0;
    }

    public int getXpForNextLevel() {
        return level * 500; // Each level requires level * 500 XP
    }

    public int getProgressPercentage() {
        int xpNeeded = getXpForNextLevel();
        return (int) ((currentXp * 100.0) / xpNeeded);
    }

    public void addXp(int amount) {
        currentXp += amount;
        totalXp += amount;

        // Check for level up
        while (currentXp >= getXpForNextLevel()) {
            currentXp -= getXpForNextLevel();
            level++;
        }
    }
}
