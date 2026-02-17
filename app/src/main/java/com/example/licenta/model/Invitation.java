package com.example.licenta.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "invitations")
public class Invitation {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int fromUserId;
    public String fromUserName;
    public int toUserId;
    public int groupId;
    public String groupName;
    public String activityName;
    public String activityDate;
    public String activityTime;
    public String status; // "pending", "accepted", "declined"
    public long sentAt;

    public Invitation() {
    }

    public Invitation(int fromUserId, String fromUserName, int toUserId,
            int groupId, String groupName, String activityName,
            String activityDate, String activityTime) {
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
