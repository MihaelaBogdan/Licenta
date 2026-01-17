package com.cityscape.app.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.cityscape.app.database.entities.Review;

import java.util.List;

/**
 * Data Access Object for Review entity
 */
@Dao
public interface ReviewDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Review review);

    @Update
    void update(Review review);

    @Delete
    void delete(Review review);

    @Query("SELECT * FROM reviews WHERE placeId = :placeId ORDER BY createdAt DESC")
    LiveData<List<Review>> getReviewsByPlace(String placeId);

    @Query("SELECT * FROM reviews WHERE placeId = :placeId ORDER BY createdAt DESC")
    List<Review> getReviewsByPlaceSync(String placeId);

    @Query("SELECT * FROM reviews WHERE userId = :userId ORDER BY createdAt DESC")
    LiveData<List<Review>> getReviewsByUser(String userId);

    @Query("SELECT * FROM reviews WHERE userId = :userId ORDER BY createdAt DESC")
    List<Review> getReviewsByUserSync(String userId);

    @Query("SELECT AVG(rating) FROM reviews WHERE placeId = :placeId")
    float getAverageRating(String placeId);

    @Query("SELECT COUNT(*) FROM reviews WHERE placeId = :placeId")
    int getReviewCount(String placeId);

    @Query("SELECT COUNT(*) FROM reviews WHERE userId = :userId")
    int getUserReviewCount(String userId);

    @Query("SELECT * FROM reviews WHERE placeId = :placeId ORDER BY helpfulCount DESC LIMIT :limit")
    LiveData<List<Review>> getTopReviews(String placeId, int limit);

    @Query("UPDATE reviews SET helpfulCount = helpfulCount + 1 WHERE id = :reviewId")
    void incrementHelpful(long reviewId);

    @Query("SELECT EXISTS(SELECT 1 FROM reviews WHERE userId = :userId AND placeId = :placeId)")
    boolean hasUserReviewed(String userId, String placeId);
}
