package com.cityscape.app.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.cityscape.app.model.MemberSchedule;
import java.util.List;

@Dao
public interface ScheduleDao {
    @Insert
    void insert(MemberSchedule schedule);

    @Update
    void update(MemberSchedule schedule);

    @Delete
    void delete(MemberSchedule schedule);

    @Query("SELECT * FROM member_schedules WHERE groupId = :groupId AND date = :date ORDER BY startTime ASC")
    List<MemberSchedule> getSchedulesForGroupOnDate(String groupId, long date);

    @Query("SELECT * FROM member_schedules WHERE groupId = :groupId ORDER BY date ASC, startTime ASC")
    List<MemberSchedule> getAllSchedulesForGroup(String groupId);

    @Query("SELECT * FROM member_schedules WHERE groupId = :groupId AND userId = :userId AND date = :date")
    List<MemberSchedule> getUserScheduleForDate(String groupId, String userId, long date);

    @Query("SELECT * FROM member_schedules WHERE groupId = :groupId AND userId = :userId ORDER BY date ASC")
    List<MemberSchedule> getUserSchedulesForGroup(String groupId, String userId);

    @Query("DELETE FROM member_schedules WHERE groupId = :groupId AND userId = :userId AND date = :date")
    void clearUserScheduleForDate(String groupId, String userId, long date);

    @Query("SELECT DISTINCT date FROM member_schedules WHERE groupId = :groupId ORDER BY date ASC")
    List<Long> getDatesWithSchedules(String groupId);

    @Query("SELECT * FROM member_schedules WHERE groupId = :groupId AND date = :date AND isAvailable = 1 ORDER BY startTime ASC")
    List<MemberSchedule> getAvailableMembersOnDate(String groupId, long date);
}
