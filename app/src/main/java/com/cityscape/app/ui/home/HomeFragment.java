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

public class HomeFragment extends Fragment {

        private FragmentHomeBinding binding;
        private ApiService apiService;
        private SessionManager sessionManager;
        private FusedLocationProviderClient fusedLocationClient;
        private Location currentLocation;
        private List<Place> nearbyPlacesList = new ArrayList<>(); // For Near You (real-time)
        private List<Place> allPlacesList = new ArrayList<>(); // For Trending (whole city)
        private List<Place> combinedPlacesList = new ArrayList<>(); // NEW: Combined/Filtered list for current city
        private List<Place> visitedPlacesList = new ArrayList<>(); // NEW: Visited Section
        private List<Place> restaurantsList = new ArrayList<>();
        private List<Place> cafesList = new ArrayList<>();
        private List<Place> museumsList = new ArrayList<>();
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
                binding.btnRevealFate.setOnClickListener(v -> {
                        // Header button feedback
                        v.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).withEndAction(() -> v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150)).start();
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
                
                // Fetch a random recommendation from the CURRENT city's places
                if (!combinedPlacesList.isEmpty()) {
                    com.cityscape.app.model.Place result = combinedPlacesList.get(new java.util.Random().nextInt(combinedPlacesList.size()));
                    
                    // Reveal with delay for dramatic effect
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
                } else {
                    // Fallback to global if nothing found in current city
                    apiService.getPlaces().enqueue(new retrofit2.Callback<List<com.cityscape.app.model.Place>>() {
                        @Override
                        public void onResponse(retrofit2.Call<List<com.cityscape.app.model.Place>> call, retrofit2.Response<List<com.cityscape.app.model.Place>> response) {
                            if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                                List<com.cityscape.app.model.Place> places = response.body();
                                com.cityscape.app.model.Place result = places.get(new java.util.Random().nextInt(places.size()));
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
                        @Override
                        public void onFailure(retrofit2.Call<List<com.cityscape.app.model.Place>> call, Throwable t) {
                            if (dialog.isShowing()) {
                                textStatus.setText("Ceața este prea densă...");
                            }
                        }
                    });
                }
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
                                        // In the future, navigate to place detail
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
                binding.recyclerEvents.setLayoutManager(
                                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                binding.recyclerVisited.setLayoutManager(
                                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                binding.recyclerRecommended.setLayoutManager(new LinearLayoutManager(getContext()));
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
                                        fetchPlaces(false); // Fetch EVERYTHING for the new category
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

                TextView title = view.findViewById(android.R.id.text1); // Not really, it's custom
                // In dialog_filters.xml it was just a TextView. I'll search for first TextView.
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
                        type = "culture"; // Maps to custom logic or landmark
                else if (currentCategory.equalsIgnoreCase(getString(R.string.category_commerce)) || currentCategory.equalsIgnoreCase("Comercial"))
                        type = "shopping"; // Maps to shopping_mall
                
                if (isAllCategory(currentCategory)) {
                        type = "mixed";
                }
                
                // If we changed category, we should also refresh NEARBY to filter by that category
                if (!isNearbyOnly) {
                    fetchPlaces(true); 
                }

                String userId = sessionManager.getUserId();
                if (isNearbyOnly) {
                    // 1. Fetch NEARBY (Strictly 2km) with personalization
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
                    // Trending / Recommended Section (Whole City)
                    apiService.getPlacesSearch(currentLocation.getLatitude(), currentLocation.getLongitude(), "", type, 15000, userId)
                        .enqueue(new Callback<List<Place>>() {
                            @Override
                            public void onResponse(Call<List<Place>> call, Response<List<Place>> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    allPlacesList.clear();
                                    allPlacesList.addAll(response.body());
                                    // Feed the Crystal Ball (Oracol) with ALL important city places
                                    combinedPlacesList.clear();
                                    combinedPlacesList.addAll(response.body());
                                    updateFilters();
                                }
                            }
                            @Override
                            public void onFailure(Call<List<Place>> call, Throwable t) {
                                Log.e("HomeFragment", "Trending fetch failed", t);
                            }
                        });
                }
        }

        private boolean isDuplicate(String id) {
            if (id == null) return false;
            for (Place p : allPlacesList) {
                if (id.equals(p.id)) return true;
            }
            return false;
        }

        private Place mapToPlace(com.cityscape.app.api.ItineraryItem item) {
            Place p = new Place();
            p.id = item.placeId != null ? item.placeId : java.util.UUID.randomUUID().toString();
            p.name = item.name;
            p.address = item.address;
            p.imageUrl = item.imageUrl;
            p.latitude = item.latitude;
            p.longitude = item.longitude;
            p.type = item.type;
            p.rating = 4.7f; // "Be in the know" places are top quality
            return p;
        }

        private void fetchStaticPlaces() {
                apiService.getPlaces().enqueue(new Callback<List<Place>>() {
                        @Override
                        public void onResponse(Call<List<Place>> call, Response<List<Place>> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                        allPlacesList = response.body();
                                        Log.d("HomeFragment", "Fetched " + allPlacesList.size() + " static places");
                                        for (Place p : allPlacesList) {
                                                p.isFavorite = sessionManager.isPlaceFavorite(p.id);
                                        }
                                        updateFilters();
                                }
                        }

                        @Override
                        public void onFailure(Call<List<Place>> call, Throwable t) {
                                Log.e("HomeFragment", "Static fetch failed", t);
                        }
                });
        }

