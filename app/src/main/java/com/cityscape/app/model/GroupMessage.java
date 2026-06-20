package com.cityscape.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "group_messages")
public class GroupMessage {
    @PrimaryKey(autoGenerate = false)
    @NonNull
    public String id;

    public String groupId;
    public String userId;
    public String userName;
    public String message;
    public long timestamp;

    public GroupMessage() {
        this.id = java.util.UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }

    public GroupMessage(String groupId, String userId, String userName, String message) {
        this.id = java.util.UUID.randomUUID().toString();
        this.groupId = groupId;
        this.userId = userId;
        this.userName = userName;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
}
