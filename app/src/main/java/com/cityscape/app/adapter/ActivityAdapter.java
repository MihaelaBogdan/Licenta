package com.cityscape.app.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.cityscape.app.R;
import com.cityscape.app.data.AppDatabase;
import com.cityscape.app.model.ActivityGroup;
import com.cityscape.app.model.PlannedActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.List;

public class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.ActivityViewHolder> {

    private Context context;
    private List<PlannedActivity> activities;
    private OnActivityActionListener listener;
    private AppDatabase db;

    public interface OnActivityActionListener {
        void onCompleteClick(PlannedActivity activity, int position);

        void onShareClick(PlannedActivity activity);

        void onCreateGroupClick(PlannedActivity activity);
        
        void onExportClick(PlannedActivity activity);
    }

    public ActivityAdapter(Context context, List<PlannedActivity> activities, OnActivityActionListener listener) {
        this.context = context;
        this.activities = activities;
        this.listener = listener;
        this.db = AppDatabase.getInstance(context);
    }

    @NonNull
    @Override
    public ActivityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_planned_activity, parent, false);
        return new ActivityViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ActivityViewHolder holder, int position) {
        PlannedActivity activity = activities.get(position);
        holder.bind(activity, position);
    }

    @Override
    public int getItemCount() {
        return activities.size();
    }

    class ActivityViewHolder extends RecyclerView.ViewHolder {
        TextView time, placeName, placeType, notes, groupMemberCount, budgetDisplay;
        ImageView btnComplete, btnShare, btnCreateGroup, btnExport;
        com.google.android.material.button.MaterialButton btnInviteFriendsText;
        LinearLayout groupInfoContainer, budgetInfoContainer;

        ActivityViewHolder(View itemView) {
            super(itemView);
            time = itemView.findViewById(R.id.activity_time);
            placeName = itemView.findViewById(R.id.activity_place_name);
            placeType = itemView.findViewById(R.id.activity_place_type);
            notes = itemView.findViewById(R.id.activity_notes);
            btnComplete = itemView.findViewById(R.id.btn_complete);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnCreateGroup = itemView.findViewById(R.id.btn_create_group);
            btnExport = itemView.findViewById(R.id.btn_export);
            btnInviteFriendsText = itemView.findViewById(R.id.btn_invite_friends_text);
            groupInfoContainer = itemView.findViewById(R.id.group_info_container);
            groupMemberCount = itemView.findViewById(R.id.group_member_count);
            budgetInfoContainer = itemView.findViewById(R.id.budget_info_container);
            budgetDisplay = itemView.findViewById(R.id.text_budget_display);
        }

        void bind(PlannedActivity activity, int position) {
            time.setText(activity.scheduledTime);
            placeName.setText(activity.placeName);
            placeType.setText(activity.placeType);

            if (activity.notes != null && !activity.notes.isEmpty()) {
                notes.setText(activity.notes);
                notes.setVisibility(View.VISIBLE);
            } else {
                notes.setVisibility(View.GONE);
            }

            // Budget Info
            if (activity.budget > 0) {
                budgetInfoContainer.setVisibility(View.VISIBLE);
                String curr = activity.currency != null ? activity.currency : "RON";
                budgetDisplay.setText(String.format(Locale.getDefault(), "%.2f %s", activity.budget, curr));
            } else {
                budgetInfoContainer.setVisibility(View.GONE);
            }

            // Check if activity has a group
            ActivityGroup group = db.groupDao().getGroupForActivity(activity.id);
            if (group != null) {
                groupInfoContainer.setVisibility(View.VISIBLE);
                int memberCount = db.groupDao().getAcceptedMemberCount(group.id);
                groupMemberCount.setText(memberCount + " friend" + (memberCount != 1 ? "s" : ""));
                btnCreateGroup.setImageResource(R.drawable.ic_group);
            } else {
                groupInfoContainer.setVisibility(View.GONE);
                btnCreateGroup.setImageResource(R.drawable.ic_group_add);
            }

            // Update completion state
            if (activity.isCompleted) {
                btnComplete.setBackgroundResource(R.drawable.circle_primary);
                itemView.setAlpha(0.6f);
            } else {
                btnComplete.setBackgroundResource(R.drawable.circle_card);
                itemView.setAlpha(1.0f);
            }

            // Click listeners
            btnComplete.setOnClickListener(v -> {
                if (listener != null && !activity.isCompleted) {
                    listener.onCompleteClick(activity, position);
                }
            });

            btnShare.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onShareClick(activity);
                }
            });

            btnCreateGroup.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCreateGroupClick(activity);
                }
            });

            btnExport.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onExportClick(activity);
                }
            });

            btnInviteFriendsText.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCreateGroupClick(activity);
                }
            });
        }
    }
}
