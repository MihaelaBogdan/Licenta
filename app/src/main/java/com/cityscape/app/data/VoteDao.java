package com.cityscape.app.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.cityscape.app.model.Vote;
import java.util.List;

@Dao
public interface VoteDao {
    @Insert
    void insert(Vote vote);

    @Query("SELECT * FROM group_votes WHERE userId = :userId AND groupId = :groupId")
    List<Vote> getUserVotesInGroup(String userId, String groupId);

    @Query("DELETE FROM group_votes WHERE userId = :userId AND suggestionId = :suggestionId")
    void removeVote(String userId, String suggestionId);

    @Query("SELECT COUNT(*) FROM group_votes WHERE suggestionId = :suggestionId")
    int getVoteCountForSuggestion(String suggestionId);
}
