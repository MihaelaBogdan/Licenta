package com.cityscape.app.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.cityscape.app.database.entities.Place;

import java.util.List;

/**
 * Data Access Object for Place entity
 * Includes queries optimized for recommendation system
 */
@Dao
public interface PlaceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Place place);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Place> places);

    @Update
    void update(Place place);

    @Delete
    void delete(Place place);

    @Query("SELECT * FROM places WHERE id = :placeId")
    LiveData<Place> getPlaceById(String placeId);

    @Query("SELECT * FROM places WHERE id = :placeId")
    Place getPlaceByIdSync(String placeId);

    @Query("SELECT * FROM places WHERE cityId = :cityId ORDER BY rating DESC")
    LiveData<List<Place>> getPlacesByCity(String cityId);

    @Query("SELECT * FROM places WHERE cityId = :cityId ORDER BY rating DESC")
    List<Place> getPlacesByCitySync(String cityId);

    @Query("SELECT * FROM places WHERE cityId = :cityId AND category = :category ORDER BY rating DESC")
    LiveData<List<Place>> getPlacesByCityAndCategory(String cityId, String category);

    @Query("SELECT * FROM places WHERE cityId = :cityId ORDER BY trendingScore DESC LIMIT :limit")
    LiveData<List<Place>> getTrendingPlaces(String cityId, int limit);

    @Query("SELECT * FROM places WHERE cityId = :cityId ORDER BY popularityScore DESC LIMIT :limit")
    LiveData<List<Place>> getPopularPlaces(String cityId, int limit);

    @Query("SELECT * FROM places WHERE cityId = :cityId ORDER BY rating DESC LIMIT :limit")
    LiveData<List<Place>> getTopRatedPlaces(String cityId, int limit);

    @Query("SELECT * FROM places WHERE cityId = :cityId AND priceLevel <= :maxPriceLevel ORDER BY rating DESC")
    LiveData<List<Place>> getPlacesByBudget(String cityId, int maxPriceLevel);

    @Query("SELECT * FROM places WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    LiveData<List<Place>> searchPlaces(String query);

    @Query("SELECT * FROM places WHERE cityId = :cityId AND (name LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%')")
    LiveData<List<Place>> searchPlacesInCity(String cityId, String query);

    // For nearby places (using bounding box approximation)
    @Query("SELECT * FROM places WHERE " +
            "latitude BETWEEN :minLat AND :maxLat AND " +
            "longitude BETWEEN :minLng AND :maxLng " +
            "ORDER BY rating DESC LIMIT :limit")
    List<Place> getNearbyPlaces(double minLat, double maxLat, double minLng, double maxLng, int limit);

    // For AI recommendations - get places matching categories
    @Query("SELECT * FROM places WHERE cityId = :cityId AND category IN (:categories) ORDER BY popularityScore DESC")
    List<Place> getPlacesByCategories(String cityId, List<String> categories);

    // For AI recommendations - get places matching atmosphere
    @Query("SELECT * FROM places WHERE cityId = :cityId AND atmosphereTags LIKE '%' || :atmosphere || '%'")
    List<Place> getPlacesByAtmosphere(String cityId, String atmosphere);

    // Update scores
    @Query("UPDATE places SET popularityScore = :score WHERE id = :placeId")
    void updatePopularityScore(String placeId, float score);

    @Query("UPDATE places SET trendingScore = :score WHERE id = :placeId")
    void updateTrendingScore(String placeId, float score);

    @Query("UPDATE places SET rating = :rating, reviewCount = :reviewCount WHERE id = :placeId")
    void updateRating(String placeId, float rating, int reviewCount);

    // Get random place based on criteria
    @Query("SELECT * FROM places WHERE cityId = :cityId AND category IN (:categories) ORDER BY RANDOM() LIMIT 1")
    Place getRandomPlace(String cityId, List<String> categories);

    // Count places
    @Query("SELECT COUNT(*) FROM places WHERE cityId = :cityId")
    int getPlaceCount(String cityId);

    @Query("SELECT COUNT(*) FROM places WHERE cityId = :cityId AND category = :category")
    int getPlaceCountByCategory(String cityId, String category);
}
