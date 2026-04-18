package com.cityscape.app.ui.map;

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
import com.cityscape.app.R;
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
                    centerMapOnLocation(location);
                } else {
                    // Try to request a fresh location if last location is null
                    com.google.android.gms.location.LocationRequest locationRequest = com.google.android.gms.location.LocationRequest.create()
                            .setPriority(com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY)
                            .setInterval(1000)
                            .setFastestInterval(500)
                            .setNumUpdates(1);
                    
                    fusedLocationClient.requestLocationUpdates(locationRequest, new com.google.android.gms.location.LocationCallback() {
                        @Override
                        public void onLocationResult(@NonNull com.google.android.gms.location.LocationResult locationResult) {
                            if (locationResult.getLastLocation() != null) {
                                centerMapOnLocation(locationResult.getLastLocation());
                            } else {
                                moveToDefaultLocation();
                            }
                        }
                    }, android.os.Looper.getMainLooper());
                }
            });
        } else {
            moveToDefaultLocation();
        }
    }

    private void centerMapOnLocation(android.location.Location location) {
        LatLng userLoc = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLoc, 14f));
        loadPlacesFromBackend(); // Get static supabase
        loadNearby(location.getLatitude(), location.getLongitude()); // Get Foursquare data
        loadEvents(location.getLatitude(), location.getLongitude()); // Get Ticketmaster
    }

    private void moveToDefaultLocation() {
        // Move to a neutral global view or a user-specified home city if location fails
        LatLng defaultLoc = new LatLng(0, 0); // Null island as absolute fallback
        
        // We will improve this to check SharedPreferences for a "Preferred City"
        com.cityscape.app.data.SessionManager sessionManager = new com.cityscape.app.data.SessionManager(requireContext());
        String preferredCity = sessionManager.getPreferredCity();
        
        if (preferredCity != null && !preferredCity.isEmpty()) {
            try {
                android.location.Geocoder geocoder = new android.location.Geocoder(requireContext());
                java.util.List<android.location.Address> addresses = geocoder.getFromLocationName(preferredCity, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    defaultLoc = new LatLng(addresses.get(0).getLatitude(), addresses.get(0).getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLoc, 11f));
                    loadPlacesFromBackend();
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLoc, 2f)); // Zoom out to world if no city set
        loadPlacesFromBackend();
    }

    private void loadNearby(double lat, double lng) {
        com.cityscape.app.api.ApiService apiService = com.cityscape.app.api.ApiClient.getClient()
                .create(com.cityscape.app.api.ApiService.class);
        apiService.getNearby(lat, lng, "mixed").enqueue(new retrofit2.Callback<java.util.List<com.cityscape.app.model.Place>>() {
            @Override
            public void onResponse(retrofit2.Call<java.util.List<com.cityscape.app.model.Place>> call,
                    retrofit2.Response<java.util.List<com.cityscape.app.model.Place>> response) {
                if (response.isSuccessful() && response.body() != null && mMap != null) {
                    for (com.cityscape.app.model.Place place : response.body()) {
                        LatLng pos = new LatLng(place.latitude, place.longitude);
                        MarkerOptions options = new MarkerOptions()
                                .position(pos)
                                .title(place.name)
                                .snippet(place.type != null ? place.type : "Foursquare")
                                .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                                        com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_AZURE));
                        mMap.addMarker(options);
                    }
                }
            }
            @Override
            public void onFailure(retrofit2.Call<java.util.List<com.cityscape.app.model.Place>> call, Throwable t) {}
        });
    }

    private void loadEvents(double lat, double lng) {
        com.cityscape.app.data.SessionManager sessionManager = new com.cityscape.app.data.SessionManager(requireContext());
        com.cityscape.app.model.User user = sessionManager.getCurrentUser();
        String interests = user != null && user.interests != null ? user.interests : "";

        com.cityscape.app.api.ApiService apiService = com.cityscape.app.api.ApiClient.getClient()
                .create(com.cityscape.app.api.ApiService.class);
        apiService.getEvents(lat, lng, interests).enqueue(new retrofit2.Callback<java.util.List<com.cityscape.app.model.Event>>() {
            @Override
            public void onResponse(retrofit2.Call<java.util.List<com.cityscape.app.model.Event>> call,
                    retrofit2.Response<java.util.List<com.cityscape.app.model.Event>> response) {
                if (response.isSuccessful() && response.body() != null && mMap != null) {
                    for (com.cityscape.app.model.Event event : response.body()) {
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
            public void onFailure(retrofit2.Call<java.util.List<com.cityscape.app.model.Event>> call, Throwable t) {}
        });
    }

    private void loadPlacesFromBackend() {
        com.cityscape.app.data.SessionManager sessionManager = new com.cityscape.app.data.SessionManager(
                requireContext());
        com.cityscape.app.api.ApiService apiService = com.cityscape.app.api.ApiClient.getClient()
                .create(com.cityscape.app.api.ApiService.class);
        apiService.getPlaces().enqueue(new retrofit2.Callback<java.util.List<com.cityscape.app.model.Place>>() {
            @Override
            public void onResponse(retrofit2.Call<java.util.List<com.cityscape.app.model.Place>> call,
                    retrofit2.Response<java.util.List<com.cityscape.app.model.Place>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    
                    com.cityscape.app.model.User user = sessionManager.getCurrentUser();
                    String userInterests = (user != null && user.interests != null) ? user.interests : "";

                    for (com.cityscape.app.model.Place place : response.body()) {
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
            public void onFailure(retrofit2.Call<java.util.List<com.cityscape.app.model.Place>> call, Throwable t) {
                t.printStackTrace();
            }
        });
    }
}
