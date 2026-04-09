package com.example.licenta.ui.itinerary;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.licenta.R;
import com.example.licenta.api.ItineraryItem;
import android.widget.Toast;
import com.example.licenta.api.ApiClient;
import com.example.licenta.api.ApiService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.example.licenta.databinding.FragmentItineraryBinding;
import com.example.licenta.data.AppDatabase;
import com.example.licenta.data.SessionManager;
import com.example.licenta.data.SupabaseSyncManager;
import com.example.licenta.model.PlannedActivity;
import android.app.DatePickerDialog;
import java.util.Calendar;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PatternItem;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Arrays;
import java.util.List;

public class ItineraryFragment extends Fragment {

    private FragmentItineraryBinding binding;
    private GoogleMap googleMap;
    private List<ItineraryItem> itineraryItems;
    private List<List<ItineraryItem>> variants = new java.util.ArrayList<>();
    private ItineraryStepAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentItineraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String json = getArguments() != null ? getArguments().getString("itinerary_json") : null;
        if (json != null) {
            itineraryItems = new Gson().fromJson(json, new TypeToken<List<ItineraryItem>>() {
            }.getType());
            variants.clear();
            variants.add(itineraryItems); // Add the first one received
            
            String type = getArguments().getString("itinerary_type", "Explorare");
            if (binding.tvItineraryTitle != null) {
                binding.tvItineraryTitle.setText("Plan: " + type.substring(0, 1).toUpperCase() + type.substring(1));
            }

            fetchExtraVariants();
        }

        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        binding.btnRegenerate.setOnClickListener(v -> regenerateItinerary());

        binding.btnSaveItinerary.setOnClickListener(v -> saveToCalendar());

