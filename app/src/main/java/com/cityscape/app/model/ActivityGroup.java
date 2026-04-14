package com.cityscape.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import com.google.gson.annotations.SerializedName;

@Entity(tableName = "activity_groups")
public class ActivityGroup {
    @PrimaryKey(autoGenerate = false)
    @NonNull
    public String id;

    @SerializedName("activity_id")
    public String activityId;
    @SerializedName("creator_id")
    public String creatorId;
    @SerializedName("group_name")
    public String groupName;
    @SerializedName("group_code")
    public String groupCode; // Unique code for sharing
    @SerializedName("created_at")
    public long createdAt;
    @SerializedName("max_members")
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
