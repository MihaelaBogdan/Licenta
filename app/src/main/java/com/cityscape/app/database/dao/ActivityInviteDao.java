package com.cityscape.app.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.cityscape.app.database.entities.ActivityInvite;

import java.util.List;

/**
 * Data Access Object for ActivityInvite entity
 */
@Dao
public interface ActivityInviteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ActivityInvite invite);

    @Update
    void update(ActivityInvite invite);

    @Delete
    void delete(ActivityInvite invite);

    // Get invites sent by a user
    @Query("SELECT * FROM activity_invites WHERE senderId = :userId ORDER BY createdAt DESC")
    LiveData<List<ActivityInvite>> getInvitesBySender(String userId);

    // Get invites received by a user (by email or userId)
    @Query("SELECT * FROM activity_invites WHERE inviteeEmail = :email OR inviteeUserId = :userId ORDER BY createdAt DESC")
    LiveData<List<ActivityInvite>> getInvitesForUser(String email, String userId);

    // Get pending invites for a user
    @Query("SELECT * FROM activity_invites WHERE (inviteeEmail = :email OR inviteeUserId = :userId) AND status = 'pending' ORDER BY createdAt DESC")
    LiveData<List<ActivityInvite>> getPendingInvites(String email, String userId);

    // Get invites for an activity
    @Query("SELECT * FROM activity_invites WHERE activityId = :activityId ORDER BY createdAt DESC")
    LiveData<List<ActivityInvite>> getInvitesForActivity(String activityId);

    // Get invites for activity (sync)
    @Query("SELECT * FROM activity_invites WHERE activityId = :activityId")
    List<ActivityInvite> getInvitesForActivitySync(String activityId);

    // Count pending invites
    @Query("SELECT COUNT(*) FROM activity_invites WHERE (inviteeEmail = :email OR inviteeUserId = :userId) AND status = 'pending'")
    LiveData<Integer> getPendingInviteCount(String email, String userId);

    // Update invite status
    @Query("UPDATE activity_invites SET status = :status WHERE id = :inviteId")
    void updateStatus(String inviteId, String status);

    // Mark notification sent
    @Query("UPDATE activity_invites SET notificationSent = 1 WHERE id = :inviteId")
    void markNotificationSent(String inviteId);

    // Mark email sent
    @Query("UPDATE activity_invites SET emailSent = 1 WHERE id = :inviteId")
    void markEmailSent(String inviteId);
}
