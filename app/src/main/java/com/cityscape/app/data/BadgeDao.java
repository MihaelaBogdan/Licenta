package com.cityscape.app.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.cityscape.app.model.UserBadge;
import java.util.List;

@Dao
public interface BadgeDao {
    @Insert
    void insert(UserBadge badge);

    @Update
    void update(UserBadge badge);

    @Delete
    void delete(UserBadge badge);

    @Query("SELECT * FROM user_badges WHERE userId = :userId")
    List<UserBadge> getBadgesForUser(String userId);

    @Query("SELECT * FROM user_badges WHERE userId = :userId AND isUnlocked = 1")
    List<UserBadge> getUnlockedBadges(String userId);

    @Query("SELECT COUNT(*) FROM user_badges WHERE userId = :userId AND isUnlocked = 1")
    int getUnlockedBadgeCount(String userId);

    @Query("SELECT * FROM user_badges WHERE userId = :userId AND badgeId = :badgeId LIMIT 1")
    UserBadge getBadge(String userId, String badgeId);
}
