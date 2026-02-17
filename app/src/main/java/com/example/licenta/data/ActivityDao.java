package com.example.licenta.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.example.licenta.model.PlannedActivity;
import java.util.List;

@Dao
public interface ActivityDao {
    @Insert
    long insert(PlannedActivity activity);

    @Update
    void update(PlannedActivity activity);

    @Delete
    void delete(PlannedActivity activity);

    @Query("SELECT * FROM planned_activities WHERE userId = :userId ORDER BY scheduledDate ASC")
    List<PlannedActivity> getActivitiesForUser(int userId);

    @Query("SELECT * FROM planned_activities WHERE userId = :userId AND scheduledDate = :date ORDER BY scheduledTime ASC")
    List<PlannedActivity> getActivitiesForDate(int userId, long date);

    @Query("SELECT * FROM planned_activities WHERE userId = :userId AND scheduledDate >= :startDate AND scheduledDate <= :endDate ORDER BY scheduledDate ASC, scheduledTime ASC")
    List<PlannedActivity> getActivitiesInRange(int userId, long startDate, long endDate);

    @Query("SELECT * FROM planned_activities WHERE userId = :userId AND isCompleted = 0 ORDER BY scheduledDate ASC LIMIT :limit")
    List<PlannedActivity> getUpcomingActivities(int userId, int limit);

    @Query("SELECT DISTINCT scheduledDate FROM planned_activities WHERE userId = :userId")
    List<Long> getDatesWithActivities(int userId);
}