        private void updateFilters() {
                if (binding == null) return;

                // 1. Manage Trending Visibility and Location Title
                if (currentLocation != null) {
                    String city = binding.textLocation.getText().toString();
                    
                    // Only geocode if NOT in manual mode (to keep manual selection stable)
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
                if (currentLocation != null) {
                    // Show Near You always if we have a location (GPS or Manual)
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
                }


                // 1. Show Near You (Foursquare search results)
                List<Place> filteredNearby = new ArrayList<>(nearbyPlacesList);
                updateNearYouAdapter(filteredNearby);

                Map<String, Integer> preferences = sessionManager.getPreferredCategories(allPlacesList);
                com.cityscape.app.model.User user = sessionManager.getCurrentUser();
                String userInterests = (user != null && user.interests != null) ? user.interests.toLowerCase() : "";

                // 2. Filter Trending (City wide - respects all filters + Quality Gate)
                List<Place> stageFiltered = new ArrayList<>();
                String[] blacklist = {"mcdonald", "kfc", "subway", "5 to go", "five to go", "mc donald"};
                
                for (Place p : allPlacesList) {
                    if (matchesCategoryAndFilters(p)) {
                        String nameLower = p.name != null ? p.name.toLowerCase() : "";
                        boolean isBlacklisted = false;
                        for (String term : blacklist) {
                            if (nameLower.contains(term)) {
                                isBlacklisted = true;
                                break;
                            }
                        }
                        // 1. Blacklist check
                        if (isBlacklisted) continue;

                        // 2. Proximity Check: If it's already in 'Near You', don't repeat it in 'Trending'
                        boolean alreadyNearby = false;
                        for (Place nearby : nearbyPlacesList) {
                            if (p.id.equals(nearby.id)) {
                                alreadyNearby = true;
                                break;
                            }
                        }
                        if (alreadyNearby) continue;

                        // 3. Quality Gate for "Be in the know": Must be reasonable quality
                        if (p.rating >= 4.0 || p.type.toLowerCase().contains("museum") || p.type.toLowerCase().contains("landmark") || p.type.toLowerCase().contains("historic")) {
                            stageFiltered.add(p);
                        } else if (p.rating == 0) {
                            stageFiltered.add(p);
                        }
                    }
                }

                // 2. Filter by Category and Search
                // 3. Sort by Ultra-Personalized Engine
                // stageFiltered now contains only trending results
                // userInterests already defined earlier in this method
                int currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);

                Collections.sort(stageFiltered, (p1, p2) -> {
                        if (p1 == null && p2 == null) return 0;
                        if (p1 == null) return 1;
                        if (p2 == null) return -1;

                        double score1 = p1.rating != 0 ? p1.rating : 3.0;
                        double score2 = p2.rating != 0 ? p2.rating : 3.0;
                        
                        // ICONIC BOOST: Prioritize "The Greats" (National Museums, Palaces, Parks)
                        String n1 = p1.name != null ? p1.name.toLowerCase() : "";
                        String n2 = p2.name != null ? p2.name.toLowerCase() : "";
                        String[] iconicTerms = {"national", "muzeul", "palatul", "parcul", "ateneul", "carturesti", "famous"};
                        
                        for (String term : iconicTerms) {
                            if (n1.contains(term)) score1 += 5.0; // Massive boost for icons
                            if (n2.contains(term)) score2 += 5.0;
                        }

                        String t1 = p1.type != null ? p1.type.toLowerCase() : "";
                        String t2 = p2.type != null ? p2.type.toLowerCase() : "";

                        if (p1.type != null && preferences.containsKey(p1.type)) {
                            Integer v1 = preferences.get(p1.type);
                            if (v1 != null) score1 += v1 * 0.8;
                        }
                        if (p2.type != null && preferences.containsKey(p2.type)) {
                            Integer v2 = preferences.get(p2.type);
                            if (v2 != null) score2 += v2 * 0.8;
                        }

                        // Vector 2: Weather Context
                        if (currentWeather != null) {
                            String cond = currentWeather.condition != null ? currentWeather.condition.toLowerCase() : "";
                            
                            // It's raining/bad weather - push indoors (cafes, museums)
                            if (cond.contains("rain") || cond.contains("snow")) {
                                if (t1.contains("museum") || t1.contains("cafe") || t1.contains("store")) score1 += 1.5;
                                if (t2.contains("museum") || t2.contains("cafe") || t2.contains("store")) score2 += 1.5;
                            }
                            // It's nice and sunny - push outdoors (parks, landmarks)
                            else if (cond.contains("clear") || cond.contains("sun")) {
                                if (t1.contains("park") || t1.contains("nature") || t1.contains("tourist")) score1 += 1.2;
                                if (t2.contains("park") || t2.contains("nature") || t2.contains("tourist")) score2 += 1.2;
                            }
                            // It's cold - push warm places 
                            if (currentWeather.temp < 15) {
                                if (t1.contains("cafe") || t1.contains("restaurant") || t1.contains("shopping")) score1 += 0.8;
                                if (t2.contains("cafe") || t2.contains("restaurant") || t2.contains("shopping")) score2 += 0.8;
                            }
                        }

                        // Vector 2: User Onboarding/Settings Explicit Interests
                        if (!userInterests.isEmpty() && !userInterests.equals("trending")) {
                                if (isTypeMatchingInterests(p1, userInterests)) score1 += 6.0;
                                if (isTypeMatchingInterests(p2, userInterests)) score2 += 6.0;
                        }

                        // Vector 3: Contextual Weather Filtering
                        if (currentWeather != null) {
                                boolean badWeather = (currentWeather.condition != null && (currentWeather.condition.equalsIgnoreCase("Rain") || currentWeather.condition.equalsIgnoreCase("Snow"))) || currentWeather.temp < 10;
                                boolean hotWeather = currentWeather.temp > 25 && currentWeather.condition != null && currentWeather.condition.contains("Clear");

                                if (badWeather) {
                                        if (t1.contains("museum") || t1.contains("cafe") || t1.contains("shopping") || t1.contains("art") || t1.contains("food")) score1 += 2.5;
                                        if (t2.contains("museum") || t2.contains("cafe") || t2.contains("shopping") || t2.contains("art") || t2.contains("food")) score2 += 2.5;
                                        if (t1.contains("park") || t1.contains("nature")) score1 -= 2.0;
                                        if (t2.contains("park") || t2.contains("nature")) score2 -= 2.0;
                                }
                                if (hotWeather) {
                                        if (t1.contains("park") || t1.contains("nature") || t1.contains("water") || t1.contains("ice")) score1 += 2.0;
                                        if (t2.contains("park") || t2.contains("nature") || t2.contains("water") || t2.contains("ice")) score2 += 2.0;
                                }
                        }

                        // Vector 4: Contextual Time of Day
                        if (currentHour >= 6 && currentHour <= 11) {
                                if (t1.contains("cafe") || t1.contains("coffee") || t1.contains("bakery")) score1 += 2.0;
                                if (t2.contains("cafe") || t2.contains("coffee") || t2.contains("bakery")) score2 += 2.0;
                        } else if (currentHour >= 12 && currentHour <= 15) {
                                if (t1.contains("restaurant") || t1.contains("food")) score1 += 1.5;
                                if (t2.contains("restaurant") || t2.contains("food")) score2 += 1.5;
                        } else if (currentHour >= 18 && currentHour <= 23) {
                                if (t1.contains("bar") || t1.contains("night_club") || t1.contains("event") || t1.contains("restaurant")) score1 += 2.0;
                                if (t2.contains("bar") || t2.contains("night_club") || t2.contains("event") || t2.contains("restaurant")) score2 += 2.0;
                        } else if (currentHour >= 22 || currentHour <= 5) {
                                if (t1.contains("museum") || t1.contains("park")) score1 -= 3.0;
                                if (t2.contains("museum") || t2.contains("park")) score2 -= 3.0;
                        }

                        // Vector 5: Contextual Geo-Proximity
                        if (currentLocation != null) {
                                if (p1.latitude != 0 && p1.longitude != 0) {
                                        float[] res1 = new float[1];
                                        android.location.Location.distanceBetween(currentLocation.getLatitude(), currentLocation.getLongitude(), p1.latitude, p1.longitude, res1);
                                        if (res1[0] < 1500) score1 += 2.0; else if (res1[0] < 5000) score1 += 0.5;
                                }
                                if (p2.latitude != 0 && p2.longitude != 0) {
                                        float[] res2 = new float[1];
                                        android.location.Location.distanceBetween(currentLocation.getLatitude(), currentLocation.getLongitude(), p2.latitude, p2.longitude, res2);
                                        if (res2[0] < 1500) score2 += 2.0; else if (res2[0] < 5000) score2 += 0.5;
                                }
                        }

                        // Vector 6: Retain user explicit favorites near the top
                        if (sessionManager.isPlaceFavorite(p1.id)) score1 += 1.0;
                        if (sessionManager.isPlaceFavorite(p2.id)) score2 += 1.0;

                        return Double.compare(score2, score1); // Descending
                });

                // Update UI
                for (Place p : stageFiltered) {
                        if (p.imageUrl == null || p.imageUrl.isEmpty()) p.imageUrl = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=400";
                }
                
                // Show Nearby (Already sorted by backend distance)
                updateNearYouAdapter(nearbyPlacesList);

                // Show Trending (Exclude those already in nearby to ensure variety)
                List<Place> realTrending = new ArrayList<>();
                for (Place p : stageFiltered) {
                    boolean alreadyNearby = false;
                    for (Place ny : nearbyPlacesList) {
                        if (ny.id.equals(p.id)) { alreadyNearby = true; break; }
                    }
                    if (!alreadyNearby) realTrending.add(p);
                }
                if (realTrending.isEmpty()) realTrending = stageFiltered;

                updateRecommendedAdapter(realTrending);
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

            // Category check
            boolean matchesCat = isAll;
            if (!isAll) {
                String currCatLower = currentCategory.toLowerCase();
                if (currCatLower.contains("rest") && (pType.contains("restaurant") || pType.contains("food"))) matchesCat = true;
                else if (currCatLower.contains("caf") && (pType.contains("cafe") || pType.contains("coffee"))) matchesCat = true;
                else if (currCatLower.contains("par") && (pType.contains("park") || pType.contains("nature"))) matchesCat = true;
                else if (currCatLower.contains("mus") && (pType.contains("museum") || pType.contains("art"))) matchesCat = true;
            }

            if (!matchesCat) return false;

            // Search check
            if (!searchLower.isEmpty() && !pName.contains(searchLower) && !pType.contains(searchLower)) return false;

            // Music check
            if (!currentMusicFilter.equals("All") && !currentMusicFilter.equals("Oricare")) {
                if (!pName.contains(currentMusicFilter.toLowerCase()) && !pType.contains(currentMusicFilter.toLowerCase())) return false;
            }

            return true;
        }

