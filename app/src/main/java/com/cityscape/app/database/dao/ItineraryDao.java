package com.cityscape.app.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cityscape.app.database.entities.Itinerary;

import java.util.List;

/**
 * Data Access Object for Itinerary entity
 */
@Dao
public interface ItineraryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Itinerary itinerary);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Itinerary> itineraries);

    @Delete
    void delete(Itinerary itinerary);

    @Query("SELECT * FROM itineraries WHERE id = :itineraryId")
    LiveData<Itinerary> getItineraryById(String itineraryId);

    @Query("SELECT * FROM itineraries WHERE id = :itineraryId")
    Itinerary getItineraryByIdSync(String itineraryId);

    @Query("SELECT * FROM itineraries WHERE cityId = :cityId AND isPublic = 1 ORDER BY usageCount DESC")
    LiveData<List<Itinerary>> getItinerariesByCity(String cityId);

    @Query("SELECT * FROM itineraries WHERE cityId = :cityId AND theme = :theme ORDER BY rating DESC")
    LiveData<List<Itinerary>> getItinerariesByTheme(String cityId, String theme);

    @Query("SELECT * FROM itineraries WHERE createdBy = :userId ORDER BY createdAt DESC")
    LiveData<List<Itinerary>> getUserItineraries(String userId);

    @Query("SELECT * FROM itineraries WHERE cityId = :cityId ORDER BY rating DESC LIMIT :limit")
    LiveData<List<Itinerary>> getTopItineraries(String cityId, int limit);

    @Query("UPDATE itineraries SET usageCount = usageCount + 1 WHERE id = :itineraryId")
    void incrementUsage(String itineraryId);

    @Query("SELECT * FROM itineraries WHERE cityId = :cityId AND estimatedBudget <= :maxBudget ORDER BY rating DESC")
    LiveData<List<Itinerary>> getItinerariesByBudget(String cityId, int maxBudget);
}
