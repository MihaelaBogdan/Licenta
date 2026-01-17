package com.cityscape.app.database.entities;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * PlacePhoto entity for place images
 */
@Entity(tableName = "place_photos", foreignKeys = @ForeignKey(entity = Place.class, parentColumns = "id", childColumns = "placeId", onDelete = ForeignKey.CASCADE), indices = {
        @Index("placeId") })
public class PlacePhoto {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String placeId;

    private String photoUrl;
    private String thumbnailUrl;
    private String uploadedBy; // User ID or "google"
    private long uploadedAt;
    private int width;
    private int height;
    private String caption;

    // Constructor
    public PlacePhoto(@NonNull String placeId, String photoUrl) {
        this.placeId = placeId;
        this.photoUrl = photoUrl;
        this.uploadedAt = System.currentTimeMillis();
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

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public long getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(long uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }
}
