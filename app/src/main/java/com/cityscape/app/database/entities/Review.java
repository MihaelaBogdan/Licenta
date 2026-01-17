package com.cityscape.app.database.entities;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Review entity for user reviews of places
 */
@Entity(tableName = "reviews", foreignKeys = {
        @ForeignKey(entity = Place.class, parentColumns = "id", childColumns = "placeId", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = User.class, parentColumns = "id", childColumns = "userId", onDelete = ForeignKey.CASCADE)
}, indices = { @Index("placeId"), @Index("userId") })
public class Review {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String placeId;

    @NonNull
    private String userId;

    private float rating; // 1-5
    private String text;
    private String photoUrls; // Comma-separated URLs
    private long createdAt;
    private long updatedAt;
    private int helpfulCount;
    private boolean isVerifiedVisit;

    // Constructor
    public Review(@NonNull String placeId, @NonNull String userId,
            float rating, String text) {
        this.placeId = placeId;
        this.userId = userId;
        this.rating = rating;
        this.text = text;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.helpfulCount = 0;
        this.isVerifiedVisit = false;
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getPlaceId() {
        return placeId;
    }

    public void setPlaceId(@NonNull String placeId) {
        this.placeId = placeId;
    }

    @NonNull
    public String getUserId() {
        return userId;
    }

    public void setUserId(@NonNull String userId) {
        this.userId = userId;
    }

    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getPhotoUrls() {
        return photoUrls;
    }

    public void setPhotoUrls(String photoUrls) {
        this.photoUrls = photoUrls;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getHelpfulCount() {
        return helpfulCount;
    }

    public void setHelpfulCount(int helpfulCount) {
        this.helpfulCount = helpfulCount;
    }

    public boolean isVerifiedVisit() {
        return isVerifiedVisit;
    }

    public void setVerifiedVisit(boolean verifiedVisit) {
        isVerifiedVisit = verifiedVisit;
    }
}
