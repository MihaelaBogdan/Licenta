package com.example.licenta.ui.map;

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
import com.example.licenta.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private GoogleMap mMap;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        return view;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Apply dark map style
        try {
            mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style_dark));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Enable location if permission granted
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            
            com.google.android.gms.location.FusedLocationProviderClient fusedLocationClient =
                com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(requireActivity());
                
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    LatLng userLoc = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLoc, 14f));
                    
                    loadPlacesFromBackend(); // Get static supabase
                    loadNearby(location.getLatitude(), location.getLongitude()); // Get google API
                    loadEvents(location.getLatitude(), location.getLongitude()); // Get Ticketmaster
                } else {
                    LatLng defaultLoc = new LatLng(45.9432, 24.9668); // Center of Romania
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLoc, 6f));
                    loadPlacesFromBackend();
                }
            });
        } else {
            LatLng defaultLoc = new LatLng(45.9432, 24.9668); // Center of Romania
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLoc, 6f));
            loadPlacesFromBackend();
        }
    }

    private void loadNearby(double lat, double lng) {
        com.example.licenta.api.ApiService apiService = com.example.licenta.api.ApiClient.getClient()
                .create(com.example.licenta.api.ApiService.class);
        apiService.getNearby(lat, lng, "mixed").enqueue(new retrofit2.Callback<java.util.List<com.example.licenta.model.Place>>() {
            @Override
            public void onResponse(retrofit2.Call<java.util.List<com.example.licenta.model.Place>> call,
                    retrofit2.Response<java.util.List<com.example.licenta.model.Place>> response) {
                if (response.isSuccessful() && response.body() != null && mMap != null) {
                    for (com.example.licenta.model.Place place : response.body()) {
                        LatLng pos = new LatLng(place.latitude, place.longitude);
                        MarkerOptions options = new MarkerOptions()
                                .position(pos)
                                .title(place.name)
                                .snippet(place.type != null ? place.type : "Google Places")
                                .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                                        com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_AZURE));
                        mMap.addMarker(options);
                    }
                }
            }
            @Override
            public void onFailure(retrofit2.Call<java.util.List<com.example.licenta.model.Place>> call, Throwable t) {}
        });
    }

    private void loadEvents(double lat, double lng) {
        com.example.licenta.data.SessionManager sessionManager = new com.example.licenta.data.SessionManager(requireContext());
        com.example.licenta.model.User user = sessionManager.getCurrentUser();
        String interests = user != null && user.interests != null ? user.interests : "";

        com.example.licenta.api.ApiService apiService = com.example.licenta.api.ApiClient.getClient()
                .create(com.example.licenta.api.ApiService.class);
        apiService.getEvents(lat, lng, interests).enqueue(new retrofit2.Callback<java.util.List<com.example.licenta.model.Event>>() {
            @Override
            public void onResponse(retrofit2.Call<java.util.List<com.example.licenta.model.Event>> call,
                    retrofit2.Response<java.util.List<com.example.licenta.model.Event>> response) {
                if (response.isSuccessful() && response.body() != null && mMap != null) {
                    for (com.example.licenta.model.Event event : response.body()) {
                        if (event.latitude != 0 && event.longitude != 0) {
                            LatLng pos = new LatLng(event.latitude, event.longitude);
                            MarkerOptions options = new MarkerOptions()
                                    .position(pos)
                                    .title(event.title)
                                    .snippet("Bilet / Eveniment")
                                    .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                                            com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_VIOLET));
                            mMap.addMarker(options);
                        }
                    }
                }
            }
            @Override
            public void onFailure(retrofit2.Call<java.util.List<com.example.licenta.model.Event>> call, Throwable t) {}
        });
    }

    private void loadPlacesFromBackend() {
        com.example.licenta.data.SessionManager sessionManager = new com.example.licenta.data.SessionManager(
                requireContext());
        com.example.licenta.api.ApiService apiService = com.example.licenta.api.ApiClient.getClient()
                .create(com.example.licenta.api.ApiService.class);
        apiService.getPlaces().enqueue(new retrofit2.Callback<java.util.List<com.example.licenta.model.Place>>() {
            @Override
            public void onResponse(retrofit2.Call<java.util.List<com.example.licenta.model.Place>> call,
                    retrofit2.Response<java.util.List<com.example.licenta.model.Place>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    
                    com.example.licenta.model.User user = sessionManager.getCurrentUser();
                    String userInterests = (user != null && user.interests != null) ? user.interests : "";

                    for (com.example.licenta.model.Place place : response.body()) {
                        boolean isFav = sessionManager.isPlaceFavorite(place.id);

                        // --- LOGICĂ PENTRU RECOMANDĂRI ---
                        if (userInterests.equals("TRENDING")) {
                            // Dacă a dat "Skip" și vrea doar trending, arătăm doar locurile foarte populare / cu rating mare
                            if (place.rating < 4.5f && !isFav) {
                                continue; 
                            }
                        } else if (!userInterests.isEmpty()) {
                            // Dacă și-a ales interese, verificăm dacă categoria locului se regăsește în preferințele lui
                            boolean matchesInterest = false;
                            String placeTypeLower = (place.type != null) ? place.type.toLowerCase() : "";
                            
                            for (String interest : userInterests.split(",")) {
                                String interestLower = interest.trim().toLowerCase();
                                boolean mappedMatch = false;

                                if (placeTypeLower == null) placeTypeLower = "";
                                
                                if ((interestLower.contains("muz") || interestLower.contains("mus") || interestLower.contains("artă")) && 
                                    (placeTypeLower.contains("museum") || placeTypeLower.contains("art") || placeTypeLower.contains("landmark"))) mappedMatch = true;
                                else if ((interestLower.contains("rest") || interestLower.contains("food")) && 
                                    (placeTypeLower.contains("restaurant") || placeTypeLower.contains("food") || placeTypeLower.contains("dining"))) mappedMatch = true;
                                else if ((interestLower.contains("parc") || interestLower.contains("natur")) && 
                                    (placeTypeLower.contains("park") || placeTypeLower.contains("nature") || placeTypeLower.contains("garden"))) mappedMatch = true;
                                else if ((interestLower.contains("caf") || interestLower.contains("cafe") || interestLower.contains("coffee")) && 
                                    (placeTypeLower.contains("cafe") || placeTypeLower.contains("coffee"))) mappedMatch = true;
                                else if ((interestLower.contains("cultur") || interestLower.contains("istor") || interestLower.contains("locuri")) && 
                                    (placeTypeLower.contains("landmark") || placeTypeLower.contains("historical") || placeTypeLower.contains("culture"))) mappedMatch = true;
                                else if ((interestLower.contains("shop")) && 
                                    (placeTypeLower.contains("shop") || placeTypeLower.contains("store") || placeTypeLower.contains("mall"))) mappedMatch = true;
                                else if ((interestLower.contains("sport") || interestLower.contains("stadium")) && 
                                    (placeTypeLower.contains("sport") || placeTypeLower.contains("stadium") || placeTypeLower.contains("arena"))) mappedMatch = true;
                                else if ((interestLower.contains("viața") || interestLower.contains("noapt")) && 
                                    (placeTypeLower.contains("bar") || placeTypeLower.contains("club") || placeTypeLower.contains("night"))) mappedMatch = true;
                                else if ((interestLower.contains("evenim")) && 
                                    (placeTypeLower.contains("event") || placeTypeLower.contains("concert") || placeTypeLower.contains("stadium"))) mappedMatch = true;
                                else if (placeTypeLower.contains(interestLower)) mappedMatch = true; // Fallback exact match

                                if (mappedMatch) {
                                    matchesInterest = true;
                                    break;
                                }
                            }
                            
                            // Dacă nu îi place tipul acestui loc, nu e favorite și nici măcar nu e o locație ultra-premium, nu o arătăm
                            if (!matchesInterest && !isFav && place.rating < 4.8f) {
                                continue;
                            }
                        }
                        // -----------------------------------

                        LatLng pos = new LatLng(place.latitude, place.longitude);
                        MarkerOptions options = new MarkerOptions()
                                .position(pos)
                                .title(place.name)
                                .snippet(place.type);

                        if (isFav) {
                            options.icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                                    com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_YELLOW));
                        }

                        mMap.addMarker(options);
                    }
                }
            }

            @Override
            public void onFailure(retrofit2.Call<java.util.List<com.example.licenta.model.Place>> call, Throwable t) {
                t.printStackTrace();
            }
        });
    }
}
