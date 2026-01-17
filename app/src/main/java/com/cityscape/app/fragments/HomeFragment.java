package com.cityscape.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cityscape.app.CityScapeApp;
import com.cityscape.app.R;
import com.cityscape.app.activities.MainActivity;
import com.cityscape.app.adapters.PlaceCardAdapter;
import com.cityscape.app.adapters.CategoryAdapter;
import com.cityscape.app.ai.RecommendationEngine;
import com.cityscape.app.ai.MLRecommendationEngine;
import com.cityscape.app.ai.TrendAnalyzer;
import com.cityscape.app.database.AppDatabase;
import com.cityscape.app.database.entities.City;
import com.cityscape.app.database.entities.Place;
import com.cityscape.app.database.entities.PlannedActivity;
import com.cityscape.app.databinding.FragmentHomeBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Home fragment showing personalized recommendations
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private AppDatabase database;
    private RecommendationEngine recommendationEngine;
    private MLRecommendationEngine mlRecommendationEngine;
    private PlaceCardAdapter recommendedAdapter;
    private PlaceCardAdapter trendingAdapter;
    private CategoryAdapter categoryAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        database = CityScapeApp.getInstance().getDatabase();
        recommendationEngine = new RecommendationEngine(requireContext());
        mlRecommendationEngine = new MLRecommendationEngine(requireContext());

        setupUI();
        setupRecyclerViews();
        setupListeners();
        loadData();
    }

    private void setupUI() {
        // Set greeting
        String greeting = TrendAnalyzer.getTimeBasedGreeting();
        String userId = CityScapeApp.getInstance().getCurrentUserId();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            var user = database.userDao().getUserByIdSync(userId);
            String cityId = CityScapeApp.getInstance().getSelectedCityId();
            City city = database.cityDao().getCityByIdSync(cityId);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (user != null) {
                        binding.greetingText.setText(greeting + ", " + user.getName());
                    } else {
                        binding.greetingText.setText(greeting);
                    }

                    if (city != null) {
                        binding.cityName.setText(city.getName());
                    }
                });
            }
        });
    }

    private void setupRecyclerViews() {
        // Categories
        binding.categoriesRecycler.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter = new CategoryAdapter(getCategories(), category -> {
            // Navigate to explore with category filter
        });
        binding.categoriesRecycler.setAdapter(categoryAdapter);

        // Personalized recommendations
        binding.recommendedRecycler.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recommendedAdapter = new PlaceCardAdapter(new ArrayList<>(), place -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openPlaceDetails(place.getId());
            }
        });
        binding.recommendedRecycler.setAdapter(recommendedAdapter);

        // Trending places
        binding.trendingRecycler.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        trendingAdapter = new PlaceCardAdapter(new ArrayList<>(), place -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openPlaceDetails(place.getId());
            }
        });
        binding.trendingRecycler.setAdapter(trendingAdapter);
    }

    private void setupListeners() {
        // City selector
        binding.citySelector.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openCitySelect();
            }
        });

        // See all buttons
        binding.seeAllRecommended.setOnClickListener(v -> {
            // Navigate to full list
        });

        binding.seeAllTrending.setOnClickListener(v -> {
            // Navigate to full list
        });

        // Chatbot FAB
        binding.fabChatbot.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openChatbot();
            }
        });

        // Search bar
        binding.searchBar.setOnClickListener(v -> {
            // Navigate to search
        });

        // Itineraries
        binding.itineraryCard.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openItineraries();
            }
        });

        // Calendar
        binding.calendarCard.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openCalendar();
            }
        });
    }

    private void loadData() {
        String userId = CityScapeApp.getInstance().getCurrentUserId();
        String cityId = CityScapeApp.getInstance().getSelectedCityId();

        // Load recommendations in background
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // Get personalized recommendations
            List<RecommendationEngine.RecommendedPlace> recommendations = recommendationEngine
                    .getPersonalizedRecommendations(userId, cityId, 10);

            List<Place> recommendedPlaces = new ArrayList<>();
            Map<String, Integer> compatibilityScores = new HashMap<>();

            for (var rec : recommendations) {
                Place place = rec.getPlace();
                recommendedPlaces.add(place);

                // Calculate ML compatibility score
                MLRecommendationEngine.MLCompatibilityResult mlResult = mlRecommendationEngine
                        .calculateCompatibility(userId, place);
                compatibilityScores.put(place.getId(), mlResult.overallScore);
            }

            // Get trending places
            List<RecommendationEngine.RecommendedPlace> trending = recommendationEngine.getTrendingPlaces(cityId, 10);

            List<Place> trendingPlaces = new ArrayList<>();
            Map<String, Integer> trendingScores = new HashMap<>();

            for (var rec : trending) {
                Place place = rec.getPlace();
                trendingPlaces.add(place);

                // Calculate ML compatibility score
                MLRecommendationEngine.MLCompatibilityResult mlResult = mlRecommendationEngine
                        .calculateCompatibility(userId, place);
                trendingScores.put(place.getId(), mlResult.overallScore);
            }

            // Get upcoming activities for today
            String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            List<PlannedActivity> todayActivities = database.plannedActivityDao()
                    .getActivitiesByUserAndDateSync(userId, todayDate);

            // Update UI on main thread
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    recommendedAdapter.updateData(recommendedPlaces);
                    recommendedAdapter.setCompatibilityScores(compatibilityScores);

                    trendingAdapter.updateData(trendingPlaces);
                    trendingAdapter.setCompatibilityScores(trendingScores);

                    // Update calendar card subtitle with upcoming activities
                    if (todayActivities != null && !todayActivities.isEmpty()) {
                        PlannedActivity next = todayActivities.get(0);
                        String subtitle = "📍 " + next.getPlaceName() + " la " + next.getTime();
                        if (todayActivities.size() > 1) {
                            subtitle += " (+" + (todayActivities.size() - 1) + " altele)";
                        }
                        binding.calendarSubtitle.setText(subtitle);
                    } else {
                        binding.calendarSubtitle.setText("Planifică vizite și invită prieteni");
                    }

                    // Hide loading, show content
                    binding.progressBar.setVisibility(View.GONE);
                    binding.contentContainer.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private List<CategoryAdapter.Category> getCategories() {
        List<CategoryAdapter.Category> categories = new ArrayList<>();
        categories
                .add(new CategoryAdapter.Category(Place.CATEGORY_RESTAURANT, "Restaurants", R.drawable.ic_restaurant));
        categories.add(new CategoryAdapter.Category(Place.CATEGORY_CAFE, "Cafes", R.drawable.ic_cafe));
        categories.add(new CategoryAdapter.Category(Place.CATEGORY_BAR, "Bars", R.drawable.ic_bar));
        categories.add(new CategoryAdapter.Category(Place.CATEGORY_CULTURE, "Culture", R.drawable.ic_culture));
        categories.add(new CategoryAdapter.Category(Place.CATEGORY_NATURE, "Nature", R.drawable.ic_nature));
        categories.add(new CategoryAdapter.Category(Place.CATEGORY_SHOPPING, "Shopping", R.drawable.ic_shopping));
        return categories;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
