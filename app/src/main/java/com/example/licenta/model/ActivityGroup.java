package com.example.licenta.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "activity_groups")
public class ActivityGroup {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int activityId;
    public int creatorId;
    public String groupName;
    public String groupCode; // Unique code for sharing
    public long createdAt;
    public int maxMembers;

    public ActivityGroup() {
    }

    public ActivityGroup(int activityId, int creatorId, String groupName) {
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

    public String getShareLink() {
        return "https://mysticminds.app/group/" + groupCode;
    }
}
