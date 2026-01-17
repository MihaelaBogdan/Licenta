package com.cityscape.app.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.cityscape.app.CityScapeApp;
import com.cityscape.app.R;
import com.cityscape.app.databinding.ActivityMainBinding;
import com.cityscape.app.fragments.HomeFragment;
import com.cityscape.app.fragments.ExploreFragment;
import com.cityscape.app.fragments.FavoritesFragment;
import com.cityscape.app.fragments.ProfileFragment;
import com.google.android.material.navigation.NavigationBarView;

/**
 * Main activity hosting bottom navigation and fragments
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if logged in
        if (!CityScapeApp.getInstance().isLoggedIn()) {
            navigateToWelcome();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupBottomNavigation();

        // Load home fragment by default
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }
    }

    private void setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                fragment = new HomeFragment();
            } else if (itemId == R.id.nav_explore) {
                fragment = new ExploreFragment();
            } else if (itemId == R.id.nav_favorites) {
                fragment = new FavoritesFragment();
            } else if (itemId == R.id.nav_profile) {
                fragment = new ProfileFragment();
            }

            if (fragment != null) {
                loadFragment(fragment);
                return true;
            }
            return false;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    private void navigateToWelcome() {
        Intent intent = new Intent(this, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Navigate to chatbot
     */
    public void openChatbot() {
        Intent intent = new Intent(this, ChatbotActivity.class);
        startActivity(intent);
    }

    /**
     * Navigate to place details
     */
    public void openPlaceDetails(String placeId) {
        Intent intent = new Intent(this, PlaceDetailActivity.class);
        intent.putExtra("placeId", placeId);
        startActivity(intent);
    }

    /**
     * Navigate to city selection
     */
    public void openCitySelect() {
        Intent intent = new Intent(this, CitySelectActivity.class);
        startActivity(intent);
    }

    /**
     * Navigate to itineraries
     */
    public void openItineraries() {
        Intent intent = new Intent(this, ItineraryActivity.class);
        startActivity(intent);
    }

    /**
     * Navigate to calendar
     */
    public void openCalendar() {
        Intent intent = new Intent(this, CalendarActivity.class);
        startActivity(intent);
    }

    /**
     * Navigate to settings
     */
    public void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
