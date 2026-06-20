package com.cityscape.app.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.cityscape.app.model.FeedBookmark;
import java.util.List;

@Dao
public interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FeedBookmark bookmark);

    @Delete
    void delete(FeedBookmark bookmark);

    @Query("SELECT postId FROM feed_bookmarks WHERE userId = :userId")
    List<String> getBookmarkedPostIds(String userId);

    @Query("SELECT EXISTS(SELECT 1 FROM feed_bookmarks WHERE postId = :postId AND userId = :userId)")
    boolean isBookmarked(String postId, String userId);
}
