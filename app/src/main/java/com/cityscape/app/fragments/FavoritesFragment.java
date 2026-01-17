package com.cityscape.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cityscape.app.CityScapeApp;
import com.cityscape.app.R;
import com.cityscape.app.activities.MainActivity;
import com.cityscape.app.adapters.PlaceListAdapter;
import com.cityscape.app.database.AppDatabase;
import com.cityscape.app.database.entities.Place;
import com.cityscape.app.databinding.FragmentFavoritesBinding;

import java.util.ArrayList;

/**
 * Favorites fragment showing saved places
 */
public class FavoritesFragment extends Fragment {

    private FragmentFavoritesBinding binding;
    private AppDatabase database;
    private PlaceListAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        binding = FragmentFavoritesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        database = CityScapeApp.getInstance().getDatabase();

        setupRecyclerView();
        loadFavorites();
    }

    private void setupRecyclerView() {
        binding.favoritesRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PlaceListAdapter(new ArrayList<>(), place -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openPlaceDetails(place.getId());
            }
        });
        binding.favoritesRecycler.setAdapter(adapter);
    }

    private void loadFavorites() {
        String userId = CityScapeApp.getInstance().getCurrentUserId();

        database.favoriteDao().getFavoritePlaces(userId).observe(getViewLifecycleOwner(), places -> {
            if (places != null && !places.isEmpty()) {
                adapter.updateData(places);
                binding.emptyState.setVisibility(View.GONE);
                binding.favoritesRecycler.setVisibility(View.VISIBLE);
            } else {
                binding.emptyState.setVisibility(View.VISIBLE);
                binding.favoritesRecycler.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
