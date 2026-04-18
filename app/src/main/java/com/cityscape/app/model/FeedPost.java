package com.cityscape.app.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class FeedPost {
    public String id;
    
    @SerializedName("user_id")
    public String userId;
    
    @SerializedName("user_name")
    public String userName;
    
    @SerializedName("user_avatar")
    public String userAvatar;
    
    @SerializedName("place_name")
    public String placeName;
    
    @SerializedName("place_id")
    public String placeId;
    
    @SerializedName("image_url")
    public String imageUrl;
    
    public String caption;
    public double rating;
    public double latitude;
    public double longitude;
    
    @SerializedName("created_at")
    public String createdAt;
    
    @SerializedName("likes_count")
    public int likesCount;
    
    @SerializedName("comments_count")
    public int commentsCount;
    
    @SerializedName("is_liked")
    public boolean isLiked;
    
    public List<FeedComment> comments;
}
