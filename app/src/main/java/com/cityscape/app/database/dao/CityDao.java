package com.cityscape.app.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.cityscape.app.database.entities.City;

import java.util.List;

/**
 * Data Access Object for City entity
 */
@Dao
public interface CityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(City city);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<City> cities);

    @Update
    void update(City city);

    @Delete
    void delete(City city);

    @Query("SELECT * FROM cities WHERE id = :cityId")
    LiveData<City> getCityById(String cityId);

    @Query("SELECT * FROM cities WHERE id = :cityId")
    City getCityByIdSync(String cityId);

    @Query("SELECT * FROM cities ORDER BY name ASC")
    LiveData<List<City>> getAllCities();

    @Query("SELECT * FROM cities ORDER BY name ASC")
    List<City> getAllCitiesSync();

    @Query("SELECT * FROM cities WHERE isPopular = 1 ORDER BY placeCount DESC")
    LiveData<List<City>> getPopularCities();

    @Query("SELECT * FROM cities WHERE country = :country ORDER BY name ASC")
    LiveData<List<City>> getCitiesByCountry(String country);

    @Query("SELECT * FROM cities WHERE name LIKE '%' || :query || '%' OR country LIKE '%' || :query || '%'")
    LiveData<List<City>> searchCities(String query);

    @Query("SELECT DISTINCT country FROM cities ORDER BY country ASC")
    LiveData<List<String>> getAllCountries();

    @Query("UPDATE cities SET placeCount = placeCount + 1 WHERE id = :cityId")
    void incrementPlaceCount(String cityId);
}
