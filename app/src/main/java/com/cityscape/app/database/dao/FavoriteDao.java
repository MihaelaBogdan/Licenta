package com.cityscape.app.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cityscape.app.database.entities.Favorite;
import com.cityscape.app.database.entities.Place;

import java.util.List;

/**
 * Data Access Object for Favorite entity
 */
@Dao
public interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Favorite favorite);

    @Delete
    void delete(Favorite favorite);

    @Query("DELETE FROM favorites WHERE userId = :userId AND placeId = :placeId")
    void deleteByUserAndPlace(String userId, String placeId);

    @Query("SELECT * FROM favorites WHERE userId = :userId ORDER BY addedAt DESC")
    LiveData<List<Favorite>> getFavoritesByUser(String userId);

    @Query("SELECT p.* FROM places p INNER JOIN favorites f ON p.id = f.placeId WHERE f.userId = :userId ORDER BY f.addedAt DESC")
    LiveData<List<Place>> getFavoritePlaces(String userId);

    @Query("SELECT p.* FROM places p INNER JOIN favorites f ON p.id = f.placeId WHERE f.userId = :userId ORDER BY f.addedAt DESC")
    List<Place> getFavoritePlacesSync(String userId);

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE userId = :userId AND placeId = :placeId)")
    LiveData<Boolean> isFavorite(String userId, String placeId);

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE userId = :userId AND placeId = :placeId)")
    boolean isFavoriteSync(String userId, String placeId);

    @Query("SELECT COUNT(*) FROM favorites WHERE userId = :userId")
    LiveData<Integer> getFavoriteCount(String userId);

    @Query("SELECT COUNT(*) FROM favorites WHERE userId = :userId")
    int getFavoriteCountSync(String userId);
}
