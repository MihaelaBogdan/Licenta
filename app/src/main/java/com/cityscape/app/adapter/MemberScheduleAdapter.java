package com.cityscape.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.cityscape.app.R;
import com.cityscape.app.model.MemberSchedule;
import java.util.List;

public class MemberScheduleAdapter extends RecyclerView.Adapter<MemberScheduleAdapter.ScheduleViewHolder> {

    private Context context;
    private List<MemberSchedule> schedules;

    public MemberScheduleAdapter(Context context, List<MemberSchedule> schedules) {
        this.context = context;
        this.schedules = schedules;
    }

    public void updateSchedules(List<MemberSchedule> newSchedules) {
        this.schedules = newSchedules;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ScheduleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_member_schedule, parent, false);
        return new ScheduleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScheduleViewHolder holder, int position) {
        MemberSchedule schedule = schedules.get(position);
        holder.bind(schedule);
    }

    @Override
    public int getItemCount() {
        return schedules.size();
    }

    class ScheduleViewHolder extends RecyclerView.ViewHolder {
        View statusIndicator;
        TextView memberName, memberNote, memberTimeRange;

        ScheduleViewHolder(View itemView) {
            super(itemView);
            statusIndicator = itemView.findViewById(R.id.status_indicator);
            memberName = itemView.findViewById(R.id.member_name);
            memberNote = itemView.findViewById(R.id.member_note);
            memberTimeRange = itemView.findViewById(R.id.member_time_range);
        }

        void bind(MemberSchedule schedule) {
            memberName.setText(schedule.userName);
            memberTimeRange.setText(schedule.getTimeRange());

            if (schedule.isAvailable) {
                statusIndicator.setBackgroundResource(R.drawable.circle_green);
            } else {
                statusIndicator.setBackgroundResource(R.drawable.circle_red);
            }

            if (schedule.note != null && !schedule.note.isEmpty()) {
                memberNote.setText(schedule.note);
                memberNote.setVisibility(View.VISIBLE);
            } else {
                memberNote.setVisibility(View.GONE);
            }
        }
    }
}
