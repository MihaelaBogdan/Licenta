package com.cityscape.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.cityscape.app.CityScapeApp;
import com.cityscape.app.R;
import com.cityscape.app.ai.CompatibilityCalculator;
import com.cityscape.app.database.AppDatabase;
import com.cityscape.app.database.entities.Favorite;
import com.cityscape.app.database.entities.Place;
import com.cityscape.app.database.entities.PlacePhoto;
import com.cityscape.app.databinding.ActivityPlaceDetailBinding;

import java.util.List;

/**
 * Activity showing detailed place information
 */
public class PlaceDetailActivity extends AppCompatActivity {

    private ActivityPlaceDetailBinding binding;
    private AppDatabase database;
    private String placeId;
    private String userId;
    private boolean isFavorite = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlaceDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        database = CityScapeApp.getInstance().getDatabase();
        userId = CityScapeApp.getInstance().getCurrentUserId();
        placeId = getIntent().getStringExtra("placeId");

        if (placeId == null) {
            finish();
            return;
        }

        setupListeners();
        loadPlaceData();
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnFavorite.setOnClickListener(v -> toggleFavorite());

        binding.btnShare.setOnClickListener(v -> sharePlace());

        binding.btnGetDirections.setOnClickListener(v -> openDirections());

        binding.btnWriteReview.setOnClickListener(v -> {
            // Open review dialog
            Toast.makeText(this, "Review feature coming soon!", Toast.LENGTH_SHORT).show();
        });

        binding.btnCall.setOnClickListener(v -> {
            // Open dialer
            Toast.makeText(this, "Call feature coming soon!", Toast.LENGTH_SHORT).show();
        });

        binding.btnWebsite.setOnClickListener(v -> {
            // Open website
            Toast.makeText(this, "Website feature coming soon!", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadPlaceData() {
        database.placeDao().getPlaceById(placeId).observe(this, place -> {
            if (place != null) {
                displayPlace(place);
            }
        });

        // Check if favorite
        database.favoriteDao().isFavorite(userId, placeId).observe(this, favorite -> {
            isFavorite = favorite != null && favorite;
            updateFavoriteButton();
        });
    }

    private void displayPlace(Place place) {
        binding.placeName.setText(place.getName());
        binding.placeCategory.setText(getCategoryLabel(place.getCategory()));
        binding.placeRating.setText(String.format("%.1f", place.getRating()));
        binding.reviewCount.setText(String.format("(%d reviews)", place.getReviewCount()));
        binding.priceLevel.setText(place.getPriceLevelString());
        binding.placeAddress.setText(place.getAddress());

        // Description
        if (place.getDescription() != null && !place.getDescription().isEmpty()) {
            binding.placeDescription.setText(place.getDescription());
        } else {
            binding.descriptionSection.setVisibility(View.GONE);
        }

        // Load main image
        if (place.getPhotoUrl() != null && !place.getPhotoUrl().isEmpty()) {
            Glide.with(this)
                    .load(place.getPhotoUrl())
                    .placeholder(R.drawable.placeholder_place)
                    .centerCrop()
                    .into(binding.placeImage);
        }

        // Opening hours
        if (place.getOpeningHours() != null) {
            binding.openingHours.setText(place.getOpeningHours());
        } else {
            binding.hoursSection.setVisibility(View.GONE);
        }

        // Atmosphere tags
        if (place.getAtmosphereTags() != null && !place.getAtmosphereTags().isEmpty()) {
            binding.atmosphereTags.setText(place.getAtmosphereTags().replace(",", " • "));
        } else {
            binding.atmosphereSection.setVisibility(View.GONE);
        }

        // Calculate compatibility score
        AppDatabase.databaseWriteExecutor.execute(() -> {
            CompatibilityCalculator calculator = new CompatibilityCalculator(this);
            int score = calculator.calculate(userId, place);

            runOnUiThread(() -> {
                binding.compatibilityScore.setText(score + "%");
                binding.compatibilityLabel.setText(CompatibilityCalculator.getCompatibilityLabel(score));
                binding.compatibilityProgress.setProgress(score);
            });
        });
    }

    private void toggleFavorite() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (isFavorite) {
                database.favoriteDao().deleteByUserAndPlace(userId, placeId);
            } else {
                Favorite favorite = new Favorite(userId, placeId);
                database.favoriteDao().insert(favorite);
            }
        });
    }

    private void updateFavoriteButton() {
        if (isFavorite) {
            binding.btnFavorite.setImageResource(R.drawable.ic_favorite_filled);
            binding.btnFavorite.setColorFilter(getColor(R.color.error_red));
        } else {
            binding.btnFavorite.setImageResource(R.drawable.ic_favorite_outline);
            binding.btnFavorite.setColorFilter(getColor(R.color.text_primary));
        }
    }

    private void sharePlace() {
        // Share functionality
        Toast.makeText(this, "Share feature coming soon!", Toast.LENGTH_SHORT).show();
    }

    private void openDirections() {
        // Open Maps for directions
        Toast.makeText(this, "Directions feature coming soon!", Toast.LENGTH_SHORT).show();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
