package com.cityscape.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.cityscape.app.R;
import com.cityscape.app.model.Badge;
import java.util.List;

public class BadgeAdapter extends RecyclerView.Adapter<BadgeAdapter.BadgeViewHolder> {

    private List<Badge> badges;

    public BadgeAdapter(List<Badge> badges) {
        this.badges = badges;
    }

    @NonNull
    @Override
    public BadgeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_badge, parent, false);
        return new BadgeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BadgeViewHolder holder, int position) {
        Badge badge = badges.get(position);
        holder.badgeName.setText(badge.name);
        holder.badgeIcon.setImageResource(badge.iconResId);
        holder.badgeIcon.setAlpha(badge.isUnlocked ? 1.0f : 0.3f);

        holder.itemView.setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(v.getContext(), R.style.DarkDialogTheme)
                .setTitle(badge.name)
                .setMessage(badge.description + "\n\n" + 
                           (badge.isUnlocked ? "✅ DEBLOCATĂ" : "🔒 ÎNCĂ ÎNCUIATĂ") + "\n\n" + 
                           "CERINȚĂ: " + badge.requirement)
                .setPositiveButton("OK", null)
                .show();
        });
    }

    @Override
    public int getItemCount() {
        return badges.size();
    }

    static class BadgeViewHolder extends RecyclerView.ViewHolder {
        ImageView badgeIcon;
        TextView badgeName;

        BadgeViewHolder(View itemView) {
            super(itemView);
            badgeIcon = itemView.findViewById(R.id.badge_icon);
            badgeName = itemView.findViewById(R.id.badge_name);
        }
    }
}
