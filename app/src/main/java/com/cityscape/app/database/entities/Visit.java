package com.cityscape.app.database.entities;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Visit entity for visit history tracking
 */
@Entity(tableName = "visits", foreignKeys = {
        @ForeignKey(entity = User.class, parentColumns = "id", childColumns = "userId", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = Place.class, parentColumns = "id", childColumns = "placeId", onDelete = ForeignKey.CASCADE)
}, indices = { @Index("userId"), @Index("placeId") })
public class Visit {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String userId;

    @NonNull
    private String placeId;

    private long visitDate;
    private float userRating; // Optional rating given during visit
    private String notes;
    private String photoUrls; // Photos taken during visit
    private int spentAmount; // Amount spent in local currency
    private int durationMinutes; // Duration of visit

    // Constructor
    public Visit(@NonNull String userId, @NonNull String placeId, long visitDate) {
        this.userId = userId;
        this.placeId = placeId;
        this.visitDate = visitDate;
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
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

    public long getVisitDate() {
        return visitDate;
    }

    public void setVisitDate(long visitDate) {
        this.visitDate = visitDate;
    }

    public float getUserRating() {
        return userRating;
    }

    public void setUserRating(float userRating) {
        this.userRating = userRating;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getPhotoUrls() {
        return photoUrls;
    }

    public void setPhotoUrls(String photoUrls) {
        this.photoUrls = photoUrls;
    }

    public int getSpentAmount() {
        return spentAmount;
    }

    public void setSpentAmount(int spentAmount) {
        this.spentAmount = spentAmount;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public long getVisitedAt() {
        return 0;
    }
}
