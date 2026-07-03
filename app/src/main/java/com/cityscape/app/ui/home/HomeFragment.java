package com.cityscape.app.ui.home;

import com.cityscape.app.BuildConfig;

import android.app.Activity;
import androidx.navigation.Navigation;
import android.content.Intent;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.HorizontalScrollView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.cityscape.app.R;
import com.cityscape.app.adapter.PlaceAdapter;
import com.cityscape.app.api.ApiClient;
import com.cityscape.app.api.ApiService;
import com.cityscape.app.data.SessionManager;
import com.cityscape.app.databinding.FragmentHomeBinding;
import com.cityscape.app.model.Place;
import com.cityscape.app.model.User;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.google.android.material.datepicker.MaterialDatePicker;
import java.util.Date;

public class HomeFragment extends Fragment implements com.google.android.gms.maps.OnMapReadyCallback {
    private com.google.android.gms.maps.GoogleMap googleMap;
    private android.speech.tts.TextToSpeech textToSpeech;
    private final java.util.Set<String> discoveredPlaceIds = new java.util.HashSet<>();
    private com.google.android.gms.location.LocationCallback locationCallback;

        private FragmentHomeBinding binding;
        private android.content.Context appContext;

        @Override
        public void onAttach(@NonNull android.content.Context context) {
            super.onAttach(context);
            appContext = context.getApplicationContext();
        }
        private ApiService apiService;
        private SessionManager sessionManager;
        private PlaceAdapter aiPicksAdapter;
        private PlaceAdapter recommendedAdapter;
        private PlaceAdapter nearYouAdapter;
        private FusedLocationProviderClient fusedLocationClient;
        private Location currentLocation;
        private Location actualGpsLocation;
        private List<Place> nearbyPlacesList = new ArrayList<>(); // For Near You (real-time)
        private List<Place> allPlacesList = new ArrayList<>(); // For Trending (whole city)
        private List<Place> combinedPlacesList = new ArrayList<>(); // NEW: Combined/Filtered list for current city
        private List<Place> visitedPlacesList = new ArrayList<>(); // NEW: Visited Section
        private List<Place> restaurantsList = new ArrayList<>();
        private List<Place> cafesList = new ArrayList<>();
        private List<Place> museumsList = new ArrayList<>();
        private List<Place> aiPicksList = new ArrayList<>(); // NEW: AI Recommendations
        private List<com.cityscape.app.model.Event> eventsList = new ArrayList<>();
        private String currentCategory = "All";
        private String searchQuery = "";
        private String currentMusicFilter = "All";
        private double currentRatingFilter = 0.0;
        private int currentPriceFilter = 0;
        private double currentDistanceFilter = 50.0;
        private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        private Runnable searchRunnable;
        private String eventMusicFilter = "All";
        private com.cityscape.app.api.WeatherResponse currentWeather;
        private boolean isManualLocation = false;
        private static final int REQUEST_CHECK_SETTINGS = 101;

        public View onCreateView(@NonNull LayoutInflater inflater,
                        ViewGroup container, Bundle savedInstanceState) {
                binding = FragmentHomeBinding.inflate(inflater, container, false);

                apiService = ApiClient.getClient().create(ApiService.class);
                sessionManager = new SessionManager(requireContext());
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
                initLocationCallback();

                setupGreeting();
                setupRecyclers();
                setupCategoryChips();
                setupSearch();
                setupMoodSearch();
                setupItineraryButton();
                setupCrystalBall();
                // Hide location-only sections until GPS confirms real location
                View battleCard = binding.getRoot().findViewById(R.id.card_home_live_battle);
                if (battleCard != null) battleCard.setVisibility(View.GONE);
                androidx.cardview.widget.CardView smartCard = binding.getRoot().findViewById(R.id.card_smart_recommendation);
                if (smartCard != null) smartCard.setVisibility(View.GONE);
                if (binding.cardDailyQuest != null) binding.cardDailyQuest.setVisibility(View.GONE);
                setupHypeBattle();
                binding.btnResetLocation.setOnClickListener(v -> setupLocationReset());
                
                com.google.android.gms.maps.SupportMapFragment mapFragment = (com.google.android.gms.maps.SupportMapFragment) getChildFragmentManager()
                        .findFragmentById(R.id.map_preview);
                if (mapFragment != null) {
                    mapFragment.getMapAsync(this);
                }

                binding.textLocation.setOnClickListener(v -> showCitySelectionDialog());
                binding.textWeather.setOnClickListener(v -> checkWeatherPlanB());
                checkLocationPermission();
                showPreviousSessionPromptIfAvailable();
                setupSeeAllButtons();

                // DEBUG: Show the current API URL on start to verify build updates
                try {
                    String apiUrl = BuildConfig.FLASK_API_URL;
                    Toast.makeText(getContext(), getString(R.string.connected_to) + apiUrl, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Log.e("HomeFragment", "Debug URL toast failed", e);
                }

                return binding.getRoot();
        }

        private void showPreviousSessionPromptIfAvailable() {
            // No prompt needed anymore, we automatically respect the preferred city
            // as per user request to ignore current location when set.
        }

        private void setupLocationReset() {
            if (binding.btnResetLocation != null) {
                binding.btnResetLocation.setOnClickListener(v -> {
                    isManualLocation = false;
                    sessionManager.setPreferredCity(null);
                    Toast.makeText(getContext(), getString(R.string.reverting_to_gps), Toast.LENGTH_SHORT).show();
                    checkLocationPermission();
                });
            }
        }

        private void setupItineraryButton() {
                binding.btnPlanDay.setOnClickListener(v -> {
                        Log.d("HomeFragment", "Itinerary button clicked");
                        if (currentLocation == null) {
                                Toast.makeText(getContext(), getString(R.string.finding_location),
                                                Toast.LENGTH_SHORT)
                                                .show();
                                getCurrentLocation();
                                return;
                        }

                        // Show dialog for scope selection
                        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(
                                        requireContext(), R.style.DarkDialogTheme);
                        View dialogView = getLayoutInflater().inflate(R.layout.dialog_plan_itinerary, null);
                        builder.setView(dialogView);

                        androidx.appcompat.app.AlertDialog dialog = builder.create();
                        if (dialog.getWindow() != null) {
                            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                        }

                        com.google.android.material.button.MaterialButtonToggleGroup toggleScope = dialogView
                                        .findViewById(R.id.dialog_toggle_scope);
                        com.google.android.material.chip.ChipGroup styleChips = dialogView
                                        .findViewById(R.id.dialog_style_chips);
                        com.google.android.material.slider.Slider budgetSlider = dialogView
                                        .findViewById(R.id.dialog_budget_slider);
                        TextView tvBudgetValue = dialogView.findViewById(R.id.tv_budget_value);

                        budgetSlider.addOnChangeListener((slider, value, fromUser) -> {
                                tvBudgetValue.setText(String.format("%.0f RON", value));
                        });

                        com.google.android.material.slider.Slider durationSlider = dialogView.findViewById(R.id.dialog_duration_slider);
                        TextView tvDurationValue = dialogView.findViewById(R.id.tv_duration_value);
                        if (durationSlider != null && tvDurationValue != null) {
                            durationSlider.addOnChangeListener((slider, value, fromUser) -> {
                                tvDurationValue.setText(String.format("%.0fh", value));
                            });
                        }

                        com.google.android.material.slider.Slider densitySlider = dialogView.findViewById(R.id.dialog_density_slider);
                        TextView tvDensityValue = dialogView.findViewById(R.id.tv_density_value);
                        if (densitySlider != null && tvDensityValue != null) {
                            densitySlider.addOnChangeListener((slider, value, fromUser) -> {
                                tvDensityValue.setText(String.format("%.0f locații", value));
                            });
                        }

                        com.google.android.material.chip.ChipGroup interestChips = dialogView.findViewById(R.id.dialog_interest_chips);

                        com.google.android.material.slider.Slider startHourSlider = dialogView.findViewById(R.id.dialog_start_hour_slider);
                        TextView tvStartHourValue = dialogView.findViewById(R.id.tv_start_hour_value);
                        if (startHourSlider != null && tvStartHourValue != null) {
                            startHourSlider.addOnChangeListener((slider, value, fromUser) -> {
                                tvStartHourValue.setText(String.format("%02d:00", (int) value));
                            });
                        }

                        View btnPlan = dialogView.findViewById(R.id.btn_generate_itinerary);

                        btnPlan.setOnClickListener(v1 -> {
                                String scope = (toggleScope.getCheckedButtonId() == R.id.btn_scope_city) ? "city"
                                                : "nearby";
                                                
                                String type = "exploration";
                                int checkedChipId = styleChips.getCheckedChipId();
                                
                                if (checkedChipId == R.id.chip_type_relaxation) type = "relaxation";
                                else if (checkedChipId == R.id.chip_type_cultural) type = "cultural";
                                else if (checkedChipId == R.id.chip_type_gastronomic) type = "gastronomic";
                                else if (checkedChipId == R.id.chip_type_nightlife) type = "nightlife";
                                else if (checkedChipId == R.id.chip_type_cinema) type = "cinema";
                                else if (checkedChipId == R.id.chip_type_sport) type = "sport";

                                // Collect interests
                                List<String> selectedInterests = new ArrayList<>();
                                if (interestChips != null) {
                                    for (int i = 0; i < interestChips.getChildCount(); i++) {
                                        com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) interestChips.getChildAt(i);
                                        if (chip.isChecked()) {
                                            selectedInterests.add(chip.getText().toString().replace("🎬 ", "").replace("🎾 ", "").replace("🏛️ ", "").replace("🌳 ", "").replace("🍔 ", ""));
                                        }
                                    }
                                }
                                String interestsString = String.join(",", selectedInterests);

                                int budget = (int) budgetSlider.getValue();

                                int duration = (int) (durationSlider != null ? durationSlider.getValue() : 6);
                                int points = (int) (densitySlider != null ? densitySlider.getValue() : 4);

                                // Read transport mode
                                String travelMode = "walking";
                                com.google.android.material.chip.ChipGroup transportChips = dialogView.findViewById(R.id.dialog_transport_chips);
                                if (transportChips != null) {
                                    int transportChipId = transportChips.getCheckedChipId();
                                    if (transportChipId == R.id.chip_transport_transit) travelMode = "transit";
                                    else if (transportChipId == R.id.chip_transport_driving) travelMode = "driving";
                                }

                                // Read companion
                                String companion = "solo";
                                com.google.android.material.chip.ChipGroup companionChips = dialogView.findViewById(R.id.dialog_companion_chips);
                                if (companionChips != null) {
                                    int companionChipId = companionChips.getCheckedChipId();
                                    if (companionChipId == R.id.chip_companion_couple) companion = "couple";
                                    else if (companionChipId == R.id.chip_companion_friends) companion = "friends";
                                    else if (companionChipId == R.id.chip_companion_family) companion = "family";
                                }

                                // Read start hour
                                int startHour = (startHourSlider != null) ? (int) startHourSlider.getValue() : 8;

                                // Read avoid crowds
                                boolean avoidCrowds = false;
                                com.google.android.material.materialswitch.MaterialSwitch switchAvoidCrowds = dialogView.findViewById(R.id.switch_avoid_crowds);
                                if (switchAvoidCrowds != null) {
                                    avoidCrowds = switchAvoidCrowds.isChecked();
                                }

                                dialog.dismiss();
                                fetchItinerary(scope, type, budget, duration, points, interestsString, travelMode, startHour, companion, avoidCrowds);
                        });

                        dialog.show();
                });
        }

