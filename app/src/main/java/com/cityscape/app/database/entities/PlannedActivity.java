package com.cityscape.app.database.entities;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * PlannedActivity entity for calendar/planning feature
 */
@Entity(tableName = "planned_activities", foreignKeys = {
        @ForeignKey(entity = User.class, parentColumns = "id", childColumns = "userId", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = Place.class, parentColumns = "id", childColumns = "placeId", onDelete = ForeignKey.CASCADE)
}, indices = { @Index("userId"), @Index("placeId"), @Index("plannedDate") })
public class PlannedActivity {

    @PrimaryKey
    @NonNull
    private String id;

    @NonNull
    private String userId;

    @NonNull
    private String placeId;

    private long plannedDate;
    private String plannedTime; // "HH:mm" format

    // String-based date/time for easier calendar integration
    private String date; // "yyyy-MM-dd" format
    private String time; // "HH:mm" format
    private String placeName; // Cached place name

    private String notes;
    private int estimatedDuration; // in minutes
    private int estimatedBudget;
    private boolean isCompleted;
    private boolean reminderEnabled;
    private int reminderMinutesBefore;

    // Primary constructor with ID
    public PlannedActivity(@NonNull String id, @NonNull String userId,
            @NonNull String placeId, String date) {
        this.id = id;
        this.userId = userId;
        this.placeId = placeId;
        this.date = date;
        this.isCompleted = false;
        this.reminderEnabled = true;
        this.reminderMinutesBefore = 60;
    }

    // Getters and Setters
    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public String getUserId() {
        return userId;
    }

    public void setUserId(@NonNull String userId) {
        this.userId = userId;
    }

    @NonNull
    public String getPlaceId() {
        return placeId;
    }

    public void setPlaceId(@NonNull String placeId) {
        this.placeId = placeId;
    }

    public long getPlannedDate() {
        return plannedDate;
    }

    public void setPlannedDate(long plannedDate) {
        this.plannedDate = plannedDate;
    }

    public String getPlannedTime() {
        return plannedTime;
    }

    public void setPlannedTime(String plannedTime) {
        this.plannedTime = plannedTime;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getPlaceName() {
        return placeName;
    }

    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public int getEstimatedDuration() {
        return estimatedDuration;
    }

    public void setEstimatedDuration(int estimatedDuration) {
        this.estimatedDuration = estimatedDuration;
    }

    public int getEstimatedBudget() {
        return estimatedBudget;
    }

    public void setEstimatedBudget(int estimatedBudget) {
        this.estimatedBudget = estimatedBudget;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public boolean isReminderEnabled() {
        return reminderEnabled;
    }

    public void setReminderEnabled(boolean reminderEnabled) {
        this.reminderEnabled = reminderEnabled;
    }

    public int getReminderMinutesBefore() {
        return reminderMinutesBefore;
    }

    public void setReminderMinutesBefore(int reminderMinutesBefore) {
        this.reminderMinutesBefore = reminderMinutesBefore;
    }
}