        binding.itineraryToggleCurrency.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                boolean isEur = checkedId == R.id.itinerary_btn_eur;
                if (adapter != null) {
                    adapter.setCurrency(isEur);
                }
                calculateAndDisplayBudget();
            }
        });

        binding.itineraryTabs
                .addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
                    @Override
                    public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                        int pos = tab.getPosition();
                        if (pos < variants.size()) {
                            itineraryItems = variants.get(pos);
                            if (adapter != null)
                                adapter.updateItems(itineraryItems);
                            setupMap();
                            calculateAndDisplayBudget();
                        }
                    }

                    @Override
                    public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {
                    }

                    @Override
                    public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {
                    }
                });

        setupList();
        calculateAndDisplayBudget();

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.itinerary_map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(map -> {
                this.googleMap = map;
                setupMap();
            });
        }
    }

    private void setupList() {
        if (itineraryItems == null)
            return;
        binding.rvItinerarySteps.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ItineraryStepAdapter(itineraryItems, item -> {
            if (googleMap != null) {
                googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(new LatLng(item.latitude, item.longitude), 15));
            }
        });

        boolean isEur = binding.itineraryToggleCurrency.getCheckedButtonId() == R.id.itinerary_btn_eur;
        adapter.setCurrency(isEur);

        binding.rvItinerarySteps.setAdapter(adapter);
    }

    private void setupMap() {
        if (googleMap == null || itineraryItems == null || itineraryItems.isEmpty())
            return;

        googleMap.clear();
        googleMap.getUiSettings().setAllGesturesEnabled(true);
        googleMap.getUiSettings().setZoomControlsEnabled(true);

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        PolylineOptions polylineOptions = new PolylineOptions()
                .width(10)
                .color(Color.parseColor("#4CAF50"))
                .geodesic(true);

        List<PatternItem> pattern = Arrays.asList(new Dash(30), new Gap(20));
        polylineOptions.pattern(pattern);

        for (int i = 0; i < itineraryItems.size(); i++) {
            ItineraryItem item = itineraryItems.get(i);
            LatLng pos = new LatLng(item.latitude, item.longitude);
            boundsBuilder.include(pos);

            googleMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(item.slot + ": " + item.name)
                    .icon(BitmapDescriptorFactory.defaultMarker(
                            i == 0 ? BitmapDescriptorFactory.HUE_GREEN : BitmapDescriptorFactory.HUE_AZURE)));

            polylineOptions.add(pos);
        }

        googleMap.addPolyline(polylineOptions);

        // Zoom to fit all markers
        googleMap.setOnMapLoadedCallback(() -> {
            try {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 150));
            } catch (Exception ignored) {
            }
        });
    }

    private void calculateAndDisplayBudget() {
        if (itineraryItems == null || itineraryItems.isEmpty())
            return;

        double totalRon = 0;
        for (ItineraryItem item : itineraryItems) {
            totalRon += item.estimatedCost;
        }

        // Improved Accuracy: Transport costs based on scope
        String scope = getArguments() != null ? getArguments().getString("scope", "nearby") : "nearby";
        double transportPerLeg = "city".equalsIgnoreCase(scope) ? 25.0 : 12.0;

        double totalTransport = (itineraryItems.size() - 1) * transportPerLeg;
        totalRon += totalTransport;

        // Add 10% safety buffer for tips/unexpected
        totalRon *= 1.1;

        boolean isEur = binding.itineraryToggleCurrency.getCheckedButtonId() == R.id.itinerary_btn_eur;
        final double EUR_RATE = 4.97;

        if (isEur) {
            double totalEur = totalRon / EUR_RATE;
            binding.tvTotalBudget.setText(String.format(java.util.Locale.getDefault(), "%.2f EUR", totalEur));
        } else {
            binding.tvTotalBudget.setText(String.format(java.util.Locale.getDefault(), "%.0f RON", totalRon));
        }
    }

    private void fetchExtraVariants() {
        Bundle args = getArguments();
        if (args == null)
            return;
        double lat = args.getDouble("lat");
        double lng = args.getDouble("lng");
        String scope = args.getString("scope", "nearby");
        String type = args.getString("itinerary_type", "exploration");
        int radius = "city".equalsIgnoreCase(scope) ? 20000 : 3000;

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        // Fetch 2 more to have a total of 3
        for (int i = 0; i < 2; i++) {
            apiService.getItinerary(lat, lng, scope, radius, type).enqueue(new Callback<List<ItineraryItem>>() {
                @Override
                public void onResponse(Call<List<ItineraryItem>> call, Response<List<ItineraryItem>> response) {
                    if (isAdded() && response.isSuccessful() && response.body() != null) {
                        synchronized (variants) {
                            variants.add(response.body());
                        }
                    }
                }

                @Override
                public void onFailure(Call<List<ItineraryItem>> call, Throwable t) {
                }
            });
        }
    }

    private void regenerateItinerary() {
        Bundle args = getArguments();
        if (args == null || binding == null)
            return;

        double lat = args.getDouble("lat");
        double lng = args.getDouble("lng");
        String scope = args.getString("scope", "nearby");
        String type = args.getString("itinerary_type", "exploration");
        int radius = "city".equalsIgnoreCase(scope) ? 20000 : 3000;

        // Visual feedback
        binding.btnRegenerate.setEnabled(false);
        binding.btnRegenerate.animate().rotationBy(720).setDuration(1000).start();
        Toast.makeText(getContext(), "Generăm planuri noi...", Toast.LENGTH_SHORT).show();

        variants.clear();
        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        final int[] successCount = { 0 };
        for (int i = 0; i < 3; i++) {
            apiService.getItinerary(lat, lng, scope, radius, type).enqueue(new Callback<List<ItineraryItem>>() {
                @Override
                public void onResponse(Call<List<ItineraryItem>> call, Response<List<ItineraryItem>> response) {
                    if (isAdded() && binding != null) {
                        if (response.isSuccessful() && response.body() != null) {
                            synchronized (variants) {
                                variants.add(response.body());
                                successCount[0]++;

                                if (successCount[0] == 1) {
                                    // Set current items and update UI
                                    itineraryItems = response.body();
                                    if (adapter != null) {
                                        adapter.updateItems(itineraryItems);
                                    } else {
                                        setupList();
                                    }
                                    setupMap();
                                    calculateAndDisplayBudget();

                                    // Reset to first tab when regenerating
                                    if (binding.itineraryTabs.getSelectedTabPosition() != 0) {
                                        binding.itineraryTabs.getTabAt(0).select();
                                    }
                                }

                                if (successCount[0] >= 3) {
                                    binding.btnRegenerate.setEnabled(true);
                                }
                            }
                        }
                    }
                }

                @Override
                public void onFailure(Call<List<ItineraryItem>> call, Throwable t) {
                    if (isAdded() && binding != null) {
                        // Allow retry if at least one fails but some might have succeeded
                        binding.btnRegenerate.setEnabled(true);
                    }
                }
            });
        }
    }

    private void saveToCalendar() {
        if (itineraryItems == null || itineraryItems.isEmpty()) {
            Toast.makeText(getContext(), "Generează un itinerariu mai întâi!", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (view, year, month, day) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, day, 0, 0, 0);
            selected.set(Calendar.MILLISECOND, 0);
            long timestamp = selected.getTimeInMillis();

            performSave(timestamp);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void performSave(long date) {
        SessionManager sessionManager = new SessionManager(requireContext());
        String userId = sessionManager.getUserId();
        if (userId == null) {
            Toast.makeText(getContext(), "Trebuie să fii autentificat!", Toast.LENGTH_SHORT).show();
            return;
        }

        AppDatabase db = AppDatabase.getInstance(requireContext());
        SupabaseSyncManager syncManager = SupabaseSyncManager.getInstance(requireContext());

        boolean isEur = binding.itineraryToggleCurrency.getCheckedButtonId() == R.id.itinerary_btn_eur;
        String currency = isEur ? "EUR" : "RON";

        new Thread(() -> {
            try {
                for (ItineraryItem item : itineraryItems) {
                    PlannedActivity activity = new PlannedActivity(
                            userId,
                            null, // placeId unknown for AI generated ones
                            item.name,
                            "Generat",
                            item.imageUrl,
                            date,
                            item.slot);
                    activity.notes = item.address;
                    activity.budget = item.estimatedCost;
                    activity.currency = currency;

                    db.activityDao().insert(activity);
                    syncManager.pushActivityToCloud(activity);
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Itinerariul a fost salvat în calendar!", Toast.LENGTH_LONG).show();
                        // Navigate to calendar to see results
                        try {
                            Navigation.findNavController(getView())
                                    .navigate(R.id.action_itineraryFragment_to_navigation_calendar);
                        } catch (Exception e) {
                            // Fallback if action not found
                            Navigation.findNavController(getView()).navigateUp();
                        }
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Eroare la salvare: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }
}
