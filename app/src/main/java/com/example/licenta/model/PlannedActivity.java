package com.example.licenta.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "planned_activities")
public class PlannedActivity {
    @PrimaryKey(autoGenerate = false)
    @NonNull
    public String id;

    public String userId;
    public String placeId;
    public String placeName;
    public String placeType;
    public String placeImageUrl;
    public long scheduledDate;
    public String scheduledTime;
    public boolean isCompleted;
    public String notes;
    public double budget;
    public String currency;

    public PlannedActivity() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    public PlannedActivity(String userId, String placeId, String placeName, String placeType,
            String placeImageUrl, long scheduledDate, String scheduledTime) {
        this.id = java.util.UUID.randomUUID().toString();
        this.userId = userId;
        this.placeId = placeId;
        this.placeName = placeName;
        this.placeType = placeType;
        this.placeImageUrl = placeImageUrl;
        this.scheduledDate = scheduledDate;
        this.scheduledTime = scheduledTime;
        this.isCompleted = false;
        this.notes = "";
    }
}
