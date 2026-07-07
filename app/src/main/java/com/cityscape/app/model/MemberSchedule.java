package com.cityscape.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "member_schedules")
public class MemberSchedule {
    @PrimaryKey(autoGenerate = false)
    @NonNull
    public String id;

    public String groupId;
    public String userId;
    public String userName;
    public long date; 
    public String startTime; 
    public String endTime; 
    public boolean isAvailable; 
    public String note; 

    public MemberSchedule() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    public MemberSchedule(String groupId, String userId, String userName, long date,
            String startTime, String endTime, boolean isAvailable) {
        this.id = java.util.UUID.randomUUID().toString();
        this.groupId = groupId;
        this.userId = userId;
        this.userName = userName;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isAvailable = isAvailable;
        this.note = "";
    }

    public String getTimeRange() {
        return startTime + " - " + endTime;
    }
}
