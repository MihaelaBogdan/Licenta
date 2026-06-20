package com.cityscape.app.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.cityscape.app.model.GroupMessage;
import java.util.List;

@Dao
public interface GroupMessageDao {
    @Insert
    void insert(GroupMessage message);

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    List<GroupMessage> getMessagesForGroup(String groupId);

    @Query("DELETE FROM group_messages WHERE groupId = :groupId")
    void deleteMessagesForGroup(String groupId);
}
