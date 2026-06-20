package com.cityscape.app.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "feed_bookmarks", primaryKeys = {"postId", "userId"})
public class FeedBookmark {
    @NonNull
    public String postId;
    
    @NonNull
    public String userId;

    public FeedBookmark(@NonNull String postId, @NonNull String userId) {
        this.postId = postId;
        this.userId = userId;
    }
}
