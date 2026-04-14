package com.cityscape.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import com.google.gson.annotations.SerializedName;

@Entity(tableName = "group_members")
public class GroupMember {
    @PrimaryKey(autoGenerate = false)
    @NonNull
    public String id;

    @SerializedName("group_id")
    public String groupId;
    @SerializedName("user_id")
    public String userId;
    @SerializedName("user_name")
    public String userName;
    public String status; // "pending", "accepted", "declined"
    @SerializedName("joined_at")
    public long joinedAt;
    @SerializedName("is_creator")
    public boolean isCreator;

    public GroupMember() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    public GroupMember(String groupId, String userId, String userName, boolean isCreator) {
        this.id = java.util.UUID.randomUUID().toString();
        this.groupId = groupId;
        this.userId = userId;
        this.userName = userName;
        this.status = isCreator ? "accepted" : "pending";
        this.joinedAt = System.currentTimeMillis();
        this.isCreator = isCreator;
    }
}
