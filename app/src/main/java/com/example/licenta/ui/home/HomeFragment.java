package com.example.licenta.ui.home;

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
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.licenta.R;
import com.example.licenta.adapter.PlaceAdapter;
import com.example.licenta.api.ApiClient;
import com.example.licenta.api.ApiService;
import com.example.licenta.data.SessionManager;
import com.example.licenta.databinding.FragmentHomeBinding;
import com.example.licenta.model.Place;
import com.example.licenta.model.User;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
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
import java.util.Random;
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
        private List<Place> allPlacesList = new ArrayList<>(); // For Trending (whole city)
        private List<Place> nearbyPlacesList = new ArrayList<>(); // For Near You (real-time)
        private List<com.example.licenta.model.Event> eventsList = new ArrayList<>();
        private String currentCategory = "All";
        private String searchQuery = "";
        private com.example.licenta.api.WeatherResponse currentWeather;
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
                checkLocationPermission();

                return binding.getRoot();
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
                        com.google.android.material.button.MaterialButtonToggleGroup toggleType = dialogView
                                        .findViewById(R.id.dialog_toggle_type);
                        View btnPlan = dialogView.findViewById(R.id.btn_generate_itinerary);

                        btnPlan.setOnClickListener(v1 -> {
                                String scope = (toggleScope.getCheckedButtonId() == R.id.btn_scope_city) ? "city"
                                                : "nearby";
                                                
                                String type = "exploration";
                                int typeId = toggleType.getCheckedButtonId();
                                if (typeId == R.id.btn_type_relaxation) type = "relaxation";
                                else if (typeId == R.id.btn_type_cultural) type = "cultural";
                                else if (typeId == R.id.btn_type_gastronomic) type = "gastronomic";

                                dialog.dismiss();
                                fetchItinerary(scope, type);
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

                // Fetch a random recommendation
                apiService.getPlaces().enqueue(new Callback<List<com.example.licenta.model.Place>>() {
                        @Override
                        public void onResponse(Call<List<com.example.licenta.model.Place>> call, Response<List<com.example.licenta.model.Place>> response) {
                                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                                        List<com.example.licenta.model.Place> places = response.body();
                                        int randomIndex = new Random().nextInt(places.size());
                                        com.example.licenta.model.Place result = places.get(randomIndex);

                                        // Reveal with delay for dramatic effect
                                        textReveal.postDelayed(() -> {
                                                if (dialog.isShowing()) {
                                                        textStatus.setText("Destinul tău este:");
                                                        
                                                        // "Clarify the dust" animation
                                                        imgMist.animate().alpha(0.15f).setDuration(2500).start();
                                                        
                                                        textReveal.setText(result.name);
                                                        textReveal.animate().alpha(1.0f).setDuration(2000).start();
                                                        
                                                        btnGo.setVisibility(View.VISIBLE);
                                                        btnGo.setOnClickListener(v -> {
                                                                dialog.dismiss();
                                                                showRecommendationDialog(result);
                                                        });
                                                }
                                        }, 2500);
                                }
                        }

                        @Override
                        public void onFailure(Call<List<com.example.licenta.model.Place>> call, Throwable t) {
                                if (dialog.isShowing()) {
                                        textStatus.setText("Ceața este prea densă...");
                                        textReveal.setText("?");
                                        textReveal.animate().alpha(0.5f).setDuration(500).start();
                                }
                        }
                });
        }

        private void showRecommendationDialog(com.example.licenta.model.Place place) {
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
                        performLocationFetch();
                });

                task.addOnFailureListener(requireActivity(), e -> {
                        if (e instanceof ResolvableApiException) {
                                try {
                                        ResolvableApiException resolvable = (ResolvableApiException) e;
                                        resolvable.startResolutionForResult(requireActivity(), REQUEST_CHECK_SETTINGS);
                                } catch (android.content.IntentSender.SendIntentException sendEx) {
                                        Log.e("HomeFragment", "Error resolving settings", sendEx);
                                        onLocationNotFound();
                                }
                        } else {
                                onLocationNotFound();
                        }
                });
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
                                        .addOnSuccessListener(requireActivity(), location -> {
                                                if (location != null) {
                                                        currentLocation = location;
                                                        updateLocationUI();
                                                        fetchPlaces(true);
                                                        fetchWeather(); // Fetch weather context
                                                        fetchEvents();  // Ensure events are populated on launch
                                                } else {
                                                        onLocationNotFound();
                                                }
                                        })
                                        .addOnFailureListener(requireActivity(), e -> {
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
                                .enqueue(new Callback<com.example.licenta.api.WeatherResponse>() {
                                        @Override
                                        public void onResponse(Call<com.example.licenta.api.WeatherResponse> call,
                                                        Response<com.example.licenta.api.WeatherResponse> response) {
                                                if (response.isSuccessful() && response.body() != null) {
                                                        currentWeather = response.body();
                                                        updateWeatherUI();
                                                        updateFilters();
                                                }
                                        }

                                        @Override
                                        public void onFailure(Call<com.example.licenta.api.WeatherResponse> call,
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

        private void fetchItinerary(String scope, String type) {
                if (currentLocation == null) {
                        Toast.makeText(getContext(), "Locația nu este setată încă. Încercăm să o găsim...",
                                        Toast.LENGTH_SHORT).show();
                        return;
                }

                double lat = currentLocation.getLatitude();
                double lng = currentLocation.getLongitude();
                int radius = 3000; // Default radius (3km) for nearby

                if ("city".equals(scope)) {
                        radius = 20000; // Larger radius for city-wide (20km)
                        try {
                                String cityName = binding.textLocation.getText().toString();
                                // Clean up city name if it contains "Detecting" or coordinates
                                if (!cityName.contains("Detect") && !cityName.contains(":")) {
                                        android.location.Geocoder geocoder = new android.location.Geocoder(
                                                        requireContext(), java.util.Locale.getDefault());
                                        List<android.location.Address> centers = geocoder.getFromLocationName(cityName,
                                                        1);
                                        if (centers != null && !centers.isEmpty()) {
                                                lat = centers.get(0).getLatitude();
                                                lng = centers.get(0).getLongitude();
                                                Log.d("HomeFragment", "Using city center for itinerary: " + cityName);
                                        }
                                }
                        } catch (Exception e) {
                                Log.e("HomeFragment", "Error finding city center", e);
                        }
                }

                final double finalLat = lat;
                final double finalLng = lng;

                Toast.makeText(getContext(),
                                "Generăm un itinerariu de " + type + "...",
                                Toast.LENGTH_SHORT).show();

                android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(getContext());
                progressDialog.setMessage("Generăm itinerariul tău magic...");
                progressDialog.setCancelable(false);
                progressDialog.show();

                apiService.getItinerary(finalLat, finalLng, scope, radius, type)
                                .enqueue(new Callback<List<com.example.licenta.api.ItineraryItem>>() {
                                        @Override
                                        public void onResponse(Call<List<com.example.licenta.api.ItineraryItem>> call,
                                                        Response<List<com.example.licenta.api.ItineraryItem>> response) {
                                                progressDialog.dismiss();
                                                if (response.isSuccessful() && response.body() != null) {
                                                        showItineraryDialog(response.body(), scope, finalLat, finalLng, type);
                                                } else {
                                                        Toast.makeText(getContext(),
                                                                        "Eroare Server: " + response.code(),
                                                                        Toast.LENGTH_SHORT).show();
                                                }
                                        }

                                        @Override
                                        public void onFailure(Call<List<com.example.licenta.api.ItineraryItem>> call,
                                                        Throwable t) {
                                                progressDialog.dismiss();
                                                Log.e("HomeFragment", "Itinerary fetch failed", t);
                                                Toast.makeText(getContext(), "Eroare de conexiune la server",
                                                                Toast.LENGTH_SHORT).show();
                                        }
                                });
        }

        private void showItineraryDialog(List<com.example.licenta.api.ItineraryItem> items, String scope,
                        double lat, double lng, String type) {
                if (getContext() == null || binding == null)
                        return;

                if (items == null || items.isEmpty()) {
                        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                                        .setTitle("Oups!")
                                        .setMessage("Nu am găsit destule locații " + type + " deschise în apropiere. Mai încearcă mai târziu!")
                                        .setPositiveButton("Ok", null)
                                        .show();
                        return;
                }

                // Navigate to the pro itinerary fragment
                String json = new com.google.gson.Gson().toJson(items);
                Bundle args = new Bundle();
                args.putString("itinerary_json", json);
                args.putDouble("lat", lat);
                args.putDouble("lng", lng);
                args.putString("scope", scope);
                args.putString("itinerary_type", type);
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

                                String detectedCity = addr.getLocality() != null ? addr.getLocality() : cityName;
                                binding.textLocation.setText(detectedCity);

                                Toast.makeText(getContext(), "Bun venit în " + detectedCity + "!", Toast.LENGTH_SHORT)
                                                .show();

                                // Refresh everything for new city
                                updateLocationUI();
                                fetchPlaces(false);
                                fetchWeather();
                                fetchEvents();
                        } else {
                                Toast.makeText(getContext(), "Nu am găsit orașul: " + cityName, Toast.LENGTH_SHORT)
                                                .show();
                        }
                } catch (Exception e) {
                        Log.e("HomeFragment", "City search error", e);
                        Toast.makeText(getContext(), "Eroare la căutarea orașului", Toast.LENGTH_SHORT).show();
                }
        }

        private void onLocationNotFound() {
                if (getContext() == null)
                        return;
                Toast.makeText(getContext(), "GPS-ul nu a răspuns. Verifică dacă ai locația activată în setări.",
                                Toast.LENGTH_LONG).show();
                fetchPlaces(false);
        }

        private void setupGreeting() {
                User user = sessionManager.getCurrentUser();
                String greeting = getGreetingForTime();
                if (user != null && user.name != null) {
                        binding.textGreeting.setText(greeting + ", " + user.name + "! 👋");
                } else {
                        binding.textGreeting.setText(greeting + "! 👋");
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
                binding.recyclerRecommended.setLayoutManager(new LinearLayoutManager(getContext()));
                fetchPlaces(false);
                fetchEvents();
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
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                                searchQuery = s.toString().toLowerCase().trim();
                                updateFilters();
                        }

                        @Override
                        public void afterTextChanged(android.text.Editable s) {
                        }
                });
        }

        private void fetchPlaces(boolean isNearbyOnly) {
                // 1. Fetch Trending from Supabase (Whole City)
                if (!isNearbyOnly) {
                        fetchStaticPlaces();
                }

                // 2. Fetch Nearby from Google (Real-time)
                if (currentLocation != null) {
                        double lat = currentLocation.getLatitude();
                        double lng = currentLocation.getLongitude();

                        String type = "restaurant";
                        if (currentCategory.equalsIgnoreCase(getString(R.string.category_cafes)) || currentCategory.equalsIgnoreCase("Cafenele"))
                                type = "cafe";
                        if (currentCategory.equalsIgnoreCase(getString(R.string.category_parks)) || currentCategory.equalsIgnoreCase("Parcuri"))
                                type = "park";
                        if (currentCategory.equalsIgnoreCase(getString(R.string.category_museums)) || currentCategory.equalsIgnoreCase("Muzee"))
                                type = "museum";
                        
                        if (isAllCategory(currentCategory)) {
                                type = null; // No type filter = all types
                        }

                        apiService.getNearby(lat, lng, type).enqueue(new Callback<List<Place>>() {
                                @Override
                                public void onResponse(Call<List<Place>> call, Response<List<Place>> response) {
                                        if (response.isSuccessful() && response.body() != null) {
                                                nearbyPlacesList = response.body();
                                                Log.d("HomeFragment", "Fetched " + nearbyPlacesList.size()
                                                                + " nearby places");
                                                for (Place p : nearbyPlacesList) {
                                                        p.isFavorite = sessionManager.isPlaceFavorite(p.id);
                                                }
                                                updateFilters();
                                        }
                                }

                                @Override
                                public void onFailure(Call<List<Place>> call, Throwable t) {
                                        Log.e("HomeFragment", "Nearby fetch failed", t);
                                }
                        });
                }
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

                // Update UI Titles based on Location
                if (currentLocation != null) {
                        try {
                                Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                                List<Address> addresses = geocoder.getFromLocation(
                                                currentLocation.getLatitude(), currentLocation.getLongitude(), 1);
                                if (addresses != null && !addresses.isEmpty()) {
                                        String city = addresses.get(0).getLocality();
                                        binding.textLocation.setText(city != null ? city : "Locație generală");
                                }
                        } catch (Exception e) {
                                Log.e("HomeFragment", "Geocoder fail", e);
                        }
                }

                Map<String, Integer> preferences = sessionManager.getPreferredCategories(allPlacesList);
                boolean hasPreferences = !preferences.isEmpty();
                if (hasPreferences && isAllCategory(currentCategory) && searchQuery.isEmpty()) {
                        binding.trendingTitle.setText("Special pentru tine");
                } else {
                        binding.trendingTitle.setText(getString(R.string.trending_now));
                }

                // 1. Combine lists (avoiding duplicates)
                List<Place> combined = new ArrayList<>(allPlacesList);
                for (Place nearby : nearbyPlacesList) {
                        boolean alreadyHandled = false;
                        for (Place staticPlace : allPlacesList) {
                                if (staticPlace.name.equalsIgnoreCase(nearby.name) || 
                                    (staticPlace.googlePlaceId != null && staticPlace.googlePlaceId.equals(nearby.id)) ||
                                    (staticPlace.id != null && staticPlace.id.equals(nearby.id))) {
                                        alreadyHandled = true;
                                        break;
                                }
                        }
                        if (!alreadyHandled) {
                                combined.add(nearby);
                        }
                }

                // 2. Filter by Category and Search
                List<Place> stageFiltered = new ArrayList<>();
                String searchLower = searchQuery.toLowerCase().trim();
                boolean isAll = isAllCategory(currentCategory);

                for (Place p : combined) {
                        String pName = p.name != null ? p.name.toLowerCase() : "";
                        String pType = p.type != null ? p.type.toLowerCase() : "";
                        String pAddr = p.address != null ? p.address.toLowerCase() : "";

                        // Robust category matching
                        boolean matchesCat = isAll;
                        if (!isAll) {
                                String catRest = getString(R.string.category_restaurants).toLowerCase();
                                String catCafe = getString(R.string.category_cafes).toLowerCase();
                                String catPark = getString(R.string.category_parks).toLowerCase();
                                String catMuseum = getString(R.string.category_museums).toLowerCase();
                                
                                String currCatLower = currentCategory.toLowerCase();

                                if ((currCatLower.contains("rest") || currCatLower.equals(catRest)) && 
                                    (pType.contains("restaurant") || pType.contains("food") || pType.contains("dining"))) matchesCat = true;
                                else if ((currCatLower.contains("caf") || currCatLower.contains("cafe") || currCatLower.equals(catCafe)) && 
                                         (pType.contains("cafe") || pType.contains("coffee") || pType.contains("bar"))) matchesCat = true;
                                else if ((currCatLower.contains("par") || currCatLower.equals(catPark)) && 
                                         (pType.contains("park") || pType.contains("garden") || pType.contains("nature"))) matchesCat = true;
                                else if ((currCatLower.contains("muz") || currCatLower.contains("mus") || currCatLower.equals(catMuseum)) && 
                                         (pType.contains("museum") || pType.contains("art") || pType.contains("gallery"))) matchesCat = true;
                        }

                        boolean matchesSearch = searchLower.isEmpty() || pName.contains(searchLower) || pType.contains(searchLower) || pAddr.contains(searchLower);

                        if (matchesCat && matchesSearch) {
                                stageFiltered.add(p);
                        }
                }

                // 3. Sort by Ultra-Personalized Engine
                com.example.licenta.model.User user = sessionManager.getCurrentUser();
                String userInterests = (user != null && user.interests != null) ? user.interests.toLowerCase() : "";
                int currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);

                Collections.sort(stageFiltered, (p1, p2) -> {
                        if (p1 == null && p2 == null) return 0;
                        if (p1 == null) return 1;
                        if (p2 == null) return -1;

                        double score1 = p1.rating != 0 ? p1.rating : 3.0;
                        double score2 = p2.rating != 0 ? p2.rating : 3.0;
                        
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
                                if (p1.type != null && isTypeMatchingInterests(p1.type, userInterests)) score1 += 4.0;
                                if (p2.type != null && isTypeMatchingInterests(p2.type, userInterests)) score2 += 4.0;
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
                updateRecommendedAdapter(stageFiltered);

                // Near You - Strictly Nearby
                List<Place> nearYou = new ArrayList<>(nearbyPlacesList);
                if (nearYou.isEmpty()) nearYou = new ArrayList<>(allPlacesList); // Fallback
                Collections.sort(nearYou, (p1, p2) -> {
                    if (p1 == null && p2 == null) return 0;
                    if (p1 == null) return 1;
                    if (p2 == null) return -1;
                    return Double.compare(p2.rating, p1.rating);
                });
                int limit = Math.min(10, nearYou.size());
                updateNearYouAdapter(nearYou.subList(0, limit));
        }

        private boolean isAllCategory(String cat) {
                if (cat == null) return true;
                return cat.equalsIgnoreCase("All") || 
                       cat.equalsIgnoreCase("Toate") || 
                       cat.equalsIgnoreCase(getString(R.string.category_all)) ||
                       cat.isEmpty();
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
                                });
                binding.recyclerNearYou.setAdapter(adapter);
        }

        private void updateRecommendedAdapter(List<Place> list) {
                if (binding == null)
                        return;
                PlaceAdapter adapter = new PlaceAdapter(getContext(), list, false,
                                new PlaceAdapter.OnPlaceClickListener() {
                                        @Override
                                        public void onPlaceClick(Place place) {
                                                sessionManager.recordPlaceVisit(place.name);
                                                Toast.makeText(getContext(),
                                                                String.format(getString(R.string.visited_place),
                                                                                place.name),
                                                                Toast.LENGTH_SHORT).show();
                                        }

                                        @Override
                                        public void onFavoriteClick(Place place) {
                                                sessionManager.setPlaceFavorite(place.id, place.isFavorite);
                                        }
                                });
                binding.recyclerRecommended.setAdapter(adapter);
        }

        private void fetchEvents() {
                if (currentLocation == null)
                        return;

                User user = sessionManager.getCurrentUser();
                String interests = user != null && user.interests != null ? user.interests : "";

                apiService.getEvents(currentLocation.getLatitude(), currentLocation.getLongitude(), interests)
                                .enqueue(new Callback<List<com.example.licenta.model.Event>>() {
                                        @Override
                                        public void onResponse(Call<List<com.example.licenta.model.Event>> call,
                                                        Response<List<com.example.licenta.model.Event>> response) {
                                                if (response.isSuccessful() && response.body() != null) {
                                                        eventsList = response.body();
                                                        updateEventsAdapter();
                                                }
                                        }

                                        @Override
                                        public void onFailure(Call<List<com.example.licenta.model.Event>> call,
                                                        Throwable t) {
                                                Log.e("HomeFragment", "Events fetch failed", t);
                                        }
                                });
        }

        private void updateEventsAdapter() {
                if (binding == null)
                        return;
                com.example.licenta.adapter.EventAdapter adapter = new com.example.licenta.adapter.EventAdapter(
                                eventsList, event -> {
                                        // Invite logic: create a group for this event
                                        handleEventInvite(event);
                                });
                binding.recyclerEvents.setAdapter(adapter);
        }

        private void handleEventInvite(com.example.licenta.model.Event event) {
                User user = sessionManager.getCurrentUser();
                if (user == null) {
                        Toast.makeText(getContext(), "Trebuie să fii autentificat!", Toast.LENGTH_SHORT).show();
                        return;
                }

                long eventTimeMillis = System.currentTimeMillis();
                String displayTime = event.time != null ? event.time.trim() : "TBA";
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

                com.example.licenta.data.AppDatabase db = com.example.licenta.data.AppDatabase.getInstance(requireContext());
                com.example.licenta.model.PlannedActivity activity = new com.example.licenta.model.PlannedActivity(
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
                        com.example.licenta.data.SupabaseSyncManager.getInstance(requireContext())
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

        private boolean isTypeMatchingInterests(String placeType, String userInterests) {
                if (placeType == null || userInterests == null || userInterests.isEmpty()) return false;
                
                String placeTypeLower = placeType.toLowerCase();
                
                for (String interest : userInterests.split(",")) {
                        String interestLower = interest.trim().toLowerCase();
                        
                        if ((interestLower.contains("muz") || interestLower.contains("mus") || interestLower.contains("artă")) && 
                            (placeTypeLower.contains("museum") || placeTypeLower.contains("art") || placeTypeLower.contains("landmark"))) return true;
                        if ((interestLower.contains("rest") || interestLower.contains("food")) && 
                            (placeTypeLower.contains("restaurant") || placeTypeLower.contains("food") || placeTypeLower.contains("dining"))) return true;
                        if ((interestLower.contains("parc") || interestLower.contains("natur")) && 
                            (placeTypeLower.contains("park") || placeTypeLower.contains("nature") || placeTypeLower.contains("garden"))) return true;
                        if ((interestLower.contains("caf") || interestLower.contains("cafe") || interestLower.contains("coffee")) && 
                            (placeTypeLower.contains("cafe") || placeTypeLower.contains("coffee"))) return true;
                        if ((interestLower.contains("cultur") || interestLower.contains("istor") || interestLower.contains("locuri")) && 
                            (placeTypeLower.contains("landmark") || placeTypeLower.contains("historical") || placeTypeLower.contains("culture"))) return true;
                        if ((interestLower.contains("shop")) && 
                            (placeTypeLower.contains("shop") || placeTypeLower.contains("store") || placeTypeLower.contains("mall"))) return true;
                        if ((interestLower.contains("sport") || interestLower.contains("stadium")) && 
                            (placeTypeLower.contains("sport") || placeTypeLower.contains("stadium") || placeTypeLower.contains("arena"))) return true;
                        if ((interestLower.contains("viața") || interestLower.contains("noapt")) && 
                            (placeTypeLower.contains("bar") || placeTypeLower.contains("club") || placeTypeLower.contains("night"))) return true;
                        if ((interestLower.contains("evenim")) && 
                            (placeTypeLower.contains("event") || placeTypeLower.contains("concert") || placeTypeLower.contains("stadium"))) return true;
                        if (placeTypeLower.contains(interestLower)) return true;
                }
                return false;
        }

        @Override
        public void onDestroyView() {
                super.onDestroyView();
                binding = null;
        }
}
