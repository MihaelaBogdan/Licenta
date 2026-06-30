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

        String dateDisplay = (event.date != null && !event.date.isEmpty()) ? event.date
                : (event.date_str != null ? event.date_str : (event.time != null ? event.time : ""));
        holder.time.setText(dateDisplay);
        holder.location.setText(event.location != null ? event.location : "");

        if (event.imageUrl != null && !event.imageUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                .load(event.imageUrl)
                .placeholder(R.drawable.placeholder_place)
                .into(holder.image);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEventClick(event);
        });

        // AI confidence badge
        int confidence = event.confidence > 0 ? event.confidence
                : (event.relevance_score > 0 ? event.relevance_score : 0);

        // Fallback: check if title matches interests manually
        if (confidence == 0 && !userInterests.isEmpty()) {
            for (String interest : userInterests.split(",")) {
                String kw = interest.trim();
                if (!kw.isEmpty() && event.title != null && event.title.toLowerCase().contains(kw)) {
                    confidence = 60;
                    break;
                }
            }
        }

        if (confidence > 0) {
            holder.badgeContainer.setVisibility(View.VISIBLE);
            holder.badgeRecommended.setText("🎯 " + confidence + "%");

            if (event.aiReason != null && !event.aiReason.isEmpty()) {
                holder.textAiReasonCard.setVisibility(View.VISIBLE);
                holder.textAiReasonCard.setText(event.aiReason);
            } else {
                holder.textAiReasonCard.setVisibility(View.GONE);
            }
        } else {
            holder.badgeContainer.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView title, time, location, badgeRecommended, textAiReasonCard;
        ImageView image;
        View badgeContainer;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_event_title);
            time = itemView.findViewById(R.id.text_event_time);
            location = itemView.findViewById(R.id.text_event_location);
            image = itemView.findViewById(R.id.img_event);
            badgeContainer = itemView.findViewById(R.id.badge_ai_container);
            badgeRecommended = itemView.findViewById(R.id.badge_recommended);
            textAiReasonCard = itemView.findViewById(R.id.text_ai_reason_card);
        }
    }
}
