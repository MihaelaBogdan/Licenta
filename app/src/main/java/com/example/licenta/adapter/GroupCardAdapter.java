package com.example.licenta.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.licenta.R;
import com.example.licenta.data.AppDatabase;
import com.example.licenta.model.ActivityGroup;
import com.example.licenta.model.PlannedActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GroupCardAdapter extends RecyclerView.Adapter<GroupCardAdapter.GroupViewHolder> {

    private Context context;
    private List<ActivityGroup> groups;
    private OnGroupActionListener listener;
    private AppDatabase db;

    public interface OnGroupActionListener {
        void onViewSchedule(ActivityGroup group);

        void onShareWhatsApp(ActivityGroup group);

        void onGroupClick(ActivityGroup group);
    }

    public GroupCardAdapter(Context context, List<ActivityGroup> groups, OnGroupActionListener listener) {
        this.context = context;
        this.groups = groups;
        this.listener = listener;
        this.db = AppDatabase.getInstance(context);
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_group_card, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        ActivityGroup group = groups.get(position);
        holder.bind(group);
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    class GroupViewHolder extends RecyclerView.ViewHolder {
        TextView groupName, groupMemberCount, groupCode, groupActivityName, groupActivityTime;
        Button btnViewSchedule, btnShareWhatsApp;
        LinearLayout activityInfoContainer;

        GroupViewHolder(View itemView) {
            super(itemView);
            groupName = itemView.findViewById(R.id.group_name);
            groupMemberCount = itemView.findViewById(R.id.group_member_count);
            groupCode = itemView.findViewById(R.id.group_code);
            groupActivityName = itemView.findViewById(R.id.group_activity_name);
            groupActivityTime = itemView.findViewById(R.id.group_activity_time);
            btnViewSchedule = itemView.findViewById(R.id.btn_view_schedule);
            btnShareWhatsApp = itemView.findViewById(R.id.btn_share_whatsapp);
            activityInfoContainer = itemView.findViewById(R.id.activity_info_container);
        }

        void bind(ActivityGroup group) {
            groupName.setText(group.groupName);
            groupCode.setText(group.groupCode);

            int memberCount = db.groupDao().getAcceptedMemberCount(group.id);
            groupMemberCount.setText(memberCount + (memberCount == 1 ? " membru" : " membri"));

            // Load associated activity
            PlannedActivity activity = null;
            if (group.activityId != null && !group.activityId.isEmpty()) {
                List<PlannedActivity> allActivities = db.activityDao().getActivitiesForUser(group.creatorId);
                for (PlannedActivity a : allActivities) {
                    if (a.id != null && a.id.equals(group.activityId)) {
                        activity = a;
                        break;
                    }
                }
            }

            if (activity != null) {
                activityInfoContainer.setVisibility(View.VISIBLE);
                groupActivityName.setText(activity.placeName);
                groupActivityTime.setText(activity.scheduledTime);
            } else {
                activityInfoContainer.setVisibility(View.GONE);
            }

            // Click listeners
            btnViewSchedule.setOnClickListener(v -> {
                if (listener != null)
                    listener.onViewSchedule(group);
            });

            btnShareWhatsApp.setOnClickListener(v -> {
                if (listener != null)
                    listener.onShareWhatsApp(group);
            });

            itemView.setOnClickListener(v -> {
                if (listener != null)
                    listener.onGroupClick(group);
            });
        }
    }
}
