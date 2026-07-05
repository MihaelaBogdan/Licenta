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
        
        boolean isEn = "en".equals(com.cityscape.app.data.LocaleHelper.getLanguage(holder.itemView.getContext()));
        String displaySlot = item.slot;
        if (isEn && item.slot != null) {
            String lower = item.slot.toLowerCase();
            if (lower.contains("mic dejun")) displaySlot = "Breakfast";
            else if (lower.contains("cafea & plimbare") || lower.contains("cafea si plimbare")) displaySlot = "Coffee & Walk";
            else if (lower.contains("activitate dimineață") || lower.contains("activitate dimineata")) displaySlot = "Morning Activity";
            else if (lower.contains("prânz") || lower.contains("pranz")) displaySlot = "Lunch";
            else if (lower.contains("activitate după-amiază") || lower.contains("activitate dupa-amiaza")) displaySlot = "Afternoon Activity";
            else if (lower.contains("pauză / ceai") || lower.contains("pauza / ceai") || lower.contains("pauză ceai")) displaySlot = "Tea Time / Break";
            else if (lower.contains("activitate seară") || lower.contains("activitate seara")) displaySlot = "Evening Activity";
            else if (lower.contains("cină") || lower.contains("cina")) displaySlot = "Dinner";
            else if (lower.contains("ieșire de seară") || lower.contains("iesire de seara") || lower.contains("viata de noapte")) displaySlot = "Nightlife";
        }
        holder.textSlot.setText(displaySlot);
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

        // Travel time separator
        if (holder.travelSeparator != null) {
            if (position > 0 && item.travelMinutes > 0) {
                holder.travelSeparator.setVisibility(View.VISIBLE);
                if (holder.textTravelTime != null) {
                    String label = (item.travelLabel != null && !item.travelLabel.isEmpty())
                        ? item.travelLabel + (isEn ? " to here" : " până aici")
                        : "🚶 ~" + item.travelMinutes + (isEn ? " min to here" : " min până aici");
                    holder.textTravelTime.setText(label);
                }
            } else {
                holder.travelSeparator.setVisibility(View.GONE);
            }
        }

        // Show warning if place is closed
        if (holder.badgeWarning != null) {
            holder.badgeWarning.setVisibility(item.is_open ? View.GONE : View.VISIBLE);
        }

        // Show tip / recommendation
        if (holder.textTip != null) {
            if (item.tip != null && !item.tip.isEmpty()) {
                holder.textTip.setText("💡 " + item.tip);
                holder.textTip.setVisibility(View.VISIBLE);
            } else {
                holder.textTip.setVisibility(View.GONE);
            }
        }

        // Mock distance for demo (improved calculation)
        double dist = (position == 0) ? 0.5 : 1.2 + (position * 0.5);
        holder.textDistance.setText(String.format(java.util.Locale.getDefault(), isEn ? "%.1f km distance" : "%.1f km distanță", dist));

        Glide.with(holder.itemView.getContext())
                .load(item.imageUrl)
                .placeholder(R.drawable.placeholder_place)
                .into(holder.imgStep);

        holder.itemView.setOnClickListener(v -> listener.onStepClick(item));
        holder.btnSwap.setOnClickListener(v -> listener.onStepSwap(position));
        holder.btnDelete.setOnClickListener(v -> listener.onStepDelete(position));

        // Google Maps navigation button
        if (holder.btnNavigate != null) {
            if (item.mapsUrl != null && !item.mapsUrl.isEmpty()) {
                holder.btnNavigate.setVisibility(View.VISIBLE);
                String label = position == 0 
                    ? (isEn ? "View on map →" : "Vezi pe hartă →") 
                    : (isEn ? "Navigate from previous point →" : "Navighează din punctul anterior →");
                holder.btnNavigate.setText(label);
                holder.btnNavigate.setOnClickListener(v -> {
                    android.net.Uri uri = android.net.Uri.parse(item.mapsUrl);
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, uri);
                    intent.setPackage("com.google.android.apps.maps");
                    if (intent.resolveActivity(v.getContext().getPackageManager()) != null) {
                        v.getContext().startActivity(intent);
                    } else {
                        v.getContext().startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, uri));
                    }
                });
            } else {
                holder.btnNavigate.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textSlot, textName, textAddress, textDistance, textCost, textTime, badgeWarning, textTip, textTravelTime;
        View travelSeparator;
        ImageView imgStep;
        android.widget.Button btnNavigate;

        ViewHolder(View view) {
            super(view);
            travelSeparator = view.findViewById(R.id.travel_separator);
            textTravelTime  = view.findViewById(R.id.text_travel_time);
            textSlot = view.findViewById(R.id.text_slot_label);
            textName = view.findViewById(R.id.text_step_name);
            textAddress = view.findViewById(R.id.text_step_address);
            textDistance = view.findViewById(R.id.text_distance);
            textCost = view.findViewById(R.id.text_step_cost);
            textTime = view.findViewById(R.id.text_step_time);
            badgeWarning = view.findViewById(R.id.badge_closed_warning);
            textTip = view.findViewById(R.id.text_step_tip);
            imgStep = view.findViewById(R.id.img_step);
            btnSwap = view.findViewById(R.id.btn_step_swap);
            btnDelete = view.findViewById(R.id.btn_step_delete);
            btnNavigate = view.findViewById(R.id.btn_navigate);
        }

        android.widget.ImageButton btnSwap, btnDelete;
    }
}
