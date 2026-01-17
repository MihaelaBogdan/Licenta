package com.cityscape.app.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cityscape.app.database.entities.Badge;
import com.cityscape.app.database.entities.UserBadge;

import java.util.List;

/**
 * Data Access Object for Badge and UserBadge entities
 */
@Dao
public interface BadgeDao {

    // Badge operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBadge(Badge badge);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAllBadges(List<Badge> badges);

    @Query("SELECT * FROM badges")
    LiveData<List<Badge>> getAllBadges();

    @Query("SELECT * FROM badges")
    List<Badge> getAllBadgesSync();

    @Query("SELECT * FROM badges WHERE id = :badgeId")
    Badge getBadgeById(String badgeId);

    // UserBadge operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void awardBadge(UserBadge userBadge);

    @Query("SELECT b.* FROM badges b INNER JOIN user_badges ub ON b.id = ub.badgeId WHERE ub.userId = :userId ORDER BY ub.earnedAt DESC")
    LiveData<List<Badge>> getUserBadges(String userId);

    @Query("SELECT b.* FROM badges b INNER JOIN user_badges ub ON b.id = ub.badgeId WHERE ub.userId = :userId ORDER BY ub.earnedAt DESC")
    List<Badge> getUserBadgesSync(String userId);

    @Query("SELECT EXISTS(SELECT 1 FROM user_badges WHERE userId = :userId AND badgeId = :badgeId)")
    boolean hasUserBadge(String userId, String badgeId);

    @Query("SELECT COUNT(*) FROM user_badges WHERE userId = :userId")
    int getUserBadgeCount(String userId);

    @Query("SELECT b.* FROM badges b INNER JOIN user_badges ub ON b.id = ub.badgeId WHERE ub.userId = :userId AND ub.isNew = 1")
    List<Badge> getNewBadges(String userId);

    @Query("UPDATE user_badges SET isNew = 0 WHERE userId = :userId")
    void markBadgesAsSeen(String userId);

    // Get badges user hasn't earned yet
    @Query("SELECT b.* FROM badges b WHERE b.id NOT IN (SELECT badgeId FROM user_badges WHERE userId = :userId)")
    LiveData<List<Badge>> getUnearnedBadges(String userId);
}
