package com.example.licenta.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.example.licenta.model.GroupSuggestion;
import java.util.List;

@Dao
public interface GroupSuggestionDao {
    @Insert
    void insert(GroupSuggestion suggestion);

    @Update
    void update(GroupSuggestion suggestion);

    @Query("SELECT * FROM group_suggestions WHERE groupId = :groupId ORDER BY voteCount DESC")
    List<GroupSuggestion> getSuggestionsForGroup(String groupId);

    @Query("SELECT * FROM group_suggestions WHERE id = :id")
    GroupSuggestion getSuggestionById(String id);
}
