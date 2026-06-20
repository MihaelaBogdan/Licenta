package com.cityscape.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.cityscape.app.R;
import com.cityscape.app.model.Event;
import java.util.ArrayList;
import java.util.List;

public class FeedEventAdapter extends RecyclerView.Adapter<FeedEventAdapter.ViewHolder> {

    private List<Event> events = new ArrayList<>();
    private final OnEventClickListener listener;

    public interface OnEventClickListener {
        void onEventClick(Event event);
    }

    public FeedEventAdapter(OnEventClickListener listener) {
        this.listener = listener;
    }

    public void setEvents(List<Event> events) {
        this.events = events != null ? events : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_feed_event, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Event event = events.get(position);
        holder.title.setText(event.title);
        
        String timeStr = (event.time != null && !event.time.isEmpty()) ? event.time : event.date_str;
        holder.time.setText(timeStr != null ? timeStr.toUpperCase() : "ÎN CURÂND");
        holder.location.setText(event.location);

        if (event.imageUrl != null && !event.imageUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                .load(event.imageUrl)
                .placeholder(R.drawable.placeholder_place)
                .centerCrop()
                .into(holder.image);
        } else {
            holder.image.setImageResource(R.drawable.placeholder_place);
        }

        if (event.relevance_score > 0) {
            holder.indicatorRecommended.setVisibility(View.VISIBLE);
        } else {
            holder.indicatorRecommended.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEventClick(event);
            }
        });
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, time, location;
        ImageView image;
        View indicatorRecommended;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_event_title);
            time = itemView.findViewById(R.id.text_event_time);
            location = itemView.findViewById(R.id.text_event_location);
            image = itemView.findViewById(R.id.img_event);
            indicatorRecommended = itemView.findViewById(R.id.indicator_recommended);
        }
    }
}
