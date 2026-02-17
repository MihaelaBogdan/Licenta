package com.example.licenta.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_badges")
public class UserBadge {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int userId;
    public String badgeId;
    public String name;
    public String description;
    public String iconName;
    public boolean isUnlocked;
    public long unlockedAt;

    public UserBadge() {
    }

    public UserBadge(int userId, String badgeId, String name, String description, String iconName) {
        this.userId = userId;
        this.badgeId = badgeId;
        this.name = name;
        this.description = description;
        this.iconName = iconName;
        this.isUnlocked = false;
        this.unlockedAt = 0;
    }

    public void unlock() {
        this.isUnlocked = true;
        this.unlockedAt = System.currentTimeMillis();
    }
}
