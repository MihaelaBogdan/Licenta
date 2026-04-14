package com.cityscape.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cityscape.app.R;
import com.cityscape.app.model.Place;
import java.util.List;

public class PlaceAdapter extends RecyclerView.Adapter<PlaceAdapter.PlaceViewHolder> {

    private final Context context;
    private final List<Place> placeList;
    private final OnPlaceClickListener listener;
    private final boolean isHorizontal;
    private boolean isManualMode = false;

    public interface OnPlaceClickListener {
        void onPlaceClick(Place place);
        void onFavoriteClick(Place place);
        void onVisitedClick(Place place);
        void onPlanClick(Place place);
    }

    public PlaceAdapter(Context context, List<Place> placeList, boolean isHorizontal, OnPlaceClickListener listener) {
        this.context = context;
        this.placeList = placeList;
        this.isHorizontal = isHorizontal;
        this.listener = listener;
    }

    public void setManualMode(boolean manualMode) {
        this.isManualMode = manualMode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutRes = isHorizontal ? R.layout.item_place_card : R.layout.item_place_list;
        View view = LayoutInflater.from(context).inflate(layoutRes, parent, false);
        return new PlaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaceViewHolder holder, int position) {
        Place place = placeList.get(position);
        holder.bind(place);
    }

    @Override
    public int getItemCount() {
        return placeList.size();
    }

    class PlaceViewHolder extends RecyclerView.ViewHolder {
        ImageView placeImage, btnFavorite;
        TextView placeName, placeType, placeRating, placeAddress;

        PlaceViewHolder(@NonNull View itemView) {
            super(itemView);
            placeImage = itemView.findViewById(isHorizontal ? R.id.place_image : R.id.placeImage);
            btnFavorite = itemView.findViewById(isHorizontal ? R.id.btn_favorite : R.id.btn_favorite); // If ID is the
                                                                                                       // same
            if (btnFavorite == null)
                btnFavorite = itemView.findViewById(R.id.btn_favorite);

            placeName = itemView.findViewById(isHorizontal ? R.id.place_name : R.id.placeName);
            placeType = itemView.findViewById(isHorizontal ? R.id.place_type : R.id.placeCategory);
            placeRating = itemView.findViewById(isHorizontal ? R.id.place_rating : R.id.placeRating);
            placeAddress = itemView.findViewById(isHorizontal ? R.id.place_address : R.id.placeAddress);

            View btnVisited = itemView.findViewById(isHorizontal ? R.id.btn_visited : R.id.btnVisited);
            if (btnVisited != null) {
                btnVisited.setOnClickListener(v -> {
                    if (listener != null) {
                        int pos = getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            listener.onVisitedClick(placeList.get(pos));
                        }
                    }
                });
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onPlaceClick(placeList.get(position));
                    }
                }
            });

            if (btnFavorite != null) {
                btnFavorite.setOnClickListener(v -> {
                    if (listener != null) {
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            Place place = placeList.get(position);
                            place.isFavorite = !place.isFavorite;
                            notifyItemChanged(position);
                            listener.onFavoriteClick(place);
                        }
                    }
                });
            }

            View btnPlan = itemView.findViewById(R.id.btn_plan);
            if (btnPlan != null) {
                btnPlan.setOnClickListener(v -> {
                    if (listener != null) {
                        int pos = getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            listener.onPlanClick(placeList.get(pos));
                        }
                    }
                });
            }
        }

        void bind(Place place) {
            if (placeName != null)
                placeName.setText(place.name != null ? place.name : "");
            if (placeType != null)
                placeType.setText(place.type != null ? place.type : "");
            if (placeRating != null)
                placeRating.setText(String.format("%.1f", place.rating));
            if (placeAddress != null) {
                placeAddress.setText(place.address != null ? place.address : "");
            }

            Glide.with(context)
                    .load(place.imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.placeholder_place)
                    .into(placeImage);

            // Toggle button visibilities based on mode
            View btnPlan = ((View)placeImage.getParent()).findViewById(R.id.btn_plan);
            if (isManualMode) {
                if (btnFavorite != null) btnFavorite.setVisibility(View.GONE);
                if (itemView.findViewById(isHorizontal ? R.id.btn_visited : R.id.btnVisited) != null) 
                    itemView.findViewById(isHorizontal ? R.id.btn_visited : R.id.btnVisited).setVisibility(View.GONE);
                if (btnPlan != null) btnPlan.setVisibility(View.VISIBLE);
            } else {
                if (btnFavorite != null) btnFavorite.setVisibility(View.VISIBLE);
                if (itemView.findViewById(isHorizontal ? R.id.btn_visited : R.id.btnVisited) != null) 
                    itemView.findViewById(isHorizontal ? R.id.btn_visited : R.id.btnVisited).setVisibility(View.VISIBLE);
                if (btnPlan != null) btnPlan.setVisibility(View.GONE);
            }

            // Toggle favorite icon
            if (btnFavorite != null && !isManualMode) {
                if (place.isFavorite) {
                    btnFavorite.setImageResource(R.drawable.ic_favorite_filled);
                    btnFavorite.setColorFilter(context.getColor(R.color.accent_green));
                } else {
                    btnFavorite.setImageResource(R.drawable.ic_favorite_border);
                    btnFavorite.setColorFilter(context.getColor(R.color.white));
                }
            }
        }
    }
}
