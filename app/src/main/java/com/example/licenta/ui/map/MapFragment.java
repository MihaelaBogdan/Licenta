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

        // Center on Bucharest
        LatLng bucharest = new LatLng(44.4268, 26.1025);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(bucharest, 14f));

        // Fetch places from real backend via Retrofit
        loadPlacesFromBackend();

        // Enable location if permission granted
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }
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
                    for (com.example.licenta.model.Place place : response.body()) {
                        // Check local session for favorite status
                        boolean isFav = sessionManager.isPlaceFavorite(place.id);

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
