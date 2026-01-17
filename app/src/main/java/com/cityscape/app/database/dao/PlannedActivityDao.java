package com.cityscape.app.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.cityscape.app.database.entities.PlannedActivity;
import com.cityscape.app.database.entities.Place;

import java.util.List;

/**
 * Data Access Object for PlannedActivity entity
 */
@Dao
public interface PlannedActivityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PlannedActivity activity);

    @Update
    void update(PlannedActivity activity);

    @Delete
    void delete(PlannedActivity activity);

    @Query("SELECT * FROM planned_activities WHERE userId = :userId ORDER BY plannedDate ASC, plannedTime ASC")
    LiveData<List<PlannedActivity>> getActivitiesByUser(String userId);

    @Query("SELECT * FROM planned_activities WHERE userId = :userId AND plannedDate >= :fromDate ORDER BY plannedDate ASC")
    LiveData<List<PlannedActivity>> getUpcomingActivities(String userId, long fromDate);

    @Query("SELECT * FROM planned_activities WHERE userId = :userId AND plannedDate = :date ORDER BY plannedTime ASC")
    LiveData<List<PlannedActivity>> getActivitiesByDate(String userId, long date);

    @Query("SELECT * FROM planned_activities WHERE userId = :userId AND isCompleted = 0 ORDER BY plannedDate ASC")
    LiveData<List<PlannedActivity>> getPendingActivities(String userId);

    @Query("UPDATE planned_activities SET isCompleted = 1 WHERE id = :activityId")
    void markAsCompleted(long activityId);

    @Query("SELECT COUNT(*) FROM planned_activities WHERE userId = :userId AND isCompleted = 0")
    LiveData<Integer> getPendingCount(String userId);

    @Query("DELETE FROM planned_activities WHERE id = :activityId")
    void deleteById(long activityId);

    @Query("SELECT SUM(estimatedBudget) FROM planned_activities WHERE userId = :userId AND plannedDate BETWEEN :startDate AND :endDate")
    int getTotalBudgetForPeriod(String userId, long startDate, long endDate);

    // Sync method for CalendarActivity (date as String 'yyyy-MM-dd')
    @Query("SELECT * FROM planned_activities WHERE userId = :userId AND date = :date ORDER BY time ASC")
    List<PlannedActivity> getActivitiesByUserAndDateSync(String userId, String date);

    // Get activity by ID (sync)
    @Query("SELECT * FROM planned_activities WHERE id = :activityId")
    PlannedActivity getActivityByIdSync(String activityId);
}
