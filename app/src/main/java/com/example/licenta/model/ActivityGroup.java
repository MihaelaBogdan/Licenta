package com.example.licenta.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "activity_groups")
public class ActivityGroup {
    @PrimaryKey(autoGenerate = false)
    @NonNull
    public String id;

    public String activityId;
    public String creatorId;
    public String groupName;
    public String groupCode; // Unique code for sharing
    public long createdAt;
    public int maxMembers;

    public ActivityGroup() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    public ActivityGroup(String activityId, String creatorId, String groupName) {
        this.id = java.util.UUID.randomUUID().toString();
        this.activityId = activityId;
        this.creatorId = creatorId;
        this.groupName = groupName;
        this.groupCode = generateGroupCode();
        this.createdAt = System.currentTimeMillis();
        this.maxMembers = 10;
    }

    private String generateGroupCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int index = (int) (Math.random() * chars.length());
            code.append(chars.charAt(index));
        }
        return code.toString();
    }

}
