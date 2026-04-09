package com.example.licenta.ui.itinerary;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.licenta.R;
import com.example.licenta.api.ItineraryItem;
import java.util.List;

public class ItineraryStepAdapter extends RecyclerView.Adapter<ItineraryStepAdapter.ViewHolder> {

    private List<ItineraryItem> items;
    private OnStepClickListener listener;
    private boolean isEur = false;
    private final double EUR_RATE = 4.97;

    public interface OnStepClickListener {
        void onStepClick(ItineraryItem item);
    }

    public ItineraryStepAdapter(List<ItineraryItem> items, OnStepClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setCurrency(boolean isEur) {
        this.isEur = isEur;
        notifyDataSetChanged();
    }

    public void updateItems(List<ItineraryItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_itinerary_step, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ItineraryItem item = items.get(position);
        holder.textSlot.setText(item.slot);
        holder.textName.setText(item.name);
        holder.textAddress.setText(item.address);

        if (isEur) {
            double costEur = item.estimatedCost / EUR_RATE;
            holder.textCost.setText(String.format(java.util.Locale.getDefault(), "~ %.2f EUR", costEur));
        } else {
            holder.textCost.setText("~ " + (int) item.estimatedCost + " RON");
        }

        if (item.duration != null && !item.duration.isEmpty()) {
            holder.textDurationItem.setText(item.duration);
        } else {
            // Mock duration based on slot
            String mockDur = item.slot.contains("Prânz") || item.slot.contains("Cină") ? "1h 30m" : "1h";
            holder.textDurationItem.setText(mockDur);
        }

        // Mock distance for demo (improved calculation)
        double dist = (position == 0) ? 0.5 : 1.2 + (position * 0.5);
        holder.textDistance.setText(String.format(java.util.Locale.getDefault(), "%.1f km distanță", dist));

        Glide.with(holder.itemView.getContext())
                .load(item.imageUrl)
                .placeholder(R.drawable.placeholder_place)
                .into(holder.imgStep);

        holder.itemView.setOnClickListener(v -> listener.onStepClick(item));
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textSlot, textName, textAddress, textDistance, textCost, textDurationItem;
        ImageView imgStep;

        ViewHolder(View view) {
            super(view);
            textSlot = view.findViewById(R.id.text_slot_label);
            textName = view.findViewById(R.id.text_step_name);
            textAddress = view.findViewById(R.id.text_step_address);
            textDistance = view.findViewById(R.id.text_distance);
            textCost = view.findViewById(R.id.text_step_cost);
            textDurationItem = view.findViewById(R.id.text_step_duration);
            imgStep = view.findViewById(R.id.img_step);
        }
    }
}
