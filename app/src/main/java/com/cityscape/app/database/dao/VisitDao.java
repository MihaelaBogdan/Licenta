package com.cityscape.app.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cityscape.app.database.entities.Visit;

import java.util.List;

/**
 * Data Access Object for Visit entity
 */
@Dao
public interface VisitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Visit visit);

    @Delete
    void delete(Visit visit);

    @Query("SELECT * FROM visits WHERE userId = :userId ORDER BY visitDate DESC")
    LiveData<List<Visit>> getVisitsByUser(String userId);

    @Query("SELECT * FROM visits WHERE userId = :userId ORDER BY visitDate DESC")
    List<Visit> getVisitsByUserSync(String userId);

    @Query("SELECT * FROM visits WHERE userId = :userId AND placeId = :placeId ORDER BY visitDate DESC")
    LiveData<List<Visit>> getVisitsByUserAndPlace(String userId, String placeId);

    @Query("SELECT COUNT(*) FROM visits WHERE userId = :userId")
    int getVisitCount(String userId);

    @Query("SELECT COUNT(DISTINCT placeId) FROM visits WHERE userId = :userId")
    int getUniquePlaceCount(String userId);

    @Query("SELECT SUM(spentAmount) FROM visits WHERE userId = :userId AND visitDate BETWEEN :startDate AND :endDate")
    int getTotalSpent(String userId, long startDate, long endDate);

    @Query("SELECT placeId FROM visits WHERE userId = :userId GROUP BY placeId ORDER BY COUNT(*) DESC LIMIT :limit")
    List<String> getMostVisitedPlaceIds(String userId, int limit);

    @Query("SELECT COUNT(DISTINCT cityId) FROM visits v INNER JOIN places p ON v.placeId = p.id WHERE v.userId = :userId")
    int getVisitedCityCount(String userId);
}
