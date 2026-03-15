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
import java.util.Map;
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
        private List<Place> allPlacesList = new ArrayList<>(); // For Trending (whole city)
        private List<Place> nearbyPlacesList = new ArrayList<>(); // For Near You (real-time)
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
                checkLocationPermission();

                return binding.getRoot();
        }

        private void setupItineraryButton() {
                binding.btnPlanDay.setOnClickListener(v -> {
                        Log.d("HomeFragment", "Itinerary button clicked");
                        if (currentLocation != null) {
                                fetchItinerary();
                        } else {
                                Toast.makeText(getContext(), "Locația nu este disponibilă încă. Încercăm să o găsim...",
                                                Toast.LENGTH_SHORT)
                                                .show();
                                getCurrentLocation();
                        }
                });
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
                        String weatherText = String.format("%.0f°C %s", currentWeather.temp,
                                        currentWeather.condition.contains("Rain") ? "🌧️"
                                                        : (currentWeather.condition.contains("Clear") ? "☀️" : "☁️"));
                        binding.textWeather.setText(weatherText);
                }
        }

        private void fetchItinerary() {
                if (currentLocation == null) {
                        Toast.makeText(getContext(), "Locația nu este setată încă. Încercăm să o găsim...",
                                        Toast.LENGTH_SHORT).show();
                        return;
                }
                Toast.makeText(getContext(), "Generăm planul perfect...", Toast.LENGTH_SHORT).show();

                android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(getContext());
                progressDialog.setMessage("Generăm itinerariul tău magic...");
                progressDialog.setCancelable(false);
                progressDialog.show();

                apiService.getItinerary(currentLocation.getLatitude(), currentLocation.getLongitude())
                                .enqueue(new Callback<List<com.example.licenta.api.ItineraryItem>>() {
                                        @Override
                                        public void onResponse(Call<List<com.example.licenta.api.ItineraryItem>> call,
                                                        Response<List<com.example.licenta.api.ItineraryItem>> response) {
                                                progressDialog.dismiss();
                                                if (response.isSuccessful() && response.body() != null) {
                                                        showItineraryDialog(response.body());
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

        private void showItineraryDialog(List<com.example.licenta.api.ItineraryItem> items) {
                if (getContext() == null || binding == null)
                        return;

                if (items == null || items.isEmpty()) {
                        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                                        .setTitle("Oups!")
                                        .setMessage("Nu am găsit destule locații deschise în apropiere pentru un plan complet. Mai încearcă mai târziu!")
                                        .setPositiveButton("Ok", null)
                                        .show();
                        return;
                }

                // Navigate to the pro itinerary fragment
                String json = new com.google.gson.Gson().toJson(items);
                Bundle args = new Bundle();
                args.putString("itinerary_json", json);
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
                        Toast.makeText(getContext(), "Căutăm locația ta...", Toast.LENGTH_SHORT).show();
                        getCurrentLocation();
                });
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
                binding.recyclerRecommended.setLayoutManager(new LinearLayoutManager(getContext()));
                fetchPlaces(false);
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
                        if (currentCategory.equals("Cafenele"))
                                type = "cafe";
                        if (currentCategory.equals("Parcuri"))
                                type = "park";
                        if (currentCategory.equals("Muzee"))
                                type = "museum";
                        if (isAllCategory(currentCategory))
                                type = "mixed";

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
                Log.d("HomeFragment", "updateFilters called. Current places: "
                                + (allPlacesList != null ? allPlacesList.size() : "null"));
                if (allPlacesList == null || allPlacesList.isEmpty()) {
                        Log.w("HomeFragment", "No places to filter!");
                        return;
                }

                String targetCat = currentCategory.toLowerCase();
                Map<String, Integer> preferences = sessionManager.getPreferredCategories(allPlacesList);
                boolean hasPreferences = !preferences.isEmpty();

                // Update UI Titles based on Location
                if (binding != null) {
                        if (currentLocation != null) {
                                try {
                                        Geocoder geocoder = new Geocoder(requireContext(),
                                                        java.util.Locale.getDefault());
                                        List<Address> addresses = geocoder.getFromLocation(
                                                        currentLocation.getLatitude(), currentLocation.getLongitude(),
                                                        1);
                                        if (addresses != null && !addresses.isEmpty()) {
                                                String city = addresses.get(0).getLocality();
                                                binding.textLocation.setText(
                                                                city != null ? city : getString(R.string.bucharest));
                                        }
                                } catch (Exception e) {
                                        Log.e("HomeFragment", "Geocoder fail", e);
                                }
                        }

                        if (hasPreferences && isAllCategory(currentCategory) && searchQuery.isEmpty()) {
                                binding.trendingTitle.setText("Special pentru tine");
                        } else {
                                binding.trendingTitle.setText(getString(R.string.trending_now));
                        }
                }

                // Start with Supabase places (Whole City)
                List<Place> filtered = new ArrayList<>(allPlacesList);

                // Add Google places (Nearby) and avoid duplicates
                for (Place googlePlace : nearbyPlacesList) {
                        boolean exists = false;
                        for (Place existing : filtered) {
                                if (existing.name.equalsIgnoreCase(googlePlace.name) ||
                                                (existing.googlePlaceId != null
                                                                && existing.googlePlaceId.equals(googlePlace.id))) {
                                        exists = true;
                                        break;
                                }
                        }
                        if (!exists)
                                filtered.add(googlePlace);
                }

                List<Place> finalFiltered = new ArrayList<>();
                for (Place p : filtered) {
                        String pName = p.name != null ? p.name.toLowerCase() : "";
                        String pType = p.type != null ? p.type.toLowerCase() : "";

                        boolean matchesCategory = isAllCategory(currentCategory);
                        // Robust category matching
                        if (currentCategory.equals("Restaurante") && (pType.contains("restaurant")
                                        || pType.contains("food") || pType.contains("dining")))
                                matchesCategory = true;
                        if (currentCategory.equals("Cafenele") && (pType.contains("cafe") || pType.contains("coffee")
                                        || pType.contains("bar") || pType.contains("breakfast")))
                                matchesCategory = true;
                        if (currentCategory.equals("Parcuri") && (pType.contains("park") || pType.contains("garden")
                                        || pType.contains("nature") || pType.contains("forest")))
                                matchesCategory = true;
                        if (currentCategory.equals("Muzee") && (pType.contains("museum") || pType.contains("art")
                                        || pType.contains("history") || pType.contains("gallery")))
                                matchesCategory = true;

                        boolean matchesSearch = pName.contains(searchQuery.toLowerCase()) ||
                                        pType.contains(searchQuery.toLowerCase()) ||
                                        (p.address != null
                                                        && p.address.toLowerCase().contains(searchQuery.toLowerCase()));

                        if (matchesCategory && matchesSearch)
                                finalFiltered.add(p);
                }

                // Personalization Engine: Rating + User Behavior (Favorites/Visits)
                Collections.sort(finalFiltered, (p1, p2) -> {
                        double score1 = p1.rating;
                        double score2 = p2.rating;

                        // Boost based on category preference (from visits/favorites)
                        if (preferences.containsKey(p1.type))
                                score1 += (preferences.get(p1.type) * 0.5);
                        if (preferences.containsKey(p2.type))
                                score2 += (preferences.get(p2.type) * 0.5);

                        // Weather context boost
                        if (currentWeather != null) {
                                boolean isBadWeather = currentWeather.condition.equalsIgnoreCase("Rain") ||
                                                currentWeather.condition.equalsIgnoreCase("Snow") ||
                                                currentWeather.temp < 10;

                                String t1 = p1.type != null ? p1.type.toLowerCase() : "";
                                String t2 = p2.type != null ? p2.type.toLowerCase() : "";

                                if (isBadWeather) {
                                        // Favor indoor: Cafe, Museum, Restaurant
                                        if (t1.contains("cafe") || t1.contains("museum") || t1.contains("food")
                                                        || t1.contains("restaurant"))
                                                score1 += 2.0;
                                        if (t2.contains("cafe") || t2.contains("museum") || t2.contains("food")
                                                        || t2.contains("restaurant"))
                                                score2 += 2.0;
                                        // Penalize outdoor
                                        if (t1.contains("park"))
                                                score1 -= 3.0;
                                        if (t2.contains("park"))
                                                score2 -= 3.0;
                                } else if (currentWeather.temp > 18) {
                                        // Favor outdoor: Park, Garden
                                        if (t1.contains("park") || t1.contains("garden"))
                                                score1 += 2.0;
                                        if (t2.contains("park") || t2.contains("garden"))
                                                score2 += 2.0;
                                }
                        }

                        // Small boost for actual favorites to keep them top of mind
                        if (sessionManager.isPlaceFavorite(p1.id))
                                score1 += 2.0;
                        if (sessionManager.isPlaceFavorite(p2.id))
                                score2 += 2.0;

                        return Double.compare(score2, score1);
                });

                // Ensure all items in finalFiltered have valid images
                for (Place p : finalFiltered) {
                        if (p.imageUrl == null || p.imageUrl.isEmpty() || p.imageUrl.contains("placeholder")) {
                                p.imageUrl = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=800&q=80";
                        }
                }

                updateRecommendedAdapter(finalFiltered);

                // Update Near You section - Strictly Real-time Nearby
                List<Place> nearYou = new ArrayList<>(nearbyPlacesList);
                if (nearYou.isEmpty() && currentLocation == null) {
                        // Fallback only if no GPS and no data
                        nearYou = new ArrayList<>(allPlacesList);
                        Collections.sort(nearYou, (p1, p2) -> Double.compare(p2.rating, p1.rating));
                }

                int limit = Math.min(10, nearYou.size());
                updateNearYouAdapter(nearYou.subList(0, limit));
        }

        private boolean isAllCategory(String cat) {
                return cat.equalsIgnoreCase("All") || cat.equalsIgnoreCase("Toate") || cat.isEmpty();
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

        @Override
        public void onDestroyView() {
                super.onDestroyView();
                binding = null;
        }
}
