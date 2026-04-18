package com.cityscape.app.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.cityscape.app.R;
import com.cityscape.app.model.CalendarDate;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class CalendarDateAdapter extends RecyclerView.Adapter<CalendarDateAdapter.DateViewHolder> {

    private final List<CalendarDate> dates;
    private final OnDateClickListener listener;
    private final SimpleDateFormat dayNameFmt = new SimpleDateFormat("EEE", new Locale("ro"));
    private final SimpleDateFormat dayNumFmt = new SimpleDateFormat("d", Locale.getDefault());

    public interface OnDateClickListener {
        void onDateClick(CalendarDate date);
    }

    public CalendarDateAdapter(List<CalendarDate> dates, OnDateClickListener listener) {
        this.dates = dates;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_date, parent, false);
        return new DateViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DateViewHolder holder, int position) {
        CalendarDate date = dates.get(position);
        
        holder.dayName.setText(dayNameFmt.format(date.date).toUpperCase());
        holder.dayNumber.setText(dayNumFmt.format(date.date));
        
        holder.eventDot.setVisibility(date.hasEvents ? View.VISIBLE : View.INVISIBLE);
        
        if (date.isSelected) {
            holder.card.setCardBackgroundColor(Color.parseColor("#10B981")); // Primary
            holder.dayName.setTextColor(Color.WHITE);
            holder.dayNumber.setTextColor(Color.WHITE);
            holder.eventDot.setBackgroundResource(R.drawable.circle_white);
        } else {
            holder.card.setCardBackgroundColor(Color.parseColor("#1A808080"));
            holder.dayName.setTextColor(Color.parseColor("#9CA3AF")); // Gray 400
            holder.dayNumber.setTextColor(Color.parseColor("#F3F4F6")); // Gray 100
            holder.eventDot.setBackgroundResource(R.drawable.circle_primary);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onDateClick(date);
        });
    }

    @Override
    public int getItemCount() {
        return dates.size();
    }

    static class DateViewHolder extends RecyclerView.ViewHolder {
        TextView dayName, dayNumber;
        View eventDot;
        CardView card;

        DateViewHolder(@NonNull View itemView) {
            super(itemView);
            dayName = itemView.findViewById(R.id.text_day_name);
            dayNumber = itemView.findViewById(R.id.text_day_number);
            eventDot = itemView.findViewById(R.id.dot_event);
            card = itemView.findViewById(R.id.card_date);
        }
    }
}
