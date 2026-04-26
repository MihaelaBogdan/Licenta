package com.cityscape.app.ui.home;

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

public class HomeFragment extends Fragment {

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

                setupGreeting();
                setupRecyclers();
                setupCategoryChips();
                setupSearch();
                setupItineraryButton();
                setupCrystalBall();
                setupLocationReset();
                
                binding.textLocation.setOnClickListener(v -> showCitySelectionDialog());
                checkLocationPermission();
                showPreviousSessionPromptIfAvailable();

                return binding.getRoot();
        }

        private void showPreviousSessionPromptIfAvailable() {
            String preferredCity = sessionManager.getPreferredCity();
            if (preferredCity != null && !preferredCity.isEmpty()) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.DarkDialogTheme)
                    .setTitle("Sesiune anterioară")
                    .setMessage("Te-ai întors! Vrei să continui explorarea în " + preferredCity + "?")
                    .setPositiveButton("Da", (dialog, which) -> {
                        searchForCity(preferredCity);
                    })
                    .setNegativeButton("Nu, rămân aici", (dialog, which) -> {
                        sessionManager.setPreferredCity(null);
                        updateFilters();
                    })
                    .show();
            }
        }

        private void setupLocationReset() {
            if (binding.btnResetLocation != null) {
                binding.btnResetLocation.setOnClickListener(v -> {
                    isManualLocation = false;
                    sessionManager.setPreferredCity(null);
                    Toast.makeText(getContext(), "Revenire la locația GPS...", Toast.LENGTH_SHORT).show();
                    checkLocationPermission();
                });
            }
        }

        private void setupItineraryButton() {
                binding.btnPlanDay.setOnClickListener(v -> {
                        Log.d("HomeFragment", "Itinerary button clicked");
                        if (currentLocation == null) {
                                Toast.makeText(getContext(), "Locația nu este disponibilă încă. Încercăm să o găsim...",
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

                                int budget = (int) budgetSlider.getValue();
                                
                                int duration = (int) (durationSlider != null ? durationSlider.getValue() : 6);
                                int points = (int) (densitySlider != null ? densitySlider.getValue() : 4);

                                dialog.dismiss();
                                fetchItinerary(scope, type, budget, duration, points);
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
                TextView textReveal = dialogView.findViewById(R.id.text_magic_reveal_name);
                ImageView imgBall = dialogView.findViewById(R.id.img_magic_reveal_ball);
                ImageView imgMist = dialogView.findViewById(R.id.img_magic_reveal_mist);
                com.google.android.material.button.MaterialButton btnGo = dialogView.findViewById(R.id.btn_magic_go);

                // --- SNAPPY ANIMATIONS & BLENDING ---
                // No color filter needed anymore since the image has a transparent background.

                // 1. Subtle Breath (Faster for better feel)
                android.animation.ObjectAnimator scaleX = android.animation.ObjectAnimator.ofFloat(imgBall, "scaleX", 1.0f, 1.05f);
                android.animation.ObjectAnimator scaleY = android.animation.ObjectAnimator.ofFloat(imgBall, "scaleY", 1.0f, 1.05f);
                scaleX.setDuration(1500); scaleY.setDuration(1500);
                scaleX.setRepeatCount(android.animation.ValueAnimator.INFINITE); scaleY.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                scaleX.setRepeatMode(android.animation.ValueAnimator.REVERSE); scaleY.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                scaleX.start(); scaleY.start();

                // 2. Swirling Mist (Initial "Thick Dust" phase)
                imgMist.setAlpha(0.9f); // Thick fog/dust
                
                android.animation.ObjectAnimator rotateMist = android.animation.ObjectAnimator.ofFloat(imgMist, "rotation", 0f, 360f);
                rotateMist.setDuration(10000);
                rotateMist.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                rotateMist.setInterpolator(new android.view.animation.LinearInterpolator());
                rotateMist.start();

                dialog.show();
                
                // Fetch high-quality AI recommendation from backend
                apiService.getMagicRecommendation(currentLocation.getLatitude(), currentLocation.getLongitude(), sessionManager.getUserId())
                    .enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
                        @Override
                        public void onResponse(retrofit2.Call<com.google.gson.JsonObject> call, retrofit2.Response<com.google.gson.JsonObject> response) {
                            if (isAdded() && response.isSuccessful() && response.body() != null) {
                                com.google.gson.JsonObject result = response.body();
                                String name = result.get("name").getAsString();
                                String reason = result.get("reason").getAsString();
                                com.google.gson.JsonArray activities = result.getAsJsonArray("activities");
                                
                                // Reveal with delay for dramatic effect
                                textReveal.postDelayed(() -> {
                                    if (dialog.isShowing()) {
                                        textStatus.setText("Destinul tău este:");
                                        imgMist.animate().alpha(0.15f).setDuration(2500).start();
                                        textReveal.setText(name);
                                        textReveal.animate().alpha(1.0f).setDuration(2000).start();
                                        
                                        btnGo.setVisibility(View.VISIBLE);
                                        btnGo.setOnClickListener(v1 -> {
                                            dialog.dismiss();
                                            showMagicDetailDialog(result);
                                        });
                                    }
                                }, 2500);
                            } else {
                                fallbackToRandomPlace(dialog, textStatus, textReveal, imgMist, btnGo);
                            }
                        }

                        @Override
                        public void onFailure(retrofit2.Call<com.google.gson.JsonObject> call, Throwable t) {
                            fallbackToRandomPlace(dialog, textStatus, textReveal, imgMist, btnGo);
                        }
                    });
        }

        private void fallbackToRandomPlace(androidx.appcompat.app.AlertDialog dialog, TextView textStatus, TextView textReveal, ImageView imgMist, View btnGo) {
             if (!combinedPlacesList.isEmpty()) {
                com.cityscape.app.model.Place result = combinedPlacesList.get(new java.util.Random().nextInt(combinedPlacesList.size()));
                textReveal.postDelayed(() -> {
                    if (dialog.isShowing()) {
                        textStatus.setText("Destinul tău este:");
                        imgMist.animate().alpha(0.15f).setDuration(2500).start();
                        textReveal.setText(result.name);
                        textReveal.animate().alpha(1.0f).setDuration(2000).start();
                        btnGo.setVisibility(View.VISIBLE);
                        btnGo.setOnClickListener(v1 -> {
                            dialog.dismiss();
                            showRecommendationDialog(result);
                        });
                    }
                }, 2500);
            }
        }

        private void showMagicDetailDialog(com.google.gson.JsonObject result) {
             if (getContext() == null) return;
             
             String name = result.get("name").getAsString();
             String reason = result.get("reason").getAsString();
             com.google.gson.JsonArray activitiesArr = result.getAsJsonArray("activities");
             
             StringBuilder actStr = new StringBuilder();
             if (activitiesArr != null) {
                 for (int i = 0; i < activitiesArr.size(); i++) {
                     actStr.append("  ✨ ").append(activitiesArr.get(i).getAsString()).append("\n");
                 }
             }

             new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setTitle("🔮 DESTINUL TĂU")
                .setMessage("\n" + name.toUpperCase() + "\n\n" + 
                           "\"" + reason + "\"\n\n" + 
                           "ACTIVITĂȚI PROFETIZATE:\n" + actStr.toString())
                .setPositiveButton("URMEAZĂ DESTINUL", (d, w) -> {
                    Toast.makeText(getContext(), "Aventura începe acum! 🚀", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("ALTA VIZIUNE", (d, w) -> {
                    if (binding != null) binding.btnRevealFate.performClick();
                })
                .setNegativeButton("ÎNCHIDE", null)
                .show();
        }

        private void showRecommendationDialog(com.cityscape.app.model.Place place) {
                if (getContext() == null)
                        return;

                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(
                                requireContext(), R.style.DarkDialogTheme);

                // Use custom view if possible, or just standard alert
                builder.setTitle("🎲 Recomandarea Ta!")
                                .setMessage("Ce zici de o vizită la " + place.name + "?\n\n"
                                                + place.description)
                                .setPositiveButton("Arată-mi", (d, w) -> {
                                        // Open detail
                                        Toast.makeText(getContext(), "Detalii pentru: " + place.name,
                                                        Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("Mai dă o dată", (d, w) -> binding.btnRevealFate.performClick())
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
                        binding.textLocation.setText("Detectăm locația...");
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
                        // Settings are good, get location
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
                                                }
                                        }

                                        @Override
                                        public void onFailure(Call<com.cityscape.app.api.WeatherResponse> call,
                                                        Throwable t) {
                                        }
                                });
        }

        private void updateWeatherUI() {
                if (currentWeather != null && binding != null) {
                        String weatherText = String.format(java.util.Locale.US, "%.0f°C %s", currentWeather.temp,
                                        currentWeather.condition != null && currentWeather.condition.contains("Rain") ? "🌧️"
                                                        : (currentWeather.condition != null && currentWeather.condition.contains("Clear") ? "☀️" : "☁️"));
                        binding.textWeather.setText(weatherText);
                }
        }

        private void fetchItinerary(String scope, String type, int budget, int duration, int points) {
                if (currentLocation == null) return;
                
                Bundle args = new Bundle();
                args.putDouble("lat", currentLocation.getLatitude());
                args.putDouble("lng", currentLocation.getLongitude());
                args.putString("scope", scope);
                args.putString("itinerary_type", type);
                args.putInt("itinerary_budget", budget);
                args.putInt("itinerary_duration", duration);
                args.putInt("itinerary_points", points);
                
                User user = sessionManager.getCurrentUser();
                String interests = user != null && user.interests != null ? user.interests : "";
                args.putString("itinerary_interests", interests);
                Navigation.findNavController(binding.getRoot()).navigate(R.id.navigation_itinerary, args);
        }

        private void updateLocationUI() {
                if (currentLocation == null || binding == null)
                        return;
                // Update city text
                try {
                        android.location.Geocoder geocoder = new android.location.Geocoder(requireContext(),
                                        java.util.Locale.getDefault());
                        List<android.location.Address> addresses = geocoder.getFromLocation(
                                        currentLocation.getLatitude(), currentLocation.getLongitude(), 1);
                        if (addresses != null && !addresses.isEmpty()) {
                                String city = addresses.get(0).getLocality();
                                binding.textLocation.setText(
                                                city != null ? city : "Locație: " + currentLocation.getLatitude());
                        } else {
                                binding.textLocation.setText("Locație: " + currentLocation.getLatitude() + ", "
                                                + currentLocation.getLongitude());
                        }
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
                                .setMessage("Introdu numele orașului pe care vrei să-l explorezi:")
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
                try {
                        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                        List<Address> addresses = geocoder.getFromLocationName(cityName, 1);
                        if (addresses != null && !addresses.isEmpty()) {
                                Address addr = addresses.get(0);
                                Location mockLoc = new Location("manual");
                                mockLoc.setLatitude(addr.getLatitude());
                                mockLoc.setLongitude(addr.getLongitude());
                                currentLocation = mockLoc;
                                isManualLocation = true;

                                String detectedCity = addr.getLocality() != null ? addr.getLocality() : cityName;
                                binding.textLocation.setText(detectedCity);

                                Toast.makeText(getContext(), "Bun venit în " + detectedCity + "!", Toast.LENGTH_SHORT)
                                                .show();

                                // Clear all old data to prevent mixing cities
                                allPlacesList.clear();
                                nearbyPlacesList.clear();
                                eventsList.clear();
                                updateFilters(); // Refresh UI to show loading/empty state

                                // Refresh everything for new city
                                updateLocationUI();
                                fetchPlaces(false);
                                fetchWeather();
                                fetchEvents();
                        } else {
                                // Fallback: try to see if it's a known city even if geocoder fails
                                handleManualCityFallback(cityName);
                        }
                } catch (Exception e) {
                        Log.e("HomeFragment", "City search error", e);
                        Toast.makeText(getContext(), "Eroare la căutarea orașului", Toast.LENGTH_SHORT).show();
                }
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
                Toast.makeText(getContext(), "Nu am găsit orașul: " + cityName, Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(getContext(), "GPS-ul nu a răspuns. Setează un oraș din Setări sau activează locația.",
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
                                fetchAIPicks();
                        }
                        @Override
                        public void afterTextChanged(android.text.Editable s) { }
                });

                if (binding.btnFilter != null) {
                        binding.btnFilter.setOnClickListener(v -> showFilterBottomSheet(false));
                }
                if (binding.btnFilterEvents != null) {
                    binding.btnFilterEvents.setOnClickListener(v -> showFilterBottomSheet(true));
                }
        }

        private void showFilterBottomSheet(boolean forEvents) {
                com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext(), R.style.TransparentBottomSheetDialogTheme);
                View view = getLayoutInflater().inflate(R.layout.dialog_filters, null);
                dialog.setContentView(view);

                TextView titleTv = (TextView) ((LinearLayout)view).getChildAt(1); 
                titleTv.setText(forEvents ? "Filtre Evenimente" : "Filtre Avansate");

                com.google.android.material.chip.ChipGroup musicGroup = view.findViewById(R.id.filter_music_group);
                
                // Set initial states
                String activeMusic = forEvents ? eventMusicFilter : currentMusicFilter;

                // Music chips match by text
                for (int i = 0; i < musicGroup.getChildCount(); i++) {
                        com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) musicGroup.getChildAt(i);
                        if (chip.getText().toString().equals(activeMusic)) {
                                chip.setChecked(true);
                        }
                }

                view.findViewById(R.id.btn_apply_filters).setOnClickListener(v -> {
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
                            updateFilters();
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
                }

                String userId = sessionManager.getUserId();
                if (isNearbyOnly) {
                    apiService.getNearby(currentLocation.getLatitude(), currentLocation.getLongitude(), type, sessionManager.getUserId())
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
                    apiService.getPlacesSearch(currentLocation.getLatitude(), currentLocation.getLongitude(), "", type, 50000, userId)
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
                                    if (isAdded()) Toast.makeText(getContext(), "Am găsit " + results.size() + " locații în Trending!", Toast.LENGTH_SHORT).show();
                                    
                                    updateFilters();
                                } else {
                                    if (isAdded()) Toast.makeText(getContext(), "Server-ul nu a returnat date populare.", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(Call<List<Place>> call, Throwable t) {
                                Log.e("HomeFragment", "Trending fetch failed", t);
                                if (isAdded()) Toast.makeText(getContext(), "Eroare rețea Trending: " + t.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                }
        }

        private void fetchAIPicks() {
                if (currentLocation == null || !isAdded()) return;
                
                String userId = sessionManager.getUserId();
                apiService.getPersonalizedRecommendations(currentLocation.getLatitude(), currentLocation.getLongitude(), userId, searchQuery, currentCategory)
                    .enqueue(new Callback<List<Place>>() {
                        @Override
                        public void onResponse(Call<List<Place>> call, Response<List<Place>> response) {
                            if (binding != null && response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                                aiPicksList.clear();
                                aiPicksList.addAll(response.body());
                                if (aiPicksAdapter != null) aiPicksAdapter.notifyDataSetChanged();
                                binding.sectionAiPicks.setVisibility(View.VISIBLE);
                            } else if (binding != null) {
                                binding.sectionAiPicks.setVisibility(View.GONE);
                            }
                        }

                        @Override
                        public void onFailure(Call<List<Place>> call, Throwable t) {
                            Log.e("HomeFragment", "AI Picks fetch failed", t);
                            if (binding != null) binding.sectionAiPicks.setVisibility(View.GONE);
                        }
                    });
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
                    binding.trendingTitle.setText("Fii la curent din " + (city != null && !city.isEmpty() ? city : "București"));
                } else {
                    binding.sectionTrending.setVisibility(View.GONE);
                    binding.recyclerRecommended.setVisibility(View.GONE);
                }

                // 2. Manage Near You Visibility
                boolean isReallyNear = true;
                if (actualGpsLocation != null && currentLocation != null) {
                    float distance = actualGpsLocation.distanceTo(currentLocation);
                    if (distance > 10000) { // More than 10km away
                        isReallyNear = false;
                    }
                }

                if (currentLocation != null && isReallyNear) {
                    binding.sectionNearYou.setVisibility(View.VISIBLE);
                    binding.recyclerNearYou.setVisibility(View.VISIBLE);
                    
                    if (isManualLocation) {
                        binding.btnResetLocation.setVisibility(View.VISIBLE);
                        binding.nearYouTitle.setText("În apropiere de locația aleasă");
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

            return true;
        }

        private void updateNearYouAdapter(List<Place> list) {
                if (binding == null) return;
                PlaceAdapter adapter = new PlaceAdapter(getContext(), list, true,
                                new PlaceAdapter.OnPlaceClickListener() {
                                        @Override
                                        public void onPlaceClick(Place place) {
                                                sessionManager.recordPlaceVisit(place.name);
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
                adapter.setManualMode(isManualLocation);
                binding.recyclerNearYou.setAdapter(adapter);
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
                        if (getContext() != null) Toast.makeText(getContext(), "Bravo! Vizită înregistrată. 🎉", Toast.LENGTH_SHORT).show();
                        fetchVisitedPlaces();
                        showFeedbackDialog(place);
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Toast.makeText(getContext(), "Eroare la înregistrarea vizitei.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void showFeedbackDialog(Place place) {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setTitle("Feedback pentru " + place.name)
                .setMessage("Cum a fost vizita ta aici? Ajută-ți prietenii cu o recomandare!")
                .setPositiveButton("Grozav! ⭐", (d, w) -> Toast.makeText(getContext(), "Mulțumim!", Toast.LENGTH_SHORT).show())
                .setNeutralButton("E OK", (d, w) -> Toast.makeText(getContext(), "Mulțumim!", Toast.LENGTH_SHORT).show())
                .setNegativeButton("Slab", (d, w) -> Toast.makeText(getContext(), "Ne pare rău!", Toast.LENGTH_SHORT).show())
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
                    public void onPlaceClick(Place place) { }
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

        private void updateRecommendedAdapter(List<Place> list) {
                if (binding == null) return;
                PlaceAdapter adapter = new PlaceAdapter(getContext(), list, false,
                                new PlaceAdapter.OnPlaceClickListener() {
                                        @Override
                                        public void onPlaceClick(Place place) {
                                                sessionManager.recordPlaceVisit(place.name);
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
                adapter.setManualMode(isManualLocation);
                binding.recyclerRecommended.setAdapter(adapter);
        }

        private void showPlanPlaceDialog(Place place) {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Alege data pentru " + place.name)
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .setTheme(R.style.CustomMaterialCalendar)
                .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                savePlannedActivity(place, selection);
            });
            
            datePicker.show(getChildFragmentManager(), "DATE_PICKER");
        }

        private void savePlannedActivity(Place place, long dateMillis) {
            User user = sessionManager.getCurrentUser();
            if (user == null) return;

            com.cityscape.app.model.PlannedActivity activity = new com.cityscape.app.model.PlannedActivity(
                user.id, null, place.name, "Atracție", place.imageUrl, dateMillis, "Toată ziua"
            );
            activity.notes = "Locație: " + place.address;

            android.content.Context context = getContext();
            if (context == null) return;
            new Thread(() -> {
                com.cityscape.app.data.AppDatabase.getInstance(appContext).activityDao().insert(activity);
                com.cityscape.app.data.SupabaseSyncManager.getInstance(appContext).pushActivityToCloud(activity);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (getContext() != null) Toast.makeText(getContext(), "Adăugat la planul tău! 🎒", Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        }

        private void fetchEvents() {
                if (currentLocation == null) return;
                User user = sessionManager.getCurrentUser();
                String interests = user != null && user.interests != null ? user.interests : "";

                apiService.getEvents(currentLocation.getLatitude(), currentLocation.getLongitude(), 50, interests)
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
                                                Log.e("HomeFragment", "Events fetch failed", t);
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

        @Override
        public void onDestroyView() {
                super.onDestroyView();
                binding = null;
        }
}
