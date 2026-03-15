package com.example.licenta.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "group_members")
public class GroupMember {
    @PrimaryKey(autoGenerate = false)
    @NonNull
    public String id;

    public String groupId;
    public String userId;
    public String userName;
    public String status; // "pending", "accepted", "declined"
    public long joinedAt;
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
