package com.example.licenta.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.example.licenta.model.Vote;
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
