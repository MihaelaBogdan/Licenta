package com.cityscape.app.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.cityscape.app.model.Place;
import java.util.List;

@Dao
public interface PlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPlaces(List<Place> places);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPlace(Place place);

    @Query("SELECT * FROM places")
    List<Place> getAllPlaces();

    @Query("SELECT * FROM places WHERE type = :type")
    List<Place> getPlacesByType(String type);

    @Query("SELECT * FROM places WHERE isFavorite = 1")
    List<Place> getFavoritePlaces();

    @Query("SELECT * FROM places WHERE id = :id")
    Place getPlaceById(String id);

    @Query("DELETE FROM places")
    void clearAllPlaces();
}
