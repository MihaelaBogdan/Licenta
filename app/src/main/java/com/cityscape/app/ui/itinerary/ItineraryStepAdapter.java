package com.cityscape.app.ui.itinerary;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.cityscape.app.R;
import com.cityscape.app.api.ItineraryItem;
import java.util.List;

public class ItineraryStepAdapter extends RecyclerView.Adapter<ItineraryStepAdapter.ViewHolder> {

    private List<ItineraryItem> items;
    private OnStepClickListener listener;
    private boolean isEur = false;
    private final double EUR_RATE = 4.97;

    public interface OnStepClickListener {
        void onStepClick(ItineraryItem item);
        void onStepDelete(int position);
        void onStepSwap(int position);
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

        if (item.time != null && !item.time.isEmpty()) {
            holder.textTime.setText(item.time);
        } else {
            // Fallback mock time
            holder.textTime.setText("09:00 - 10:00");
        }

        // Show warning if place is closed
        if (holder.badgeWarning != null) {
            holder.badgeWarning.setVisibility(item.is_open ? View.GONE : View.VISIBLE);
        }

        // Mock distance for demo (improved calculation)
        double dist = (position == 0) ? 0.5 : 1.2 + (position * 0.5);
        holder.textDistance.setText(String.format(java.util.Locale.getDefault(), "%.1f km distanță", dist));

        Glide.with(holder.itemView.getContext())
                .load(item.imageUrl)
                .placeholder(R.drawable.placeholder_place)
                .into(holder.imgStep);

        holder.itemView.setOnClickListener(v -> listener.onStepClick(item));
        
        holder.btnSwap.setOnClickListener(v -> listener.onStepSwap(position));
        holder.btnDelete.setOnClickListener(v -> listener.onStepDelete(position));
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textSlot, textName, textAddress, textDistance, textCost, textTime, badgeWarning;
        ImageView imgStep;

        ViewHolder(View view) {
            super(view);
            textSlot = view.findViewById(R.id.text_slot_label);
            textName = view.findViewById(R.id.text_step_name);
            textAddress = view.findViewById(R.id.text_step_address);
            textDistance = view.findViewById(R.id.text_distance);
            textCost = view.findViewById(R.id.text_step_cost);
            textTime = view.findViewById(R.id.text_step_time);
            badgeWarning = view.findViewById(R.id.badge_closed_warning);
            imgStep = view.findViewById(R.id.img_step);
            btnSwap = view.findViewById(R.id.btn_step_swap);
            btnDelete = view.findViewById(R.id.btn_step_delete);
        }

        android.widget.ImageButton btnSwap, btnDelete;
    }
}
