package com.cityscape.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cityscape.app.R;
import com.cityscape.app.database.entities.Badge;

import java.util.List;

/**
 * Adapter for displaying badges in a grid
 */
public class BadgeAdapter extends RecyclerView.Adapter<BadgeAdapter.ViewHolder> {

    private List<Badge> badges;

    public BadgeAdapter(List<Badge> badges) {
        this.badges = badges;
    }

    public void updateData(List<Badge> newBadges) {
        this.badges = newBadges;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_badge, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Badge badge = badges.get(position);
        holder.bind(badge);
    }

    @Override
    public int getItemCount() {
        return badges != null ? badges.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView badgeIcon;
        private final TextView badgeName;
        private final View badgeContainer;

        ViewHolder(View itemView) {
            super(itemView);
            badgeIcon = itemView.findViewById(R.id.badgeIcon);
            badgeName = itemView.findViewById(R.id.badgeName);
            badgeContainer = itemView.findViewById(R.id.badgeContainer);
        }

        void bind(Badge badge) {
            badgeName.setText(badge.getName());

            // Set badge icon based on tier
            int color = getTierColor(badge.getTier());
            badgeContainer.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(color));

            // Default icon - in real app, load from iconName
            badgeIcon.setImageResource(R.drawable.ic_badge);
        }

        private int getTierColor(String tier) {
            switch (tier) {
                case Badge.TIER_GOLD:
                    return itemView.getContext().getColor(R.color.gold_accent);
                case Badge.TIER_SILVER:
                    return itemView.getContext().getColor(R.color.text_secondary);
                case Badge.TIER_PLATINUM:
                    return itemView.getContext().getColor(R.color.accent_green);
                default:
                    return itemView.getContext().getColor(R.color.category_coffee);
            }
        }
    }
}
