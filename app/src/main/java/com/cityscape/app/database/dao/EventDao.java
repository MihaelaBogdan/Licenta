package com.cityscape.app.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cityscape.app.database.entities.Event;

import java.util.List;

/**
 * Data Access Object for Event entity
 */
@Dao
public interface EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Event event);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Event> events);

    @Delete
    void delete(Event event);

    @Query("SELECT * FROM events WHERE id = :eventId")
    LiveData<Event> getEventById(String eventId);

    @Query("SELECT * FROM events WHERE placeId = :placeId AND startDate >= :currentDate ORDER BY startDate ASC")
    LiveData<List<Event>> getUpcomingEventsByPlace(String placeId, long currentDate);

    @Query("SELECT e.* FROM events e INNER JOIN favorites f ON e.placeId = f.placeId " +
            "WHERE f.userId = :userId AND e.startDate >= :currentDate ORDER BY e.startDate ASC")
    LiveData<List<Event>> getEventsAtFavoritePlaces(String userId, long currentDate);

    @Query("SELECT e.* FROM events e INNER JOIN places p ON e.placeId = p.id " +
            "WHERE p.cityId = :cityId AND e.startDate >= :currentDate ORDER BY e.startDate ASC LIMIT :limit")
    LiveData<List<Event>> getUpcomingEventsInCity(String cityId, long currentDate, int limit);

    @Query("UPDATE events SET interestedCount = interestedCount + 1 WHERE id = :eventId")
    void incrementInterested(String eventId);

    @Query("UPDATE events SET goingCount = goingCount + 1 WHERE id = :eventId")
    void incrementGoing(String eventId);
}
