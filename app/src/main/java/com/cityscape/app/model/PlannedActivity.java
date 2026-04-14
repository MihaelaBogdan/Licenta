package com.cityscape.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import com.google.gson.annotations.SerializedName;

@Entity(tableName = "planned_activities")
public class PlannedActivity {
    @PrimaryKey(autoGenerate = false)
    @NonNull
    public String id;

    @SerializedName("user_id")
    public String userId;
    @SerializedName("place_id")
    public String placeId;
    @SerializedName("place_name")
    public String placeName;
    @SerializedName("place_type")
    public String placeType;
    @SerializedName("place_image_url")
    public String placeImageUrl;
    @SerializedName("scheduled_date")
    public long scheduledDate;
    @SerializedName("scheduled_time")
    public String scheduledTime;
    @SerializedName("is_completed")
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
