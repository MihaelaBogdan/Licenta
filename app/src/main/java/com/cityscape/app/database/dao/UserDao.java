package com.cityscape.app.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.cityscape.app.database.entities.User;

import java.util.List;

/**
 * Data Access Object for User entity
 */
@Dao
public interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(User user);

    @Update
    void update(User user);

    @Delete
    void delete(User user);

    @Query("SELECT * FROM users WHERE id = :userId")
    LiveData<User> getUserById(String userId);

    @Query("SELECT * FROM users WHERE id = :userId")
    User getUserByIdSync(String userId);

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    User getUserByEmailSync(String email);

    @Query("SELECT * FROM users")
    LiveData<List<User>> getAllUsers();

    @Query("UPDATE users SET selectedCityId = :cityId WHERE id = :userId")
    void updateSelectedCity(String userId, String cityId);

    @Query("UPDATE users SET totalPoints = totalPoints + :points WHERE id = :userId")
    void addPoints(String userId, int points);

    @Query("UPDATE users SET lastLoginAt = :timestamp WHERE id = :userId")
    void updateLastLogin(String userId, long timestamp);

    @Query("UPDATE users SET isDarkMode = :isDarkMode WHERE id = :userId")
    void updateDarkMode(String userId, boolean isDarkMode);

    @Query("DELETE FROM users WHERE id = :userId")
    void deleteById(String userId);

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    User getUserByEmail(String email);
}
