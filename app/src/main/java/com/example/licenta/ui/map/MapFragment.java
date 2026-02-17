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

        // Add sample markers
        addPlaceMarkers();

        // Enable location if permission granted
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }
    }

    private void addPlaceMarkers() {
        // Sample places
        mMap.addMarker(new MarkerOptions()
                .position(new LatLng(44.4323, 26.1037))
                .title("Caru' cu Bere"));

        mMap.addMarker(new MarkerOptions()
                .position(new LatLng(44.4296, 26.1003))
                .title("Origo Coffee Shop"));

        mMap.addMarker(new MarkerOptions()
                .position(new LatLng(44.4412, 26.0958))
                .title("Cișmigiu Park"));

        mMap.addMarker(new MarkerOptions()
                .position(new LatLng(44.4275, 26.0878))
                .title("National Art Museum"));
    }
}
