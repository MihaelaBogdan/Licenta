package com.cityscape.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.cityscape.app.R;
import com.cityscape.app.model.FeedPost;
import java.util.ArrayList;
import java.util.List;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.PostViewHolder> {

    private List<FeedPost> posts = new ArrayList<>();
    private OnPostActionListener listener;

    public interface OnPostActionListener {
        void onLikeClicked(FeedPost post, int position);
        void onCommentClicked(FeedPost post);
        void onShareClicked(FeedPost post);
    }

    public FeedAdapter(OnPostActionListener listener) {
        this.listener = listener;
    }

    public void setPosts(List<FeedPost> newPosts) {
        this.posts = newPosts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_feed_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        FeedPost post = posts.get(position);

        String name = post.userName != null ? post.userName : "Explorer";
        holder.userName.setText(name);
        holder.captionUser.setText(name.toLowerCase());
        holder.placeName.setText(post.placeName != null ? post.placeName : "Locație necunoscută");
        holder.caption.setText(post.caption != null ? post.caption : "");
        
        // Likes formatting
        String likesText = String.format("%,d aprecieri", post.likesCount);
        holder.likesCount.setText(likesText);

        // Time ago - uppercase for style
        holder.timeAgo.setText(getTimeAgo(post.createdAt).toUpperCase());

        // Post Image
        if (post.imageUrl != null && !post.imageUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                .load(post.imageUrl)
                .centerCrop()
                .placeholder(R.color.app_surface)
                .into(holder.postImage);
        }

        // User Avatar
        if (post.userAvatar != null && !post.userAvatar.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                .load(post.userAvatar)
                .circleCrop()
                .placeholder(R.drawable.ic_profile)
                .into(holder.userAvatar);
        }

        // Like state - change icon if liked
        if (post.isLiked) {
            holder.btnLike.setImageResource(R.drawable.ic_heart); // Should be filled heart
            holder.btnLike.setColorFilter(holder.itemView.getContext().getColor(R.color.primary));
        } else {
            holder.btnLike.setImageResource(R.drawable.ic_heart); // Should be outline heart
            holder.btnLike.setColorFilter(holder.itemView.getContext().getColor(R.color.app_text_primary));
        }

        // Click listeners
        holder.btnLike.setOnClickListener(v -> {
            if (listener != null) listener.onLikeClicked(post, position);
        });

        holder.btnComment.setOnClickListener(v -> {
            if (listener != null) listener.onCommentClicked(post);
        });

        holder.btnShare.setOnClickListener(v -> {
            if (listener != null) listener.onShareClicked(post);
        });
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    private String getTimeAgo(String isoDate) {
        if (isoDate == null) return "ACUM";
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
            java.util.Date date = sdf.parse(isoDate.split("\\.")[0]);
            long diff = System.currentTimeMillis() - date.getTime();
            long mins = diff / 60000;
            if (mins < 1) return "acum 1 minut";
            if (mins < 60) return "acum " + mins + " minute";
            long hours = mins / 60;
            if (hours < 24) return "acum " + hours + " ore";
            long days = hours / 24;
            if (days < 7) return days + " zile";
            return days / 7 + " săptămâni";
        } catch (Exception e) {
            return "ACUM";
        }
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView userAvatar, postImage;
        TextView userName, placeName, caption, captionUser, timeAgo, likesCount;
        ImageButton btnLike, btnComment, btnShare, btnBookmark, btnOptions;

        PostViewHolder(@NonNull View v) {
            super(v);
            userAvatar = v.findViewById(R.id.post_user_avatar);
            postImage = v.findViewById(R.id.post_image);
            userName = v.findViewById(R.id.post_user_name);
            placeName = v.findViewById(R.id.post_place_name);
            caption = v.findViewById(R.id.post_caption);
            captionUser = v.findViewById(R.id.post_caption_user);
            timeAgo = v.findViewById(R.id.post_time_ago);
            likesCount = v.findViewById(R.id.post_likes_count);
            btnLike = v.findViewById(R.id.btn_like);
            btnComment = v.findViewById(R.id.btn_comment);
            btnShare = v.findViewById(R.id.btn_share_post);
            btnBookmark = v.findViewById(R.id.btn_bookmark);
            btnOptions = v.findViewById(R.id.btn_post_options);
        }
    }
}
