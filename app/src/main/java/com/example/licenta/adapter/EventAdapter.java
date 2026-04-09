package com.example.licenta.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.licenta.R;
import com.example.licenta.model.Event;
import com.bumptech.glide.Glide;
import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    private final List<Event> events;
    private final OnEventClickListener listener;

    public interface OnEventClickListener {
        void onInviteClick(Event event);
    }

    public EventAdapter(List<Event> events, OnEventClickListener listener) {
        this.events = events;
        this.listener = listener;
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
        holder.time.setText(event.time);
        holder.location.setText(event.location);

        if (event.imageUrl != null && !event.imageUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                .load(event.imageUrl)
                .placeholder(R.drawable.placeholder_place)
                .into(holder.image);
        }

        holder.btnInvite.setOnClickListener(v -> {
            if (listener != null) listener.onInviteClick(event);
        });
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView title, time, location;
        ImageView image;
        View btnInvite;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_event_title);
            time = itemView.findViewById(R.id.text_event_time);
            location = itemView.findViewById(R.id.text_event_location);
            image = itemView.findViewById(R.id.img_event);
            btnInvite = itemView.findViewById(R.id.btn_invite_friends);
        }
    }
}
