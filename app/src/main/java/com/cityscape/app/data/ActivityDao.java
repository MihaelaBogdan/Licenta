package com.cityscape.app.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.cityscape.app.model.PlannedActivity;
import java.util.List;

@Dao
public interface ActivityDao {
    @Insert
    void insert(PlannedActivity activity);

    @Update
    void update(PlannedActivity activity);

    @Delete
    void delete(PlannedActivity activity);

    @Query("SELECT * FROM planned_activities WHERE userId = :userId ORDER BY scheduledDate ASC")
    List<PlannedActivity> getActivitiesForUser(String userId);

    @Query("SELECT * FROM planned_activities WHERE userId = :userId AND scheduledDate = :date ORDER BY scheduledTime ASC")
    List<PlannedActivity> getActivitiesForDate(String userId, long date);

    @Query("SELECT * FROM planned_activities WHERE userId = :userId AND scheduledDate >= :startDate AND scheduledDate <= :endDate ORDER BY scheduledDate ASC, scheduledTime ASC")
    List<PlannedActivity> getActivitiesInRange(String userId, long startDate, long endDate);

    @Query("SELECT * FROM planned_activities WHERE userId = :userId AND isCompleted = 0 ORDER BY scheduledDate ASC LIMIT :limit")
    List<PlannedActivity> getUpcomingActivities(String userId, int limit);

    @Query("SELECT DISTINCT scheduledDate FROM planned_activities WHERE userId = :userId")
    List<Long> getDatesWithActivities(String userId);

    @Query("SELECT * FROM planned_activities WHERE id = :activityId LIMIT 1")
    PlannedActivity getActivityById(String activityId);
}
