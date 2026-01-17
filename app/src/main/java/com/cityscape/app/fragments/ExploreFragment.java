package com.cityscape.app.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.cityscape.app.CityScapeApp;
import com.cityscape.app.R;
import com.cityscape.app.activities.MainActivity;
import com.cityscape.app.ai.RecommendationEngine;
import com.cityscape.app.database.AppDatabase;
import com.cityscape.app.database.entities.Place;
import com.cityscape.app.databinding.FragmentExploreBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import java.util.List;

/**
 * Explore fragment with Google Maps integration
 */
public class ExploreFragment extends Fragment implements OnMapReadyCallback {

    private FragmentExploreBinding binding;
    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;
    private AppDatabase database;
    private RecommendationEngine recommendationEngine;

    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        binding = FragmentExploreBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        database = CityScapeApp.getInstance().getDatabase();
        recommendationEngine = new RecommendationEngine(requireContext());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        setupMap();
        setupListeners();
    }

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;

        // Configure map style for dark theme
        // googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(),
        // R.raw.map_style_dark));

        // Enable location if permission granted
        enableMyLocation();

        // Set default location (Bucharest)
        String cityId = CityScapeApp.getInstance().getSelectedCityId();
        loadCityOnMap(cityId);

        // Configure map UI
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);

        // Marker click listener
        googleMap.setOnMarkerClickListener(marker -> {
            String placeId = (String) marker.getTag();
            if (placeId != null && getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openPlaceDetails(placeId);
            }
            return false;
        });
    }

    private void loadCityOnMap(String cityId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            var city = database.cityDao().getCityByIdSync(cityId);
            List<Place> places = database.placeDao().getPlacesByCitySync(cityId);

            if (getActivity() != null && city != null) {
                getActivity().runOnUiThread(() -> {
                    // Center map on city
                    LatLng cityCenter = new LatLng(city.getLatitude(), city.getLongitude());
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(cityCenter, 13f));

                    // Add markers for places
                    for (Place place : places) {
                        addPlaceMarker(place);
                    }

                    binding.cityNameText.setText(city.getName());
                });
            }
        });
    }

    private void addPlaceMarker(Place place) {
        if (googleMap == null)
            return;

        LatLng position = new LatLng(place.getLatitude(), place.getLongitude());
        float hue = getCategoryHue(place.getCategory());

        var marker = googleMap.addMarker(new MarkerOptions()
                .position(position)
                .title(place.getName())
                .snippet(String.format("%.1f ★ - %s", place.getRating(), place.getCategory()))
                .icon(BitmapDescriptorFactory.defaultMarker(hue)));

        if (marker != null) {
            marker.setTag(place.getId());
        }
    }

    private float getCategoryHue(String category) {
        if (category == null)
            return BitmapDescriptorFactory.HUE_GREEN;

        switch (category) {
            case Place.CATEGORY_RESTAURANT:
                return BitmapDescriptorFactory.HUE_ORANGE;
            case Place.CATEGORY_CAFE:
                return BitmapDescriptorFactory.HUE_YELLOW;
            case Place.CATEGORY_BAR:
                return BitmapDescriptorFactory.HUE_VIOLET;
            case Place.CATEGORY_CULTURE:
                return BitmapDescriptorFactory.HUE_CYAN;
            case Place.CATEGORY_NATURE:
                return BitmapDescriptorFactory.HUE_GREEN;
            case Place.CATEGORY_SHOPPING:
                return BitmapDescriptorFactory.HUE_ROSE;
            default:
                return BitmapDescriptorFactory.HUE_GREEN;
        }
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
            getCurrentLocation();
        } else {
            requestPermissions(
                    new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    LOCATION_PERMISSION_REQUEST);
        }
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        // Optionally center on current location
                    }
                });
    }

    private void setupListeners() {
        binding.btnDiscoverNearby.setOnClickListener(v -> {
            discoverNearby();
        });

        binding.searchBar.setOnClickListener(v -> {
            // Open search
        });

        binding.btnFilter.setOnClickListener(v -> {
            // Show filter dialog
        });
    }

    private void discoverNearby() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    LOCATION_PERMISSION_REQUEST);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        String userId = CityScapeApp.getInstance().getCurrentUserId();

                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            List<RecommendationEngine.RecommendedPlace> nearby = recommendationEngine
                                    .getNearbyRecommendations(
                                            userId, location.getLatitude(), location.getLongitude(),
                                            2.0, 10);

                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    // Clear existing markers and add nearby
                                    googleMap.clear();
                                    for (var rec : nearby) {
                                        addPlaceMarker(rec.getPlace());
                                    }

                                    // Center on current location
                                    LatLng currentPos = new LatLng(location.getLatitude(), location.getLongitude());
                                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPos, 15f));
                                });
                            }
                        });
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
