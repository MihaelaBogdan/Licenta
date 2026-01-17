package com.cityscape.app.database.entities;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.annotation.NonNull;

/**
 * UserBadge junction entity for earned badges
 */
@Entity(tableName = "user_badges", primaryKeys = { "userId", "badgeId" }, foreignKeys = {
        @ForeignKey(entity = User.class, parentColumns = "id", childColumns = "userId", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = Badge.class, parentColumns = "id", childColumns = "badgeId", onDelete = ForeignKey.CASCADE)
}, indices = { @Index("userId"), @Index("badgeId") })
public class UserBadge {

    @NonNull
    private String userId;

    @NonNull
    private String badgeId;

    private long earnedAt;
    private boolean isNew; // For showing "New!" indicator

    // Constructor
    public UserBadge(@NonNull String userId, @NonNull String badgeId) {
        this.userId = userId;
        this.badgeId = badgeId;
        this.earnedAt = System.currentTimeMillis();
        this.isNew = true;
    }

    // Getters and Setters
    @NonNull
    public String getUserId() {
        return userId;
    }

    public void setUserId(@NonNull String userId) {
        this.userId = userId;
    }

    @NonNull
    public String getBadgeId() {
        return badgeId;
    }

    public void setBadgeId(@NonNull String badgeId) {
        this.badgeId = badgeId;
    }

    public long getEarnedAt() {
        return earnedAt;
    }

    public void setEarnedAt(long earnedAt) {
        this.earnedAt = earnedAt;
    }

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean aNew) {
        isNew = aNew;
    }
}
