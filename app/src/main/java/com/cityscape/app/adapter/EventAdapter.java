package com.cityscape.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.cityscape.app.R;
import com.cityscape.app.model.Event;
import com.bumptech.glide.Glide;
import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    private final List<Event> events;
    private final OnEventClickListener listener;
    private String userInterests = "";

    public interface OnEventClickListener {
        void onEventClick(Event event);
    }

    public EventAdapter(List<Event> events, OnEventClickListener listener) {
        this.events = events;
        this.listener = listener;
    }

    public void setUserInterests(String interests) {
        this.userInterests = interests != null ? interests.toLowerCase() : "";
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = events.get(position);
        holder.title.setText(event.title);
        holder.time.setText((event.time != null && !event.time.isEmpty()) ? event.time : event.date_str);
        holder.location.setText(event.location);

        if (event.imageUrl != null && !event.imageUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                .load(event.imageUrl)
                .placeholder(R.drawable.placeholder_place)
                .into(holder.image);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEventClick(event);
        });

        // Highlight if matches interests
        boolean isRecommended = event.relevance_score > 0;
        if (!isRecommended && !userInterests.isEmpty()) {
            String[] splitInterests = userInterests.split(",");
            for (String interest : splitInterests) {
                String trimInt = interest.trim();
                if (!trimInt.isEmpty() && event.title.toLowerCase().contains(trimInt)) {
                    isRecommended = true;
                    break;
                }
            }
        }
        holder.badgeRecommended.setVisibility(isRecommended ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView title, time, location, badgeRecommended;
        ImageView image;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_event_title);
            time = itemView.findViewById(R.id.text_event_time);
            location = itemView.findViewById(R.id.text_event_location);
            image = itemView.findViewById(R.id.img_event);
            badgeRecommended = itemView.findViewById(R.id.badge_recommended);
        }
    }
}
