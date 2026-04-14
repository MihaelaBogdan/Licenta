package com.cityscape.app.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import com.cityscape.app.model.UserAchievement;
import java.util.List;

@Dao
public interface AchievementDao {
    @Insert
    void insert(UserAchievement achievement);

    @Delete
    void delete(UserAchievement achievement);

    @Query("SELECT * FROM user_achievements WHERE userId = :userId ORDER BY earnedAt DESC")
    List<UserAchievement> getAchievementsForUser(String userId);

    @Query("SELECT * FROM user_achievements WHERE userId = :userId ORDER BY earnedAt DESC LIMIT :limit")
    List<UserAchievement> getRecentAchievements(String userId, int limit);

    @Query("SELECT SUM(xpReward) FROM user_achievements WHERE userId = :userId")
    int getTotalXpEarned(String userId);
}
