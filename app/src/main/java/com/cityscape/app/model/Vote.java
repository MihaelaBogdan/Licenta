package com.cityscape.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "group_votes")
public class Vote {
    @PrimaryKey(autoGenerate = false)
    @NonNull
    public String id;

    public String suggestionId;
    public String userId;
    public String groupId;

    public Vote() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    public Vote(String suggestionId, String userId, String groupId) {
        this.id = java.util.UUID.randomUUID().toString();
        this.suggestionId = suggestionId;
        this.userId = userId;
        this.groupId = groupId;
    }
}
