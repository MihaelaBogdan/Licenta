package com.example.licenta.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "group_suggestions")
public class GroupSuggestion {
    @PrimaryKey(autoGenerate = false)
    @NonNull
    public String id;

    public String groupId;
    public String placeId;
    public String placeName;
    public String suggestedByUserId;
    public String suggestedByUserName;
    public int voteCount;

    public GroupSuggestion() {
        this.id = java.util.UUID.randomUUID().toString();
        this.voteCount = 0;
    }

    public GroupSuggestion(String groupId, String placeId, String placeName, String userId, String userName) {
        this.id = java.util.UUID.randomUUID().toString();
        this.groupId = groupId;
        this.placeId = placeId;
        this.placeName = placeName;
        this.suggestedByUserId = userId;
        this.suggestedByUserName = userName;
        this.voteCount = 1; // Auto-vote from suggester
    }
}
