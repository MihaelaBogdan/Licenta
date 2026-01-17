package com.cityscape.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cityscape.app.R;
import com.cityscape.app.database.entities.Place;

import java.util.List;

/**
 * Adapter for horizontal place cards
 */
public class PlaceCardAdapter extends RecyclerView.Adapter<PlaceCardAdapter.ViewHolder> {

    private List<Place> places;
    private final OnPlaceClickListener listener;
    private java.util.Map<String, Integer> compatibilityScores = new java.util.HashMap<>();

    public interface OnPlaceClickListener {
        void onPlaceClick(Place place);
    }

    public PlaceCardAdapter(List<Place> places, OnPlaceClickListener listener) {
        this.places = places;
        this.listener = listener;
    }

    public void updateData(List<Place> newPlaces) {
        this.places = newPlaces;
        notifyDataSetChanged();
    }

    public void setCompatibilityScores(java.util.Map<String, Integer> scores) {
        this.compatibilityScores = scores;
        notifyDataSetChanged();
    }

    public void setCompatibilityScore(String placeId, int score) {
        compatibilityScores.put(placeId, score);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_place_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Place place = places.get(position);
        holder.bind(place);
    }

    @Override
    public int getItemCount() {
        return places != null ? places.size() : 0;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView placeImage;
        private final TextView placeName;
        private final TextView placeCategory;
        private final TextView placeRating;
        private final ImageView favoriteIcon;
        private final TextView compatibilityBadge;

        ViewHolder(View itemView) {
            super(itemView);
            placeImage = itemView.findViewById(R.id.placeImage);
            placeName = itemView.findViewById(R.id.placeName);
            placeCategory = itemView.findViewById(R.id.placeCategory);
            placeRating = itemView.findViewById(R.id.placeRating);
            favoriteIcon = itemView.findViewById(R.id.favoriteIcon);
            compatibilityBadge = itemView.findViewById(R.id.compatibilityBadge);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPlaceClick(places.get(pos));
                }
            });
        }

        void bind(Place place) {
            placeName.setText(place.getName());
            placeCategory.setText(getCategoryLabel(place.getCategory()));
            placeRating.setText(String.format("%.1f ★", place.getRating()));

            // Show compatibility score if available
            Integer score = compatibilityScores.get(place.getId());
            if (score != null && compatibilityBadge != null) {
                compatibilityBadge.setText(score + "% match");
                compatibilityBadge.setVisibility(View.VISIBLE);

                // Color based on score
                int bgColor;
                if (score >= 75) {
                    bgColor = 0xCC4CAF50; // Green
                } else if (score >= 50) {
                    bgColor = 0xCCFFB74D; // Orange
                } else {
                    bgColor = 0xCCFF7043; // Red-orange
                }
                compatibilityBadge.getBackground().setTint(bgColor);
            } else if (compatibilityBadge != null) {
                compatibilityBadge.setVisibility(View.GONE);
            }

            // Load image
            if (place.getPhotoUrl() != null && place.getPhotoUrl().length() != 0) {
                Glide.with(itemView.getContext())
                        .load(place.getPhotoUrl())
                        .placeholder(R.drawable.placeholder_place)
                        .centerCrop()
                        .into(placeImage);
            } else {
                placeImage.setImageResource(R.drawable.placeholder_place);
            }
        }

        private String getCategoryLabel(String category) {
            if (category == null)
                return "Place";
            switch (category) {
                case Place.CATEGORY_RESTAURANT:
                    return "Restaurant";
                case Place.CATEGORY_CAFE:
                    return "Cafe";
                case Place.CATEGORY_BAR:
                    return "Bar";
                case Place.CATEGORY_CULTURE:
                    return "Culture";
                case Place.CATEGORY_NATURE:
                    return "Nature";
                case Place.CATEGORY_SHOPPING:
                    return "Shopping";
                default:
                    return "Place";
            }
        }
    }
}
