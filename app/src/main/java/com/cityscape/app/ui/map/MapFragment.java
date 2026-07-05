package com.cityscape.app.ui.map;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import com.cityscape.app.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    // Marker category constants
    private static final String CAT_NEARBY     = "nearby";
    private static final String CAT_FAVORITE   = "favorite";
    private static final String CAT_TRENDING   = "trending";
    private static final String CAT_RESTAURANT = "restaurant";
    private static final String CAT_CAFE       = "cafe";
    private static final String CAT_PARK       = "park";
    private static final String CAT_MUSEUM     = "museum";
    private static final String CAT_EVENT      = "event";
    private static final String CAT_NIGHTLIFE  = "nightlife";

    // Helper class to hold marker details for the bottom card
    private static class MarkerData {
        com.cityscape.app.model.Place place;
        com.cityscape.app.model.Event event;
        boolean isEvent;

        MarkerData(com.cityscape.app.model.Place place) {
            this.place = place;
            this.isEvent = false;
        }

        MarkerData(com.cityscape.app.model.Event event) {
            this.event = event;
            this.isEvent = true;
        }
    }

    private static class MapItem {
        Marker marker;
        java.util.Set<String> categories = new java.util.HashSet<>();
        boolean isFavorite;

        MapItem(Marker marker, java.util.Set<String> cats, boolean isFav) {
            this.marker = marker;
            this.categories = cats;
            this.isFavorite = isFav;
        }
    }

    private GoogleMap mMap;
    private final java.util.List<MapItem> mapItems = new java.util.ArrayList<>();
    private android.location.Location mUserLocation;
    private com.cityscape.app.data.SessionManager sessionManager;
    private android.speech.tts.TextToSpeech textToSpeech;

    // Filter chips
    private com.google.android.material.chip.Chip chipNearby, chipFavorites, chipTrending;
    private com.google.android.material.chip.Chip chipRestaurants, chipCafes, chipParks, chipMuseums, chipEvents, chipNightlife;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        if (getContext() != null)
            sessionManager = new com.cityscape.app.data.SessionManager(getContext());

        textToSpeech = new android.speech.tts.TextToSpeech(getContext(), status -> {
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                if (textToSpeech != null) {
                    textToSpeech.setLanguage(new java.util.Locale("ro", "RO"));
                }
            }
        });

        chipNearby      = view.findViewById(R.id.chip_filter_nearby);
        chipFavorites   = view.findViewById(R.id.chip_filter_favorites);
        chipTrending    = view.findViewById(R.id.chip_filter_trending);
        chipRestaurants = view.findViewById(R.id.chip_filter_restaurants);
        chipCafes       = view.findViewById(R.id.chip_filter_cafes);
        chipParks       = view.findViewById(R.id.chip_filter_parks);
        chipMuseums     = view.findViewById(R.id.chip_filter_museums);
        chipEvents      = view.findViewById(R.id.chip_filter_events);
        chipNightlife   = view.findViewById(R.id.chip_filter_nightlife);

        com.google.android.material.chip.Chip[] allChips = {
            chipNearby, chipFavorites, chipTrending, chipRestaurants,
            chipCafes, chipParks, chipMuseums, chipEvents, chipNightlife
        };
        for (com.google.android.material.chip.Chip chip : allChips) {
            if (chip != null) chip.setOnCheckedChangeListener((btn, checked) -> applyFilters());
        }

        EditText searchInput = view.findViewById(R.id.map_search_input);
        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyFilters();
                }
                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton btnMyLocation =
                view.findViewById(R.id.btn_my_location);
        if (btnMyLocation != null) {
            btnMyLocation.setOnClickListener(v -> {
                // Animație de confirmare pe buton
                btnMyLocation.setEnabled(false);
                btnMyLocation.setText(getString(R.string.btn_searching));
                centerMapOnUserLocation();
                // Reactivează butonul după 3s
                btnMyLocation.postDelayed(() -> {
                    if (isAdded()) {
                        btnMyLocation.setEnabled(true);
                        btnMyLocation.setText(getString(R.string.btn_my_location));
                    }
                }, 3000);
            });
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroyView();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        if (!isAdded()) return;
        mMap = googleMap;

        try {
            if (getContext() != null && sessionManager != null) {
                int styleRes = sessionManager.isDarkMode() ? R.raw.map_style_dark : R.raw.map_style_light;
                googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(getContext(), styleRes));
            }
        } catch (Exception ignored) {}

        mMap.setOnMarkerClickListener(marker -> {
            marker.showInfoWindow();
            showBottomCard(marker);
            return true;
        });

        mMap.setOnMapClickListener(latLng -> hideBottomCard());

        // Handle deep-link arguments (from chatbot)
        Bundle args = getArguments();
        if (args != null && args.containsKey("latitude") && args.containsKey("longitude")) {
            double targetLat = args.getDouble("latitude");
            double targetLng = args.getDouble("longitude");
            String targetName = args.getString("place_name", "Locație");
            LatLng targetPos = new LatLng(targetLat, targetLng);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(targetPos, 16f));
            
            com.cityscape.app.model.Place p = new com.cityscape.app.model.Place();
            p.name = targetName;
            p.latitude = targetLat;
            p.longitude = targetLng;
            MarkerData data = new MarkerData(p);

            Marker marker = mMap.addMarker(new MarkerOptions().position(targetPos).title(targetName)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            if (marker != null) marker.setTag(data);

            if (getContext() != null && ActivityCompat.checkSelfPermission(getContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
            }
            loadAllData(targetLat, targetLng);
            return;
        }

        if (getContext() != null && ActivityCompat.checkSelfPermission(getContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
            fetchCurrentLocationAndCenter(true);
        } else {
            moveToDefaultLocation();
        }
    }

    private void centerMapOnUserLocation() {
        if (mMap == null) return;
        if (getContext() != null && ActivityCompat.checkSelfPermission(getContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            fetchCurrentLocationAndCenter(false);
        } else {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
        }
    }

    /**
     * Cere locația GPS reală (nu din cache) și centrează harta pe ea.
     * @param loadData dacă e true, încarcă și pinii după centrare (la init).
     */
    private void fetchCurrentLocationAndCenter(boolean loadData) {
        if (!isAdded() || getContext() == null) return;

        com.google.android.gms.location.FusedLocationProviderClient client =
                com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(requireActivity());

        // getCurrentLocation() cere o locație proaspătă, nu din cache
        com.google.android.gms.tasks.CancellationTokenSource cts =
                new com.google.android.gms.tasks.CancellationTokenSource();

        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        client.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                cts.getToken()
        ).addOnSuccessListener(location -> {
            if (!isAdded()) return;
            if (location != null) {
                mUserLocation = location;
                LatLng pos = new LatLng(location.getLatitude(), location.getLongitude());
                if (loadData) {
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 14f));
                    loadAllData(location.getLatitude(), location.getLongitude());
                } else {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f));
                }
            } else {
                // GPS-ul n-a răspuns rapid → fallback la ultima locație din cache
                client.getLastLocation().addOnSuccessListener(last -> {
                    if (!isAdded()) return;
                    if (last != null) {
                        mUserLocation = last;
                        LatLng pos = new LatLng(last.getLatitude(), last.getLongitude());
                        if (loadData) {
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 14f));
                            loadAllData(last.getLatitude(), last.getLongitude());
                        } else {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f));
                        }
                    } else {
                        if (loadData) moveToDefaultLocation();
                        else android.widget.Toast.makeText(getContext(),
                                "GPS-ul nu are semnal. Activează locația și încearcă din nou.",
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).addOnFailureListener(e -> {
            if (!isAdded()) return;
            if (loadData) moveToDefaultLocation();
        });
    }

    private void loadAllData(double lat, double lng) {
        loadNearby(lat, lng);
        loadPersonalized(lat, lng);
        loadFavorites();
        loadByCategory(lat, lng, "restaurant", CAT_RESTAURANT);
        loadByCategory(lat, lng, "cafe", CAT_CAFE);
        loadByCategory(lat, lng, "park", CAT_PARK);
        loadByCategory(lat, lng, "museum", CAT_MUSEUM);
        loadByCategory(lat, lng, "night_club", CAT_NIGHTLIFE);
        loadEvents(lat, lng);
        loadTrending(lat, lng);
    }

    private void loadNearby(double lat, double lng) {
        if (!isAdded() || getContext() == null) return;
        com.cityscape.app.api.ApiService api = com.cityscape.app.api.ApiClient.getClient()
                .create(com.cityscape.app.api.ApiService.class);
        String userId = sessionManager != null ? sessionManager.getUserId() : null;
        com.cityscape.app.model.User user = sessionManager != null ? sessionManager.getCurrentUser() : null;
        String interestsStr = user != null && user.interests != null ? user.interests : "";
        String city = sessionManager != null ? sessionManager.getPreferredCity() : "București";

        new Thread(() -> {
            final boolean[] success = { false };
            try {
                // Build interests list
                java.util.List<String> interestsList = new java.util.ArrayList<>();
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
                requestBody.put("lat", lat);
                requestBody.put("lng", lng);
                requestBody.put("interests", new org.json.JSONArray(interestsList));
                requestBody.put("city_name", city != null ? city : "București");
                requestBody.put("limit", 10);
                requestBody.put("trending", true);
                requestBody.put("language", "ro");

                String baseUrl = com.cityscape.app.api.ApiClient.getBaseUrl();
                String apiUrl = baseUrl.replace("/api/", "") + "recommendations/explainable";

                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
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
                        java.util.List<com.cityscape.app.model.Place> places = com.cityscape.app.utils.RecommendationMapper
                            .mapRecommendationsToPlaces(recommendations);
                        if (places != null && !places.isEmpty()) {
                            success[0] = true;
                            // Cache in Room
                            try {
                                com.cityscape.app.data.AppDatabase.getInstance(getContext()).placeDao().insertPlaces(places);
                            } catch (Exception ignored) {}

                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    if (isAdded()) {
                                        addPlacesToMap(places);
                                    }
                                });
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Fallback to personalized recommendations if explainable check didn't succeed
            if (!success[0]) {
                api.getPersonalizedRecommendations(lat, lng, userId, "", "All", city, interestsStr)
                    .enqueue(new retrofit2.Callback<java.util.List<com.cityscape.app.model.Place>>() {
                        @Override
                        public void onResponse(retrofit2.Call<java.util.List<com.cityscape.app.model.Place>> call,
                                retrofit2.Response<java.util.List<com.cityscape.app.model.Place>> resp) {
                            if (!isAdded() || resp.body() == null || mMap == null) return;
                            
                            // Cache in Room
                            final java.util.List<com.cityscape.app.model.Place> places = resp.body();
                            new Thread(() -> {
                                try {
                                    com.cityscape.app.data.AppDatabase.getInstance(getContext()).placeDao().insertPlaces(places);
                                } catch (Exception ignored) {}
                            }).start();

                            addPlacesToMap(places);
                        }
                        @Override 
                        public void onFailure(retrofit2.Call<java.util.List<com.cityscape.app.model.Place>> c, Throwable t) {
                            // Load from Room cache on network failure
                            if (!isAdded() || getContext() == null) return;
                            new Thread(() -> {
                                try {
                                    final java.util.List<com.cityscape.app.model.Place> cached = 
                                            com.cityscape.app.data.AppDatabase.getInstance(getContext()).placeDao().getAllPlaces();
                                    if (cached != null && !cached.isEmpty()) {
                                        requireActivity().runOnUiThread(() -> {
                                            if (mMap == null || !isAdded()) return;
                                            addPlacesToMap(cached);
                                            android.widget.Toast.makeText(getContext(), getString(R.string.offline_mode), android.widget.Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                } catch (Exception ignored) {}
                            }).start();
                        }
                    });
            }
        }).start();
    }

    private void loadPersonalized(double lat, double lng) {
        // Unified with loadNearby to prevent duplicate network calls and overlapping pins
    }

    private void addPlacesToMap(java.util.List<com.cityscape.app.model.Place> places) {
        if (mMap == null || !isAdded()) return;
        for (com.cityscape.app.model.Place place : places) {
            if (place.latitude == 0 && place.longitude == 0) continue;

            MapItem existing = getExistingItem(place.latitude, place.longitude);
            if (existing != null) {
                existing.categories.add(CAT_NEARBY);
                if (place.rating >= 4.5f) existing.categories.add(CAT_TRENDING);
                applyFilterToItem(existing);
                continue;
            }

            boolean isFav = sessionManager != null && sessionManager.isPlaceFavorite(place.id);
            java.util.Set<String> cats = new java.util.HashSet<>();
            cats.add(CAT_NEARBY);
            if (place.rating >= 4.5f) cats.add(CAT_TRENDING);
            if (isFav) cats.add(CAT_FAVORITE);
            cats.addAll(categoryFromType(place.type));

            MarkerData data = new MarkerData(place);
            addMarker(place.latitude, place.longitude,
                    "✨ " + place.name, place.type,
                    BitmapDescriptorFactory.HUE_ORANGE, cats, isFav, data);
        }
    }

    private void loadFavorites() {
        if (!isAdded() || getContext() == null || sessionManager == null) return;
        String userId = sessionManager.getUserId();
        if (userId == null) return;
        com.cityscape.app.api.ApiService api = com.cityscape.app.api.ApiClient.getClient()
                .create(com.cityscape.app.api.ApiService.class);
        api.getVisited(userId)
                .enqueue(new retrofit2.Callback<java.util.List<com.cityscape.app.model.Place>>() {
            @Override
            public void onResponse(retrofit2.Call<java.util.List<com.cityscape.app.model.Place>> call,
                    retrofit2.Response<java.util.List<com.cityscape.app.model.Place>> resp) {
                if (!isAdded() || resp.body() == null || mMap == null) return;
                for (com.cityscape.app.model.Place place : resp.body()) {
                    if (place.latitude == 0 && place.longitude == 0) continue;
                    boolean isFav = sessionManager.isPlaceFavorite(place.id);
                    if (!isFav) continue;

                    MapItem existing = getExistingItem(place.latitude, place.longitude);
                    if (existing != null) {
                        existing.categories.add(CAT_FAVORITE);
                        existing.isFavorite = true;
                        if (existing.marker != null && existing.marker.getTag() == null) {
                            existing.marker.setTag(new MarkerData(place));
                        }
                        applyFilterToItem(existing);
                        continue;
                    }

                    java.util.Set<String> cats = new java.util.HashSet<>();
                    cats.add(CAT_FAVORITE);
                    cats.addAll(categoryFromType(place.type));

                    MarkerData data = new MarkerData(place);
                    addMarker(place.latitude, place.longitude,
                            "❤️ " + place.name, place.type,
                            BitmapDescriptorFactory.HUE_VIOLET, cats, true, data);
                }
            }
            @Override public void onFailure(retrofit2.Call<java.util.List<com.cityscape.app.model.Place>> c, Throwable t) {}
        });
    }

    private void loadByCategory(double lat, double lng, String type, String catTag) {
        if (!isAdded() || getContext() == null) return;
        String userId = sessionManager != null ? sessionManager.getUserId() : null;
        com.cityscape.app.api.ApiService api = com.cityscape.app.api.ApiClient.getClient()
                .create(com.cityscape.app.api.ApiService.class);
        api.getPlacesSearch(lat, lng, type, type, 15000, userId, null, java.util.Locale.getDefault().getLanguage())
                .enqueue(new retrofit2.Callback<java.util.List<com.cityscape.app.model.Place>>() {
            @Override
            public void onResponse(retrofit2.Call<java.util.List<com.cityscape.app.model.Place>> call,
                    retrofit2.Response<java.util.List<com.cityscape.app.model.Place>> resp) {
                if (!isAdded() || resp.body() == null || mMap == null) return;
                for (com.cityscape.app.model.Place place : resp.body()) {
                    if (place.latitude == 0 && place.longitude == 0) continue;

                    MapItem existing = getExistingItem(place.latitude, place.longitude);
                    if (existing != null) {
                        existing.categories.add(catTag);
                        if (place.rating >= 4.5f) existing.categories.add(CAT_TRENDING);
                        if (existing.marker != null && existing.marker.getTag() == null) {
                            existing.marker.setTag(new MarkerData(place));
                        }
                        applyFilterToItem(existing);
                        continue;
                    }

                    boolean isFav = sessionManager != null && sessionManager.isPlaceFavorite(place.id);
                    java.util.Set<String> cats = new java.util.HashSet<>();
                    cats.add(catTag);
                    if (place.rating >= 4.5f) cats.add(CAT_TRENDING);
                    if (isFav) cats.add(CAT_FAVORITE);
                    
                    float hue = hueForCat(catTag);
                    MarkerData data = new MarkerData(place);
                    addMarker(place.latitude, place.longitude, place.name, place.type, hue, cats, isFav, data);
                }
            }
            @Override public void onFailure(retrofit2.Call<java.util.List<com.cityscape.app.model.Place>> c, Throwable t) {}
        });
    }

    private void loadEvents(double lat, double lng) {
        if (!isAdded() || getContext() == null) return;
        com.cityscape.app.model.User user = sessionManager != null ? sessionManager.getCurrentUser() : null;
        String interests = user != null && user.interests != null ? user.interests : "";
        com.cityscape.app.api.ApiService api = com.cityscape.app.api.ApiClient.getClient()
                .create(com.cityscape.app.api.ApiService.class);
        String userId = sessionManager != null ? sessionManager.getUserId() : null;
        api.getEvents(lat, lng, 50, interests, userId, java.util.Locale.getDefault().getLanguage())
                .enqueue(new retrofit2.Callback<java.util.List<com.cityscape.app.model.Event>>() {
            @Override
            public void onResponse(retrofit2.Call<java.util.List<com.cityscape.app.model.Event>> call,
                    retrofit2.Response<java.util.List<com.cityscape.app.model.Event>> resp) {
                if (!isAdded() || resp.body() == null || mMap == null) return;
                for (com.cityscape.app.model.Event event : resp.body()) {
                    if (event.latitude == 0 && event.longitude == 0) continue;
                    
                    java.util.Set<String> cats = new java.util.HashSet<>();
                    cats.add(CAT_EVENT);
                    
                    MarkerData data = new MarkerData(event);
                    addMarker(event.latitude, event.longitude,
                            "🎭 " + (event.title != null ? event.title : "Eveniment"),
                            "Eveniment", BitmapDescriptorFactory.HUE_MAGENTA, cats, false, data);
                }
            }
            @Override public void onFailure(retrofit2.Call<java.util.List<com.cityscape.app.model.Event>> c, Throwable t) {}
        });
    }

    private void loadTrending(double lat, double lng) {
        if (!isAdded() || getContext() == null) return;
        String city = sessionManager != null ? sessionManager.getPreferredCity() : "București";
        if (city == null || city.isEmpty()) city = "București";
        
        com.cityscape.app.api.ApiService api = com.cityscape.app.api.ApiClient.getClient()
                .create(com.cityscape.app.api.ApiService.class);
        
        api.getTrendingStories(city)
                .enqueue(new retrofit2.Callback<java.util.List<com.cityscape.app.model.Place>>() {
            @Override
            public void onResponse(retrofit2.Call<java.util.List<com.cityscape.app.model.Place>> call,
                    retrofit2.Response<java.util.List<com.cityscape.app.model.Place>> resp) {
                if (!isAdded() || resp.body() == null || mMap == null) return;
                for (com.cityscape.app.model.Place place : resp.body()) {
                    if (place.latitude == 0 && place.longitude == 0) continue;
                    
                    java.util.Set<String> cats = new java.util.HashSet<>();
                    cats.add(CAT_TRENDING);
                    cats.add(CAT_NEARBY); // Show in Nearby too
                    cats.addAll(categoryFromType(place.type));
                    
                    boolean isFav = sessionManager != null && sessionManager.isPlaceFavorite(place.id);
                    if (isFav) cats.add(CAT_FAVORITE);
                    
                    if (place.description == null || place.description.isEmpty()) {
                        place.description = "Locație extrem de populară pe Instagram și TikTok în această săptămână.";
                    } else if (!place.description.contains("Instagram")) {
                        place.description = place.description + " (Popular pe Instagram & TikTok 🔥)";
                    }
                    
                    MarkerData data = new MarkerData(place);
                    addMarker(place.latitude, place.longitude,
                            "🔥 " + place.name, "Trending pe Instagram & TikTok",
                            BitmapDescriptorFactory.HUE_RED, cats, isFav, data);
                }
            }
            @Override public void onFailure(retrofit2.Call<java.util.List<com.cityscape.app.model.Place>> c, Throwable t) {}
        });
    }

    private void addMarker(double lat, double lng, String title, String snippet,
                           float hue, java.util.Set<String> cats, boolean isFav, MarkerData data) {
        if (mMap == null || !isAdded()) return;
        try {
            requireActivity().runOnUiThread(() -> {
                if (mMap == null || !isAdded()) return;
                Marker marker = mMap.addMarker(new MarkerOptions()
                        .position(new LatLng(lat, lng))
                        .title(title != null ? title : "Locație")
                        .snippet(snippet != null ? snippet : "")
                        .icon(BitmapDescriptorFactory.defaultMarker(hue)));
                if (marker == null) return;
                
                marker.setTag(data);
                MapItem item = new MapItem(marker, cats, isFav);
                mapItems.add(item);
                
                // Apply current filter state to new marker
                applyFilterToItem(item);
            });
        } catch (Exception ignored) {}
    }

    private MapItem getExistingItem(double lat, double lng) {
        for (MapItem item : mapItems) {
            if (item.marker == null) continue;
            LatLng pos = item.marker.getPosition();
            if (Math.abs(pos.latitude - lat) < 0.0001 && Math.abs(pos.longitude - lng) < 0.0001)
                return item;
        }
        return null;
    }

    private void applyFilters() {
        for (MapItem item : mapItems) {
            applyFilterToItem(item);
        }
    }

    private void applyFilterToItem(MapItem item) {
        if (item.marker == null) return;

        // 1. Search Query Filter
        View fragmentView = getView();
        if (fragmentView != null) {
            EditText searchInput = fragmentView.findViewById(R.id.map_search_input);
            if (searchInput != null) {
                String query = searchInput.getText().toString().toLowerCase().trim();
                if (!query.isEmpty()) {
                    String title = item.marker.getTitle() != null ? item.marker.getTitle().toLowerCase() : "";
                    String snippet = item.marker.getSnippet() != null ? item.marker.getSnippet().toLowerCase() : "";
                    if (!title.contains(query) && !snippet.contains(query)) {
                        item.marker.setVisible(false);
                        return;
                    }
                }
            }
        }

        // 2. Chip Filters
        boolean fNearby   = chipNearby      != null && chipNearby.isChecked();
        boolean fFav      = chipFavorites   != null && chipFavorites.isChecked();
        boolean fTrend    = chipTrending    != null && chipTrending.isChecked();
        boolean fRest     = chipRestaurants != null && chipRestaurants.isChecked();
        boolean fCafe     = chipCafes       != null && chipCafes.isChecked();
        boolean fPark     = chipParks       != null && chipParks.isChecked();
        boolean fMuseum   = chipMuseums     != null && chipMuseums.isChecked();
        boolean fEvent    = chipEvents      != null && chipEvents.isChecked();
        boolean fNight    = chipNightlife   != null && chipNightlife.isChecked();

        boolean hasStatusFilter = fNearby || fFav || fTrend;
        boolean hasCategoryFilter = fRest || fCafe || fPark || fMuseum || fEvent || fNight;

        if (!hasStatusFilter && !hasCategoryFilter) {
            item.marker.setVisible(true);
            return;
        }

        Object markerTag = item.marker.getTag();
        java.util.Set<String> itemCats = new java.util.HashSet<>(item.categories);
        if (markerTag instanceof MarkerData) {
            MarkerData data = (MarkerData) markerTag;
            if (data.place != null) {
                itemCats.addAll(categoryFromType(data.place.type));
            }
        }

        // Determine Nearby status (actual distance <= 6km)
        boolean isItemNearby = itemCats.contains(CAT_NEARBY);
        if (mUserLocation != null && item.marker != null) {
            float[] results = new float[1];
            android.location.Location.distanceBetween(
                mUserLocation.getLatitude(), mUserLocation.getLongitude(),
                item.marker.getPosition().latitude, item.marker.getPosition().longitude,
                results
            );
            if (results[0] <= 6000) { // 6 km limit
                isItemNearby = true;
            }
        }

        // Determine Trending status (rating >= 4.5)
        boolean isItemTrending = itemCats.contains(CAT_TRENDING);
        if (markerTag instanceof MarkerData) {
            MarkerData data = (MarkerData) markerTag;
            if (data.place != null && data.place.rating >= 4.5f) {
                isItemTrending = true;
            }
        }

        // Determine Favorite status
        boolean isItemFavorite = item.isFavorite || itemCats.contains(CAT_FAVORITE);
        if (markerTag instanceof MarkerData) {
            MarkerData data = (MarkerData) markerTag;
            if (data.place != null && sessionManager != null && sessionManager.isPlaceFavorite(data.place.id)) {
                isItemFavorite = true;
            }
        }

        // Evaluate status filters (AND logic: if checked, item must satisfy it)
        boolean statusMatch = true;
        if (fNearby && !isItemNearby) statusMatch = false;
        if (fFav && !isItemFavorite) statusMatch = false;
        if (fTrend && !isItemTrending) statusMatch = false;

        // Evaluate category filters (OR logic: if any matches, categoryMatch is true)
        boolean categoryMatch = !hasCategoryFilter;
        if (fRest && itemCats.contains(CAT_RESTAURANT)) categoryMatch = true;
        if (fCafe && itemCats.contains(CAT_CAFE)) categoryMatch = true;
        if (fPark && itemCats.contains(CAT_PARK)) categoryMatch = true;
        if (fMuseum && itemCats.contains(CAT_MUSEUM)) categoryMatch = true;
        if (fEvent && itemCats.contains(CAT_EVENT)) categoryMatch = true;
        if (fNight && itemCats.contains(CAT_NIGHTLIFE)) categoryMatch = true;

        item.marker.setVisible(statusMatch && categoryMatch);
    }

    private void showBottomCard(Marker marker) {
        View view = getView();
        if (view == null) return;

        androidx.cardview.widget.CardView card = view.findViewById(R.id.card_selected_place);
        if (card == null) return;

        Object tag = marker.getTag();
        if (!(tag instanceof MarkerData)) {
            card.setVisibility(View.GONE);
            return;
        }

        MarkerData data = (MarkerData) tag;

        TextView nameText = view.findViewById(R.id.selected_place_name);
        TextView addressText = view.findViewById(R.id.selected_place_address);
        TextView ratingText = view.findViewById(R.id.selected_place_rating);
        ImageView imageView = view.findViewById(R.id.selected_place_image);
        Button btnDirections = view.findViewById(R.id.btn_directions);

        String name = "";
        String address = "";
        float rating = 0;
        String imageUrl = "";
        double latitude = 0;
        double longitude = 0;

        if (data.isEvent && data.event != null) {
            name = data.event.title;
            address = data.event.location;
            rating = (float) data.event.googleRating;
            imageUrl = data.event.imageUrl;
            latitude = data.event.latitude;
            longitude = data.event.longitude;
        } else if (data.place != null) {
            name = data.place.name;
            address = data.place.address;
            rating = data.place.rating;
            imageUrl = data.place.imageUrl;
            latitude = data.place.latitude;
            longitude = data.place.longitude;
        }

        if (nameText != null) nameText.setText(name);
        if (addressText != null) addressText.setText(address != null && !address.isEmpty() ? address : "București");
        
        int confidence = 0;
        if (!data.isEvent && data.place != null) {
            confidence = (int) data.place.confidence;
        } else if (data.isEvent && data.event != null) {
            confidence = data.event.confidence > 0 ? data.event.confidence : data.event.relevance_score;
        }

        if (ratingText != null) {
            String ratingStr = rating > 0 ? String.format(java.util.Locale.US, "%.1f", rating) : "N/A";
            if (confidence > 0) {
                ratingText.setText(ratingStr + " · 🎯 " + confidence + "%");
            } else {
                ratingText.setText(ratingStr);
            }
        }

        if (imageView != null && isAdded()) {
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(this)
                        .load(imageUrl)
                        .placeholder(R.drawable.rounded_corners_8)
                        .error(R.drawable.rounded_corners_8)
                        .into(imageView);
            } else {
                imageView.setImageResource(R.drawable.ic_launcher_foreground);
            }
        }

        final double finalLat = latitude;
        final double finalLng = longitude;
        if (btnDirections != null) {
            btnDirections.setOnClickListener(v -> {
                try {
                    String uri = String.format(java.util.Locale.US, "google.navigation:q=%f,%f", finalLat, finalLng);
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri));
                    intent.setPackage("com.google.android.apps.maps");
                    startActivity(intent);
                } catch (Exception e) {
                    try {
                        String uri = String.format(java.util.Locale.US, "https://www.google.com/maps/search/?api=1&query=%f,%f", finalLat, finalLng);
                        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri));
                        startActivity(intent);
                    } catch (Exception ignored) {}
                }
            });
        }

        // Click on card opens premium detail dialog
        if (!data.isEvent && data.place != null) {
            card.setOnClickListener(v -> showPlaceDetailDialog(data.place));
        } else {
            card.setOnClickListener(null);
        }

        card.setVisibility(View.VISIBLE);
    }

    private void showPlaceDetailDialog(com.cityscape.app.model.Place place) {
        if (!isAdded()) return;
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme);
        View v = getLayoutInflater().inflate(R.layout.dialog_place_detail, null);
        builder.setView(v);
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        ImageView image = v.findViewById(R.id.detail_place_image);
        TextView txtType = v.findViewById(R.id.detail_place_type);
        TextView txtRating = v.findViewById(R.id.detail_place_rating);
        TextView txtWeather = v.findViewById(R.id.detail_place_weather);
        TextView txtName = v.findViewById(R.id.detail_place_name);
        TextView txtAddress = v.findViewById(R.id.detail_place_address);
        TextView txtDescription = v.findViewById(R.id.detail_place_description);
        TextView lblAiSummary = v.findViewById(R.id.lbl_ai_summary);
        Button btnDirections = v.findViewById(R.id.btn_plan_place_detail); // We rename this to "Traseu"
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
        
        if (txtAddress != null) txtAddress.setText(place.address != null && !place.address.isEmpty() ? place.address : "București");
        
        // Load image
        if (image != null) {
            if (place.imageUrl != null && !place.imageUrl.isEmpty()) {
                Glide.with(this)
                        .load(place.imageUrl)
                        .placeholder(R.drawable.rounded_corners_8)
                        .error(R.drawable.rounded_corners_8)
                        .into(image);
            } else {
                image.setImageResource(R.drawable.ic_launcher_foreground);
            }
        }

        // Weather
        if (txtWeather != null && place.latitude != 0 && place.longitude != 0) {
            com.cityscape.app.api.ApiService api = com.cityscape.app.api.ApiClient.getClient()
                    .create(com.cityscape.app.api.ApiService.class);
            api.getWeather(place.latitude, place.longitude).enqueue(new retrofit2.Callback<com.cityscape.app.api.WeatherResponse>() {
                @Override
                public void onResponse(retrofit2.Call<com.cityscape.app.api.WeatherResponse> call, retrofit2.Response<com.cityscape.app.api.WeatherResponse> response) {
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
                public void onFailure(retrofit2.Call<com.cityscape.app.api.WeatherResponse> call, Throwable t) {}
            });
        }

        boolean isEn = "en".equals(java.util.Locale.getDefault().getLanguage());
        String desc = place.aiSuggestion;
        if (desc == null || desc.isEmpty()) {
            desc = place.ai_summary;
        }
        if (desc == null || desc.isEmpty()) {
            desc = place.description;
        }
        if (desc == null || desc.isEmpty()) {
            desc = isEn ? "This location is recommended for its unique experience and excellent reviews." : "Această locație este recomandată pentru experiența sa unică și recenziile sale excelente.";
            if (lblAiSummary != null) lblAiSummary.setText(isEn ? "ℹ️ ABOUT LOCATION" : "ℹ️ DESPRE LOCAȚIE");
        } else {
            if (lblAiSummary != null) lblAiSummary.setText(isEn ? "💡 EXPLORER\'S ADVICE" : "💡 SFATUL EXPLORATORULUI");
        }
        
        if (txtDescription != null) txtDescription.setText(desc);

        if (btnDirections != null) {
            btnDirections.setText(isEn ? "Directions" : "Traseu");
            btnDirections.setOnClickListener(v1 -> {
                dialog.dismiss();
                try {
                    String uri = String.format(java.util.Locale.US, "google.navigation:q=%f,%f", place.latitude, place.longitude);
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri));
                    intent.setPackage("com.google.android.apps.maps");
                    startActivity(intent);
                } catch (Exception e) {
                    try {
                        String uri = String.format(java.util.Locale.US, "https://www.google.com/maps/search/?api=1&query=%f,%f", place.latitude, place.longitude);
                        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri));
                        startActivity(intent);
                    } catch (Exception ignored) {}
                }
            });
        }

        if (btnClose != null) {
            btnClose.setText(isEn ? "Close" : "Închide");
            btnClose.setOnClickListener(v1 -> dialog.dismiss());
        }

        // Text to Speech
        final String speechText = desc;
        if (btnSpeakAi != null) {
            btnSpeakAi.setOnClickListener(v1 -> {
                if (textToSpeech != null) {
                    textToSpeech.speak(speechText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null);
                }
            });
        }

        // AI Analysis Section
        android.view.View aiAnalysisSection = v.findViewById(R.id.aiAnalysisSection);
        TextView txtTotalConfidence = v.findViewById(R.id.txt_total_confidence);
        TextView txtFactorInterests = v.findViewById(R.id.txt_factor_interests);
        android.widget.ProgressBar progressFactorInterests = v.findViewById(R.id.progress_factor_interests);
        TextView txtFactorFreshness = v.findViewById(R.id.txt_factor_freshness);
        android.widget.ProgressBar progressFactorFreshness = v.findViewById(R.id.progress_factor_freshness);
        TextView txtFactorPopularity = v.findViewById(R.id.txt_factor_popularity);
        android.widget.ProgressBar progressFactorPopularity = v.findViewById(R.id.progress_factor_popularity);
        TextView txtFactorLevel = v.findViewById(R.id.txt_factor_level);
        android.widget.ProgressBar progressFactorLevel = v.findViewById(R.id.progress_factor_level);
        TextView txtFactorDiversity = v.findViewById(R.id.txt_factor_diversity);
        android.widget.ProgressBar progressFactorDiversity = v.findViewById(R.id.progress_factor_diversity);
        TextView txtFactorWeather = v.findViewById(R.id.txt_factor_weather);
        android.widget.ProgressBar progressFactorWeather = v.findViewById(R.id.progress_factor_weather);

        if (place.confidence > 0) {
            if (aiAnalysisSection != null) aiAnalysisSection.setVisibility(View.VISIBLE);
            if (txtTotalConfidence != null) txtTotalConfidence.setText(String.format(java.util.Locale.US, isEn ? "%.1f%% Match" : "%.1f%% Potrivire", place.confidence));
            if (txtFactorInterests != null) txtFactorInterests.setText(String.format(java.util.Locale.US, "%.1f%%", place.matchPrefsPct));
            if (progressFactorInterests != null) progressFactorInterests.setProgress(Math.round(place.matchPrefsPct));
            if (txtFactorFreshness != null) txtFactorFreshness.setText(String.format(java.util.Locale.US, "%.1f%%", place.freshnessPct));
            if (progressFactorFreshness != null) progressFactorFreshness.setProgress(Math.round(place.freshnessPct));
            if (txtFactorPopularity != null) txtFactorPopularity.setText(String.format(java.util.Locale.US, "%.1f%%", place.popularityPct));
            if (progressFactorPopularity != null) progressFactorPopularity.setProgress(Math.round(place.popularityPct));
            if (txtFactorLevel != null) txtFactorLevel.setText(String.format(java.util.Locale.US, "%.1f%%", place.userLevelPct));
            if (progressFactorLevel != null) progressFactorLevel.setProgress(Math.round(place.userLevelPct));
            if (txtFactorDiversity != null) txtFactorDiversity.setText(String.format(java.util.Locale.US, "%.1f%%", place.diversityPct));
            if (progressFactorDiversity != null) progressFactorDiversity.setProgress(Math.round(place.diversityPct));
            if (txtFactorWeather != null) txtFactorWeather.setText(String.format(java.util.Locale.US, "%.1f%%", place.weatherMatchPct));
            if (progressFactorWeather != null) progressFactorWeather.setProgress(Math.round(place.weatherMatchPct));

            // Populate criteria chips
            com.google.android.material.chip.ChipGroup dialogCriteriaChips = v.findViewById(R.id.dialog_criteria_chips);
            if (dialogCriteriaChips != null) {
                dialogCriteriaChips.removeAllViews();
                
                // User Interests chips
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

        // Vibeometer logic
        if (layoutVibeometer != null && vibeEmoji != null && vibeTitle != null && vibeDescription != null) {
            int currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            String typeLower = place.type != null ? place.type.toLowerCase() : "";
            
            String emoji = "🍃";
            String title = isEn ? "Chill Vibe (Peaceful & Relaxing)" : "Chill Vibe (Liniștit & Relaxant)";
            String descriptionStr = isEn ? "Many visitors prefer this location for relaxation and quiet." : "Mulți vizitatori preferă această locație pentru relaxare și liniște.";
            
            if (typeLower.contains("restaurant") || typeLower.contains("cafe") || typeLower.contains("club") || typeLower.contains("bar") || typeLower.contains("pub")) {
                if (currentHour >= 18 && currentHour <= 23) {
                    emoji = "🔥";
                    title = isEn ? "Hype Vibe (Extremely Crowded)" : "Hype Vibe (Extrem de aglomerat)";
                    descriptionStr = isEn ? "The atmosphere is buzzing right now! Lots of people and great vibe." : "Atmosfera este incendiară acum! Foarte mulți oameni și vibe excelent.";
                } else if (currentHour >= 12 && currentHour <= 15) {
                    emoji = "⚡";
                    title = isEn ? "Energy Vibe (Popular & Active)" : "Energy Vibe (Popular & Activ)";
                    descriptionStr = isEn ? "The location is quite busy around lunch. Maximum energy!" : "Locația este destul de populată la ora prânzului. Energie maximă!";
                } else {
                    emoji = "🍃";
                    title = isEn ? "Chill Vibe (Relaxing & Pleasant)" : "Chill Vibe (Relaxant & Plăcut)";
                    descriptionStr = isEn ? "Perfect for casual chats and relaxation off peak hours." : "Perfect pentru discuții lejere și relaxare în afara orelor de vârf.";
                }
            } else {
                if (currentHour >= 10 && currentHour <= 16) {
                    emoji = "⚡";
                    title = isEn ? "Energy Vibe (Popular & Active)" : "Energy Vibe (Popular & Activ)";
                    descriptionStr = isEn ? "Ideal time to visit. Visitors are exploring in large numbers!" : "Ora ideală de vizitat. Vizitatorii explorează în număr mare!";
                } else if (currentHour >= 17 || currentHour <= 9) {
                    emoji = "💤";
                    title = isEn ? "Peaceful Vibe (Quite Empty)" : "Peaceful Vibe (Destul de liber)";
                    descriptionStr = isEn ? "An oasis of peace and quiet at this moment. Excellent for relaxation and photos!" : "Oază de liniște și pace în acest moment. Excelent pentru relaxare și poze!";
                }
            }
            
            vibeEmoji.setText(emoji);
            vibeTitle.setText(title);
            vibeDescription.setText(descriptionStr);
        }

        // Show reviews section - load cached/fallback reviews immediately first
        if (place.reviews != null && !place.reviews.isEmpty()) {
            renderReviews(v, place.reviews);
        } else {
            loadFallbackReviews(v, place);
        }

        // Fetch live details/reviews/description asynchronously
        com.cityscape.app.api.ApiService api = com.cityscape.app.api.ApiClient.getClient()
                .create(com.cityscape.app.api.ApiService.class);
        api.getPlaceDetails(place.id, java.util.Locale.getDefault().getLanguage()).enqueue(new retrofit2.Callback<com.cityscape.app.model.Place>() {
            @Override
            public void onResponse(retrofit2.Call<com.cityscape.app.model.Place> call, retrofit2.Response<com.cityscape.app.model.Place> response) {
                if (isAdded() && response.isSuccessful() && response.body() != null) {
                    com.cityscape.app.model.Place details = response.body();
                    place.description = details.description;
                    place.aiSuggestion = details.aiSuggestion;
                    place.ai_summary = details.ai_summary;
                    place.reviews = details.reviews;
                    place.imageUrl = details.imageUrl;
                    
                    String finalDesc = place.aiSuggestion;
                    if (finalDesc == null || finalDesc.isEmpty()) {
                        finalDesc = place.ai_summary;
                    }
                    if (finalDesc == null || finalDesc.isEmpty()) {
                        finalDesc = place.description;
                    }
                    
                    if (finalDesc != null && !finalDesc.isEmpty()) {
                        if (txtDescription != null) txtDescription.setText(finalDesc);
                        if (lblAiSummary != null) lblAiSummary.setText(isEn ? "💡 EXPLORER\'S ADVICE" : "💡 SFATUL EXPLORATORULUI");
                    }
                    
                    if (place.reviews != null && !place.reviews.isEmpty()) {
                        renderReviews(v, place.reviews);
                    }
                    
                    if (image != null && place.imageUrl != null && !place.imageUrl.isEmpty()) {
                        Glide.with(requireContext())
                            .load(place.imageUrl)
                            .centerCrop()
                            .placeholder(R.drawable.rounded_corners_8)
                            .into(image);
                    }
                }
            }

            @Override
            public void onFailure(retrofit2.Call<com.cityscape.app.model.Place> call, Throwable t) {
                // Fail silently, fallbacks are already visible
            }
        });

        dialog.show();
    }

    private void hideBottomCard() {
        View view = getView();
        if (view == null) return;
        androidx.cardview.widget.CardView card = view.findViewById(R.id.card_selected_place);
        if (card != null) {
            card.setVisibility(View.GONE);
        }
    }

    // Infer category tags from Google Places type string
    private java.util.Set<String> categoryFromType(String type) {
        java.util.Set<String> cats = new java.util.HashSet<>();
        if (type == null) return cats;
        String t = type.toLowerCase();
        
        // Restaurants
        if (t.contains("restaurant") || t.contains("food") || t.contains("meal") || 
            t.contains("bistro") || t.contains("diner") || t.contains("pizzeria") || 
            t.contains("steakhouse") || t.contains("fast_food") || t.contains("fast food") ||
            t.contains("sushi") || t.contains("burger")) {
            cats.add(CAT_RESTAURANT);
        }
        
        // Cafes
        if (t.contains("cafe") || t.contains("coffee") || t.contains("bakery") || 
            t.contains("tea") || t.contains("sweet") || t.contains("donut") || 
            t.contains("gelato") || t.contains("ice_cream") || t.contains("ice cream") ||
            t.contains("pastry")) {
            cats.add(CAT_CAFE);
        }
        
        // Parks
        if (t.contains("park") || t.contains("garden") || t.contains("natural") || 
            t.contains("zoo") || t.contains("amusement") || t.contains("playground") || 
            t.contains("forest") || t.contains("lake")) {
            cats.add(CAT_PARK);
        }
        
        // Museums & Culture
        if (t.contains("museum") || t.contains("art_gallery") || t.contains("gallery") || 
            t.contains("exhibition") || t.contains("historical") || t.contains("castle") || 
            t.contains("church") || t.contains("cathedral") || t.contains("palace") || 
            t.contains("monument") || t.contains("sight") || t.contains("tourist_attraction") ||
            t.contains("tourist attraction")) {
            cats.add(CAT_MUSEUM);
        }
        
        // Nightlife
        if (t.contains("night_club") || t.contains("night club") || t.contains("bar") || 
            t.contains("club") || t.contains("pub") || t.contains("disco") || 
            t.contains("lounge") || t.contains("dance")) {
            cats.add(CAT_NIGHTLIFE);
        }
        
        return cats;
    }

    private float hueForCat(String cat) {
        switch (cat) {
            case CAT_RESTAURANT: return BitmapDescriptorFactory.HUE_RED;
            case CAT_CAFE:       return BitmapDescriptorFactory.HUE_YELLOW;
            case CAT_PARK:       return BitmapDescriptorFactory.HUE_GREEN;
            case CAT_MUSEUM:     return BitmapDescriptorFactory.HUE_CYAN;
            case CAT_NIGHTLIFE:  return BitmapDescriptorFactory.HUE_BLUE;
            default:             return BitmapDescriptorFactory.HUE_AZURE;
        }
    }

    private void moveToDefaultLocation() {
        if (!isAdded() || getContext() == null) return;
        com.cityscape.app.data.SessionManager sm = new com.cityscape.app.data.SessionManager(getContext());
        String preferredCity = sm.getPreferredCity();
        if (preferredCity != null && !preferredCity.isEmpty()) {
            try {
                android.location.Geocoder geocoder = new android.location.Geocoder(requireContext());
                java.util.List<android.location.Address> addresses = geocoder.getFromLocationName(preferredCity, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    LatLng pos = new LatLng(addresses.get(0).getLatitude(), addresses.get(0).getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 11f));
                    loadAllData(pos.latitude, pos.longitude);
                    return;
                }
            } catch (Exception ignored) {}
        }
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(0, 0), 2f));
    }

    private void loadFallbackReviews(View v, com.cityscape.app.model.Place place) {
        if (!isAdded()) return;
        java.util.List<com.cityscape.app.model.Place.Review> mockReviews = new java.util.ArrayList<>();
        
        com.cityscape.app.model.Place.Review r1 = new com.cityscape.app.model.Place.Review();
        r1.author = "Alexandru Popescu";
        r1.rating = 5f;
        r1.time = "acum 2 zile";
        r1.text = "O locație superbă, cu servire rapidă și energie excelentă! Perfect pentru un weekend relaxant.";
        mockReviews.add(r1);

        com.cityscape.app.model.Place.Review r2 = new com.cityscape.app.model.Place.Review();
        r2.author = "Andreea Stoica";
        r2.rating = 4f;
        r2.time = "acum o săptămână";
        r2.text = "Atmosferă caldă, perfectă pentru poze și ieșit cu prietenii. Cu siguranță voi mai reveni aici.";
        mockReviews.add(r2);

        place.reviews = mockReviews;
        renderReviews(v, place.reviews);
    }

    private void renderReviews(View v, java.util.List<com.cityscape.app.model.Place.Review> reviews) {
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
}

