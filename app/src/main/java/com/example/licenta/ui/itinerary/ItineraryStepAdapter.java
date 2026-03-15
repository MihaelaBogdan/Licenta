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

    public interface OnStepClickListener {
        void onStepClick(ItineraryItem item);
    }

    public ItineraryStepAdapter(List<ItineraryItem> items, OnStepClickListener listener) {
        this.items = items;
        this.listener = listener;
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
        holder.textCost.setText("~ " + (int) item.estimatedCost + " RON");

        // Mock distance for demo
        holder.textDistance.setText((position + 1) * 0.8 + " km distanță");

        Glide.with(holder.itemView.getContext())
                .load(item.imageUrl)
                .placeholder(R.drawable.placeholder_place)
                .into(holder.imgStep);

        holder.itemView.setOnClickListener(v -> listener.onStepClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textSlot, textName, textAddress, textDistance, textCost;
        ImageView imgStep;

        ViewHolder(View view) {
            super(view);
            textSlot = view.findViewById(R.id.text_slot_label);
            textName = view.findViewById(R.id.text_step_name);
            textAddress = view.findViewById(R.id.text_step_address);
            textDistance = view.findViewById(R.id.text_distance);
            textCost = view.findViewById(R.id.text_step_cost);
            imgStep = view.findViewById(R.id.img_step);
        }
    }
}
