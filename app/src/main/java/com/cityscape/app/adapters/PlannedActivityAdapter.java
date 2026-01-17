package com.cityscape.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cityscape.app.R;
import com.cityscape.app.database.entities.PlannedActivity;

import java.util.List;

/**
 * Adapter for displaying planned activities in calendar
 */
public class PlannedActivityAdapter extends RecyclerView.Adapter<PlannedActivityAdapter.ActivityViewHolder> {

    private List<PlannedActivity> activities;
    private OnActivityClickListener listener;

    public interface OnActivityClickListener {
        void onActivityClick(PlannedActivity activity);

        void onDeleteClick(PlannedActivity activity);
    }

    public PlannedActivityAdapter(List<PlannedActivity> activities, OnActivityClickListener listener) {
        this.activities = activities;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ActivityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_planned_activity, parent, false);
        return new ActivityViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ActivityViewHolder holder, int position) {
        holder.bind(activities.get(position));
    }

    @Override
    public int getItemCount() {
        return activities.size();
    }

    class ActivityViewHolder extends RecyclerView.ViewHolder {
        private TextView timeText;
        private TextView placeNameText;
        private TextView notesText;
        private ImageButton deleteButton;

        ActivityViewHolder(@NonNull View itemView) {
            super(itemView);
            timeText = itemView.findViewById(R.id.activityTime);
            placeNameText = itemView.findViewById(R.id.activityPlaceName);
            notesText = itemView.findViewById(R.id.activityNotes);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }

        void bind(PlannedActivity activity) {
            timeText.setText(activity.getTime() != null ? activity.getTime() : "");
            placeNameText.setText(activity.getPlaceName() != null ? activity.getPlaceName() : "Activity");

            if (activity.getNotes() != null && !activity.getNotes().isEmpty()) {
                notesText.setVisibility(View.VISIBLE);
                notesText.setText(activity.getNotes());
            } else {
                notesText.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onActivityClick(activity);
                }
            });

            deleteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(activity);
                }
            });
        }
    }
}
