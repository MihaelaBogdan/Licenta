package com.example.licenta.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "user_badges")
public class UserBadge {
    @PrimaryKey(autoGenerate = false)
    @NonNull
    public String id;

    public String userId;
    public String badgeId;
    public String name;
    public String description;
    public String iconName;
    public boolean isUnlocked;
    public long unlockedAt;

    public UserBadge() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    public UserBadge(String userId, String badgeId, String name, String description, String iconName) {
        this.id = java.util.UUID.randomUUID().toString();
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
