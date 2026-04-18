package com.cityscape.app.model;

import com.google.gson.annotations.SerializedName;

public class FeedComment {
    public String id;
    
    @SerializedName("post_id")
    public String postId;
    
    @SerializedName("user_id")
    public String userId;
    
    @SerializedName("user_name")
    public String userName;
    
    @SerializedName("comment_text")
    public String commentText;
    
    @SerializedName("created_at")
    public String createdAt;
}
