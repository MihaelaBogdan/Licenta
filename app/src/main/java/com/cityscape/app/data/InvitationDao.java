package com.cityscape.app.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.cityscape.app.model.Invitation;
import java.util.List;

@Dao
public interface InvitationDao {
    @Insert
    void insert(Invitation invitation);

    @Update
    void update(Invitation invitation);

    @Delete
    void delete(Invitation invitation);

    @Query("SELECT * FROM invitations WHERE toUserId = :userId ORDER BY sentAt DESC")
    List<Invitation> getInvitationsForUser(String userId);

    @Query("SELECT * FROM invitations WHERE toUserId = :userId AND status = 'pending' ORDER BY sentAt DESC")
    List<Invitation> getPendingInvitations(String userId);

    @Query("SELECT COUNT(*) FROM invitations WHERE toUserId = :userId AND status = 'pending'")
    int getPendingCount(String userId);

    @Query("SELECT * FROM invitations WHERE fromUserId = :userId ORDER BY sentAt DESC")
    List<Invitation> getSentInvitations(String userId);

    @Query("SELECT * FROM invitations WHERE id = :invitationId")
    Invitation getInvitationById(int invitationId);
}
