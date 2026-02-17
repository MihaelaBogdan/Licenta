package com.example.licenta.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "group_members")
public class GroupMember {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int groupId;
    public int userId;
    public String userName;
    public String status; // "pending", "accepted", "declined"
    public long joinedAt;
    public boolean isCreator;

    public GroupMember() {
    }

    public GroupMember(int groupId, int userId, String userName, boolean isCreator) {
        this.groupId = groupId;
        this.userId = userId;
        this.userName = userName;
        this.status = isCreator ? "accepted" : "pending";
        this.joinedAt = System.currentTimeMillis();
        this.isCreator = isCreator;
    }
}
