package com.cityscape.app.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.cityscape.app.CityScapeApp;
import com.cityscape.app.R;
import com.cityscape.app.databinding.ActivitySettingsBinding;

/**
 * Settings activity for app preferences
 */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupUI();
        setupListeners();
    }

    private void setupUI() {
        // Set current dark mode state
        binding.darkModeSwitch.setChecked(CityScapeApp.getInstance().isDarkMode());

        // Set current notification state
        binding.notificationSwitch.setChecked(true); // Default on
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        // Dark mode toggle
        binding.darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            CityScapeApp.getInstance().setDarkMode(isChecked);
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        // Edit profile
        binding.editProfileItem.setOnClickListener(v -> {
            // Navigate to edit profile
        });

        // Change city
        binding.changeCityItem.setOnClickListener(v -> {
            startActivity(new android.content.Intent(this, CitySelectActivity.class));
        });

        // Privacy policy
        binding.privacyItem.setOnClickListener(v -> {
            // Open privacy policy
        });

        // Terms
        binding.termsItem.setOnClickListener(v -> {
            // Open terms
        });

        // About
        binding.aboutItem.setOnClickListener(v -> {
            // Show about dialog
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
