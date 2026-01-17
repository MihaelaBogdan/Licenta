package com.cityscape.app.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cityscape.app.database.entities.PlacePhoto;

import java.util.List;

/**
 * Data Access Object for PlacePhoto entity
 */
@Dao
public interface PlacePhotoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PlacePhoto photo);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PlacePhoto> photos);

    @Delete
    void delete(PlacePhoto photo);

    @Query("SELECT * FROM place_photos WHERE placeId = :placeId ORDER BY uploadedAt DESC")
    LiveData<List<PlacePhoto>> getPhotosByPlace(String placeId);

    @Query("SELECT * FROM place_photos WHERE placeId = :placeId ORDER BY uploadedAt DESC")
    List<PlacePhoto> getPhotosByPlaceSync(String placeId);

    @Query("SELECT * FROM place_photos WHERE placeId = :placeId ORDER BY uploadedAt DESC LIMIT 1")
    PlacePhoto getFirstPhoto(String placeId);

    @Query("SELECT COUNT(*) FROM place_photos WHERE placeId = :placeId")
    int getPhotoCount(String placeId);

    @Query("SELECT COUNT(*) FROM place_photos WHERE uploadedBy = :userId")
    int getUserPhotoCount(String userId);
}
