package com.example.licenta.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.licenta.R;
import com.example.licenta.model.Place;
import java.util.List;

public class PlaceAdapter extends RecyclerView.Adapter<PlaceAdapter.PlaceViewHolder> {

    private final Context context;
    private final List<Place> placeList;
    private final OnPlaceClickListener listener;

    public interface OnPlaceClickListener {
        void onPlaceClick(Place place);
    }

    public PlaceAdapter(Context context, List<Place> placeList, OnPlaceClickListener listener) {
        this.context = context;
        this.placeList = placeList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PlaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_place_card, parent, false);
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
        TextView placeName, placeType, placeRating;

        PlaceViewHolder(@NonNull View itemView) {
            super(itemView);
            placeImage = itemView.findViewById(R.id.place_image);
            btnFavorite = itemView.findViewById(R.id.btn_favorite);
            placeName = itemView.findViewById(R.id.place_name);
            placeType = itemView.findViewById(R.id.place_type);
            placeRating = itemView.findViewById(R.id.place_rating);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onPlaceClick(placeList.get(position));
                    }
                }
            });
        }

        void bind(Place place) {
            placeName.setText(place.name);
            placeType.setText(place.type);
            placeRating.setText(String.valueOf(place.rating));

            Glide.with(context)
                    .load(place.imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.rounded_corners_8)
                    .into(placeImage);

            // Toggle favorite icon
            if (place.isFavorite) {
                btnFavorite.setImageResource(R.drawable.ic_favorite_filled);
            } else {
                btnFavorite.setImageResource(R.drawable.ic_favorite_border);
            }
        }
    }
}