        private void setupCrystalBall() {
                // Add a subtle pulsing animation to make it look "alive"
                android.animation.ObjectAnimator pulseX = android.animation.ObjectAnimator.ofFloat(binding.btnRevealFate, "scaleX", 1f, 1.15f);
                android.animation.ObjectAnimator pulseY = android.animation.ObjectAnimator.ofFloat(binding.btnRevealFate, "scaleY", 1f, 1.15f);
                pulseX.setDuration(1200); pulseY.setDuration(1200);
                pulseX.setRepeatCount(android.animation.ValueAnimator.INFINITE); pulseY.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                pulseX.setRepeatMode(android.animation.ValueAnimator.REVERSE); pulseY.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                pulseX.start(); pulseY.start();

                binding.btnRevealFate.setOnClickListener(v -> {
                        // Header button feedback
                        v.animate().scaleX(1.4f).scaleY(1.4f).setDuration(150).withEndAction(() -> v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150)).start();
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        
                        showRealisticMagicDialog();
                });
        }

        private void showRealisticMagicDialog() {
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme);
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_magic_ball, null);
                builder.setView(dialogView);
                androidx.appcompat.app.AlertDialog dialog = builder.create();
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

                TextView textStatus = dialogView.findViewById(R.id.text_magic_status);
                TextView textInstruction = dialogView.findViewById(R.id.text_magic_instruction);
                TextView textReveal = dialogView.findViewById(R.id.text_magic_reveal_name);
                com.cityscape.app.ui.widget.CrystalBallView crystalBallView = dialogView.findViewById(R.id.view_crystal_ball);
                View glow1 = dialogView.findViewById(R.id.view_glow_1);
                View glow2 = dialogView.findViewById(R.id.view_glow_2);
                com.google.android.material.chip.ChipGroup chipGroup = dialogView.findViewById(R.id.chip_group_magic_cats);
                com.google.android.material.button.MaterialButton btnGo = dialogView.findViewById(R.id.btn_magic_go);
                com.google.android.material.button.MaterialButton btnClose = dialogView.findViewById(R.id.btn_magic_close);
                View ballContainer = dialogView.findViewById(R.id.layout_magic_ball_container);
                View shimmer = dialogView.findViewById(R.id.view_shimmer_glint);
                View ballShadow = dialogView.findViewById(R.id.view_ball_shadow);

                com.google.android.material.chip.Chip prediction1 = dialogView.findViewById(R.id.prediction_1);
                com.google.android.material.chip.Chip prediction2 = dialogView.findViewById(R.id.prediction_2);
                com.google.android.material.chip.Chip prediction3 = dialogView.findViewById(R.id.prediction_3);

                // Setup initial state
                ballContainer.setVisibility(View.GONE);
                textReveal.setAlpha(0f);
                btnGo.setVisibility(View.GONE);

                // Puls subtil al glow-ului în timp ce utilizatorul alege categoria
                android.animation.ObjectAnimator idlePulse = android.animation.ObjectAnimator.ofFloat(glow1, "alpha", 0.2f, 0.5f);
                idlePulse.setDuration(2000);
                idlePulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                idlePulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                idlePulse.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                idlePulse.start();

                fetchAndDisplayPredictions(prediction1, prediction2, prediction3);

                chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
                        if (checkedId == -1) return;

                        String category = "general";
                        if (checkedId == R.id.chip_magic_food) category = "restaurant";
                        else if (checkedId == R.id.chip_magic_fun) category = "entertainment";
                        else if (checkedId == R.id.chip_magic_relax) category = "park";
                        else if (checkedId == R.id.chip_magic_culture) category = "museum";

                        idlePulse.cancel();
                        chipGroup.setVisibility(View.GONE);
                        if (crystalBallView != null) crystalBallView.resetBall();
                        ballContainer.setVisibility(View.VISIBLE);

                        textStatus.setText(getString(R.string.globe_watching));
                        textInstruction.setText(getString(R.string.energy_concentrating));

                        startMagicAnimations(glow1, glow2, shimmer, ballShadow, ballContainer);

                        fetchMagicRecommendation(dialog, category, textStatus, textReveal, crystalBallView, shimmer, btnGo);
                });

                if (btnClose != null) {
                    btnClose.setOnClickListener(v -> dialog.dismiss());
                }

                dialog.show();
        }

        private void fetchAndDisplayPredictions(com.google.android.material.chip.Chip pred1, com.google.android.material.chip.Chip pred2, com.google.android.material.chip.Chip pred3) {
                String userId = sessionManager.getUserId();
                apiService.getCrystalBallTimeline(userId, java.util.Locale.getDefault().getLanguage()).enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
                    @Override
                    public void onResponse(retrofit2.Call<com.google.gson.JsonObject> call, retrofit2.Response<com.google.gson.JsonObject> response) {
                        if (isAdded() && response.isSuccessful() && response.body() != null) {
                            com.google.gson.JsonObject result = response.body();
                            com.google.gson.JsonArray timeline = result.getAsJsonArray("timeline");

                            if (timeline != null && timeline.size() >= 3) {
                                com.google.gson.JsonObject pred = timeline.get(0).getAsJsonObject();
                                String type = pred.get("type").getAsString();
                                int conf = pred.get("confidence").getAsInt();
                                pred1.setText(String.format("%s (%d%%)", type, conf));

                                pred = timeline.get(1).getAsJsonObject();
                                type = pred.get("type").getAsString();
                                conf = pred.get("confidence").getAsInt();
                                pred2.setText(String.format("%s (%d%%)", type, conf));

                                pred = timeline.get(2).getAsJsonObject();
                                type = pred.get("type").getAsString();
                                conf = pred.get("confidence").getAsInt();
                                pred3.setText(String.format("%s (%d%%)", type, conf));
                            }
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<com.google.gson.JsonObject> call, Throwable t) {
                        Log.e("CrystalBall", "Failed to fetch predictions", t);
                    }
                });
        }

        private void startMagicAnimations(View glow1, View glow2, View shimmer,
                                           View ballShadow, View ballContainer) {
                android.view.animation.AccelerateDecelerateInterpolator smooth =
                        new android.view.animation.AccelerateDecelerateInterpolator();

                // Levitare: globul plutește sus-jos
                android.animation.ObjectAnimator levitate = android.animation.ObjectAnimator.ofFloat(
                        ballContainer, "translationY", 0f, -18f);
                levitate.setDuration(2600);
                levitate.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                levitate.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                levitate.setInterpolator(smooth);
                levitate.start();

                // Umbra se micșorează sincronizat cu levitarea
                if (ballShadow != null) {
                    android.animation.ObjectAnimator shadowScale = android.animation.ObjectAnimator.ofFloat(
                            ballShadow, "scaleX", 1.0f, 0.50f);
                    shadowScale.setDuration(2600);
                    shadowScale.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                    shadowScale.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                    shadowScale.setInterpolator(smooth);
                    shadowScale.start();

                    android.animation.ObjectAnimator shadowAlpha = android.animation.ObjectAnimator.ofFloat(
                            ballShadow, "alpha", 0.5f, 0.14f);
                    shadowAlpha.setDuration(2600);
                    shadowAlpha.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                    shadowAlpha.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                    shadowAlpha.setInterpolator(smooth);
                    shadowAlpha.start();
                }

                // Glow 1: puls dramatic cu scalare
                android.animation.ObjectAnimator pulseGlow1 = android.animation.ObjectAnimator.ofFloat(
                        glow1, "alpha", 0.3f, 0.88f);
                pulseGlow1.setDuration(1900);
                pulseGlow1.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                pulseGlow1.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                pulseGlow1.setInterpolator(smooth);
                pulseGlow1.start();

                for (String prop : new String[]{"scaleX", "scaleY"}) {
                    android.animation.ObjectAnimator sg = android.animation.ObjectAnimator.ofFloat(glow1, prop, 1.0f, 1.14f);
                    sg.setDuration(1900);
                    sg.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                    sg.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                    sg.setInterpolator(smooth);
                    sg.start();
                }

                // Glow 2: în antifază față de glow1
                android.animation.ObjectAnimator pulseGlow2 = android.animation.ObjectAnimator.ofFloat(
                        glow2, "alpha", 0.18f, 0.62f);
                pulseGlow2.setDuration(2700);
                pulseGlow2.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                pulseGlow2.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                pulseGlow2.setInterpolator(smooth);
                pulseGlow2.setStartDelay(950);
                pulseGlow2.start();

                // Shimmer / glint: flash specultar periodic aleator
                if (shimmer != null) {
                    shimmer.setAlpha(0f);
                    java.util.Random rng = new java.util.Random();
                    Runnable[] holder = new Runnable[1];
                    holder[0] = () -> {
                        if (!shimmer.isAttachedToWindow()) return;
                        shimmer.animate().alpha(0.88f).setDuration(160)
                            .withEndAction(() ->
                                shimmer.animate().alpha(0f).setDuration(520)
                                    .withEndAction(() ->
                                        shimmer.postDelayed(holder[0], 1800 + rng.nextInt(3500)))
                                    .start())
                            .start();
                    };
                    shimmer.postDelayed(holder[0], 1000);
                }
        }

        private void fetchMagicRecommendation(androidx.appcompat.app.AlertDialog dialog, String category,
                TextView textStatus, TextView textReveal,
                com.cityscape.app.ui.widget.CrystalBallView crystalBallView,
                View shimmer, com.google.android.material.button.MaterialButton btnGo) {
                String interests = "";
                com.cityscape.app.model.User currentUser = sessionManager.getCurrentUser();
                if (currentUser != null && currentUser.interests != null) {
                    interests = currentUser.interests;
                }
                apiService.getMagicRecommendation(currentLocation.getLatitude(), currentLocation.getLongitude(), sessionManager.getUserId(), category, interests, java.util.Locale.getDefault().getLanguage())
                    .enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
                        @Override
                        public void onResponse(retrofit2.Call<com.google.gson.JsonObject> call, retrofit2.Response<com.google.gson.JsonObject> response) {
                            if (isAdded() && response.isSuccessful() && response.body() != null) {
                                com.google.gson.JsonObject result = response.body();
                                String name = result.get("name").getAsString();
                                revealMagicResult(dialog, textStatus, textReveal, crystalBallView, shimmer, btnGo, name, result);
                            } else {
                                fallbackToRandomPlace(dialog, textStatus, textReveal, crystalBallView, shimmer, btnGo);
                            }
                        }

                        @Override
                        public void onFailure(retrofit2.Call<com.google.gson.JsonObject> call, Throwable t) {
                            fallbackToRandomPlace(dialog, textStatus, textReveal, crystalBallView, shimmer, btnGo);
                        }
                    });
        }

        private void revealMagicResult(androidx.appcompat.app.AlertDialog dialog, TextView textStatus,
                TextView textReveal, com.cityscape.app.ui.widget.CrystalBallView crystalBallView,
                View shimmer, com.google.android.material.button.MaterialButton btnGo,
                String name, com.google.gson.JsonObject result) {
            textReveal.postDelayed(() -> {
                if (!dialog.isShowing()) return;
                textStatus.setText(getString(R.string.destiny_chosen));
                // Nebula se estompează treptat (via CrystalBallView.startReveal)
                if (crystalBallView != null) crystalBallView.startReveal();
                // Flash specultar final (glint de reveal)
                if (shimmer != null) {
                    shimmer.animate().alpha(1.0f).setDuration(150)
                        .withEndAction(() -> shimmer.animate().alpha(0.3f).setDuration(800).start())
                        .start();
                }
                // Numele apare cu fade-in întârziat față de mist
                textReveal.postDelayed(() -> {
                    textReveal.setText(name);
                    textReveal.animate().alpha(1.0f).setDuration(450)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
                    btnGo.setVisibility(View.VISIBLE);
                    btnGo.setAlpha(0f);
                    btnGo.animate().alpha(1f).setDuration(400).setStartDelay(200).start();
                    btnGo.setOnClickListener(v1 -> {
                        dialog.dismiss();
                        showMagicDetailDialog(result);
                    });
                }, 250);
            }, 600);
        }

        private void fallbackToRandomPlace(androidx.appcompat.app.AlertDialog dialog, TextView textStatus,
                TextView textReveal, com.cityscape.app.ui.widget.CrystalBallView crystalBallView,
                View shimmer, com.google.android.material.button.MaterialButton btnGo) {
            if (!combinedPlacesList.isEmpty()) {
                com.cityscape.app.model.Place result = combinedPlacesList.get(new java.util.Random().nextInt(combinedPlacesList.size()));
                com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                obj.addProperty("name", result.name);
                obj.addProperty("address", result.address);
                obj.addProperty("rating", result.rating);
                obj.addProperty("place_id", result.id);
                obj.addProperty("latitude", result.latitude);
                obj.addProperty("longitude", result.longitude);
                revealMagicResult(dialog, textStatus, textReveal, crystalBallView, shimmer, btnGo, result.name, obj);
            }
        }

        private void showMagicDetailDialog(com.google.gson.JsonObject result) {
             if (getContext() == null) return;
             
             String name = result.get("name").getAsString();
             String defaultReason = "en".equals(java.util.Locale.getDefault().getLanguage()) ? "Destiny guides you to a unique local experience!" : "Destinul te ghidează spre o experiență locală unică!";
             String reason = result.has("reason") && !result.get("reason").isJsonNull() ? result.get("reason").getAsString() : defaultReason;
             com.google.gson.JsonArray activitiesArr = result.has("activities") && !result.get("activities").isJsonNull() ? result.getAsJsonArray("activities") : null;
             
             StringBuilder actStr = new StringBuilder();
             if (activitiesArr != null) {
                 for (int i = 0; i < activitiesArr.size(); i++) {
                     actStr.append("  ✨ ").append(activitiesArr.get(i).getAsString()).append("\n");
                 }
             } else {
                 actStr.append("  ✨ Explorează împrejurimile în ritmul tău.\n");
                 actStr.append("  ✨ Admiră detaliile arhitecturale și farmecul locului.\n");
                 actStr.append("  ✨ Descoperă secretele ascunse ale comunității.\n");
             }

             // Create custom reveal dialog
             androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme);
             View dialogView = getLayoutInflater().inflate(R.layout.dialog_magic_reveal, null);
             builder.setView(dialogView);
             androidx.appcompat.app.AlertDialog revealDialog = builder.create();
             revealDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

             TextView textName = dialogView.findViewById(R.id.reveal_place_name);
             TextView textReason = dialogView.findViewById(R.id.reveal_reason);
             TextView textActivities = dialogView.findViewById(R.id.reveal_activities);
             com.google.android.material.button.MaterialButton btnNavigate = dialogView.findViewById(R.id.btn_reveal_navigate);
             com.google.android.material.button.MaterialButton btnShare = dialogView.findViewById(R.id.btn_reveal_share);
             com.google.android.material.button.MaterialButton btnClose = dialogView.findViewById(R.id.btn_reveal_close);

             textName.setText(name);
             textReason.setText("\"" + reason + "\"");
             textActivities.setText(actStr.toString());

             btnNavigate.setOnClickListener(v -> {
                 try {
                     if (result.has("latitude") && !result.get("latitude").isJsonNull()) {
                         double lat = result.get("latitude").getAsDouble();
                         double lng = result.get("longitude").getAsDouble();
                         String uri = String.format(java.util.Locale.ENGLISH, "google.navigation:q=%f,%f", lat, lng);
                         android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri));
                         intent.setPackage("com.google.android.apps.maps");
                         startActivity(intent);
                     } else {
                         Toast.makeText(getContext(), getString(R.string.coordinates_unavailable), Toast.LENGTH_SHORT).show();
                     }
                 } catch (Exception e) {
                     Toast.makeText(getContext(), getString(R.string.nav_error), Toast.LENGTH_SHORT).show();
                 }
             });

             btnShare.setOnClickListener(v -> {
                 try {
                     String shareMsg = "Hei! Globul de Cristal CityScape mi-a prezis o aventură la " + name + ". Vrei să vii cu mine? ✨🚀";
                     android.content.Intent sendIntent = new android.content.Intent();
                     sendIntent.setAction(android.content.Intent.ACTION_SEND);
                     sendIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareMsg);
                     sendIntent.setType("text/plain");
                     startActivity(android.content.Intent.createChooser(sendIntent, "Invită prieteni prin..."));
                 } catch (Exception e) {
                     Toast.makeText(getContext(), getString(R.string.share_error), Toast.LENGTH_SHORT).show();
                 }
             });

             btnClose.setOnClickListener(v -> revealDialog.dismiss());

             revealDialog.show();
        }

        private void showRecommendationDialog(com.cityscape.app.model.Place place) {
                if (getContext() == null)
                        return;

                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(
                                requireContext(), R.style.DarkDialogTheme);

                // Use custom view if possible, or just standard alert
                builder.setTitle(getString(R.string.perfect_weather))
                                .setMessage(getString(R.string.how_visit, place.name) + place.description)
                                .setPositiveButton(getString(R.string.choose), (d, w) -> {
                                        // Open detail
                                        Toast.makeText(getContext(), getString(R.string.details_place) + place.name,
                                                        Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton(getString(R.string.noted), (d, w) -> binding.btnRevealFate.performClick())
                                .show();
        }

        private void checkLocationPermission() {
                if (ContextCompat.checkSelfPermission(requireContext(),
                                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, 100);
                } else {
                        getCurrentLocation();
                }
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                        @NonNull int[] grantResults) {
                if (requestCode == 100 && grantResults.length > 0
                                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        getCurrentLocation();
                }
        }

        @SuppressLint("MissingPermission")
        private void getCurrentLocation() {
                if (binding != null) {
                        binding.textLocation.setText(getString(R.string.detecting_location));
                }
                Log.d("HomeFragment", "Requesting current location with settings check...");

                LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                                .setMinUpdateDistanceMeters(10)
                                .setMaxUpdates(1)
                                .build();

                LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                                .addLocationRequest(locationRequest);
                SettingsClient client = LocationServices.getSettingsClient(requireActivity());
                Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

                task.addOnSuccessListener(requireActivity(), locationSettingsResponse -> {
                        // Check if user has a preferred city set in Settings. 
                        // If so, we IGNORE GPS completely to respect their choice.
                        String prefCity = sessionManager.getPreferredCity();
                        if (prefCity != null && !prefCity.isEmpty()) {
                            Log.d("HomeFragment", "Using preferred city override: " + prefCity);
                            isManualLocation = true;
                            searchForCity(prefCity);
                            return;
                        }

                        // Settings are good, get location only if no manual override is active
                        if (!isManualLocation) {
                            performLocationFetch();
                        }
                });

                task.addOnFailureListener(requireActivity(), e -> {
                        if (e instanceof ResolvableApiException) {
                                try {
                                        ResolvableApiException resolvable = (ResolvableApiException) e;
                                        resolvable.startResolutionForResult(requireActivity(), REQUEST_CHECK_SETTINGS);
                                } catch (android.content.IntentSender.SendIntentException sendEx) {
                                        Log.e("HomeFragment", "Error resolving settings", sendEx);
                                        usePreferredCityOrFallback();
                                }
                        } else {
                                usePreferredCityOrFallback();
                        }
                });
        }

        private void usePreferredCityOrFallback() {
                String preferredCity = sessionManager.getPreferredCity();
                if (preferredCity != null && !preferredCity.isEmpty()) {
                        searchForCity(preferredCity);
                } else {
                        // Global Fallback and for local testing convenience
                        Log.d("HomeFragment", "Using London as global fallback city");
                        searchForCity("London");
                }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
                if (requestCode == REQUEST_CHECK_SETTINGS) {
                        if (resultCode == Activity.RESULT_OK) {
                                performLocationFetch();
                        } else {
                                onLocationNotFound();
                                fetchPlaces(false); // Fetch static if GPS denied
                        }
                }
        }

        @SuppressLint("MissingPermission")
        private void performLocationFetch() {
                com.google.android.gms.tasks.CancellationTokenSource cts = new com.google.android.gms.tasks.CancellationTokenSource();
                try {
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                                        .addOnSuccessListener(location -> {
                                                if (!isAdded()) return;
                                                if (location != null) {
                                                        currentLocation = location;
                                                        actualGpsLocation = location; // Store real physical location
                                                        sessionManager.saveLastLocation(location.getLatitude(), location.getLongitude());
                                                        updateLocationUI();
                                                        fetchPlaces(true);
                                                        fetchPlaces(false);
                                                        fetchWeather(); // Fetch weather context
                                                        fetchEvents();  // Ensure events are populated on launch
                                                } else {
                                                        onLocationNotFound();
                                                }
                                        })
                                        .addOnFailureListener(e -> {
                                                if (!isAdded()) return;
                                                Log.e("HomeFragment", "Fetch failed", e);
                                                onLocationNotFound();
                                        });
                } catch (SecurityException e) {
                        Log.e("HomeFragment", "Permission error", e);
                        onLocationNotFound();
                }
        }

        private void fetchWeather() {
                if (currentLocation == null)
                        return;
                apiService.getWeather(currentLocation.getLatitude(), currentLocation.getLongitude())
                                .enqueue(new Callback<com.cityscape.app.api.WeatherResponse>() {
                                         @Override
                                         public void onResponse(Call<com.cityscape.app.api.WeatherResponse> call,
                                                         Response<com.cityscape.app.api.WeatherResponse> response) {
                                                 if (response.isSuccessful() && response.body() != null) {
                                                         currentWeather = response.body();
                                                         updateWeatherUI();
                                                         updateFilters();
                                                 } else {
                                                         updateSmartRecommendationOffline();
                                                 }
                                         }

                                         @Override
                                         public void onFailure(Call<com.cityscape.app.api.WeatherResponse> call,
                                                         Throwable t) {
                                                 updateSmartRecommendationOffline();
                                         }
                                 });
        }

        private void updateWeatherUI() {
                if (currentWeather != null && binding != null) {
                        String weatherText = String.format(java.util.Locale.US, "%.0f°C %s", currentWeather.temp,
                                        currentWeather.condition != null && currentWeather.condition.contains("Rain") ? "🌧️"
                                                         : (currentWeather.condition != null && currentWeather.condition.contains("Clear") ? "☀️" : "☁️"));
                        binding.textWeather.setText(weatherText);
                        updateSmartRecommendation(currentWeather.temp, currentWeather.condition);
                } else {
                        updateSmartRecommendationOffline();
                }
        }

        private void updateSmartRecommendation(double temp, String condition) {
                if (!isAdded() || getView() == null) return;
                if (isManualLocation) {
                    androidx.cardview.widget.CardView cs = getView().findViewById(R.id.card_smart_recommendation);
                    if (cs != null) cs.setVisibility(View.GONE);
                    return;
                }
                androidx.cardview.widget.CardView cardSmart = getView().findViewById(R.id.card_smart_recommendation);
                TextView txtEmoji = getView().findViewById(R.id.txt_smart_emoji);
                TextView txtRec = getView().findViewById(R.id.txt_smart_recommendation);
                
                if (cardSmart == null || txtEmoji == null || txtRec == null) return;
                
                com.cityscape.app.model.User currentUser = sessionManager.getCurrentUser();
                String interests = (currentUser != null && currentUser.interests != null) ? currentUser.interests.toLowerCase() : "";
                
                String conditionLower = condition != null ? condition.toLowerCase() : "";
                boolean isRainy = conditionLower.contains("rain") || conditionLower.contains("drizzle") || conditionLower.contains("ploaie") || conditionLower.contains("storm");
                
                String recText;
                String emoji;
                
                // Helper to check user interests
                boolean likesMuseums = interests.contains("muzee") || interests.contains("cultur") || interests.contains("istor");
                boolean likesParks = interests.contains("parc") || interests.contains("natur");
                boolean likesArt = interests.contains("art") || interests.contains("design");
                boolean likesFood = interests.contains("restaurante") || interests.contains("cafenele") || interests.contains("mancare");
                boolean likesNightlife = interests.contains("noapte") || interests.contains("club") || interests.contains("bar");
                boolean likesShopping = interests.contains("shop") || interests.contains("cumparaturi");

                if (isRainy) {
                    emoji = "🌧️";
                    if (likesMuseums || likesArt) {
                        recText = getString(R.string.weather_rainy_museum);
                    } else if (likesFood) {
                        recText = getString(R.string.weather_rainy_food);
                    } else if (likesShopping) {
                        recText = getString(R.string.weather_rainy_shopping);
                    } else {
                        recText = getString(R.string.weather_rainy_default);
                    }
                } else if (temp < 12) {
                    emoji = "❄️";
                    String tempStr = String.format(java.util.Locale.US, "%.1f°C", temp);
                    if (likesFood) {
                        recText = getString(R.string.weather_cold_food, tempStr);
                    } else if (likesMuseums || likesArt) {
                        recText = getString(R.string.weather_cold_museum, tempStr);
                    } else if (likesNightlife) {
                        recText = getString(R.string.weather_cold_nightlife, tempStr);
                    } else {
                        recText = getString(R.string.weather_cold_default, tempStr);
                    }
                } else if (temp >= 12 && temp <= 22) {
                    emoji = "🌤️";
                    String tempStr = String.format(java.util.Locale.US, "%.1f°C", temp);
                    if (likesParks) {
                        recText = getString(R.string.weather_cool_parks, tempStr);
                    } else if (likesMuseums || likesArt) {
                        recText = getString(R.string.weather_cool_museum, tempStr);
                    } else if (likesFood) {
                        recText = getString(R.string.weather_cool_food, tempStr);
                    } else {
                        recText = getString(R.string.weather_cool_default, tempStr);
                    }
                } else {
                    emoji = "☀️";
                    String tempStr = String.format(java.util.Locale.US, "%.1f°C", temp);
                    if (likesParks) {
                        recText = getString(R.string.weather_hot_parks, tempStr);
                    } else if (likesFood) {
                        recText = getString(R.string.weather_hot_food, tempStr);
                    } else if (likesNightlife) {
                        recText = getString(R.string.weather_hot_nightlife, tempStr);
                    } else {
                        recText = getString(R.string.weather_hot_default, tempStr);
                    }
                }
                
                txtEmoji.setText(emoji);
                txtRec.setText(recText);
                cardSmart.setVisibility(View.VISIBLE);
        }

        private void updateSmartRecommendationOffline() {
                if (!isAdded() || getView() == null) return;
                if (isManualLocation) {
                    androidx.cardview.widget.CardView cs = getView().findViewById(R.id.card_smart_recommendation);
                    if (cs != null) cs.setVisibility(View.GONE);
                    return;
                }
                androidx.cardview.widget.CardView cardSmart = getView().findViewById(R.id.card_smart_recommendation);
                TextView txtEmoji = getView().findViewById(R.id.txt_smart_emoji);
                TextView txtRec = getView().findViewById(R.id.txt_smart_recommendation);
                
                if (cardSmart == null || txtEmoji == null || txtRec == null) return;
                
                txtEmoji.setText("🔌");
                txtRec.setText(getString(R.string.offline_mode_description));
                cardSmart.setVisibility(View.VISIBLE);
        }

        private void fetchItinerary(String scope, String type, int budget, int duration, int points, String manualInterests) {
                fetchItinerary(scope, type, budget, duration, points, manualInterests, "walking", 8, "solo", false);
        }

        private void fetchItinerary(String scope, String type, int budget, int duration, int points, String manualInterests, String travelMode, int startHour, String companion, boolean avoidCrowds) {
                if (currentLocation == null) return;

                Bundle args = new Bundle();
                args.putDouble("lat", currentLocation.getLatitude());
                args.putDouble("lng", currentLocation.getLongitude());
                args.putString("scope", scope);
                args.putString("itinerary_type", type);
                args.putInt("itinerary_budget", budget);
                args.putInt("itinerary_duration", duration);
                args.putInt("itinerary_points", points);
                args.putString("itinerary_travel_mode", travelMode);
                args.putInt("itinerary_start_hour", startHour);
                args.putString("itinerary_companion", companion);
                args.putBoolean("itinerary_avoid_crowds", avoidCrowds);

                User user = sessionManager.getCurrentUser();
                String profileInterests = user != null && user.interests != null ? user.interests : "";

                // Combine profile interests with manual ones from dialog
                String combinedInterests = profileInterests;
                if (manualInterests != null && !manualInterests.isEmpty()) {
                    combinedInterests = combinedInterests.isEmpty() ? manualInterests : combinedInterests + "," + manualInterests;
                }

                args.putString("itinerary_interests", combinedInterests);
                Navigation.findNavController(binding.getRoot()).navigate(R.id.navigation_itinerary, args);
        }

        private void updateLocationUI() {
                if (currentLocation == null || binding == null)
                        return;
                // Update city text
                try {
                        // Use a background thread for geocoding to avoid blocking the main UI if it takes too long or hangs
                        new Thread(() -> {
                            try {
                                android.location.Geocoder geocoder = new android.location.Geocoder(requireContext(),
                                                java.util.Locale.getDefault());
                                List<android.location.Address> addresses = geocoder.getFromLocation(
                                                currentLocation.getLatitude(), currentLocation.getLongitude(), 1);
                                if (isAdded()) {
                                    requireActivity().runOnUiThread(() -> {
                                        if (addresses != null && !addresses.isEmpty()) {
                                            String city = addresses.get(0).getLocality();
                                            binding.textLocation.setText(city != null ? city : "Locație: " + currentLocation.getLatitude());
                                        } else {
                                            binding.textLocation.setText("Locație: " + currentLocation.getLatitude() + ", " + currentLocation.getLongitude());
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                Log.e("HomeFragment", "Geocoder bg error", e);
                            }
                        }).start();
                } catch (Exception e) {
                        Log.e("HomeFragment", "Geocoder error", e);
                }

                // Update Map Preview with Markers
                com.google.android.gms.maps.SupportMapFragment mapFragment = (com.google.android.gms.maps.SupportMapFragment) getChildFragmentManager()
                                .findFragmentById(R.id.map_preview);
                if (mapFragment != null) {
                        mapFragment.getMapAsync(googleMap -> {
                                try {
                                        googleMap.clear();
                                        com.google.android.gms.maps.model.LatLng userPos = new com.google.android.gms.maps.model.LatLng(
                                                        currentLocation.getLatitude(), currentLocation.getLongitude());
                                        googleMap.moveCamera(com.google.android.gms.maps.CameraUpdateFactory
                                                        .newLatLngZoom(userPos, 13));
                                        googleMap.setMyLocationEnabled(true);

                                        // Add markers for all known places (Trending + Nearby)
                                        List<Place> mapPlaces = new ArrayList<>(allPlacesList);
                                        for (Place np : nearbyPlacesList) {
                                                boolean exists = false;
                                                for (Place ap : allPlacesList) {
                                                        if (ap.id.equals(np.id)) {
                                                                exists = true;
                                                                break;
                                                        }
                                                }
                                                if (!exists)
                                                        mapPlaces.add(np);
                                        }

                                        for (Place p : mapPlaces) {
                                                if (p.latitude != 0 && p.longitude != 0) {
                                                        googleMap.addMarker(
                                                                        new com.google.android.gms.maps.model.MarkerOptions()
                                                                                        .position(new com.google.android.gms.maps.model.LatLng(
                                                                                                        p.latitude,
                                                                                                        p.longitude))
                                                                                        .title(p.name));
                                                }
                                        }

                                        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
                                        googleMap.getUiSettings().setAllGesturesEnabled(false);
                                } catch (SecurityException e) {
                                        e.printStackTrace();
                                }
                        });
                }

                binding.textLocation.setOnClickListener(v -> {
                        showCitySelectionDialog();
                });

        }

        private void showCitySelectionDialog() {
                android.widget.EditText input = new android.widget.EditText(getContext());
                input.setHint("Ex: Cluj-Napoca, London, Tokyo...");
                input.setPadding(40, 40, 40, 40);

                new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                                .setTitle("Alege alt oraș")
                                .setMessage(getString(R.string.enter_city_to_explore))
                                .setView(input)
                                .setPositiveButton("Explorează", (dialog, which) -> {
                                        String cityName = input.getText().toString().trim();
                                        if (!cityName.isEmpty()) {
                                                searchForCity(cityName);
                                        }
                                })
                                .setNegativeButton("Anulează", null)
                                .show();
        }

        private void searchForCity(String cityName) {
                new Thread(() -> {
                    try {
                        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                        okhttp3.Request request = new okhttp3.Request.Builder()
                            .url("https://nominatim.openstreetmap.org/search?q=" + cityName + "&format=json&limit=1")
                            .header("User-Agent", "CityScape/1.0 (Android)")
                            .build();
                        okhttp3.Response response = client.newCall(request).execute();
                        if (response.isSuccessful() && response.body() != null) {
                            String responseData = response.body().string();
                            org.json.JSONArray jsonArray = new org.json.JSONArray(responseData);
                            if (jsonArray.length() > 0) {
                                org.json.JSONObject cityObj = jsonArray.getJSONObject(0);
                                double lat = cityObj.getDouble("lat");
                                double lng = cityObj.getDouble("lon");
                                String displayName = cityObj.getString("name");
                                
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        Location mockLoc = new Location("manual");
                                        mockLoc.setLatitude(lat);
                                        mockLoc.setLongitude(lng);
                                        currentLocation = mockLoc;
                                        isManualLocation = true;
                                        sessionManager.setPreferredCity(displayName);

                                        binding.textLocation.setText(displayName);
                                        Toast.makeText(getContext(), getString(R.string.welcome_city, displayName), Toast.LENGTH_SHORT).show();

                                        allPlacesList.clear();
                                        nearbyPlacesList.clear();
                                        eventsList.clear();
                                        updateFilters();

                                        updateLocationUI();
                                        fetchPlaces(false);
                                        fetchWeather();
                                        fetchEvents();
                                    });
                                }
                                return;
                            }
                        }
                    } catch (Exception e) {
                        Log.e("HomeFragment", "Nominatim Network geocode error", e);
                    }
                    
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> handleManualCityFallback(cityName));
                    }
                }).start();
        }

        private void handleManualCityFallback(String cityName) {
            // Hardcoded coordinates for common cities as fallback for geocoder
            double lat = 0, lng = 0;
            String cleanName = cityName.toLowerCase();
            
            if (cleanName.contains("bucuresti") || cleanName.contains("bucharest")) {
                lat = 44.4268; lng = 26.1025;
            } else if (cleanName.contains("cluj")) {
                lat = 46.7712; lng = 23.5897;
            } else if (cleanName.contains("iasi")) {
                lat = 47.1585; lng = 27.6014;
            } else if (cleanName.contains("timisoara")) {
                lat = 45.7489; lng = 21.2087;
            } else if (cleanName.contains("london")) {
                lat = 51.5074; lng = -0.1278;
            }
            
            if (lat != 0) {
                Location mockLoc = new Location("manual");
                mockLoc.setLatitude(lat);
                mockLoc.setLongitude(lng);
                currentLocation = mockLoc;
                isManualLocation = true;
                
                binding.textLocation.setText(cityName);
                updateLocationUI();
                fetchPlaces(false);
                fetchWeather();
                fetchEvents();
            } else {
                Toast.makeText(getContext(), getString(R.string.city_not_found) + cityName, Toast.LENGTH_SHORT).show();
            }
        }

        private void onLocationNotFound() {
                if (getContext() == null)
                        return;
                
                // Final fallback if even preferred city is missing
                String city = sessionManager.getPreferredCity();
                if (city != null) {
                    searchForCity(city);
                } else {
                    Toast.makeText(getContext(), getString(R.string.gps_not_responding),
                                    Toast.LENGTH_LONG).show();
                    fetchPlaces(false);
                }
        }

        private void setupGreeting() {
                User user = sessionManager.getCurrentUser();
                String greeting = getGreetingForTime();
                if (user != null && user.name != null) {
                        binding.textGreeting.setText(greeting + ", " + user.name);
                } else {
                        binding.textGreeting.setText(greeting);
                }
        }

        private String getGreetingForTime() {
                int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                if (hour >= 5 && hour < 12)
                        return getString(R.string.greeting_morning);
                if (hour >= 12 && hour < 18)
                        return getString(R.string.greeting_afternoon);
                return getString(R.string.greeting_evening);
        }

        private void setupRecyclers() {
                binding.recyclerNearYou.setLayoutManager(
                                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                binding.recyclerRecommended.setLayoutManager(new LinearLayoutManager(getContext()));
                binding.recyclerEvents.setLayoutManager(
                                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                binding.recyclerVisited.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                
                // AI Picks Recycler
                binding.recyclerAiPicks.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                aiPicksAdapter = new PlaceAdapter(getContext(), aiPicksList, true, new PlaceAdapter.OnPlaceClickListener() {
                    @Override
                    public void onPlaceClick(Place place) {
                        sessionManager.recordPlaceVisit(place.name);
                        // Award XP for exploring
                        com.cityscape.app.util.BadgeManager.addExperience(getContext(), sessionManager.getUserId(), 15);
                        com.cityscape.app.util.BadgeManager.checkVisitBadges(getContext(), sessionManager.getUserId(), place.type);
                        showPlaceDetailDialog(place);
                    }

                    @Override
                    public void onFavoriteClick(Place place) {
                        sessionManager.setPlaceFavorite(place.id, place.isFavorite);
                    }

                    @Override
                    public void onVisitedClick(Place place) {
                        handleVisitedClick(place);
                    }

                    @Override
                    public void onPlanClick(Place place) {
                        showPlanPlaceDialog(place);
                    }
                });
                binding.recyclerAiPicks.setAdapter(aiPicksAdapter);
                
                fetchPlaces(false);
                fetchEvents();
                fetchVisitedPlaces();
        }


    private void setupCategoryChips() {
                ChipGroup chipGroup = binding.categoryChipGroup;
                for (int i = 0; i < chipGroup.getChildCount(); i++) {
                        View child = chipGroup.getChildAt(i);
                        if (child instanceof Chip) {
                                Chip chip = (Chip) child;
                                chip.setOnClickListener(v -> {
                                        currentCategory = chip.getText().toString();
                                        
                                        // Highlight selected chip and reset others
                                        for (int j = 0; j < chipGroup.getChildCount(); j++) {
                                            View c = chipGroup.getChildAt(j);
                                            if (c instanceof Chip) {
                                                Chip singleChip = (Chip) c;
                                                if (singleChip == chip) {
                                                    singleChip.setChipBackgroundColorResource(R.color.primary);
                                                    singleChip.setTextColor(getResources().getColor(R.color.white));
                                                } else {
                                                    singleChip.setChipBackgroundColorResource(R.color.app_card);
                                                    singleChip.setTextColor(getResources().getColor(R.color.app_text_primary));
                                                }
                                            }
                                        }
                                        
                                        fetchPlaces(false); 
                                        fetchAIPicks();
                                });
                        }
                }
        }

        private void setupSearch() {
                binding.searchInput.addTextChangedListener(new android.text.TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                                searchQuery = s.toString();
                                updateFilters();
                                
                                if (searchRunnable != null) {
                                    searchHandler.removeCallbacks(searchRunnable);
                                }
                                searchRunnable = () -> {
                                    if (isAdded()) {
                                        fetchPlaces(false);
                                        fetchAIPicks();
                                    }
                                };
                                searchHandler.postDelayed(searchRunnable, 400); // 400ms debounce
                        }
                        @Override
                        public void afterTextChanged(android.text.Editable s) { }
                });

                if (binding.btnFilter != null) {
                        binding.btnFilter.setOnClickListener(v -> showFilterBottomSheet(false));
                }
                if (binding.btnFilterHeader != null) {
                        binding.btnFilterHeader.setOnClickListener(v -> showFilterBottomSheet(false));
                }
                if (binding.btnFilterEvents != null) {
                    binding.btnFilterEvents.setOnClickListener(v -> showFilterBottomSheet(true));
                }
        }

        private void showFilterBottomSheet(boolean forEvents) {
                com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext(), R.style.TransparentBottomSheetDialogTheme);
                View view = getLayoutInflater().inflate(R.layout.dialog_filters, null);
                dialog.setContentView(view);

                TextView titleTv = view.findViewById(R.id.dialog_filter_title);
                if (titleTv != null) {
                    titleTv.setText(forEvents ? "Filtre Evenimente" : "Filtre Avansate");
                }

                com.google.android.material.chip.ChipGroup musicGroup = view.findViewById(R.id.filter_music_group);
                com.google.android.material.chip.ChipGroup ratingGroup = view.findViewById(R.id.filter_rating_group);
                com.google.android.material.chip.ChipGroup distanceGroup = view.findViewById(R.id.filter_distance_group);
                com.google.android.material.chip.ChipGroup budgetGroup = view.findViewById(R.id.filter_budget_group);

                // If filtering for events, hide rating, distance and budget filters
                if (forEvents) {
                    if (ratingGroup != null) ratingGroup.setVisibility(View.GONE);
                    if (distanceGroup != null) distanceGroup.setVisibility(View.GONE);
                    if (budgetGroup != null) budgetGroup.setVisibility(View.GONE);
                    
                    // Also hide headers by traversing parent or finding them
                    for (int i = 0; i < ((LinearLayout)view).getChildCount(); i++) {
                        View child = ((LinearLayout)view).getChildAt(i);
                        if (child instanceof TextView) {
                            String text = ((TextView) child).getText().toString();
                            if (text.contains("Rating") || text.contains("Distan") || text.contains("preț") || text.contains("Buget")) {
                                child.setVisibility(View.GONE);
                            }
                        }
                    }
                } else {
                    // Populate initial states for advanced filters
                    if (ratingGroup != null) {
                        if (currentRatingFilter == 4.0) ratingGroup.check(R.id.chip_rating_4);
                        else if (currentRatingFilter == 4.5) ratingGroup.check(R.id.chip_rating_45);
                        else ratingGroup.check(R.id.chip_rating_any);
                    }
                    if (distanceGroup != null) {
                        if (currentDistanceFilter == 2.0) distanceGroup.check(R.id.chip_distance_2);
                        else if (currentDistanceFilter == 5.0) distanceGroup.check(R.id.chip_distance_5);
                        else if (currentDistanceFilter == 10.0) distanceGroup.check(R.id.chip_distance_10);
                        else if (currentDistanceFilter == 20.0) distanceGroup.check(R.id.chip_distance_20);
                        else distanceGroup.check(R.id.chip_distance_any);
                    }
                    if (budgetGroup != null) {
                        if (currentPriceFilter == 1) budgetGroup.check(R.id.chip_budget_1);
                        else if (currentPriceFilter == 2) budgetGroup.check(R.id.chip_budget_2);
                        else if (currentPriceFilter == 3) budgetGroup.check(R.id.chip_budget_3);
                        else budgetGroup.check(R.id.chip_budget_any);
                    }
                }

                // Music filter setup
                if (musicGroup != null) {
                    String activeMusic = forEvents ? eventMusicFilter : currentMusicFilter;
                    for (int i = 0; i < musicGroup.getChildCount(); i++) {
                        com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) musicGroup.getChildAt(i);
                        if (chip.getText().toString().equals(activeMusic)) {
                            chip.setChecked(true);
                        }
                    }
                }

                view.findViewById(R.id.btn_apply_filters).setOnClickListener(v -> {
                        if (musicGroup != null) {
                            int checkedMusic = musicGroup.getCheckedChipId();
                            String musicVal = "All";
                            if (checkedMusic != View.NO_ID) {
                                    musicVal = ((com.google.android.material.chip.Chip)view.findViewById(checkedMusic)).getText().toString();
                            }

                            if (forEvents) {
                                eventMusicFilter = musicVal;
                                fetchEvents();
                            } else {
                                currentMusicFilter = musicVal;

                                // Read rating
                                if (ratingGroup != null) {
                                    int checkedRating = ratingGroup.getCheckedChipId();
                                    if (checkedRating == R.id.chip_rating_4) currentRatingFilter = 4.0;
                                    else if (checkedRating == R.id.chip_rating_45) currentRatingFilter = 4.5;
                                    else currentRatingFilter = 0.0;
                                }

                                // Read distance
                                if (distanceGroup != null) {
                                    int checkedDistance = distanceGroup.getCheckedChipId();
                                    if (checkedDistance == R.id.chip_distance_2) currentDistanceFilter = 2.0;
                                    else if (checkedDistance == R.id.chip_distance_5) currentDistanceFilter = 5.0;
                                    else if (checkedDistance == R.id.chip_distance_10) currentDistanceFilter = 10.0;
                                    else if (checkedDistance == R.id.chip_distance_20) currentDistanceFilter = 20.0;
                                    else currentDistanceFilter = 50.0;
                                }

                                // Read budget
                                if (budgetGroup != null) {
                                    int checkedBudget = budgetGroup.getCheckedChipId();
                                    if (checkedBudget == R.id.chip_budget_1) currentPriceFilter = 1;
                                    else if (checkedBudget == R.id.chip_budget_2) currentPriceFilter = 2;
                                    else if (checkedBudget == R.id.chip_budget_3) currentPriceFilter = 3;
                                    else currentPriceFilter = 0;
                                }

                                updateFilters();
                                fetchPlaces(false);
                                fetchAIPicks();
                            }
                        }
                        dialog.dismiss();
                });

                dialog.show();
        }

        private void fetchPlaces(boolean isNearbyOnly) {
                if (!isAdded() || currentLocation == null) return;

                String type = "restaurant";
                if (currentCategory.equalsIgnoreCase(getString(R.string.category_cafes)) || currentCategory.equalsIgnoreCase("Cafenele"))
                        type = "cafe";
                else if (currentCategory.equalsIgnoreCase(getString(R.string.category_parks)) || currentCategory.equalsIgnoreCase("Parcuri"))
                        type = "park";
                else if (currentCategory.equalsIgnoreCase(getString(R.string.category_museums)) || currentCategory.equalsIgnoreCase("Muzee"))
                        type = "museum";
                else if (currentCategory.equalsIgnoreCase(getString(R.string.category_culture)) || currentCategory.equalsIgnoreCase("Cultură"))
                        type = "culture"; 
                else if (currentCategory.equalsIgnoreCase(getString(R.string.category_commerce)) || currentCategory.equalsIgnoreCase("Comercial"))
                        type = "shopping";
                
                if (isAllCategory(currentCategory)) {
                        type = "";
                }
                
                if (!isNearbyOnly) {
                    fetchPlaces(true); 
                    fetchAIPicks(); // Trigger AI Recommendations
                    loadDailyQuest(); // Trigger AI Daily Quest
                    loadHypeMap(); // Refresh Hype Map for the new city
                }

                String userId = sessionManager.getUserId();
                if (isNearbyOnly) {
                    apiService.getNearby(currentLocation.getLatitude(), currentLocation.getLongitude(), type, sessionManager.getUserId(), java.util.Locale.getDefault().getLanguage())
                        .enqueue(new Callback<List<Place>>() {
                            @Override
                            public void onResponse(Call<List<Place>> call, Response<List<Place>> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    nearbyPlacesList.clear();
                                    nearbyPlacesList.addAll(response.body());
                                    updateFilters();
                                }
                            }
                            @Override
                            public void onFailure(Call<List<Place>> call, Throwable t) {
                                Log.e("HomeFragment", "Nearby fetch failed", t);
                            }
                        });
                } else {
                    String currentCity = sessionManager.getPreferredCity();
                    apiService.getPlacesSearch(currentLocation.getLatitude(), currentLocation.getLongitude(), searchQuery, type, 50000, userId, currentCity, java.util.Locale.getDefault().getLanguage())
                        .enqueue(new Callback<List<Place>>() {
                            @Override
                            public void onResponse(Call<List<Place>> call, Response<List<Place>> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    List<Place> results = response.body();
                                    allPlacesList.clear();
                                    allPlacesList.addAll(results);
                                    combinedPlacesList.clear();
                                    combinedPlacesList.addAll(results);
                                    
                                    // DEBUG TOAST: Remove later once confirmed
                                    // Removed results toast
                                    
                                    updateFilters();
                                } else {
                                    // Removed error toast
                                }
                            }

                            @Override
                            public void onFailure(Call<List<Place>> call, Throwable t) {
                                Log.e("HomeFragment", "Trending fetch failed", t);
                                if (isAdded()) Toast.makeText(getContext(), getString(R.string.trending_error) + t.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                }
        }

        private void fetchAIPicks() {
                if (currentLocation == null || !isAdded()) return;

                String userId = sessionManager.getUserId();
                String currentCity = sessionManager.getPreferredCity();

                String interests = "";
                com.cityscape.app.model.User currentUser = sessionManager.getCurrentUser();
                if (currentUser != null && currentUser.interests != null) {
                    interests = currentUser.interests;
                }

                // First, fetch explainable recommendations with confidence scores
                fetchExplainableRecommendations(userId, currentCity, interests);

                // Fallback to regular recommendations if needed
                apiService.getPersonalizedRecommendations(currentLocation.getLatitude(), currentLocation.getLongitude(), userId, searchQuery, currentCategory, currentCity, interests)
                    .enqueue(new Callback<List<Place>>() {
                        @Override
                        public void onResponse(Call<List<Place>> call, Response<List<Place>> response) {
                            if (binding == null) return;
                            if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                                // Hide offline banner on success
                                binding.cardOfflineBanner.setVisibility(View.GONE);

                                // Cache fetched places in Room
                                final List<Place> places = response.body();
                                new Thread(() -> {
                                    try {
                                        com.cityscape.app.data.AppDatabase.getInstance(getContext()).placeDao().insertPlaces(places);
                                    } catch (Exception ignored) {}
                                }).start();

                                // Only add if explainable didn't already populate
                                if (aiPicksList.isEmpty()) {
                                    aiPicksList.clear();
                                    aiPicksList.addAll(places);
                                    if (aiPicksAdapter != null) aiPicksAdapter.notifyDataSetChanged();
                                    updateFilters();
                                }
                            }
                        }

                        @Override
                        public void onFailure(Call<List<Place>> call, Throwable t) {
                            Log.e("HomeFragment", "AI Picks fetch failed", t);
                            // Load from Room cache and show offline banner
                            new Thread(() -> {
                                try {
                                    final List<Place> cached = com.cityscape.app.data.AppDatabase.getInstance(getContext()).placeDao().getAllPlaces();
                                    if (cached != null && !cached.isEmpty() && getActivity() != null) {
                                        getActivity().runOnUiThread(() -> {
                                            if (binding != null) {
                                                binding.cardOfflineBanner.setVisibility(View.VISIBLE);
                                                if (aiPicksList.isEmpty()) {
                                                    aiPicksList.clear();
                                                    aiPicksList.addAll(cached);
                                                    if (aiPicksAdapter != null) aiPicksAdapter.notifyDataSetChanged();
                                                    updateFilters();
                                                }
                                            }
                                        });
                                    }
                                } catch (Exception ignored) {}
                            }).start();
                        }
                    });
        }

        /**
         * Fetch recommendations with explainable confidence scores and match percentages
         */
        private void fetchExplainableRecommendations(String userId, String city, String interestsStr) {
                final String categoryParam = isAllCategory(currentCategory) ? null : currentCategory;
                final String queryParam = searchQuery.isEmpty() ? null : searchQuery;
                final double ratingParam = currentRatingFilter;
                final double distanceParam = currentDistanceFilter;
                final int priceParam = currentPriceFilter;

                new Thread(() -> {
                    try {
                        // Build interests array from string
                        java.util.List<String> interestsList = new ArrayList<>();
                        if (interestsStr != null && !interestsStr.isEmpty()) {
                            String[] parts = interestsStr.split(",");
                            for (String part : parts) {
                                interestsList.add(part.trim());
                            }
                        }

                        if (interestsList.isEmpty()) {
                            interestsList.add("general");
                        }

                        // Build request JSON
                        org.json.JSONObject requestBody = new org.json.JSONObject();
                        requestBody.put("user_id", userId);
                        requestBody.put("lat", currentLocation.getLatitude());
                        requestBody.put("lng", currentLocation.getLongitude());
                        requestBody.put("interests", new org.json.JSONArray(interestsList));
                        requestBody.put("city_name", city != null ? city : "București");
                        requestBody.put("limit", 10);
                        requestBody.put("trending", true);
                        requestBody.put("language", java.util.Locale.getDefault().getLanguage());

                        if (categoryParam != null) {
                            requestBody.put("category", categoryParam);
                        }
                        if (queryParam != null) {
                            requestBody.put("query", queryParam);
                        }
                        if (ratingParam > 0) {
                            requestBody.put("min_rating", ratingParam);
                        }
                        if (distanceParam > 0 && distanceParam < 50.0) {
                            requestBody.put("max_distance", distanceParam);
                        }
                        if (priceParam > 0) {
                            requestBody.put("price_level", priceParam);
                        }

                        // Make HTTP request to explainable recommendations endpoint
                        String baseUrl = ApiClient.getBaseUrl();
                        String apiUrl = baseUrl.replace("/api/", "") + "recommendations/explainable";

                        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .build();

                        okhttp3.MediaType JSON = okhttp3.MediaType.get("application/json; charset=utf-8");
                        okhttp3.RequestBody body = okhttp3.RequestBody.create(requestBody.toString(), JSON);

                        okhttp3.Request request = new okhttp3.Request.Builder()
                            .url(apiUrl)
                            .post(body)
                            .build();

                        okhttp3.Response response = client.newCall(request).execute();
                        String responseBody = response.body().string();

                        if (response.isSuccessful()) {
                            org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                            org.json.JSONArray recommendations = jsonResponse.optJSONArray("recommendations");

                            if (recommendations != null && recommendations.length() > 0) {
                                // Convert recommendations to Place objects with match percentages
                                java.util.List<Place> places = com.cityscape.app.utils.RecommendationMapper
                                    .mapRecommendationsToPlaces(recommendations);

                                // Update UI on main thread
                                if (isAdded()) {
                                    getActivity().runOnUiThread(() -> {
                                        if (binding != null) {
                                            aiPicksList.clear();
                                            aiPicksList.addAll(places);
                                            if (aiPicksAdapter != null) aiPicksAdapter.notifyDataSetChanged();
                                            updateFilters();
                                            Log.d("HomeFragment", "Loaded " + places.size() + " explainable recommendations");
                                        }
                                    });
                                }
                            }
                        } else {
                            Log.e("HomeFragment", "Explainable recommendations failed: " + response.code());
                        }
                    } catch (Exception e) {
                        Log.e("HomeFragment", "Error fetching explainable recommendations", e);
                    }
                }).start();
        }

        private void setupMoodSearch() {
            if (binding.btnMoodSearch != null) {
                binding.btnMoodSearch.setOnClickListener(v -> {
                    v.animate().rotationBy(360f).setDuration(500).start();
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    showRealisticMagicDialog();
                });
            }
        }

        private void updateFilters() {
                if (binding == null) return;

                // 1. Manage Trending Visibility and Location Title
                if (currentLocation != null) {
                    String city = binding.textLocation.getText().toString();
                    if (!isManualLocation) {
                        try {
                            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                            List<Address> addresses = geocoder.getFromLocation(
                                             currentLocation.getLatitude(), currentLocation.getLongitude(), 1);
                            if (addresses != null && !addresses.isEmpty()) {
                                city = addresses.get(0).getLocality();
                                if (city != null) binding.textLocation.setText(city);
                            }
                        } catch (Exception e) {
                            Log.e("HomeFragment", "Geocoder fail", e);
                        }
                    }

                    binding.sectionTrending.setVisibility(View.VISIBLE);
                    binding.recyclerRecommended.setVisibility(View.VISIBLE);
                    binding.trendingTitle.setText("Trending");
                } else {
                    binding.sectionTrending.setVisibility(View.GONE);
                    binding.recyclerRecommended.setVisibility(View.GONE);
                }

                // 2. Manage Near You Visibility
                if (currentLocation != null) {
                    binding.sectionNearYou.setVisibility(View.VISIBLE);
                    binding.recyclerNearYou.setVisibility(View.VISIBLE);
                    
                    if (isManualLocation) {
                        binding.btnResetLocation.setVisibility(View.VISIBLE);
                        binding.nearYouTitle.setText(getString(R.string.near_selected_location));
                    } else {
                        binding.btnResetLocation.setVisibility(View.GONE);
                        binding.nearYouTitle.setText(getString(R.string.near_you));
                    }
                } else {
                    binding.sectionNearYou.setVisibility(View.GONE);
                    binding.recyclerNearYou.setVisibility(View.GONE);
                    if (isManualLocation) {
                        binding.btnResetLocation.setVisibility(View.VISIBLE);
                    } else {
                        binding.btnResetLocation.setVisibility(View.GONE);
                    }
                }

                // 3. Populate Lists
                List<Place> filteredNearby = new ArrayList<>(nearbyPlacesList);
                updateNearYouAdapter(filteredNearby);

                List<Place> filteredAiPicks = new ArrayList<>();
                for (Place p : aiPicksList) {
                    if (matchesCategoryAndFilters(p)) {
                        filteredAiPicks.add(p);
                    }
                }
                updateAiPicksAdapter(filteredAiPicks);
                if (filteredAiPicks.isEmpty()) {
                    binding.sectionAiPicks.setVisibility(View.GONE);
                } else {
                    binding.sectionAiPicks.setVisibility(View.VISIBLE);
                }

                com.cityscape.app.model.User currentUser = sessionManager.getCurrentUser();
                final String userInterests = (currentUser != null && currentUser.interests != null) ? currentUser.interests.toLowerCase() : "";

                List<Place> stageFiltered = new ArrayList<>();
                String[] blacklist = {"mcdonald", "kfc", "subway", "5 to go", "amanet", "casino", "supermarket", "mega image", "lidl", "kaufland", "profi", "penny"};
                
                for (Place p : allPlacesList) {
                    // EXCLUDE things already in "Near You" to ensure variety
                    boolean alreadyInNearYou = false;
                    for (Place np : nearbyPlacesList) {
                        if (np.id.equals(p.id) || (np.googlePlaceId != null && np.googlePlaceId.equals(p.googlePlaceId))) {
                            alreadyInNearYou = true;
                            break;
                        }
                    }
                    if (alreadyInNearYou) continue;

                    if (matchesCategoryAndFilters(p)) {
                        String nameLower = (p.name != null) ? p.name.toLowerCase() : "";
                        String typeLower = (p.type != null) ? p.type.toLowerCase() : "";
                        
                        boolean blocked = false;
                        for (String term : blacklist) {
                            if (nameLower.contains(term) || typeLower.contains(term)) {
                                blocked = true;
                                break;
                            }
                        }
                        if (blocked) continue;

                        // Allow high ratings OR curated types OR anything if needed
                        if (p.rating >= 3.0 || typeLower.contains("museum") || typeLower.contains("park") || 
                            typeLower.contains("cafe") || typeLower.contains("restaurant") || typeLower.contains("landmark") ||
                            typeLower.contains("art") || typeLower.contains("historic") || typeLower.contains("attraction") ||
                            typeLower.contains("landmark") || typeLower.contains("tourism") || typeLower.contains("entertainment")) {
                            stageFiltered.add(p);
                        } else if (p.rating == 0 || stageFiltered.size() < 20) {
                            stageFiltered.add(p); // Add more if we are desperate
                        }
                    }
                }

                Collections.sort(stageFiltered, (p1, p2) -> {
                        double score1 = (p1.rating != 0) ? p1.rating : 3.0;
                        double score2 = (p2.rating != 0) ? p2.rating : 3.0;
                        
                        // POPULARITY BOOST (Rating * normalized Review Count)
                        // This is NOT hardcoded - it uses real crowd data
                        score1 += (p1.reviewCount / 1000.0); 
                        score2 += (p2.reviewCount / 1000.0);

                        if (!userInterests.isEmpty()) {
                                if (isTypeMatchingInterests(p1, userInterests)) score1 += 15.0;
                                if (isTypeMatchingInterests(p2, userInterests)) score2 += 15.0;
                        }
                        if (sessionManager.isPlaceFavorite(p1.id)) score1 += 20.0;
                        if (sessionManager.isPlaceFavorite(p2.id)) score2 += 20.0;

                        return Double.compare(score2, score1);
                });


                // FINAL CURATION PULL - HEAVILY SIMPLIFIED FOR RELIABILITY
                List<Place> realTrending = new ArrayList<>();
                for (Place p : stageFiltered) {
                    if (isCuratedDiscoveryType(p)) {
                        realTrending.add(p);
                    }
                }
                
                // If curated is empty, use any filtered
                if (realTrending.isEmpty()) {
                    realTrending.addAll(stageFiltered);
                }

                // If even stageFiltered is empty, use raw data unfiltered 
                // but STILL exclude nearby ONLY if we have plenty of results
                if (realTrending.isEmpty() && !allPlacesList.isEmpty()) {
                    for (Place p : allPlacesList) {
                        boolean alreadyInNearYou = false;
                        for (Place np : nearbyPlacesList) {
                            if (np.id.equals(p.id) || (np.googlePlaceId != null && np.googlePlaceId.equals(p.googlePlaceId))) {
                                alreadyInNearYou = true;
                                break;
                            }
                        }
                        
                        // Only exclude if we have enough items already, otherwise allow it to prevent empty screen
                        if (!alreadyInNearYou || (realTrending.size() < 4 && allPlacesList.size() < 10)) {
                             realTrending.add(p);
                        }
                    }
                }

                // Clean image placeholders for trending
                for (Place p : realTrending) {
                    if (p.imageUrl == null || p.imageUrl.isEmpty()) {
                        p.imageUrl = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=400";
                    }
                }

                if (!realTrending.isEmpty()) {
                    java.util.Collections.shuffle(realTrending);
                    updateRecommendedAdapter(realTrending);
                    binding.sectionTrending.setVisibility(View.VISIBLE);
                } else {
                    // If absolutely nothing unique found in 50km (unlikely), hide section
                    binding.sectionTrending.setVisibility(View.GONE);
                }
                updateActiveFilterChips();
                setupHypeBattle();
        }

        private void updateActiveFilterChips() {
            if (binding == null || getContext() == null) return;
            HorizontalScrollView scrollActiveFilters = binding.getRoot().findViewById(R.id.scroll_active_filters);
            com.google.android.material.chip.ChipGroup activeFiltersChipGroup = binding.getRoot().findViewById(R.id.active_filters_chip_group);
            
            if (scrollActiveFilters == null || activeFiltersChipGroup == null) return;
            
            activeFiltersChipGroup.removeAllViews();
            boolean hasFilters = false;
            
            // 1. Rating filter chip
            if (currentRatingFilter > 0) {
                hasFilters = true;
                com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
                chip.setText(String.format(java.util.Locale.US, "⭐ %.1f+", currentRatingFilter));
                chip.setCloseIconVisible(true);
                chip.setOnCloseIconClickListener(v -> {
                    currentRatingFilter = 0.0;
                    updateActiveFilterChips();
                    fetchPlaces(false);
                });
                activeFiltersChipGroup.addView(chip);
            }
            
            // 2. Budget/Price filter chip
            if (currentPriceFilter > 0) {
                hasFilters = true;
                com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
                String budgetStr = "$";
                if (currentPriceFilter == 2) budgetStr = "$$";
                else if (currentPriceFilter >= 3) budgetStr = "$$$";
                chip.setText("💰 " + budgetStr);
                chip.setCloseIconVisible(true);
                chip.setOnCloseIconClickListener(v -> {
                    currentPriceFilter = 0;
                    updateActiveFilterChips();
                    fetchPlaces(false);
                });
                activeFiltersChipGroup.addView(chip);
            }
            
            // 3. Distance filter chip
            if (currentDistanceFilter > 0 && currentDistanceFilter < 50.0) {
                hasFilters = true;
                com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
                chip.setText(String.format(java.util.Locale.US, "📍 %d km", (int) currentDistanceFilter));
                chip.setCloseIconVisible(true);
                chip.setOnCloseIconClickListener(v -> {
                    currentDistanceFilter = 50.0;
                    updateActiveFilterChips();
                    fetchPlaces(false);
                });
                activeFiltersChipGroup.addView(chip);
            }
            
            // 4. Search query chip
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                hasFilters = true;
                com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
                chip.setText("🔍 \"" + searchQuery + "\"");
                chip.setCloseIconVisible(true);
                chip.setOnCloseIconClickListener(v -> {
                    searchQuery = "";
                    EditText searchInput = binding.getRoot().findViewById(R.id.search_input);
                    if (searchInput != null) {
                        searchInput.setText("");
                    }
                    updateActiveFilterChips();
                    fetchPlaces(false);
                });
                activeFiltersChipGroup.addView(chip);
            }
            
            scrollActiveFilters.setVisibility(hasFilters ? View.VISIBLE : View.GONE);
        }

        private boolean isCuratedDiscoveryType(com.cityscape.app.model.Place p) {
            String t = (p.type != null) ? p.type.toLowerCase() : "";
            String n = (p.name != null) ? p.name.toLowerCase() : "";
            
            if (t.contains("casino") || t.contains("shopping") || t.contains("store") || t.contains("mall") || t.contains("kiosk") || t.contains("lodging")) return false;
            if (n.contains("casino") || n.contains("amanet") || n.contains("supermarket") || n.contains("mega image") || n.contains("lidl") || n.contains("kaufland")) return false;

            return t.contains("restaurant") || t.contains("cafe") || t.contains("coffee") ||
                   t.contains("museum") || t.contains("park") || t.contains("nature") ||
                   t.contains("landmark") || t.contains("attraction") || t.contains("art") ||
                   t.contains("culture") || t.contains("point_of_interest") || t.contains("establishment") ||
                   t.contains("food") || t.contains("bar") || t.contains("club") || t.contains("night") ||
                   t.isEmpty();
        }

        private boolean isAllCategory(String cat) {
                if (cat == null) return true;
                return cat.equalsIgnoreCase("All") || 
                       cat.equalsIgnoreCase("Toate") || 
                       cat.equalsIgnoreCase(getString(R.string.category_all)) ||
                       cat.isEmpty();
        }

        private boolean matchesCategoryAndFilters(Place p) {
            String pName = p.name != null ? p.name.toLowerCase() : "";
            String pType = p.type != null ? p.type.toLowerCase() : "";
            String searchLower = searchQuery.toLowerCase().trim();
            boolean isAll = isAllCategory(currentCategory);

            boolean matchesCat = isAll;
            if (!isAll) {
                String currCatLower = currentCategory.toLowerCase();
                if (currCatLower.contains("rest") && (pType.contains("restaurant") || pType.contains("food"))) matchesCat = true;
                else if (currCatLower.contains("caf") && (pType.contains("cafe") || pType.contains("coffee"))) matchesCat = true;
                else if (currCatLower.contains("par") && (pType.contains("park") || pType.contains("nature"))) matchesCat = true;
                else if (currCatLower.contains("mus") && (pType.contains("museum") || pType.contains("art"))) matchesCat = true;
                else if (currCatLower.contains("cult") && (pType.contains("museum") || pType.contains("art") || pType.contains("historic") || pType.contains("landmark") || pType.contains("attraction"))) matchesCat = true;
                else if (currCatLower.contains("com") && (pType.contains("shopping") || pType.contains("store") || pType.contains("mall"))) matchesCat = true;
            }

            if (!matchesCat) return false;
            if (!searchLower.isEmpty() && !pName.contains(searchLower) && !pType.contains(searchLower)) return false;

            if (!currentMusicFilter.equals("All") && !currentMusicFilter.equals("Oricare")) {
                if (!pName.contains(currentMusicFilter.toLowerCase()) && !pType.contains(currentMusicFilter.toLowerCase())) return false;
            }

            // Rating filter
            if (currentRatingFilter > 0 && p.rating < currentRatingFilter) {
                return false;
            }

            // Price level filter
            if (currentPriceFilter > 0 && p.priceLevel > 0 && p.priceLevel != currentPriceFilter) {
                return false;
            }

            // Distance filter
            if (currentLocation != null && currentDistanceFilter > 0 && currentDistanceFilter < 50.0) {
                float[] results = new float[1];
                android.location.Location.distanceBetween(currentLocation.getLatitude(), currentLocation.getLongitude(), p.latitude, p.longitude, results);
                double distanceKm = results[0] / 1000.0;
                if (distanceKm > currentDistanceFilter) {
                    return false;
                }
            }

            return true;
        }

        private void updateNearYouAdapter(List<Place> list) {
                if (binding == null) return;
                
                if (nearYouAdapter != null) {
                    nearYouAdapter.setManualMode(isManualLocation);
                    nearYouAdapter.updateData(list);
                } else {
                    nearYouAdapter = new PlaceAdapter(getContext(), list, true,
                                    new PlaceAdapter.OnPlaceClickListener() {
                                            @Override
                                            public void onPlaceClick(Place place) {
                                                sessionManager.recordPlaceVisit(place.name);
                                                com.cityscape.app.util.BadgeManager.addExperience(getContext(), sessionManager.getUserId(), 15);
                                                com.cityscape.app.util.BadgeManager.checkVisitBadges(getContext(), sessionManager.getUserId(), place.type);
                                                showPlaceDetailDialog(place);
                                            }

                                            @Override
                                            public void onFavoriteClick(Place place) {
                                                    sessionManager.setPlaceFavorite(place.id, place.isFavorite);
                                            }

                                            @Override
                                            public void onVisitedClick(Place place) {
                                                    handleVisitedClick(place);
                                            }

                                            @Override
                                            public void onPlanClick(Place place) {
                                                    showPlanPlaceDialog(place);
                                            }
                                    });
                    nearYouAdapter.setManualMode(isManualLocation);
                    binding.recyclerNearYou.setAdapter(nearYouAdapter);
                }
        }

        private void handleVisitedClick(Place place) {
            User user = sessionManager.getCurrentUser();
            if (user == null) return;

            com.cityscape.app.api.VisitRequest request = new com.cityscape.app.api.VisitRequest(
                user.id,
                (place.id != null && place.id.length() < 10) ? place.id : null,
                (place.googlePlaceId != null) ? place.googlePlaceId : ((place.id != null && place.id.length() >= 10) ? place.id : null),
                place.name,
                place.type
            );

            apiService.recordVisit(request).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (isAdded() && response.isSuccessful()) {
                        if (getContext() != null) Toast.makeText(getContext(), getString(R.string.visit_registered), Toast.LENGTH_SHORT).show();
                        fetchVisitedPlaces();
                        showFeedbackDialog(place);
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Toast.makeText(getContext(), getString(R.string.visit_reg_error), Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void showFeedbackDialog(Place place) {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setTitle(getString(R.string.feedback_title, place.name))
                .setMessage(getString(R.string.visit_feedback))
                .setPositiveButton("Grozav! ⭐", (d, w) -> Toast.makeText(getContext(), getString(R.string.thank_you), Toast.LENGTH_SHORT).show())
                .setNeutralButton("E OK", (d, w) -> Toast.makeText(getContext(), getString(R.string.thank_you), Toast.LENGTH_SHORT).show())
                .setNegativeButton("Slab", (d, w) -> Toast.makeText(getContext(), getString(R.string.noted), Toast.LENGTH_SHORT).show())
                .show();
        }

        private void fetchVisitedPlaces() {
            User user = sessionManager.getCurrentUser();
            if (user == null || binding == null) return;

            apiService.getVisited(user.id).enqueue(new Callback<List<Place>>() {
                @Override
                public void onResponse(Call<List<Place>> call, Response<List<Place>> response) {
                    if (isAdded() && binding != null && response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        visitedPlacesList = response.body();
                        binding.sectionVisited.setVisibility(View.VISIBLE);
                        binding.recyclerVisited.setVisibility(View.VISIBLE);
                        updateVisitedAdapter();
                    } else if (isAdded() && binding != null) {
                        if (binding != null) {
                            binding.sectionVisited.setVisibility(View.GONE);
                            binding.recyclerVisited.setVisibility(View.GONE);
                        }
                    }
                }

                @Override
                public void onFailure(Call<List<Place>> call, Throwable t) {
                    Log.e("HomeFragment", "Error fetching visited", t);
                }
            });
        }

        private void updateVisitedAdapter() {
            if (binding == null) return;
            PlaceAdapter adapter = new PlaceAdapter(getContext(), visitedPlacesList, true, 
                new PlaceAdapter.OnPlaceClickListener() {
                    @Override
                    public void onPlaceClick(Place place) {
                        com.cityscape.app.util.BadgeManager.addExperience(getContext(), sessionManager.getUserId(), 15);
                        com.cityscape.app.util.BadgeManager.checkVisitBadges(getContext(), sessionManager.getUserId(), place.type);
                        showPlaceDetailDialog(place);
                    }
                    @Override
                    public void onFavoriteClick(Place place) { }
                    @Override
                    public void onVisitedClick(Place place) { }
                    @Override
                    public void onPlanClick(Place place) {
                        showPlanPlaceDialog(place);
                    }
                });
            binding.recyclerVisited.setAdapter(adapter);
        }

        private void updateAiPicksAdapter(List<Place> list) {
                if (binding == null) return;
                PlaceAdapter adapter = new PlaceAdapter(getContext(), list, true,
                                new PlaceAdapter.OnPlaceClickListener() {
                                        @Override
                                        public void onPlaceClick(Place place) {
                                                sessionManager.recordPlaceVisit(place.name);
                                                com.cityscape.app.util.BadgeManager.addExperience(getContext(), sessionManager.getUserId(), 15);
                                                com.cityscape.app.util.BadgeManager.checkVisitBadges(getContext(), sessionManager.getUserId(), place.type);
                                                showPlaceDetailDialog(place);
                                        }

                                        @Override
                                        public void onFavoriteClick(Place place) {
                                                sessionManager.setPlaceFavorite(place.id, place.isFavorite);
                                        }

                                        @Override
                                        public void onVisitedClick(Place place) {
                                                handleVisitedClick(place);
                                        }

                                        @Override
                                        public void onPlanClick(Place place) {
                                                showPlanPlaceDialog(place);
                                        }
                                });
                binding.recyclerAiPicks.setAdapter(adapter);
        }

        private void updateRecommendedAdapter(List<Place> list) {
                if (binding == null) return;
                
                // Show exactly up to 10 locations in the trending section
                List<Place> limitedList = list;
                if (list != null && list.size() > 10) {
                        limitedList = new java.util.ArrayList<>(list.subList(0, 10));
                }
                
                if (recommendedAdapter != null) {
                    recommendedAdapter.setManualMode(isManualLocation);
                    recommendedAdapter.updateData(limitedList);
                } else {
                    recommendedAdapter = new PlaceAdapter(getContext(), limitedList, false,
                                    new PlaceAdapter.OnPlaceClickListener() {
                                            @Override
                                            public void onPlaceClick(Place place) {
                                                    sessionManager.recordPlaceVisit(place.name);
                                                    com.cityscape.app.util.BadgeManager.addExperience(getContext(), sessionManager.getUserId(), 15);
                                                    com.cityscape.app.util.BadgeManager.checkVisitBadges(getContext(), sessionManager.getUserId(), place.type);
                                                    showPlaceDetailDialog(place);
                                            }

                                            @Override
                                            public void onFavoriteClick(Place place) {
                                                    sessionManager.setPlaceFavorite(place.id, place.isFavorite);
                                            }

                                            @Override
                                            public void onVisitedClick(Place place) {
                                                    handleVisitedClick(place);
                                            }

                                            @Override
                                            public void onPlanClick(Place place) {
                                                    showPlanPlaceDialog(place);
                                            }
                                    });
                    recommendedAdapter.setManualMode(isManualLocation);
                    binding.recyclerRecommended.setAdapter(recommendedAdapter);
                }
        }

        private void showPlaceDetailDialog(Place place) {
            if (!isAdded()) return;
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme);
            View v = getLayoutInflater().inflate(R.layout.dialog_place_detail, null);
            builder.setView(v);
            
            ImageView image = v.findViewById(R.id.detail_place_image);
            TextView txtType = v.findViewById(R.id.detail_place_type);
            TextView txtRating = v.findViewById(R.id.detail_place_rating);
            TextView txtWeather = v.findViewById(R.id.detail_place_weather);
            TextView txtName = v.findViewById(R.id.detail_place_name);
            TextView txtAddress = v.findViewById(R.id.detail_place_address);
            TextView txtDescription = v.findViewById(R.id.detail_place_description);
            TextView lblAiSummary = v.findViewById(R.id.lbl_ai_summary);
            Button btnPlan = v.findViewById(R.id.btn_plan_place_detail);
            Button btnClose = v.findViewById(R.id.btn_close_place_detail);
            
            ImageView btnSpeakAi = v.findViewById(R.id.btn_speak_ai);
            android.widget.LinearLayout layoutVibeometer = v.findViewById(R.id.layout_vibeometer);
            TextView vibeEmoji = v.findViewById(R.id.vibe_emoji);
            TextView vibeTitle = v.findViewById(R.id.vibe_title);
            TextView vibeDescription = v.findViewById(R.id.vibe_description);
            
            if (txtName != null) txtName.setText(place.name);
            if (txtType != null) txtType.setText(place.type != null ? place.type.toUpperCase() : "ATRACȚIE");
            if (txtRating != null) {
                StringBuilder stars = new StringBuilder();
                int r = Math.round(place.rating);
                for (int i = 0; i < 5; i++) {
                    stars.append(i < r ? "★" : "☆");
                }
                txtRating.setText(String.format(java.util.Locale.US, "%.1f %s", place.rating, stars.toString()));
            }
            if (txtAddress != null) txtAddress.setText(place.address != null ? place.address : getString(R.string.address_unspecified_text));
            
            // Dynamic Live Weather Fetch for this specific place location
            if (txtWeather != null && place.latitude != 0 && place.longitude != 0) {
                apiService.getWeather(place.latitude, place.longitude).enqueue(new Callback<com.cityscape.app.api.WeatherResponse>() {
                    @Override
                    public void onResponse(Call<com.cityscape.app.api.WeatherResponse> call, Response<com.cityscape.app.api.WeatherResponse> response) {
                        if (isAdded() && response.isSuccessful() && response.body() != null) {
                            com.cityscape.app.api.WeatherResponse w = response.body();
                            String emoji = "🌤️";
                            if (w.condition != null) {
                                String cond = w.condition.toLowerCase();
                                if (cond.contains("rain") || cond.contains("drizzle") || cond.contains("ploaie")) emoji = "🌧️";
                                else if (cond.contains("snow") || cond.contains("ninsoare")) emoji = "❄️";
                                else if (cond.contains("thunder") || cond.contains("furtună")) emoji = "⚡";
                                else if (cond.contains("clear") || cond.contains("senin")) emoji = "☀️";
                                else if (cond.contains("cloud") || cond.contains("nor")) emoji = "☁️";
                            }
                            txtWeather.setText(String.format(java.util.Locale.US, "%s %.1f°C", emoji, w.temp));
                            txtWeather.setVisibility(View.VISIBLE);
                        }
                    }
                    @Override
                    public void onFailure(Call<com.cityscape.app.api.WeatherResponse> call, Throwable t) {}
                });
            }
            
            String desc = place.aiSuggestion;
            
            LinearLayout aiAnalysisSection = v.findViewById(R.id.aiAnalysisSection);
            TextView txtTotalConfidence = v.findViewById(R.id.txt_total_confidence);
            TextView txtFactorInterests = v.findViewById(R.id.txt_factor_interests);
            ProgressBar progressFactorInterests = v.findViewById(R.id.progress_factor_interests);
            TextView txtFactorFreshness = v.findViewById(R.id.txt_factor_freshness);
            ProgressBar progressFactorFreshness = v.findViewById(R.id.progress_factor_freshness);
            TextView txtFactorPopularity = v.findViewById(R.id.txt_factor_popularity);
            ProgressBar progressFactorPopularity = v.findViewById(R.id.progress_factor_popularity);
            TextView txtFactorLevel = v.findViewById(R.id.txt_factor_level);
            ProgressBar progressFactorLevel = v.findViewById(R.id.progress_factor_level);
            TextView txtFactorDiversity = v.findViewById(R.id.txt_factor_diversity);
            ProgressBar progressFactorDiversity = v.findViewById(R.id.progress_factor_diversity);
            TextView txtFactorWeather = v.findViewById(R.id.txt_factor_weather);
            ProgressBar progressFactorWeather = v.findViewById(R.id.progress_factor_weather);

            if (desc == null || desc.isEmpty()) {
                desc = place.ai_summary;
            }
            if (desc == null || desc.isEmpty()) {
                desc = place.description;
            }
            if (desc == null || desc.isEmpty()) {
                desc = "Această locație este recomandată pentru experiența sa unică și recenziile sale excelente.";
                if (lblAiSummary != null) lblAiSummary.setText("ℹ️ DESPRE LOCAȚIE");
            } else {
                if (lblAiSummary != null) lblAiSummary.setText("💡 SFATUL EXPLORATORULUI");
                
                // Show AI Analysis Percentages if confidence score exists
                if (place.confidence > 0) {
                    if (aiAnalysisSection != null) {
                        aiAnalysisSection.setVisibility(View.VISIBLE);
                    }
                    if (txtTotalConfidence != null) {
                        txtTotalConfidence.setText(String.format(java.util.Locale.US, "%.1f%% Potrivire", place.confidence));
                    }
                    if (txtFactorInterests != null) {
                        txtFactorInterests.setText(String.format(java.util.Locale.US, "%.1f%%", place.matchPrefsPct));
                    }
                    if (progressFactorInterests != null) {
                        progressFactorInterests.setProgress(Math.round(place.matchPrefsPct));
                    }
                    if (txtFactorFreshness != null) {
                        txtFactorFreshness.setText(String.format(java.util.Locale.US, "%.1f%%", place.freshnessPct));
                    }
                    if (progressFactorFreshness != null) {
                        progressFactorFreshness.setProgress(Math.round(place.freshnessPct));
                    }
                    if (txtFactorPopularity != null) {
                        txtFactorPopularity.setText(String.format(java.util.Locale.US, "%.1f%%", place.popularityPct));
                    }
                    if (progressFactorPopularity != null) {
                        progressFactorPopularity.setProgress(Math.round(place.popularityPct));
                    }
                    if (txtFactorLevel != null) {
                        txtFactorLevel.setText(String.format(java.util.Locale.US, "%.1f%%", place.userLevelPct));
                    }
                    if (progressFactorLevel != null) {
                        progressFactorLevel.setProgress(Math.round(place.userLevelPct));
                    }
                    if (txtFactorDiversity != null) {
                        txtFactorDiversity.setText(String.format(java.util.Locale.US, "%.1f%%", place.diversityPct));
                    }
                    if (progressFactorDiversity != null) {
                        progressFactorDiversity.setProgress(Math.round(place.diversityPct));
                    }
                    if (txtFactorWeather != null) {
                        txtFactorWeather.setText(String.format(java.util.Locale.US, "%.1f%%", place.weatherMatchPct));
                    }
                    if (progressFactorWeather != null) {
                        progressFactorWeather.setProgress(Math.round(place.weatherMatchPct));
                    }

                    // Dynamically build filter and interest chips inside dialog
                    com.google.android.material.chip.ChipGroup dialogCriteriaChips = v.findViewById(R.id.dialog_criteria_chips);
                    if (dialogCriteriaChips != null) {
                        dialogCriteriaChips.removeAllViews();
                        
                        // 1. Category chip
                        if (currentCategory != null && !currentCategory.equalsIgnoreCase("All")) {
                            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
                            chip.setText("📂 Categorie: " + currentCategory);
                            chip.setChipBackgroundColorResource(android.R.color.transparent);
                            chip.setChipStrokeColorResource(android.R.color.darker_gray);
                            chip.setChipStrokeWidth(1.0f);
                            dialogCriteriaChips.addView(chip);
                        }
                        
                        // 2. Rating chip
                        if (currentRatingFilter > 0) {
                            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
                            chip.setText(String.format(java.util.Locale.US, "⭐ Rating > %.1f", currentRatingFilter));
                            chip.setChipBackgroundColorResource(android.R.color.transparent);
                            chip.setChipStrokeColorResource(android.R.color.darker_gray);
                            chip.setChipStrokeWidth(1.0f);
                            dialogCriteriaChips.addView(chip);
                        }
                        
                        // 3. Budget chip
                        if (currentPriceFilter > 0) {
                            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
                            String budgetStr = "Ieftin ($)";
                            if (currentPriceFilter == 2) budgetStr = "Mediu ($$)";
                            else if (currentPriceFilter >= 3) budgetStr = "Lux ($$$)";
                            chip.setText("💰 Buget: " + budgetStr);
                            chip.setChipBackgroundColorResource(android.R.color.transparent);
                            chip.setChipStrokeColorResource(android.R.color.darker_gray);
                            chip.setChipStrokeWidth(1.0f);
                            dialogCriteriaChips.addView(chip);
                        }
                        
                        // 4. Distance chip
                        if (currentDistanceFilter > 0 && currentDistanceFilter < 50.0) {
                            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
                            chip.setText(String.format(java.util.Locale.US, "📍 Distanță < %d km", (int) currentDistanceFilter));
                            chip.setChipBackgroundColorResource(android.R.color.transparent);
                            chip.setChipStrokeColorResource(android.R.color.darker_gray);
                            chip.setChipStrokeWidth(1.0f);
                            dialogCriteriaChips.addView(chip);
                        }
                        
                        // 5. Query chip
                        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
                            chip.setText("🔍 Căutare: \"" + searchQuery + "\"");
                            chip.setChipBackgroundColorResource(android.R.color.transparent);
                            chip.setChipStrokeColorResource(android.R.color.darker_gray);
                            chip.setChipStrokeWidth(1.0f);
                            dialogCriteriaChips.addView(chip);
                        }
                        
                        // 6. User Interests chips
                        com.cityscape.app.model.User currentUser = sessionManager.getCurrentUser();
                        String userInterests = (currentUser != null && currentUser.interests != null) ? currentUser.interests : "";
                        if (userInterests != null && !userInterests.isEmpty()) {
                            String[] parts = userInterests.split(",");
                            for (String part : parts) {
                                String clean = part.trim();
                                if (!clean.isEmpty()) {
                                    com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
                                    chip.setText("💡 " + clean);
                                    chip.setChipBackgroundColorResource(android.R.color.transparent);
                                    chip.setChipStrokeColorResource(android.R.color.darker_gray);
                                    chip.setChipStrokeWidth(1.0f);
                                    dialogCriteriaChips.addView(chip);
                                }
                            }
                        }
                    }
                }
            }
            
            if (txtDescription != null) txtDescription.setText(desc);
            
            if (image != null && place.imageUrl != null && !place.imageUrl.isEmpty()) {
                com.bumptech.glide.Glide.with(this)
                    .load(place.imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.placeholder_place)
                    .into(image);
            }

            // Vibeometer logic
            if (layoutVibeometer != null && vibeEmoji != null && vibeTitle != null && vibeDescription != null) {
                int currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
                String typeLower = place.type != null ? place.type.toLowerCase() : "";
                
                String emoji = "🍃";
                String title = "Chill Vibe (Liniștit & Relaxant)";
                String descriptionStr = "Mulți vizitatori preferă această locație pentru relaxare și liniște.";
                
                if (typeLower.contains("restaurant") || typeLower.contains("cafe") || typeLower.contains("club") || typeLower.contains("bar") || typeLower.contains("pub")) {
                    if (currentHour >= 18 && currentHour <= 23) {
                        emoji = "🔥";
                        title = "Hype Vibe (Extrem de aglomerat)";
                        descriptionStr = "Atmosfera este incendiară acum! Foarte mulți oameni și vibe excelent.";
                    } else if (currentHour >= 12 && currentHour <= 15) {
                        emoji = "⚡";
                        title = "Energy Vibe (Popular & Activ)";
                        descriptionStr = "Locația este destul de populată la ora prânzului. Energie maximă!";
                    } else {
                        emoji = "🍃";
                        title = "Chill Vibe (Relaxant & Plăcut)";
                        descriptionStr = "Perfect pentru discuții lejere și relaxare în afara orelor de vârf.";
                    }
                } else {
                    if (currentHour >= 10 && currentHour <= 16) {
                        emoji = "⚡";
                        title = "Energy Vibe (Popular & Activ)";
                        descriptionStr = "Ora ideală de vizitat. Vizitatorii explorează în număr mare!";
                    } else if (currentHour >= 17 || currentHour <= 9) {
                        emoji = "💤";
                        title = "Peaceful Vibe (Destul de liber)";
                        descriptionStr = "Oază de liniște și pace în acest moment. Excelent pentru relaxare și poze!";
                    }
                }
                
                vibeEmoji.setText(emoji);
                vibeTitle.setText(title);
                vibeDescription.setText(descriptionStr);
            }

            // Audio Guide Click Logic
            if (btnSpeakAi != null) {
                initTextToSpeech();
                final String textToRead = desc;
                btnSpeakAi.setOnClickListener(view -> {
                    if (textToSpeech != null) {
                        if (textToSpeech.isSpeaking()) {
                            textToSpeech.stop();
                            Toast.makeText(getContext(), getString(R.string.audio_guide_stopped), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(), getString(R.string.reading_ai_rec), Toast.LENGTH_SHORT).show();
                            textToSpeech.speak(textToRead, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "ai_tour");
                        }
                    } else {
                        Toast.makeText(getContext(), getString(R.string.voice_init), Toast.LENGTH_SHORT).show();
                        initTextToSpeech();
                    }
                });
            }
            
            androidx.appcompat.app.AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            }
            
            if (btnPlan != null) {
                btnPlan.setOnClickListener(view -> {
                    if (textToSpeech != null && textToSpeech.isSpeaking()) {
                        textToSpeech.stop();
                    }
                    dialog.dismiss();
                    showPlanPlaceDialog(place);
                });
            }
            
            if (btnClose != null) {
                btnClose.setOnClickListener(view -> {
                    if (textToSpeech != null && textToSpeech.isSpeaking()) {
                        textToSpeech.stop();
                    }
                    dialog.dismiss();
                });
            }

            // Show reviews section
            if (place.reviews != null && !place.reviews.isEmpty()) {
                renderReviews(v, place.reviews);
            } else {
                loadFallbackReviews(v, place);
            }
            
            dialog.show();
        }

        private void loadFallbackReviews(View v, Place place) {
            if (!isAdded()) return;
            List<Place.Review> mockReviews = new ArrayList<>();
            
            Place.Review r1 = new Place.Review();
            r1.author = "Alexandru Popescu";
            r1.rating = 5f;
            r1.time = "acum 2 zile";
            r1.text = "O locație superbă, cu servire rapidă și energie excelentă! Perfect pentru un weekend relaxant.";
            mockReviews.add(r1);

            Place.Review r2 = new Place.Review();
            r2.author = "Andreea Stoica";
            r2.rating = 4f;
            r2.time = "acum o săptămână";
            r2.text = "Atmosferă caldă, perfectă pentru poze și ieșit cu prietenii. Cu siguranță voi mai reveni aici.";
            mockReviews.add(r2);

            place.reviews = mockReviews;
            renderReviews(v, place.reviews);
        }

        private void renderReviews(View v, List<com.cityscape.app.model.Place.Review> reviews) {
            if (getActivity() == null || reviews == null || reviews.isEmpty()) return;
            
            TextView lblReviews = v.findViewById(R.id.lbl_reviews_title);
            android.widget.HorizontalScrollView scrollReviews = v.findViewById(R.id.scroll_reviews);
            android.widget.LinearLayout container = v.findViewById(R.id.layout_reviews_container);
            
            if (lblReviews != null) lblReviews.setVisibility(View.VISIBLE);
            if (scrollReviews != null) scrollReviews.setVisibility(View.VISIBLE);
            if (container != null) {
                container.removeAllViews();
                
                for (com.cityscape.app.model.Place.Review review : reviews) {
                    View card = getLayoutInflater().inflate(R.layout.item_review_card, container, false);
                    TextView txtAuthor = card.findViewById(R.id.review_author);
                    TextView txtRating = card.findViewById(R.id.review_rating);
                    TextView txtText = card.findViewById(R.id.review_text);
                    TextView txtTime = card.findViewById(R.id.review_time);
                    
                    if (txtAuthor != null) txtAuthor.setText(review.author);
                    if (txtRating != null) {
                        StringBuilder stars = new StringBuilder();
                        int r = (int) Math.round(review.rating);
                        for (int i = 0; i < 5; i++) {
                            stars.append(i < r ? "⭐" : "☆");
                        }
                        txtRating.setText(stars.toString());
                    }
                    if (txtText != null) txtText.setText(review.text);
                    if (txtTime != null) txtTime.setText(review.time != null ? review.time : "");
                    
                    container.addView(card);
                }
            }
        }

        private void showPlanPlaceDialog(Place place) {
            if (!isAdded()) return;

            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme);
            View v = getLayoutInflater().inflate(R.layout.dialog_plan_place_complex, null);
            builder.setView(v);

            TextView txtTitle = v.findViewById(R.id.txt_plan_title);
            Button btnSelectDate = v.findViewById(R.id.btn_select_date);
            Button btnStartTime = v.findViewById(R.id.btn_start_time);
            Button btnEndTime = v.findViewById(R.id.btn_end_time);
            com.google.android.material.chip.ChipGroup cgPriority = v.findViewById(R.id.cg_priority);
            EditText inputBudget = v.findViewById(R.id.input_budget);
            com.google.android.material.chip.ChipGroup cgTransit = v.findViewById(R.id.cg_transit);
            EditText inputNotes = v.findViewById(R.id.input_notes);
            Button btnCancel = v.findViewById(R.id.btn_cancel_plan);
            Button btnSave = v.findViewById(R.id.btn_save_plan);

            if (txtTitle != null) {
                txtTitle.setText("Planifică vizita la:\n" + place.name);
            }

            final long[] selectedDateMillis = {System.currentTimeMillis()};
            final int[] startHour = {10}, startMinute = {0};
            final int[] endHour = {12}, endMinute = {0};

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.getDefault());
            if (btnSelectDate != null) btnSelectDate.setText(sdf.format(new Date(selectedDateMillis[0])));

            if (btnSelectDate != null) {
                btnSelectDate.setOnClickListener(view -> {
                    android.app.DatePickerDialog datePicker = new android.app.DatePickerDialog(requireContext(), R.style.DarkDialogTheme,
                        (dp, year, month, dayOfMonth) -> {
                            Calendar cal = Calendar.getInstance();
                            cal.set(year, month, dayOfMonth);
                            selectedDateMillis[0] = cal.getTimeInMillis();
                            btnSelectDate.setText(sdf.format(cal.getTime()));
                        },
                        Calendar.getInstance().get(Calendar.YEAR),
                        Calendar.getInstance().get(Calendar.MONTH),
                        Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                    );
                    datePicker.show();
                });
            }

            if (btnStartTime != null) {
                btnStartTime.setOnClickListener(view -> {
                    android.app.TimePickerDialog timePicker = new android.app.TimePickerDialog(requireContext(), R.style.DarkDialogTheme,
                        (tp, hourOfDay, minute) -> {
                            startHour[0] = hourOfDay;
                            startMinute[0] = minute;
                            btnStartTime.setText(String.format(Locale.US, "De la: %02d:%02d", hourOfDay, minute));
                        },
                        startHour[0], startMinute[0], true
                    );
                    timePicker.show();
                });
            }

            if (btnEndTime != null) {
                btnEndTime.setOnClickListener(view -> {
                    android.app.TimePickerDialog timePicker = new android.app.TimePickerDialog(requireContext(), R.style.DarkDialogTheme,
                        (tp, hourOfDay, minute) -> {
                            endHour[0] = hourOfDay;
                            endMinute[0] = minute;
                            btnEndTime.setText(String.format(Locale.US, "Până la: %02d:%02d", hourOfDay, minute));
                        },
                        endHour[0], endMinute[0], true
                    );
                    timePicker.show();
                });
            }

            androidx.appcompat.app.AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            }

            if (btnCancel != null) btnCancel.setOnClickListener(view -> dialog.dismiss());

            if (btnSave != null) {
                btnSave.setOnClickListener(view -> {
                    String timeSlot = String.format(Locale.US, "%02d:%02d - %02d:%02d", startHour[0], startMinute[0], endHour[0], endMinute[0]);
                    
                    String priority = "🔥 Must See";
                    if (cgPriority != null) {
                        int checkedPriority = cgPriority.getCheckedChipId();
                        if (checkedPriority == R.id.chip_priority_med) priority = "🍃 Flexible";
                        else if (checkedPriority == R.id.chip_priority_low) priority = "💤 Optional";
                    }

                    double budget = 0.0;
                    if (inputBudget != null) {
                        String rawBudget = inputBudget.getText().toString().trim();
                        if (!rawBudget.isEmpty()) {
                            try {
                                budget = Double.parseDouble(rawBudget);
                            } catch (Exception e) {}
                        }
                    }

                    String transit = "🚶 Pe jos";
                    if (cgTransit != null) {
                        int checkedTransit = cgTransit.getCheckedChipId();
                        if (checkedTransit == R.id.chip_transit_bus) transit = "🚌 Transport comun";
                        else if (checkedTransit == R.id.chip_transit_car) transit = "🚗 Auto/Uber";
                    }

                    String userNotes = inputNotes != null ? inputNotes.getText().toString().trim() : "";

                    String formattedEndTime = String.format(Locale.US, "%02d:%02d", endHour[0], endMinute[0]);

                    dialog.dismiss();
                    savePlannedActivityComplex(place, selectedDateMillis[0], timeSlot, formattedEndTime, priority, transit, budget, userNotes);
                });
            }

            dialog.show();
        }

        private void savePlannedActivityComplex(Place place, long dateMillis, String timeSlot, String endTime, String priority, String transitMode, double budget, String notes) {
            User user = sessionManager.getCurrentUser();
            if (user == null) return;

            com.cityscape.app.model.PlannedActivity activity = new com.cityscape.app.model.PlannedActivity(
                user.id, null, place.name, place.type != null ? place.type : "Atracție", place.imageUrl, dateMillis, timeSlot
            );
            activity.endTime = endTime;
            activity.priority = priority;
            activity.transitMode = transitMode;
            activity.notes = notes;
            activity.budget = budget;
            activity.currency = "RON";

            android.content.Context context = getContext();
            if (context == null) return;
            new Thread(() -> {
                com.cityscape.app.data.AppDatabase.getInstance(appContext).activityDao().insert(activity);
                com.cityscape.app.data.SupabaseSyncManager.getInstance(appContext).pushActivityToCloud(activity);
                com.cityscape.app.util.BadgeManager.addExperience(appContext, user.id, 75);
                com.cityscape.app.util.BadgeManager.awardBadge(appContext, user.id, "planner", "Master Planner", "Ai început să îți organizezi aventura!", "Planifică cel puțin o activitate în jurnal", "ic_badge_generic");
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (getContext() != null) Toast.makeText(getContext(), getString(R.string.added_to_plan), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        }

        private void fetchEvents() {
                if (currentLocation == null) return;
                User user = sessionManager.getCurrentUser();
                String interests = user != null && user.interests != null ? user.interests : "";

                String userId = sessionManager.getUserId();
                apiService.getEvents(currentLocation.getLatitude(), currentLocation.getLongitude(), 50, interests, userId, java.util.Locale.getDefault().getLanguage())
                                .enqueue(new Callback<List<com.cityscape.app.model.Event>>() {
                                        @Override
                                        public void onResponse(Call<List<com.cityscape.app.model.Event>> call,
                                                        Response<List<com.cityscape.app.model.Event>> response) {
                                                if (isAdded() && response.isSuccessful() && response.body() != null) {
                                                        List<com.cityscape.app.model.Event> events = response.body();
                                                        List<com.cityscape.app.model.Event> filteredEvents = new ArrayList<>();
                                                        for (com.cityscape.app.model.Event e : events) {
                                                                boolean mMusic = eventMusicFilter.equals("All") || eventMusicFilter.equals("Oricare") || 
                                                                                 (e.title != null && e.title.contains(eventMusicFilter)) || 
                                                                                 (e.musicType != null && e.musicType.contains(eventMusicFilter));
                                                                if (mMusic) filteredEvents.add(e);
                                                        }
                                                        applyIntentScoring(filteredEvents);
                                                        eventsList = filteredEvents;
                                                        updateEventsAdapter();
                                                }
                                        }

                                        @Override
                                        public void onFailure(Call<List<com.cityscape.app.model.Event>> call, Throwable t) {
                                                Log.e("HomeFragment", "Events fetch failed: " + t.getMessage(), t);
                                                if (isAdded()) {
                                                        android.widget.Toast.makeText(getContext(), getString(R.string.events_load_error), android.widget.Toast.LENGTH_SHORT).show();
                                                }
                                        }
                                });
        }

        private void applyIntentScoring(List<com.cityscape.app.model.Event> events) {
            User user = sessionManager.getCurrentUser();
            if (user == null) return;
            String interests = user.interests != null ? user.interests.toLowerCase() : "";
            
            java.util.Collections.sort(events, (e1, e2) -> {
                int score1 = 0; int score2 = 0;
                if (!interests.isEmpty()) {
                    for (String interest : interests.split(",")) {
                        String trimInt = interest.trim();
                        if (!trimInt.isEmpty()) {
                            if (e1.title.toLowerCase().contains(trimInt)) score1 += 50;
                            if (e2.title.toLowerCase().contains(trimInt)) score2 += 50;
                        }
                    }
                }
                if (score1 == score2) return e1.title.compareTo(e2.title);
                return Integer.compare(score2, score1);
            });
        }

        private void updateEventsAdapter() {
                if (binding == null) return;
                com.cityscape.app.adapter.EventAdapter adapter = new com.cityscape.app.adapter.EventAdapter(
                                eventsList, event -> {
                                Intent intent = new Intent(getContext(), com.cityscape.app.ui.home.EventDetailActivity.class);
                                intent.putExtra("event_json", new com.google.gson.Gson().toJson(event));
                                startActivity(intent);
                });
                User user = sessionManager.getCurrentUser();
                if (user != null && user.interests != null) {
                        adapter.setUserInterests(user.interests);
                }
                if (binding.recyclerEvents.getLayoutManager() == null) {
                        binding.recyclerEvents.setLayoutManager(
                                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                }
                binding.recyclerEvents.setAdapter(adapter);
        }

        private boolean isTypeMatchingInterests(Place place, String userInterests) {
                if (place == null || userInterests == null || userInterests.isEmpty()) return false;
                String pType = place.type != null ? place.type.toLowerCase() : "";
                String pName = place.name != null ? place.name.toLowerCase() : "";
                for (String interest : userInterests.split(",")) {
                        String interestLower = interest.trim().toLowerCase();
                        if (interestLower.isEmpty()) continue;
                        if (pType.contains(interestLower) || pName.contains(interestLower)) return true;
                        if ((interestLower.contains("muz") || interestLower.contains("mus") || interestLower.contains("artă")) && 
                            (pType.contains("museum") || pType.contains("art") || pType.contains("landmark") || pType.contains("culture"))) return true;
                        if ((interestLower.contains("rest") || interestLower.contains("food") || interestLower.contains("mânca")) && 
                            (pType.contains("restaurant") || pType.contains("food") || pType.contains("dining") || pType.contains("eat"))) return true;
                        if ((interestLower.contains("parc") || interestLower.contains("natur") || interestLower.contains("aer")) && 
                            (pType.contains("park") || pType.contains("nature") || pType.contains("garden") || pType.contains("outdoors"))) return true;
                        if ((interestLower.contains("caf") || interestLower.contains("cafe") || interestLower.contains("coffee")) && 
                            (pType.contains("cafe") || pType.contains("coffee") || pType.contains("bakery"))) return true;
                }
                return false;
        }



        private void loadDailyQuest() {
            if (currentLocation == null || !isAdded()) return;
            if (isManualLocation) {
                if (binding != null) binding.cardDailyQuest.setVisibility(View.GONE);
                return;
            }
            
            User user = sessionManager.getCurrentUser();
            String userId = user != null ? user.id : "";
            String interests = user != null ? (user.interests != null ? user.interests : "") : "";
            
            apiService.getDailyQuest(userId, currentLocation.getLatitude(), currentLocation.getLongitude(), interests, java.util.Locale.getDefault().getLanguage())
                .enqueue(new Callback<com.google.gson.JsonObject>() {
                    @Override
                    public void onResponse(Call<com.google.gson.JsonObject> call, Response<com.google.gson.JsonObject> response) {
                        if (response.isSuccessful() && response.body() != null && binding != null) {
                            try {
                                com.google.gson.JsonObject quest = response.body();
                                binding.cardDailyQuest.setVisibility(View.VISIBLE);
                                binding.textQuestTitle.setText(quest.get("title").getAsString());
                                binding.textQuestObjective.setText(quest.get("objective").getAsString());
                                binding.textQuestReward.setText(quest.get("reward").getAsString());
                                binding.textQuestReason.setText(quest.get("reason").getAsString());
                                
                                // Add a subtle animation
                                binding.cardDailyQuest.setAlpha(0f);
                                binding.cardDailyQuest.animate().alpha(1f).setDuration(500).start();
                            } catch (Exception e) {
                                Log.e("HomeFragment", "Quest parsing error: " + e.getMessage());
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<com.google.gson.JsonObject> call, Throwable t) {
                        Log.e("HomeFragment", "Quest failed: " + t.getMessage());
                    }
                });
        }

        @Override
        public void onMapReady(@NonNull com.google.android.gms.maps.GoogleMap map) {
            this.googleMap = map;
            try {
                // Style the map to be dark/premium
                boolean success = map.setMapStyle(com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style_dark));
                if (!success) Log.e("HomeFragment", "Style parsing failed.");
            } catch (Exception e) {
                Log.e("HomeFragment", "Can't find style. Error: ", e);
            }
            
            if (currentLocation != null) {
                com.google.android.gms.maps.model.LatLng latLng = new com.google.android.gms.maps.model.LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
                map.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(latLng, 14f));
            }
            
            loadHypeMap();
        }

        private void loadHypeMap() {
            if (googleMap == null || currentLocation == null) return;
            
            apiService.getHypeMap(currentLocation.getLatitude(), currentLocation.getLongitude()).enqueue(new Callback<List<Map<String, Object>>>() {
                @Override
                public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                    if (response.isSuccessful() && response.body() != null && googleMap != null) {
                        List<Map<String, Object>> points = response.body();
                        googleMap.clear();
                        
                        for (Map<String, Object> point : points) {
                            double lat = (double) point.get("lat");
                            double lng = (double) point.get("lng");
                            String source = (String) point.get("source");
                            
                            float hue = source.equals("post") ? com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_VIOLET : com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_AZURE;
                            
                            googleMap.addMarker(new com.google.android.gms.maps.model.MarkerOptions()
                                .position(new com.google.android.gms.maps.model.LatLng(lat, lng))
                                .title(source.equals("post") ? "Postare Nouă" : "Vizită Recentă")
                                .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(hue)));
                        }
                    }
                }

                @Override
                public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                    Log.e("HomeFragment", "Hype fetch failed", t);
                }
            });
        }

        @Override
        public void onResume() {
            super.onResume();
            if (!isManualLocation) {
                startLocationUpdates();
            }
            // Refresh live battle widget every time user comes back to home
            loadHomeBattleWidget();
        }

        @Override
        public void onPause() {
            super.onPause();
            stopLocationUpdates();
        }

        @Override
        public void onDestroyView() {
                if (textToSpeech != null) {
                    textToSpeech.stop();
                    textToSpeech.shutdown();
                    textToSpeech = null;
                }
                stopLocationUpdates();
                recommendedAdapter = null;
                nearYouAdapter = null;
                aiPicksAdapter = null;
                super.onDestroyView();
                binding = null;
        }

        private void initTextToSpeech() {
            if (textToSpeech == null && getContext() != null) {
                textToSpeech = new android.speech.tts.TextToSpeech(getContext().getApplicationContext(), status -> {
                    if (status == android.speech.tts.TextToSpeech.SUCCESS && textToSpeech != null) {
                        Locale roLocale = new Locale("ro", "RO");
                        int result = textToSpeech.setLanguage(roLocale);
                        
                        boolean voiceFound = false;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            try {
                                java.util.Set<android.speech.tts.Voice> voices = textToSpeech.getVoices();
                                if (voices != null) {
                                    for (android.speech.tts.Voice voice : voices) {
                                        if (voice.getLocale() != null && 
                                            (voice.getLocale().getLanguage().equals("ro") || 
                                             voice.getLocale().toString().toLowerCase().contains("ro"))) {
                                            textToSpeech.setVoice(voice);
                                            voiceFound = true;
                                            break;
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                Log.e("HomeFragment", "Error setting explicit Romanian voice", e);
                            }
                        }
                        
                        if (result == android.speech.tts.TextToSpeech.LANG_MISSING_DATA ||
                            result == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                            
                            int generalRo = textToSpeech.setLanguage(new Locale("ro"));
                            if (generalRo == android.speech.tts.TextToSpeech.LANG_MISSING_DATA ||
                                generalRo == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                                
                                textToSpeech.setLanguage(Locale.US);
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        Toast.makeText(getContext(), "Ghidul audio citește cu accent deoarece limba Română lipsește din setările TTS ale telefonului. Puteți instala pachetul în limba Română din setările Android (Text-to-speech).", Toast.LENGTH_LONG).show();
                                    });
                                }
                            }
                        }
                    }
                });
            }
        }

        private void initLocationCallback() {
            locationCallback = new com.google.android.gms.location.LocationCallback() {
                @Override
                public void onLocationResult(@NonNull com.google.android.gms.location.LocationResult locationResult) {
                    for (Location location : locationResult.getLocations()) {
                        if (location != null) {
                            currentLocation = location;
                            actualGpsLocation = location;
                            checkGeofencingQuests(location);
                        }
                    }
                }
            };
        }

        @SuppressLint("MissingPermission")
        private void startLocationUpdates() {
            if (getContext() == null || fusedLocationClient == null || locationCallback == null) return;
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                com.google.android.gms.location.LocationRequest locationRequest = new com.google.android.gms.location.LocationRequest.Builder(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 5000)
                        .setMinUpdateIntervalMillis(2000)
                        .setMinUpdateDistanceMeters(2)
                        .build();
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, android.os.Looper.getMainLooper());
            }
        }

        private void stopLocationUpdates() {
            if (fusedLocationClient != null && locationCallback != null) {
                fusedLocationClient.removeLocationUpdates(locationCallback);
            }
        }

        private void checkGeofencingQuests(Location location) {
            if (location == null) return;
            List<Place> allPlacesToCheck = new ArrayList<>();
            if (aiPicksList != null) allPlacesToCheck.addAll(aiPicksList);
            if (nearbyPlacesList != null) allPlacesToCheck.addAll(nearbyPlacesList);
            if (allPlacesList != null) allPlacesToCheck.addAll(allPlacesList);
            
            for (Place place : allPlacesToCheck) {
                if (place == null || place.name == null) continue;
                String placeId = place.id != null ? place.id : place.name;
                if (discoveredPlaceIds.contains(placeId)) continue;
                
                double placeLat = place.latitude;
                double placeLng = place.longitude;
                if (placeLat == 0 && placeLng == 0) continue;
                
                float[] results = new float[1];
                Location.distanceBetween(location.getLatitude(), location.getLongitude(), placeLat, placeLng, results);
                float distance = results[0];
                
                if (distance < 50) { // Sub 50 de metri
                    discoveredPlaceIds.add(placeId);
                    triggerQuestDiscovery(place);
                    break;
                }
            }
        }

        private void triggerQuestDiscovery(Place place) {
            if (!isAdded() || getContext() == null) return;
            
            User user = sessionManager.getCurrentUser();
            if (user == null) return;
            
            com.cityscape.app.util.BadgeManager.addExperience(appContext, user.id, 200);
            com.cityscape.app.util.BadgeManager.awardBadge(appContext, user.id, "explorer", "Master Explorer", 
                    "Ai descoperit o locație recomandată de AI în apropiere!", 
                    "Apropie-te la mai puțin de 50 de metri de o locație recomandată", 
                    "ic_badge_explorer");
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme);
                    View v = getLayoutInflater().inflate(R.layout.dialog_quest_discovery, null);
                    builder.setView(v);
                    
                    TextView txtTitle = v.findViewById(R.id.txt_quest_discovery_title);
                    TextView txtMessage = v.findViewById(R.id.txt_quest_discovery_message);
                    Button btnClaim = v.findViewById(R.id.btn_claim_quest);
                    
                    if (txtTitle != null) {
                        txtTitle.setText("🎉 RECOMPENSĂ DEBLOCATĂ!");
                    }
                    if (txtMessage != null) {
                        txtMessage.setText("Felicitări! Ai ajuns la " + place.name + " (" + (place.type != null ? place.type : "Atracție") + ") și ai finalizat Quest-ul de proximitate!\n\n🎒 Ai primit +200 XP și insigna Master Explorer! 🏆");
                    }
                    
                    androidx.appcompat.app.AlertDialog dialog = builder.create();
                    if (dialog.getWindow() != null) {
                        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
                    }
                    
                    if (btnClaim != null) {
                        btnClaim.setOnClickListener(view -> {
                            dialog.dismiss();
                            Toast.makeText(getContext(), getString(R.string.xp_badge_profile), Toast.LENGTH_LONG).show();
                        });
                    }
                    
                    dialog.show();
                });
            }
        }

        private void checkWeatherPlanB() {
            if (!isAdded()) return;
            if (currentLocation == null) {
                Toast.makeText(getContext(), getString(R.string.location_awaiting), Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(getContext(), getString(R.string.analyzing_weather), Toast.LENGTH_SHORT).show();

            apiService.getWeatherPlanB(currentLocation.getLatitude(), currentLocation.getLongitude()).enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
                @Override
                public void onResponse(retrofit2.Call<com.google.gson.JsonObject> call, retrofit2.Response<com.google.gson.JsonObject> response) {
                    if (isAdded() && response.isSuccessful() && response.body() != null) {
                        com.google.gson.JsonObject res = response.body();
                        String status = res.get("status").getAsString();
                        String message = res.get("message").getAsString();

                        if ("fine".equals(status)) {
                            new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                                    .setTitle("☀️ Vreme Perfectă!")
                                    .setMessage(message)
                                    .setPositiveButton("Minunat!", null)
                                    .show();
                        } else {
                            com.google.gson.JsonArray alts = res.getAsJsonArray("alternatives");
                            if (alts == null || alts.size() == 0) {
                                Toast.makeText(getContext(), getString(R.string.no_nearby_alternatives), Toast.LENGTH_SHORT).show();
                                return;
                            }

                            List<Place> alternativesList = new ArrayList<>();
                            for (com.google.gson.JsonElement el : alts) {
                                com.google.gson.JsonObject o = el.getAsJsonObject();
                                Place p = new Place();
                                p.name = o.get("name").getAsString();
                                p.type = o.has("type") && !o.get("type").isJsonNull() ? o.get("type").getAsString() : "Alternative Indoor";
                                p.address = o.has("address") && !o.get("address").isJsonNull() ? o.get("address").getAsString() : "În apropiere";
                                p.rating = o.has("rating") && !o.get("rating").isJsonNull() ? (float) o.get("rating").getAsDouble() : 4.5f;
                                p.imageUrl = o.has("image_url") && !o.get("image_url").isJsonNull() ? o.get("image_url").getAsString() : "";
                                p.latitude = o.has("latitude") && !o.get("latitude").isJsonNull() ? o.get("latitude").getAsDouble() : 0.0;
                                p.longitude = o.has("longitude") && !o.get("longitude").isJsonNull() ? o.get("longitude").getAsDouble() : 0.0;
                                p.aiSuggestion = o.has("description") && !o.get("description").isJsonNull() ? o.get("description").getAsString() : "Recomandat ca alternativă excelentă de interior.";
                                alternativesList.add(p);
                            }

                            String[] itemsNames = new String[alternativesList.size()];
                            for (int i = 0; i < alternativesList.size(); i++) {
                                itemsNames[i] = alternativesList.get(i).name + " (" + alternativesList.get(i).type.toUpperCase() + ")";
                            }

                            new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                                    .setTitle("☔ Plan B Weather SOS!")
                                    .setMessage(message + "\n\nAlege o alternativă indoor de mai jos pentru a o vizualiza și planifica:")
                                    .setItems(itemsNames, (dialogSub, index) -> {
                                        Place selectedAlt = alternativesList.get(index);
                                        showPlaceDetailDialog(selectedAlt);
                                        com.cityscape.app.util.BadgeManager.addExperience(appContext, sessionManager.getUserId(), 50);
                                    })
                                    .setPositiveButton("Închide", null)
                                    .show();
                        }
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<com.google.gson.JsonObject> call, Throwable t) {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Eroare de conexiune la serverul meteo.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        private void setupHypeBattle() {
            // Load live battle data into home widget
            loadHomeBattleWidget();
        }

        private void loadHomeBattleWidget() {
            View root = binding != null ? binding.getRoot() : null;
            if (root == null) return;

            android.widget.TextView nameAView  = root.findViewById(R.id.home_battle_name_a);
            android.widget.TextView nameBView  = root.findViewById(R.id.home_battle_name_b);
            android.widget.TextView pctAView   = root.findViewById(R.id.home_battle_pct_a);
            android.widget.TextView pctBView   = root.findViewById(R.id.home_battle_pct_b);
            android.widget.ProgressBar progA   = root.findViewById(R.id.home_battle_progress_a);
            android.widget.ProgressBar progB   = root.findViewById(R.id.home_battle_progress_b);
            android.widget.TextView leaderView = root.findViewById(R.id.txt_home_battle_leader);
            com.google.android.material.button.MaterialButton voteABtn = root.findViewById(R.id.btn_home_vote_a);
            com.google.android.material.button.MaterialButton voteBBtn = root.findViewById(R.id.btn_home_vote_b);
            View cardView = root.findViewById(R.id.card_home_live_battle);

            if (isManualLocation) {
                if (combinedPlacesList.size() >= 2) {
                    String city = sessionManager.getPreferredCity();
                    if (city == null || city.isEmpty()) city = "Oraș";
                    
                    int hash = Math.abs(city.hashCode());
                    int indexA = hash % combinedPlacesList.size();
                    int indexB = (indexA + 1) % combinedPlacesList.size();
                    
                    final Place pA = combinedPlacesList.get(indexA);
                    final Place pB = combinedPlacesList.get(indexB);
                    
                    final String placeAId = pA.id;
                    final String nameA = pA.name;
                    final String placeBId = pB.id;
                    final String nameB = pB.name;
                    
                    double ratingA = pA.rating > 0 ? pA.rating : 4.0;
                    double ratingB = pB.rating > 0 ? pB.rating : 4.0;
                    final double pctA = Math.round((ratingA / (ratingA + ratingB)) * 100);
                    final double pctB = 100 - pctA;
                    
                    final String battleId = "local_" + city.replaceAll("\\s+", "_") + "_" + new java.text.SimpleDateFormat("yyyy_MM_dd", java.util.Locale.US).format(new java.util.Date());
                    android.content.SharedPreferences prefs = requireContext().getSharedPreferences("local_battles", android.content.Context.MODE_PRIVATE);
                    final String userChoice = prefs.getString("local_battle_choice_" + battleId, "");
                    
                    if (nameAView != null) nameAView.setText(nameA);
                    if (nameBView != null) nameBView.setText(nameB);
                    if (pctAView  != null) pctAView.setText(String.format(java.util.Locale.US, "%.0f%%", pctA));
                    if (pctBView  != null) pctBView.setText(String.format(java.util.Locale.US, "%.0f%%", pctB));
                    if (progA     != null) progA.setProgress((int) pctA);
                    if (progB     != null) progB.setProgress((int) pctB);
                    
                    if (leaderView != null) {
                        if (userChoice.isEmpty()) {
                            leaderView.setText("⚔️ Alege favoritul tău în " + city + "!");
                        } else {
                            String choiceName = placeAId.equals(userChoice) ? nameA : nameB;
                            leaderView.setText("👑 Ai votat: " + choiceName + "!");
                        }
                    }
                    
                    if (cardView != null) {
                        cardView.setVisibility(View.VISIBLE);
                        cardView.setOnClickListener(null);
                    }
                    
                    if (voteABtn != null) {
                        voteABtn.setText(placeAId.equals(userChoice) ? "⭐ Votat" : getString(R.string.vote));
                        voteABtn.setEnabled(userChoice.isEmpty());
                        voteABtn.setOnClickListener(v2 -> {
                            prefs.edit().putString("local_battle_choice_" + battleId, placeAId).apply();
                            playClashAnimation((ViewGroup) root, () -> {
                                com.cityscape.app.util.BadgeManager.addExperience(requireContext(), sessionManager.getUserId(), 50);
                                Toast.makeText(getContext(), getString(R.string.vote_registered), Toast.LENGTH_SHORT).show();
                                loadHomeBattleWidget();
                            });
                        });
                    }
                    
                    if (voteBBtn != null) {
                        voteBBtn.setText(placeBId.equals(userChoice) ? "⭐ Votat" : getString(R.string.vote));
                        voteBBtn.setEnabled(userChoice.isEmpty());
                        voteBBtn.setOnClickListener(v2 -> {
                            prefs.edit().putString("local_battle_choice_" + battleId, placeBId).apply();
                            playClashAnimation((ViewGroup) root, () -> {
                                com.cityscape.app.util.BadgeManager.addExperience(requireContext(), sessionManager.getUserId(), 50);
                                Toast.makeText(getContext(), getString(R.string.vote_registered), Toast.LENGTH_SHORT).show();
                                loadHomeBattleWidget();
                            });
                        });
                    }
                } else {
                    if (cardView != null) cardView.setVisibility(View.GONE);
                }
                return;
            }

            String userId = sessionManager.getUserId();
            if (userId == null || userId.isEmpty()) userId = "anon";

            apiService.getHypeBattle(userId).enqueue(new Callback<com.google.gson.JsonObject>() {
                @Override
                public void onResponse(Call<com.google.gson.JsonObject> call, Response<com.google.gson.JsonObject> response) {
                    if (!isAdded() || binding == null) return;
                    if (!response.isSuccessful() || response.body() == null) return;

                    com.google.gson.JsonObject battle = response.body();

                    final String placeAId = battle.has("place_a_id")   ? battle.get("place_a_id").getAsString()   : "";
                    final String nameA    = battle.has("place_a_name") ? battle.get("place_a_name").getAsString() : "Locație A";
                    final double pctA     = battle.has("pct_a")        ? battle.get("pct_a").getAsDouble()        : 50.0;

                    final String placeBId = battle.has("place_b_id")   ? battle.get("place_b_id").getAsString()   : "";
                    final String nameB    = battle.has("place_b_name") ? battle.get("place_b_name").getAsString() : "Locație B";
                    final double pctB     = battle.has("pct_b")        ? battle.get("pct_b").getAsDouble()        : 50.0;

                    final String userChoice     = battle.has("user_choice") && !battle.get("user_choice").isJsonNull()
                                                  ? battle.get("user_choice").getAsString() : "";
                    final String mostVotedName  = battle.has("most_voted_name") ? battle.get("most_voted_name").getAsString() : "";
                    final double mostVotedPct   = battle.has("most_voted_pct")  ? battle.get("most_voted_pct").getAsDouble()  : 50.0;
                    final String battleId       = battle.has("id") ? battle.get("id").getAsString() : "";

                    if (nameAView != null) nameAView.setText(nameA);
                    if (nameBView != null) nameBView.setText(nameB);
                    if (pctAView  != null) pctAView.setText(String.format(java.util.Locale.US, "%.0f%%", pctA));
                    if (pctBView  != null) pctBView.setText(String.format(java.util.Locale.US, "%.0f%%", pctB));
                    if (progA     != null) progA.setProgress((int) pctA);
                    if (progB     != null) progB.setProgress((int) pctB);

                    if (leaderView != null) {
                        if ("Egalitate".equals(mostVotedName) || mostVotedName.isEmpty()) {
                            leaderView.setText("⚔️ Este egalitate acum! (50%)");
                        } else {
                            leaderView.setText("👑 Favorit: " + mostVotedName
                                + " (" + String.format(java.util.Locale.US, "%.0f%%", mostVotedPct) + ")");
                        }
                    }

                    if (cardView != null) {
                        cardView.setVisibility(View.VISIBLE);
                        cardView.setOnClickListener(v2 -> showHypeBattleDialog());
                    }

                    if (voteABtn != null) {
                        voteABtn.setText(placeAId.equals(userChoice) ? "⭐ Votat" : getString(R.string.vote));
                        voteABtn.setEnabled(true);
                        voteABtn.setOnClickListener(v2 -> {
                            v2.setEnabled(false); // previne dublu-click
                            ViewGroup animRoot = (ViewGroup) root;
                            playClashAnimation(animRoot, () -> submitHomeVote(placeAId, battleId, nameA, nameB));
                        });
                    }

                    if (voteBBtn != null) {
                        voteBBtn.setText(placeBId.equals(userChoice) ? "⭐ Votat" : getString(R.string.vote));
                        voteBBtn.setEnabled(true);
                        voteBBtn.setOnClickListener(v2 -> {
                            v2.setEnabled(false); // previne dublu-click
                            ViewGroup animRoot = (ViewGroup) root;
                            playClashAnimation(animRoot, () -> submitHomeVote(placeBId, battleId, nameA, nameB));
                        });
                    }

                }

                @Override
                public void onFailure(Call<com.google.gson.JsonObject> call, Throwable t) {
                    Log.e("HomeFragment", "Battle widget load failed: " + t.getMessage());
                }
            });
        }

        private void submitHomeVote(String placeId, String battleId, String nameA, String nameB) {
            if (!isAdded() || binding == null) return;

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("user_id", sessionManager.getUserId());
            payload.put("place_id", placeId);
            payload.put("battle_id", battleId);

            apiService.castHypeVote(payload).enqueue(new Callback<com.google.gson.JsonObject>() {
                @Override
                public void onResponse(Call<com.google.gson.JsonObject> call, Response<com.google.gson.JsonObject> response) {
                    if (!isAdded() || binding == null) return;
                    if (response.isSuccessful() && response.body() != null) {
                        com.google.gson.JsonObject res = response.body();

                        // Show Snackbar with XP confirmation
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.getRoot(),
                            "🎉 Vot înregistrat! +50 XP câștigat!",
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                        ).setBackgroundTint(android.graphics.Color.parseColor("#10B981"))
                         .setTextColor(android.graphics.Color.WHITE)
                         .show();

                        // Refresh widget with new vote counts
                        double newPctA = res.has("pct_a") ? res.get("pct_a").getAsDouble() : 50.0;
                        double newPctB = res.has("pct_b") ? res.get("pct_b").getAsDouble() : 50.0;
                        String newMostVoted = res.has("most_voted_name") ? res.get("most_voted_name").getAsString() : "";
                        double newMostPct   = res.has("most_voted_pct")  ? res.get("most_voted_pct").getAsDouble()  : 50.0;
                        String newChoice    = res.has("user_choice")     ? res.get("user_choice").getAsString()     : placeId;

                        View root = binding.getRoot();
                        android.widget.TextView pctAView   = root.findViewById(R.id.home_battle_pct_a);
                        android.widget.TextView pctBView   = root.findViewById(R.id.home_battle_pct_b);
                        android.widget.ProgressBar progA   = root.findViewById(R.id.home_battle_progress_a);
                        android.widget.ProgressBar progB   = root.findViewById(R.id.home_battle_progress_b);

                        com.google.android.material.button.MaterialButton voteABtn = root.findViewById(R.id.btn_home_vote_a);


                        if (pctAView != null) pctAView.setText(String.format(java.util.Locale.US, "%.0f%%", newPctA));
                        if (pctBView != null) pctBView.setText(String.format(java.util.Locale.US, "%.0f%%", newPctB));
                        if (progA    != null) progA.setProgress((int) newPctA);
                        if (progB    != null) progB.setProgress((int) newPctB);



                        // Update button labels to reflect new vote
                        // We need to know which is A and B — re-load for accuracy
                        loadHomeBattleWidget();

                    } else {
                        boolean isEn = "en".equals(java.util.Locale.getDefault().getLanguage());
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.getRoot(), isEn ? "Voting error. Please try again." : "Eroare la votare. Încearcă din nou.",
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        ).show();
                    }
                }

                @Override
                public void onFailure(Call<com.google.gson.JsonObject> call, Throwable t) {
                    if (isAdded() && binding != null) {
                        boolean isEn = "en".equals(java.util.Locale.getDefault().getLanguage());
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.getRoot(), isEn ? "Network error. Vote was not submitted." : "Eroare de rețea. Votul nu a fost transmis.",
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        ).show();
                    }
                }
            });
        }


        private void showHypeBattleDialog() {
            if (getContext() == null || !isAdded()) return;

            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme);
            View v = getLayoutInflater().inflate(R.layout.dialog_hype_battle, null);
            builder.setView(v);

            androidx.appcompat.app.AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            }

            TextView txtTimer = v.findViewById(R.id.hype_timer);
            
            // Place A views
            ImageView imgA = v.findViewById(R.id.hype_img_a);
            TextView txtNameA = v.findViewById(R.id.hype_name_a);
            TextView txtTypeA = v.findViewById(R.id.hype_type_a);
            ProgressBar progressA = v.findViewById(R.id.hype_progress_a);
            TextView txtPctA = v.findViewById(R.id.hype_pct_a);
            com.google.android.material.button.MaterialButton btnVoteA = v.findViewById(R.id.hype_btn_vote_a);
            TextView txtReviewA = v.findViewById(R.id.hype_review_text_a);
            TextView txtReviewAuthorA = v.findViewById(R.id.hype_review_author_a);

            // Place B views
            ImageView imgB = v.findViewById(R.id.hype_img_b);
            TextView txtNameB = v.findViewById(R.id.hype_name_b);
            TextView txtTypeB = v.findViewById(R.id.hype_type_b);
            ProgressBar progressB = v.findViewById(R.id.hype_progress_b);
            TextView txtPctB = v.findViewById(R.id.hype_pct_b);
            com.google.android.material.button.MaterialButton btnVoteB = v.findViewById(R.id.hype_btn_vote_b);
            TextView txtReviewB = v.findViewById(R.id.hype_review_text_b);
            TextView txtReviewAuthorB = v.findViewById(R.id.hype_review_author_b);

            // Leaderboard and winner views
            android.widget.LinearLayout leaderboardContainer = v.findViewById(R.id.battle_leaderboard_container);
            TextView txtWinnerValue = v.findViewById(R.id.txt_battle_winner_value);

            com.google.android.material.button.MaterialButton btnClose = v.findViewById(R.id.hype_btn_close);

            if (btnClose != null) {
                btnClose.setOnClickListener(v1 -> dialog.dismiss());
            }

            apiService.getHypeBattle(sessionManager.getUserId()).enqueue(new Callback<com.google.gson.JsonObject>() {
                @Override
                public void onResponse(Call<com.google.gson.JsonObject> call, Response<com.google.gson.JsonObject> response) {
                    if (isAdded() && response.isSuccessful() && response.body() != null) {
                        com.google.gson.JsonObject battle = response.body();

                        String id = battle.get("id").getAsString();
                        String placeAId = battle.get("place_a_id").getAsString();
                        String nameA = battle.get("place_a_name").getAsString();
                        String typeA = battle.get("place_a_type").getAsString();
                        String imageA = battle.get("place_a_image").getAsString();
                        double pctA = battle.get("pct_a").getAsDouble();
                        
                        String reviewA = battle.has("place_a_review") ? battle.get("place_a_review").getAsString() : "";
                        String authorA = battle.has("place_a_review_author") ? battle.get("place_a_review_author").getAsString() : "";

                        String placeBId = battle.get("place_b_id").getAsString();
                        String nameB = battle.get("place_b_name").getAsString();
                        String typeB = battle.get("place_b_type").getAsString();
                        String imageB = battle.get("place_b_image").getAsString();
                        double pctB = battle.get("pct_b").getAsDouble();
                        
                        String reviewB = battle.has("place_b_review") ? battle.get("place_b_review").getAsString() : "";
                        String authorB = battle.has("place_b_review_author") ? battle.get("place_b_review_author").getAsString() : "";

                        String userChoice = battle.has("user_choice") && !battle.get("user_choice").isJsonNull() ? battle.get("user_choice").getAsString() : "";
                        int secondsLeft = battle.get("seconds_left").getAsInt();

                        if (txtNameA != null) txtNameA.setText(nameA);
                        if (txtTypeA != null) txtTypeA.setText(typeA.toUpperCase());
                        if (txtPctA != null) txtPctA.setText(String.format(java.util.Locale.US, "%.1f%%", pctA));
                        if (progressA != null) progressA.setProgress((int) pctA);
                        if (txtReviewA != null) txtReviewA.setText("\"" + reviewA + "\"");
                        if (txtReviewAuthorA != null) txtReviewAuthorA.setText("— " + authorA);

                        if (txtNameB != null) txtNameB.setText(nameB);
                        if (txtTypeB != null) txtTypeB.setText(typeB.toUpperCase());
                        if (txtPctB != null) txtPctB.setText(String.format(java.util.Locale.US, "%.1f%%", pctB));
                        if (progressB != null) progressB.setProgress((int) pctB);
                        if (txtReviewB != null) txtReviewB.setText("\"" + reviewB + "\"");
                        if (txtReviewAuthorB != null) txtReviewAuthorB.setText("— " + authorB);

                        if (imgA != null && imageA != null && !imageA.isEmpty()) {
                            com.bumptech.glide.Glide.with(HomeFragment.this)
                                .load(imageA)
                                .centerCrop()
                                .placeholder(R.drawable.placeholder_place)
                                .into(imgA);
                        }
                        if (imgB != null && imageB != null && !imageB.isEmpty()) {
                            com.bumptech.glide.Glide.with(HomeFragment.this)
                                .load(imageB)
                                .centerCrop()
                                .placeholder(R.drawable.placeholder_place)
                                .into(imgB);
                        }

                        if (txtTimer != null) {
                            int hours = secondsLeft / 3600;
                            int minutes = (secondsLeft % 3600) / 60;
                            txtTimer.setText(String.format(java.util.Locale.getDefault(), "⏳ Se încheie în: %02d ore %02d min", hours, minutes));
                        }

                        // Populating the Dynamic Explorer Leaderboard
                        if (leaderboardContainer != null) {
                            leaderboardContainer.removeAllViews();
                            if (battle.has("leaderboard") && battle.get("leaderboard").isJsonArray()) {
                                com.google.gson.JsonArray lb = battle.getAsJsonArray("leaderboard");
                                for (int i = 0; i < lb.size(); i++) {
                                    com.google.gson.JsonObject u = lb.get(i).getAsJsonObject();
                                    String name = u.get("name").getAsString();
                                    int xp = u.get("total_xp").getAsInt();

                                    android.widget.LinearLayout row = new android.widget.LinearLayout(requireContext());
                                    row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                                    row.setPadding(0, 10, 0, 10);

                                    TextView txtRank = new TextView(requireContext());
                                    String medal = "";
                                    if (i == 0) medal = "🥇 ";
                                    else if (i == 1) medal = "🥈 ";
                                    else if (i == 2) medal = "🥉 ";
                                    else medal = " " + (i + 1) + ". ";
                                    txtRank.setText(medal);
                                    txtRank.setTextSize(13);
                                    txtRank.setPadding(0, 0, 12, 0);

                                    TextView txtUserName = new TextView(requireContext());
                                    txtUserName.setText(name);
                                    txtUserName.setTextColor(getResources().getColor(R.color.app_text_primary));
                                    txtUserName.setTextSize(12);
                                    txtUserName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                                    txtUserName.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                                    TextView txtUserXp = new TextView(requireContext());
                                    txtUserXp.setText(xp + " XP");
                                    txtUserXp.setTextColor(getResources().getColor(R.color.primary));
                                    txtUserXp.setTextSize(11);
                                    txtUserXp.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

                                    row.addView(txtRank);
                                    row.addView(txtUserName);
                                    row.addView(txtUserXp);

                                    if (i > 0) {
                                        View div = new View(requireContext());
                                        div.setLayoutParams(new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                                        div.setBackgroundColor(getResources().getColor(R.color.app_divider));
                                        leaderboardContainer.addView(div);
                                    }
                                    leaderboardContainer.addView(row);
                                }
                            }
                        }

                        // Binding the real-time winner / most voted location
                        String winnerName = battle.has("most_voted_name") ? battle.get("most_voted_name").getAsString() : "";
                        double winnerPct = battle.has("most_voted_pct") ? battle.get("most_voted_pct").getAsDouble() : 50.0;
                        if (txtWinnerValue != null) {
                            if (winnerName.equals("Egalitate")) {
                                txtWinnerValue.setText("Egalitate temporară! (50.0%)");
                            } else {
                                txtWinnerValue.setText(winnerName + " (" + String.format(java.util.Locale.US, "%.1f%%", winnerPct) + ")");
                            }
                        }

                        // High-interaction voting: buttons are ALWAYS active and click-able so the user can switch sides and test the boxing clash animation infinitely!
                        if (btnVoteA != null) {
                            btnVoteA.setText(userChoice.equals(placeAId) ? "⭐️ Votat!" : getString(R.string.choose));
                            btnVoteA.setEnabled(true);
                            btnVoteA.setOnClickListener(v2 -> castVote(dialog, placeAId, id));
                        }
                        if (btnVoteB != null) {
                            btnVoteB.setText(userChoice.equals(placeBId) ? "⭐️ Votat!" : getString(R.string.choose));
                            btnVoteB.setEnabled(true);
                            btnVoteB.setOnClickListener(v2 -> castVote(dialog, placeBId, id));
                        }
                    } else {
                        Toast.makeText(getContext(), getString(R.string.battles_offline), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                }

                @Override
                public void onFailure(Call<com.google.gson.JsonObject> call, Throwable t) {
                    Toast.makeText(getContext(), getString(R.string.battle_server_error), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            });

            dialog.show();
        }

        private void playClashAnimation(ViewGroup rootView, Runnable onEnd) {
            if (getContext() == null || rootView == null) {
                onEnd.run();
                return;
            }

            // SIMPLES: Quick flash + run callback immediately (NO fancy animations)
            android.widget.FrameLayout flashView = new android.widget.FrameLayout(requireContext());
            flashView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            flashView.setBackgroundColor(android.graphics.Color.parseColor("#30FFFFFF"));  // Light white flash
            rootView.addView(flashView);

            // Quick fade out in 200ms and remove
            flashView.animate()
                .alpha(0f)
                .setDuration(200)
                .setListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        rootView.removeView(flashView);
                        onEnd.run();  // Run vote immediately after flash
                    }
                });
        }

        private void castVote(androidx.appcompat.app.AlertDialog dialog, String placeId, String battleId) {
            if (dialog.getWindow() == null) return;
            playClashAnimation((ViewGroup) dialog.getWindow().getDecorView(), () -> {
                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("user_id", sessionManager.getUserId());
                payload.put("place_id", placeId);
                payload.put("battle_id", battleId);

                apiService.castHypeVote(payload).enqueue(new Callback<com.google.gson.JsonObject>() {
                    @Override
                    public void onResponse(Call<com.google.gson.JsonObject> call, Response<com.google.gson.JsonObject> response) {
                        if (isAdded()) {
                            if (response.isSuccessful()) {
                                Toast.makeText(getContext(), getString(R.string.vote_registered), Toast.LENGTH_LONG).show();
                                new Thread(() -> {
                                    try {
                                        sessionManager.unlockBadge("hype_master");
                                    } catch (Exception e) {
                                        Log.e("HomeFragment", "Badge unlock error", e);
                                    }
                                }).start();
                                dialog.dismiss();
                                showHypeBattleDialog();
                            } else {
                                try {
                                    String err = response.errorBody() != null ? response.errorBody().string() : "Eroare la vot";
                                    Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
                                } catch (Exception e) {
                                    Toast.makeText(getContext(), getString(R.string.vote_declined), Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<com.google.gson.JsonObject> call, Throwable t) {
                        Toast.makeText(getContext(), getString(R.string.vote_transmission_error), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        private void setupSeeAllButtons() {
            View root = binding.getRoot();
            View btnSeeAllNearYou = root.findViewById(R.id.btn_see_all_near_you);
            if (btnSeeAllNearYou != null) {
                btnSeeAllNearYou.setOnClickListener(v -> {
                    try {
                        androidx.navigation.Navigation.findNavController(root).navigate(R.id.navigation_map);
                    } catch (Exception e) {
                        Log.e("HomeFragment", "Navigation to map failed", e);
                    }
                });
            }

            View btnSeeAllVisited = root.findViewById(R.id.btn_see_all_visited);
            if (btnSeeAllVisited != null) {
                btnSeeAllVisited.setOnClickListener(v -> {
                    try {
                        androidx.navigation.Navigation.findNavController(root).navigate(R.id.navigation_profile);
                    } catch (Exception e) {
                        Log.e("HomeFragment", "Navigation to profile failed", e);
                    }
                });
            }

            View btnSeeAllEvents = root.findViewById(R.id.btn_see_all_events);
            if (btnSeeAllEvents != null) {
                btnSeeAllEvents.setOnClickListener(v -> {
                    try {
                        androidx.navigation.Navigation.findNavController(root).navigate(R.id.navigation_calendar);
                    } catch (Exception e) {
                        Log.e("HomeFragment", "Navigation to calendar failed", e);
                    }
                });
            }

            View btnSeeAllTrending = root.findViewById(R.id.btn_see_all_trending);
            if (btnSeeAllTrending != null) {
                btnSeeAllTrending.setOnClickListener(v -> {
                    try {
                        androidx.navigation.Navigation.findNavController(root).navigate(R.id.navigation_feed);
                    } catch (Exception e) {
                        Log.e("HomeFragment", "Navigation to feed failed", e);
                    }
                });
            }
        }
}
