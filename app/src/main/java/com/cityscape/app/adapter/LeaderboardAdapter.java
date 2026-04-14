package com.cityscape.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.cityscape.app.R;
import com.cityscape.app.model.User;
import java.util.List;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

    private final List<User> users;

    public LeaderboardAdapter(List<User> users) {
        this.users = users;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_leaderboard, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = users.get(position);
        holder.rank.setText(String.valueOf(position + 1));
        holder.name.setText(user.name);
        holder.xp.setText(String.format("%,d XP", user.totalXp));
        holder.stats.setText(user.placesVisited + " locuri explorate");

        // First place gets special color
        if (position == 0) {
            holder.rank.setTextColor(android.graphics.Color.parseColor("#FFD700")); // Gold
        } else if (position == 1) {
            holder.rank.setTextColor(android.graphics.Color.parseColor("#C0C0C0")); // Silver
        } else if (position == 2) {
            holder.rank.setTextColor(android.graphics.Color.parseColor("#CD7F32")); // Bronze
        } else {
            holder.rank.setTextColor(android.graphics.Color.parseColor("#4CAF50")); // Default Green
        }
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView rank, name, stats, xp;
        ImageView avatar;

        ViewHolder(View itemView) {
            super(itemView);
            rank = itemView.findViewById(R.id.text_rank);
            name = itemView.findViewById(R.id.text_name);
            stats = itemView.findViewById(R.id.text_stats);
            xp = itemView.findViewById(R.id.text_xp);
            avatar = itemView.findViewById(R.id.img_avatar);
        }
    }
}
