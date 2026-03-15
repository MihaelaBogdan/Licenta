package com.example.licenta.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "invitations")
public class Invitation {
    @PrimaryKey(autoGenerate = false)
    @NonNull
    public String id;

    public String fromUserId;
    public String fromUserName;
    public String toUserId;
    public String groupId;
    public String groupName;
    public String activityName;
    public String activityDate;
    public String activityTime;
    public String status; // "pending", "accepted", "declined"
    public long sentAt;

    public Invitation() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    public Invitation(String fromUserId, String fromUserName, String toUserId,
            String groupId, String groupName, String activityName,
            String activityDate, String activityTime) {
        this.id = java.util.UUID.randomUUID().toString();
        this.fromUserId = fromUserId;
        this.fromUserName = fromUserName;
        this.toUserId = toUserId;
        this.groupId = groupId;
        this.groupName = groupName;
        this.activityName = activityName;
        this.activityDate = activityDate;
        this.activityTime = activityTime;
        this.status = "pending";
        this.sentAt = System.currentTimeMillis();
    }
}
