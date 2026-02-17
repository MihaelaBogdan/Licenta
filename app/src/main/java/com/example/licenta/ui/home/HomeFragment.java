package com.example.licenta.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.licenta.adapter.PlaceAdapter;
import com.example.licenta.api.ApiClient;
import com.example.licenta.api.ApiService;
import com.example.licenta.data.SessionManager;
import com.example.licenta.databinding.FragmentHomeBinding;
import com.example.licenta.model.Place;
import com.example.licenta.model.User;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {

        private FragmentHomeBinding binding;
        private ApiService apiService;
        private SessionManager sessionManager;

        public View onCreateView(@NonNull LayoutInflater inflater,
                        ViewGroup container, Bundle savedInstanceState) {
                binding = FragmentHomeBinding.inflate(inflater, container, false);

                apiService = ApiClient.getClient().create(ApiService.class);
                sessionManager = new SessionManager(requireContext());

                setupGreeting();
                setupRecyclers();

                return binding.getRoot();
        }

        private void setupGreeting() {
                User user = sessionManager.getCurrentUser();
                if (user != null) {
                        String greeting = getGreetingForTime();
                        binding.textGreeting.setText(greeting + ", " + user.name);
                }
        }

        private String getGreetingForTime() {
                int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                if (hour >= 5 && hour < 12) {
                        return "Good morning";
                } else if (hour >= 12 && hour < 17) {
                        return "Good afternoon";
                } else if (hour >= 17 && hour < 21) {
                        return "Good evening";
                } else {
                        return "Good night";
                }
        }

        private void setupRecyclers() {
                binding.recyclerNearYou.setLayoutManager(
                                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                binding.recyclerRecommended.setLayoutManager(new LinearLayoutManager(getContext()));

                fetchPlaces();
        }

        private void fetchPlaces() {
                Call<List<Place>> call = apiService.getPlaces();
                call.enqueue(new Callback<List<Place>>() {
                        @Override
                        public void onResponse(Call<List<Place>> call, Response<List<Place>> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                        List<Place> allPlaces = response.body();

                                        List<Place> nearYouList = new ArrayList<>();
                                        List<Place> recommendedList = new ArrayList<>();

                                        for (int i = 0; i < allPlaces.size(); i++) {
                                                if (i < 3) {
                                                        nearYouList.add(allPlaces.get(i));
                                                } else {
                                                        recommendedList.add(allPlaces.get(i));
                                                }
                                        }

                                        PlaceAdapter nearYouAdapter = new PlaceAdapter(getContext(), nearYouList,
                                                        place -> {
                                                                sessionManager.recordPlaceVisit(place.name);
                                                                Toast.makeText(getContext(),
                                                                                "Visited: " + place.name + " (+50 XP)",
                                                                                Toast.LENGTH_SHORT).show();
                                                        });
                                        binding.recyclerNearYou.setAdapter(nearYouAdapter);

                                        PlaceAdapter recommendedAdapter = new PlaceAdapter(getContext(),
                                                        recommendedList,
                                                        place -> {
                                                                sessionManager.recordPlaceVisit(place.name);
                                                                Toast.makeText(getContext(),
                                                                                "Visited: " + place.name + " (+50 XP)",
                                                                                Toast.LENGTH_SHORT).show();
                                                        });
                                        binding.recyclerRecommended.setAdapter(recommendedAdapter);
                                } else {
                                        Toast.makeText(getContext(), "Failed to load places", Toast.LENGTH_SHORT)
                                                        .show();
                                }
                        }

                        @Override
                        public void onFailure(Call<List<Place>> call, Throwable t) {
                                Log.e("HomeFragment", "Error fetching places", t);
                                Toast.makeText(getContext(), "Error connecting to server", Toast.LENGTH_SHORT).show();
                        }
                });
        }

        @Override
        public void onDestroyView() {
                super.onDestroyView();
                binding = null;
        }
}
