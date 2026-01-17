package com.cityscape.app.activities;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cityscape.app.CityScapeApp;
import com.cityscape.app.ai.ItineraryGenerator;
import com.cityscape.app.database.AppDatabase;
import com.cityscape.app.database.entities.Itinerary;
import com.cityscape.app.databinding.ActivityItineraryBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for viewing and generating itineraries
 */
public class ItineraryActivity extends AppCompatActivity {

    private ActivityItineraryBinding binding;
    private AppDatabase database;
    private ItineraryGenerator generator;
    private String userId;
    private String cityId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityItineraryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        database = CityScapeApp.getInstance().getDatabase();
        generator = new ItineraryGenerator(this);
        userId = CityScapeApp.getInstance().getCurrentUserId();
        cityId = CityScapeApp.getInstance().getSelectedCityId();

        setupListeners();
        loadItineraries();
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        // Theme buttons
        binding.chipFoodie.setOnClickListener(v -> generateItinerary("Foodie Day"));
        binding.chipCulture.setOnClickListener(v -> generateItinerary("Culture & Arts"));
        binding.chipRelax.setOnClickListener(v -> generateItinerary("Relaxing Day"));
        binding.chipAdventure.setOnClickListener(v -> generateItinerary("Adventure"));

        binding.btnGenerate.setOnClickListener(v -> generateItinerary("Personalized"));
    }

    private void loadItineraries() {
        database.itineraryDao().getUserItineraries(userId).observe(this, itineraries -> {
            if (itineraries != null && !itineraries.isEmpty()) {
                binding.emptyState.setVisibility(View.GONE);
                binding.itinerariesRecycler.setVisibility(View.VISIBLE);
                // TODO: Setup adapter with itineraries
            } else {
                binding.emptyState.setVisibility(View.VISIBLE);
                binding.itinerariesRecycler.setVisibility(View.GONE);
            }
        });
    }

    private void generateItinerary(String theme) {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            Itinerary itinerary = generator.generateForTheme(userId, cityId, theme);

            if (itinerary != null) {
                database.itineraryDao().insert(itinerary);
            }

            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                loadItineraries();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
