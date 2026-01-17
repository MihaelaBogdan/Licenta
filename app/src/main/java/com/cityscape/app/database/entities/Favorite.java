package com.cityscape.app.database.entities;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Favorite entity for saved places
 */
@Entity(tableName = "favorites", foreignKeys = {
        @ForeignKey(entity = User.class, parentColumns = "id", childColumns = "userId", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = Place.class, parentColumns = "id", childColumns = "placeId", onDelete = ForeignKey.CASCADE)
}, indices = { @Index("userId"), @Index("placeId"), @Index(value = { "userId", "placeId" }, unique = true) })
public class Favorite {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String userId;

    @NonNull
    private String placeId;

    private long addedAt;
    private String notes;

    // Constructor
    public Favorite(@NonNull String userId, @NonNull String placeId) {
        this.userId = userId;
        this.placeId = placeId;
        this.addedAt = System.currentTimeMillis();
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

    public long getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(long addedAt) {
        this.addedAt = addedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
