package com.example.licenta.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "planned_activities")
public class PlannedActivity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int userId;
    public int placeId;
    public String placeName;
    public String placeType;
    public String placeImageUrl;
    public long scheduledDate;
    public String scheduledTime;
    public boolean isCompleted;
    public String notes;

    public PlannedActivity() {
    }

    public PlannedActivity(int userId, int placeId, String placeName, String placeType,
            String placeImageUrl, long scheduledDate, String scheduledTime) {
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
