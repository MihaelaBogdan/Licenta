package com.cityscape.app.database.entities;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * ActivityInvite entity for sharing/inviting users to activities
 */
@Entity(tableName = "activity_invites", foreignKeys = {
        @ForeignKey(entity = PlannedActivity.class, parentColumns = "id", childColumns = "activityId", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = User.class, parentColumns = "id", childColumns = "senderId", onDelete = ForeignKey.CASCADE)
}, indices = { @Index("activityId"), @Index("senderId"), @Index("inviteeEmail") })
public class ActivityInvite {

    @PrimaryKey
    @NonNull
    private String id;

    @NonNull
    private String activityId;

    @NonNull
    private String senderId;

    private String inviteeEmail;
    private String inviteeUserId; // If already a user
    private String status; // pending, accepted, declined
    private String message;
    private long createdAt;
    private long respondedAt;
    private boolean notificationSent;
    private boolean emailSent;

    // Status constants
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_DECLINED = "declined";

    // Constructor
    public ActivityInvite(@NonNull String id, @NonNull String activityId,
            @NonNull String senderId, String inviteeEmail) {
        this.id = id;
        this.activityId = activityId;
        this.senderId = senderId;
        this.inviteeEmail = inviteeEmail;
        this.status = STATUS_PENDING;
        this.createdAt = System.currentTimeMillis();
        this.notificationSent = false;
        this.emailSent = false;
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
    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(@NonNull String activityId) {
        this.activityId = activityId;
    }

    @NonNull
    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(@NonNull String senderId) {
        this.senderId = senderId;
    }

    public String getInviteeEmail() {
        return inviteeEmail;
    }

    public void setInviteeEmail(String inviteeEmail) {
        this.inviteeEmail = inviteeEmail;
    }

    public String getInviteeUserId() {
        return inviteeUserId;
    }

    public void setInviteeUserId(String inviteeUserId) {
        this.inviteeUserId = inviteeUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        if (!status.equals(STATUS_PENDING)) {
            this.respondedAt = System.currentTimeMillis();
        }
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(long respondedAt) {
        this.respondedAt = respondedAt;
    }

    public boolean isNotificationSent() {
        return notificationSent;
    }

    public void setNotificationSent(boolean notificationSent) {
        this.notificationSent = notificationSent;
    }

    public boolean isEmailSent() {
        return emailSent;
    }

    public void setEmailSent(boolean emailSent) {
        this.emailSent = emailSent;
    }
}
