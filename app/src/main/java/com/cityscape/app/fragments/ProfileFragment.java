package com.cityscape.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.cityscape.app.CityScapeApp;
import com.cityscape.app.activities.SettingsActivity;
import com.cityscape.app.activities.WelcomeActivity;
import com.cityscape.app.adapters.BadgeAdapter;
import com.cityscape.app.database.AppDatabase;
import com.cityscape.app.database.entities.Badge;
import com.cityscape.app.database.entities.User;
import com.cityscape.app.databinding.FragmentProfileBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Profile fragment with gamification stats and badges
 */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private AppDatabase database;
    private BadgeAdapter badgeAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        database = CityScapeApp.getInstance().getDatabase();

        setupBadgesRecycler();
        setupListeners();
        loadUserData();
    }

    private void setupBadgesRecycler() {
        binding.badgesRecycler.setLayoutManager(new GridLayoutManager(getContext(), 3));
        badgeAdapter = new BadgeAdapter(new ArrayList<>());
        binding.badgesRecycler.setAdapter(badgeAdapter);
    }

    private void setupListeners() {
        binding.btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), SettingsActivity.class));
        });

        binding.btnLogout.setOnClickListener(v -> {
            logout();
        });

        binding.visitHistoryCard.setOnClickListener(v -> {
            // Navigate to visit history
        });

        binding.myReviewsCard.setOnClickListener(v -> {
            // Navigate to my reviews
        });
    }

    private void loadUserData() {
        String userId = CityScapeApp.getInstance().getCurrentUserId();

        // Load user info
        database.userDao().getUserById(userId).observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                updateUserUI(user);
            }
        });

        // Load badges
        database.badgeDao().getUserBadges(userId).observe(getViewLifecycleOwner(), badges -> {
            if (badges != null) {
                badgeAdapter.updateData(badges);
                binding.badgeCountText.setText(String.valueOf(badges.size()));
            }
        });

        // Load stats
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int visitCount = database.visitDao().getVisitCount(userId);
            int reviewCount = database.reviewDao().getUserReviewCount(userId);
            int favoriteCount = database.favoriteDao().getFavoriteCountSync(userId);
            int citiesCount = database.visitDao().getVisitedCityCount(userId);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    binding.visitsCount.setText(String.valueOf(visitCount));
                    binding.reviewsCount.setText(String.valueOf(reviewCount));
                    binding.favoritesCount.setText(String.valueOf(favoriteCount));
                    binding.citiesCount.setText(String.valueOf(citiesCount));
                });
            }
        });
    }

    private void updateUserUI(User user) {
        binding.userName.setText(user.getName());
        binding.userEmail.setText(user.getEmail());
        binding.userLevel.setText("Level " + user.getLevel());
        binding.userPoints.setText(user.getTotalPoints() + " pts");

        // Calculate level progress
        int pointsInLevel = user.getTotalPoints() % 100;
        binding.levelProgress.setProgress(pointsInLevel);
    }

    private void logout() {
        CityScapeApp.getInstance().logout();

        Intent intent = new Intent(getActivity(), WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
