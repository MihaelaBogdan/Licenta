package com.cityscape.app.ui.itinerary;

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
import com.cityscape.app.R;
import com.cityscape.app.api.ItineraryItem;
import android.widget.Toast;
import com.cityscape.app.api.ApiClient;
import com.cityscape.app.api.ApiService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.cityscape.app.databinding.FragmentItineraryBinding;
import com.cityscape.app.data.AppDatabase;
import com.cityscape.app.data.SessionManager;
import com.cityscape.app.data.SupabaseSyncManager;
import com.cityscape.app.model.PlannedActivity;
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
        } else if (getArguments() != null) {
            // No initial JSON? Trigger full generation from here
            Log.d("ItineraryFragment", "No initial JSON, triggering generation...");
            variants.clear();
            regenerateItinerary();
            fetchExtraVariants();
        }

        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        binding.btnRegenerate.setOnClickListener(v -> regenerateItinerary());

        binding.btnSaveItinerary.setOnClickListener(v -> saveToCalendar());

        binding.btnExportCalendar.setOnClickListener(v -> exportToSystemCalendar());

        binding.btnAddLocation.setOnClickListener(v -> showAddLocationPicker());


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
        if (itineraryItems == null) {
            itineraryItems = new java.util.ArrayList<>();
        }
        binding.rvItinerarySteps.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ItineraryStepAdapter(itineraryItems, new ItineraryStepAdapter.OnStepClickListener() {
            @Override
            public void onStepClick(ItineraryItem item) {
                if (googleMap != null) {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(item.latitude, item.longitude), 15));
                }
            }

            @Override
            public void onStepDelete(int position) {
                if (position >= 0 && position < itineraryItems.size()) {
                    itineraryItems.remove(position);
                    adapter.notifyItemRemoved(position);
                    setupMap();
                    calculateAndDisplayBudget();
                    Toast.makeText(getContext(), "Locație eliminată", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onStepSwap(int position) {
                showSwapOptions(position);
            }
        });

        boolean isEur = binding.itineraryToggleCurrency.getCheckedButtonId() == R.id.itinerary_btn_eur;
        adapter.setCurrency(isEur);

        binding.rvItinerarySteps.setAdapter(adapter);
    }

    private void showSwapOptions(int position) {
        if (position < 0 || position >= itineraryItems.size()) return;
        ItineraryItem current = itineraryItems.get(position);
        
        Toast.makeText(getContext(), "Căutăm alternative pentru " + current.slot + "...", Toast.LENGTH_SHORT).show();
        
        // Use Api to get alternatives for this specific type
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        Bundle args = getArguments();
        double lat = args != null ? args.getDouble("lat") : 0;
        double lng = args != null ? args.getDouble("lng") : 0;
        
        apiService.getItinerary(lat, lng, "nearby", 5000, current.type, 200, "", 2, 3, null).enqueue(new Callback<List<ItineraryItem>>() {
            @Override
            public void onResponse(Call<List<ItineraryItem>> call, Response<List<ItineraryItem>> response) {
                if (isAdded() && response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    showReplacementDialog(position, response.body());
                } else {
                    Toast.makeText(getContext(), "Nu am găsit alternative potrivite acum.", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<List<ItineraryItem>> call, Throwable t) {}
        });
    }

    private void showReplacementDialog(int position, List<ItineraryItem> alternatives) {
        String[] names = new String[alternatives.size()];
        for (int i = 0; i < alternatives.size(); i++) names[i] = alternatives.get(i).name;

        new android.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle(position == -1 ? "Adaugă Locație" : "Schimbă Locația")
            .setItems(names, (dialog, which) -> {
                ItineraryItem replacement = alternatives.get(which);
                
                if (position == -1) {
                    // ADD case
                    replacement.slot = "Extra";
                    replacement.time = "Selectează ora";
                    itineraryItems.add(replacement);
                    adapter.notifyItemInserted(itineraryItems.size() - 1);
                } else {
                    // REPLACE case
                    replacement.slot = itineraryItems.get(position).slot;
                    replacement.time = itineraryItems.get(position).time;
                    itineraryItems.set(position, replacement);
                    adapter.notifyItemChanged(position);
                }
                
                setupMap();
                calculateAndDisplayBudget();
            })
            .show();
    }


    private void showAddLocationPicker() {
        Toast.makeText(getContext(), "Personalizare: Adaugă o locație din apropiere", Toast.LENGTH_SHORT).show();
        // Simple mock for now: let user pick from a search
        Bundle args = getArguments();
        double lat = args != null ? args.getDouble("lat") : 0;
        double lng = args != null ? args.getDouble("lng") : 0;

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.getItinerary(lat, lng, "nearby", 5000, "mixed", 200, "", 4, 5, null).enqueue(new Callback<List<ItineraryItem>>() {
            @Override
            public void onResponse(Call<List<ItineraryItem>> call, Response<List<ItineraryItem>> response) {
                if (isAdded() && response.isSuccessful() && response.body() != null) {
                    showReplacementDialog(-1, response.body()); // -1 means ADD
                }
            }
            @Override public void onFailure(Call<List<ItineraryItem>> call, Throwable t) {}
        });
    }

    private void setupMap() {
        if (googleMap == null || itineraryItems == null || itineraryItems.isEmpty())
            return;

        googleMap.clear();
        googleMap.getUiSettings().setAllGesturesEnabled(true);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        
        // Apply dynamic map style based on theme
        try {
            com.cityscape.app.data.SessionManager sessionManager = new com.cityscape.app.data.SessionManager(requireContext());
            int styleRes = sessionManager.isDarkMode() ? R.raw.map_style_dark : R.raw.map_style_light;
            googleMap.setMapStyle(com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(requireContext(), styleRes));
        } catch (Exception e) {
            e.printStackTrace();
        }


        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        final List<LatLng> points = new java.util.ArrayList<>();

        for (int i = 0; i < itineraryItems.size(); i++) {
            ItineraryItem item = itineraryItems.get(i);
            LatLng pos = new LatLng(item.latitude, item.longitude);
            boundsBuilder.include(pos);
            points.add(pos);

            googleMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(item.slot + ": " + item.name)
                    .icon(BitmapDescriptorFactory.defaultMarker(
                            i == 0 ? BitmapDescriptorFactory.HUE_GREEN : BitmapDescriptorFactory.HUE_AZURE)));
        }

        // Animate the Polyline drawing
        final PolylineOptions polylineOptions = new PolylineOptions()
                .width(12)
                .color(Color.parseColor("#4CAF50"))
                .geodesic(true)
                .jointType(com.google.android.gms.maps.model.JointType.ROUND);

        final com.google.android.gms.maps.model.Polyline polyline = googleMap.addPolyline(polylineOptions);

        if (points.size() > 1) {
            android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofInt(0, 100);
            animator.setDuration(2000);
            animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            animator.addUpdateListener(animation -> {
                int percentage = (int) animation.getAnimatedValue();
                int pointCount = points.size();
                int pointsToShow = (pointCount * percentage) / 100;
                
                if (pointsToShow >= 1) {
                    polyline.setPoints(points.subList(0, Math.min(pointsToShow + 1, pointCount)));
                }
            });
            animator.start();
        }

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
        int budget = args.getInt("itinerary_budget", 200);
        String interests = args.getString("itinerary_interests", "");
        int duration = args.getInt("itinerary_duration", 6);
        int points = args.getInt("itinerary_points", 4);

        SessionManager sessionManager = new SessionManager(requireContext());
        String userId = sessionManager.getUserId();
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        // Fetch 2 more to have a total of 3
        for (int i = 0; i < 2; i++) {
            apiService.getItinerary(lat, lng, scope, radius, type, budget, interests, duration, points, userId).enqueue(new Callback<List<ItineraryItem>>() {
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
        int budget = args.getInt("itinerary_budget", 200);
        String interests = args.getString("itinerary_interests", "");
        int duration = args.getInt("itinerary_duration", 6);
        int points = args.getInt("itinerary_points", 4);

        // Visual feedback
        binding.btnRegenerate.setEnabled(false);
        binding.btnRegenerate.animate().rotationBy(720).setDuration(1000).start();
        Toast.makeText(getContext(), "Generăm planuri noi...", Toast.LENGTH_SHORT).show();

        int currentTabIndex = binding.itineraryTabs.getSelectedTabPosition();
        
        SessionManager sessionManager = new SessionManager(requireContext());
        String userId = sessionManager.getUserId();
        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        apiService.getItinerary(lat, lng, scope, radius, type, budget, interests, duration, points, userId).enqueue(new Callback<List<ItineraryItem>>() {
            @Override
            public void onResponse(Call<List<ItineraryItem>> call, Response<List<ItineraryItem>> response) {
                if (isAdded() && binding != null) {
                    binding.btnRegenerate.setEnabled(true);
                    if (response.isSuccessful() && response.body() != null) {
                        synchronized (variants) {
                            if (currentTabIndex < variants.size()) {
                                variants.set(currentTabIndex, response.body());
                            } else {
                                variants.add(response.body());
                            }
                            
                            // Update current view since it's the current tab
                            itineraryItems = response.body();
                            if (adapter != null) {
                                adapter.updateItems(itineraryItems);
                            }
                            setupMap();
                            calculateAndDisplayBudget();
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<List<ItineraryItem>> call, Throwable t) {
                if (isAdded() && binding != null) {
                    binding.btnRegenerate.setEnabled(true);
                    Toast.makeText(getContext(), "Regenerare eșuată", Toast.LENGTH_SHORT).show();
                }
            }
        });
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
                            item.placeId,
                            item.name,
                            item.type,
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

    /**
     * Directly exports the itinerary items to the system calendar (Google, Samsung, etc.)
     * using Intents for a seamless user experience.
     */
    private void exportToSystemCalendar() {
        if (itineraryItems == null || itineraryItems.isEmpty()) {
            Toast.makeText(getContext(), "Generează un itinerariu mai întâi!", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (view, year, month, day) -> {
            new android.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setTitle("Export în Calendar")
                .setMessage("Vrei să adaugi cele " + itineraryItems.size() + " locații în calendarul telefonului?")
                .setPositiveButton("Da, adaugă", (d, w) -> {
                    for (ItineraryItem item : itineraryItems) {
                        addEventToCalendar(item, year, month, day);
                    }
                    Toast.makeText(getContext(), "Am trimis cererile către calendarul tău!", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Anulează", null)
                .show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void addEventToCalendar(ItineraryItem item, int year, int month, int day) {
        try {
            // Parse time from slot like "09:00 - 10:30"
            String timeSlot = item.time != null ? item.time : "09:00 - 10:00";
            String[] parts = timeSlot.split(" - ");
            String startTimeStr = parts.length > 0 ? parts[0].trim() : "09:00";
            String endTimeStr = parts.length > 1 ? parts[1].trim() : "10:30";

            String[] startParts = startTimeStr.split(":");
            String[] endParts = endTimeStr.split(":");

            int startH = Integer.parseInt(startParts[0]);
            int startM = Integer.parseInt(startParts[1]);
            int endH = Integer.parseInt(endParts[0]);
            int endM = Integer.parseInt(endParts[1]);

            Calendar beginTime = Calendar.getInstance();
            beginTime.set(year, month, day, startH, startM);

            Calendar endTime = Calendar.getInstance();
            endTime.set(year, month, day, endH, endM);

            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_INSERT)
                    .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                    .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime.getTimeInMillis())
                    .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, endTime.getTimeInMillis())
                    .putExtra(android.provider.CalendarContract.Events.TITLE, "CityScape: " + item.name)
                    .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, "Planificat în aplicația CityScape.\nLăcație: " + item.address)
                    .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, item.address != null ? item.address : item.name)
                    .putExtra(android.provider.CalendarContract.Events.AVAILABILITY, android.provider.CalendarContract.Events.AVAILABILITY_BUSY);

            startActivity(intent);
        } catch (Exception e) {
            Log.e("ItineraryFragment", "Error adding to calendar", e);
        }
    }

}
