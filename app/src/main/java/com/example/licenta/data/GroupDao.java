package com.example.licenta.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.example.licenta.model.ActivityGroup;
import com.example.licenta.model.GroupMember;
import java.util.List;

@Dao
public interface GroupDao {
    // Activity Groups
    @Insert
    void insertGroup(ActivityGroup group);

    @Update
    void updateGroup(ActivityGroup group);

    @Delete
    void deleteGroup(ActivityGroup group);

    @Query("SELECT * FROM activity_groups WHERE id = :groupId")
    ActivityGroup getGroupById(String groupId);

    @Query("SELECT * FROM activity_groups WHERE groupCode = :code LIMIT 1")
    ActivityGroup getGroupByCode(String code);

    @Query("SELECT * FROM activity_groups WHERE activityId = :activityId")
    ActivityGroup getGroupForActivity(String activityId);

    @Query("SELECT ag.* FROM activity_groups ag INNER JOIN group_members gm ON ag.id = gm.groupId WHERE gm.userId = :userId")
    List<ActivityGroup> getGroupsForUser(String userId);

    // Group Members
    @Insert
    void insertMember(GroupMember member);

    @Update
    void updateMember(GroupMember member);

    @Delete
    void deleteMember(GroupMember member);

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    List<GroupMember> getMembersForGroup(String groupId);

    @Query("SELECT * FROM group_members WHERE groupId = :groupId AND userId = :userId LIMIT 1")
    GroupMember getMember(String groupId, String userId);

    @Query("SELECT COUNT(*) FROM group_members WHERE groupId = :groupId AND status = 'accepted'")
    int getAcceptedMemberCount(String groupId);
}
