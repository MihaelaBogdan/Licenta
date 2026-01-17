package com.cityscape.app.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.cityscape.app.database.entities.UserPreference;

import java.util.List;

/**
 * Data Access Object for UserPreference entity
 */
@Dao
public interface UserPreferenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(UserPreference preference);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<UserPreference> preferences);

    @Update
    void update(UserPreference preference);

    @Delete
    void delete(UserPreference preference);

    @Query("SELECT * FROM user_preferences WHERE userId = :userId")
    LiveData<List<UserPreference>> getPreferencesByUser(String userId);

    @Query("SELECT * FROM user_preferences WHERE userId = :userId")
    List<UserPreference> getPreferencesByUserSync(String userId);

    @Query("SELECT * FROM user_preferences WHERE userId = :userId AND preferenceType = :type")
    LiveData<List<UserPreference>> getPreferencesByType(String userId, String type);

    @Query("SELECT * FROM user_preferences WHERE userId = :userId AND preferenceType = :type")
    List<UserPreference> getPreferencesByTypeSync(String userId, String type);

    @Query("DELETE FROM user_preferences WHERE userId = :userId")
    void deleteAllByUser(String userId);

    @Query("DELETE FROM user_preferences WHERE userId = :userId AND preferenceType = :type")
    void deleteByType(String userId, String type);

    @Query("SELECT preferenceValue FROM user_preferences WHERE userId = :userId AND preferenceType = 'CATEGORY'")
    List<String> getUserCategories(String userId);

    @Query("SELECT AVG(weight) FROM user_preferences WHERE userId = :userId AND preferenceType = :type AND preferenceValue = :value")
    float getPreferenceWeight(String userId, String type, String value);
}
