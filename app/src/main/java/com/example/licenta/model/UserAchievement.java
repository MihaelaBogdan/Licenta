package com.example.licenta.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "user_achievements")
public class UserAchievement {
    @PrimaryKey(autoGenerate = false)
    @NonNull
    public String id;

    public String userId;
    public String title;
    public int xpReward;
    public long earnedAt;

    public UserAchievement() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    public UserAchievement(String userId, String title, int xpReward) {
        this.id = java.util.UUID.randomUUID().toString();
        this.userId = userId;
        this.title = title;
        this.xpReward = xpReward;
        this.earnedAt = System.currentTimeMillis();
    }

    public String getTimeAgo() {
        long diff = System.currentTimeMillis() - earnedAt;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0)
            return days + " days ago";
        if (hours > 0)
            return hours + " hours ago";
        if (minutes > 0)
            return minutes + " min ago";
        return "Just now";
    }
}