        private void updateNearYouAdapter(List<Place> list) {
                if (binding == null)
                        return;
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
                    if (response.isSuccessful()) {
                        Toast.makeText(getContext(), "Bravo! Vizită înregistrată. 🎉", Toast.LENGTH_SHORT).show();
                        fetchVisitedPlaces(); // Refresh the visited list on feed
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
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        visitedPlacesList = response.body();
                        binding.sectionVisited.setVisibility(View.VISIBLE);
                        binding.recyclerVisited.setVisibility(View.VISIBLE);
                        updateVisitedAdapter();
                    } else {
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
                if (binding == null)
                        return;
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
            final java.util.Calendar calendar = java.util.Calendar.getInstance();
            android.app.DatePickerDialog datePicker = new android.app.DatePickerDialog(requireContext(), 
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    savePlannedActivity(place, calendar.getTimeInMillis());
                }, 
                calendar.get(java.util.Calendar.YEAR), 
                calendar.get(java.util.Calendar.MONTH), 
                calendar.get(java.util.Calendar.DAY_OF_MONTH));
            datePicker.setTitle("Alege data pentru " + place.name);
            datePicker.show();
        }

        private void savePlannedActivity(Place place, long dateMillis) {
            User user = sessionManager.getCurrentUser();
            if (user == null) return;

            com.cityscape.app.model.PlannedActivity activity = new com.cityscape.app.model.PlannedActivity(
                user.id,
                null,
                place.name,
                "Atracție",
                place.imageUrl,
                dateMillis,
                "Toată ziua"
            );
            activity.notes = "Locație: " + place.address;

            new Thread(() -> {
                com.cityscape.app.data.AppDatabase.getInstance(requireContext()).activityDao().insert(activity);
                com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext()).pushActivityToCloud(activity);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Adăugat la planul tău! 🎒", Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        }

        private void fetchEvents() {
                if (currentLocation == null)
                        return;

                User user = sessionManager.getCurrentUser();
                String interests = user != null && user.interests != null ? user.interests : "";

                apiService.getEvents(currentLocation.getLatitude(), currentLocation.getLongitude(), interests)
                                .enqueue(new Callback<List<com.cityscape.app.model.Event>>() {
                                        @Override
                                        public void onResponse(Call<List<com.cityscape.app.model.Event>> call,
                                                        Response<List<com.cityscape.app.model.Event>> response) {
                                                if (response.isSuccessful() && response.body() != null) {
                                                        List<com.cityscape.app.model.Event> events = response.body();
                                                        
                                                        // Filter Events locally by Price/Music if set
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
                                        public void onFailure(Call<List<com.cityscape.app.model.Event>> call,
                                                        Throwable t) {
                                                Log.e("HomeFragment", "Events fetch failed", t);
                                        }
                                });
        }



        private void applyIntentScoring(List<com.cityscape.app.model.Event> events) {
            User user = sessionManager.getCurrentUser();
            if (user == null) return;

            String interests = user.interests != null ? user.interests.toLowerCase() : "";
            
            // Basic intent scoring based on keywords in interests and titles
            java.util.Collections.sort(events, (e1, e2) -> {
                int score1 = 0;
                int score2 = 0;
                
                if (!interests.isEmpty()) {
                    for (String interest : interests.split(",")) {
                        String trimInt = interest.trim();
                        if (!trimInt.isEmpty()) {
                            if (e1.title.toLowerCase().contains(trimInt)) score1 += 50;
                            if (e2.title.toLowerCase().contains(trimInt)) score2 += 50;
                        }
                    }
                }
                
                // Tie-breaker: lexicographical if same score
                if (score1 == score2) return e1.title.compareTo(e2.title);
                return Integer.compare(score2, score1); // Descending
            });
        }

        private void updateEventsAdapter() {
                if (binding == null)
                        return;
                com.cityscape.app.adapter.EventAdapter adapter = new com.cityscape.app.adapter.EventAdapter(
                                eventsList, new com.cityscape.app.adapter.EventAdapter.OnEventClickListener() {
                        @Override
                        public void onEventClick(com.cityscape.app.model.Event event) {
                                Intent intent = new Intent(getContext(), com.cityscape.app.ui.home.EventDetailActivity.class);
                                intent.putExtra("event_json", new com.google.gson.Gson().toJson(event));
                                startActivity(intent);
                        }
                });
                binding.recyclerEvents.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                binding.recyclerEvents.setAdapter(adapter);
        }

        private void handleEventInvite(com.cityscape.app.model.Event event) {
                User user = sessionManager.getCurrentUser();
                if (user == null) {
                        Toast.makeText(getContext(), "Trebuie să fii autentificat!", Toast.LENGTH_SHORT).show();
                        return;
                }

                long eventTimeMillis = System.currentTimeMillis();
                String displayTime = (event.time != null && !event.time.isEmpty()) ? event.time.trim() : 
                                    (event.date_str != null ? event.date_str.trim() : "TBA");
                try {
                        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                        java.util.Date d = fmt.parse(displayTime);
                        if (d != null) {
                                eventTimeMillis = d.getTime();
                                displayTime = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(d);
                        }
                } catch (Exception e) {
                        try {
                                java.text.SimpleDateFormat fmt2 = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                                java.util.Date d2 = fmt2.parse(displayTime.split(" ")[0]);
                                if (d2 != null) {
                                        eventTimeMillis = d2.getTime();
                                        displayTime = "Toată ziua";
                                }
                        } catch(Exception ignored) {
                                displayTime = "În curând";
                        }
                }

                // Normalize time to midnight for database consistency (day-based lookup)
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTimeInMillis(eventTimeMillis);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                long normalizedDate = cal.getTimeInMillis();

                com.cityscape.app.data.AppDatabase db = com.cityscape.app.data.AppDatabase.getInstance(requireContext());
                com.cityscape.app.model.PlannedActivity activity = new com.cityscape.app.model.PlannedActivity(
                                user.id,
                                null,
                                event.title,
                                "Eveniment",
                                event.imageUrl,
                                normalizedDate,
                                displayTime);
                activity.notes = "Locație: " + event.location + (event.url != null ? "\nBilete: " + event.url : "");

                final long finalTargetDate = normalizedDate;
                new Thread(() -> {
                        db.activityDao().insert(activity);
                        com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext())
                                        .pushActivityToCloud(activity);

                        if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                        Toast.makeText(getContext(), "Eveniment salvat! Acum poți invita prieteni.",
                                                        Toast.LENGTH_LONG).show();
                                        
                                        // Also offer to add to system calendar
                                        try {
                                            Intent intent = new Intent(Intent.ACTION_INSERT)
                                                .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                                                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, finalTargetDate + (19 * 3600000)) // Default 19:00
                                                .putExtra(android.provider.CalendarContract.Events.TITLE, event.title)
                                                .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, "Adăugat din CityScape\n" + event.url)
                                                .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, event.location)
                                                .putExtra(android.provider.CalendarContract.Events.AVAILABILITY, android.provider.CalendarContract.Events.AVAILABILITY_BUSY);
                                            startActivity(intent);
                                        } catch (Exception e) {
                                            Log.e("HomeFragment", "Could not open system calendar", e);
                                        }

                                        Bundle args = new Bundle();
                                        args.putLong("target_date", finalTargetDate);
                                        args.putBoolean("auto_create_group", true);
                                        args.putString("target_activity_id", activity.id);
                                        
                                        Navigation.findNavController(binding.getRoot())
                                                        .navigate(R.id.navigation_calendar, args);
                                });
                        }
                }).start();
        }

        private boolean isTypeMatchingInterests(Place place, String userInterests) {
                if (place == null || userInterests == null || userInterests.isEmpty()) return false;
                
                String pType = place.type != null ? place.type.toLowerCase() : "";
                String pName = place.name != null ? place.name.toLowerCase() : "";
                
                for (String interest : userInterests.split(",")) {
                        String interestLower = interest.trim().toLowerCase();
                        if (interestLower.isEmpty()) continue;
                        
                        // Keyword based matching cross Name + Type
                        if (pType.contains(interestLower) || pName.contains(interestLower)) return true;

                        // Semantic mappings
                        if ((interestLower.contains("muz") || interestLower.contains("mus") || interestLower.contains("artă")) && 
                            (pType.contains("museum") || pType.contains("art") || pType.contains("landmark") || pType.contains("culture"))) return true;
                        
                        if ((interestLower.contains("rest") || interestLower.contains("food") || interestLower.contains("mânca")) && 
                            (pType.contains("restaurant") || pType.contains("food") || pType.contains("dining") || pType.contains("eat"))) return true;

                        if ((interestLower.contains("parc") || interestLower.contains("natur") || interestLower.contains("aer")) && 
                            (pType.contains("park") || pType.contains("nature") || pType.contains("garden") || pType.contains("outdoors"))) return true;

                        if ((interestLower.contains("caf") || interestLower.contains("cafe") || interestLower.contains("coffee")) && 
                            (pType.contains("cafe") || pType.contains("coffee") || pType.contains("bakery"))) return true;

                        if ((interestLower.contains("shop") || interestLower.contains("mall") || interestLower.contains("cumpără")) && 
                            (pType.contains("shop") || pType.contains("store") || pType.contains("mall") || pType.contains("market"))) return true;

                        if ((interestLower.contains("sport") || interestLower.contains("stadium") || interestLower.contains("activ")) && 
                            (pType.contains("sport") || pType.contains("stadium") || pType.contains("arena") || pType.contains("gym"))) return true;

                        if ((interestLower.contains("viața") || interestLower.contains("noapt") || interestLower.contains("party")) && 
                            (pType.contains("bar") || pType.contains("club") || pType.contains("night") || pType.contains("dance"))) return true;
                }
                return false;
        }

        @Override
        public void onDestroyView() {
                super.onDestroyView();
                binding = null;
        }
}
