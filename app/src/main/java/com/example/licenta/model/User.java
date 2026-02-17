package com.example.licenta.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;
    public String email;
    public String password;
    public int level;
    public int currentXp;
    public int totalXp;
    public int placesVisited;
    public int badgesEarned;

    public User() {
        this.level = 1;
        this.currentXp = 0;
        this.totalXp = 0;
        this.placesVisited = 0;
        this.badgesEarned = 0;
    }

    public User(String name, String email, String password) {
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
