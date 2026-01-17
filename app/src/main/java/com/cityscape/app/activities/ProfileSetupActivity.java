package com.cityscape.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cityscape.app.CityScapeApp;
import com.cityscape.app.R;
import com.cityscape.app.database.AppDatabase;
import com.cityscape.app.database.entities.UserPreference;
import com.cityscape.app.databinding.ActivityProfileSetupBinding;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

/**
 * Profile setup activity for collecting user preferences
 */
public class ProfileSetupActivity extends AppCompatActivity {

    private ActivityProfileSetupBinding binding;
    private AppDatabase database;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        database = CityScapeApp.getInstance().getDatabase();
        userId = CityScapeApp.getInstance().getCurrentUserId();

        setupListeners();
    }

    private void setupListeners() {
        binding.btnContinue.setOnClickListener(v -> savePreferences());
        binding.btnSkip.setOnClickListener(v -> skipSetup());
    }

    private void savePreferences() {
        List<String> selectedCategories = new ArrayList<>();
        List<String> selectedAtmospheres = new ArrayList<>();

        // Get selected categories
        for (int i = 0; i < binding.categoryChipGroup.getChildCount(); i++) {
            Chip chip = (Chip) binding.categoryChipGroup.getChildAt(i);
            if (chip.isChecked()) {
                selectedCategories.add(chip.getText().toString());
            }
        }

        // Get selected atmospheres
        for (int i = 0; i < binding.atmosphereChipGroup.getChildCount(); i++) {
            Chip chip = (Chip) binding.atmosphereChipGroup.getChildAt(i);
            if (chip.isChecked()) {
                selectedAtmospheres.add(chip.getText().toString());
            }
        }

        // Get budget preference
        String budget = "medium";
        int budgetId = binding.budgetRadioGroup.getCheckedRadioButtonId();
        if (budgetId == R.id.budgetLow)
            budget = "low";
        else if (budgetId == R.id.budgetHigh)
            budget = "high";

        // Validate - need at least one category
        if (selectedCategories.isEmpty()) {
            Toast.makeText(this, "Please select at least one category of interest", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save preferences
        String categoryPrefs = String.join(",", selectedCategories);
        String atmospherePrefs = String.join(",", selectedAtmospheres);

        UserPreference preference = new UserPreference(userId, "categories", categoryPrefs, 1.0f);
        UserPreference atmospherePref = new UserPreference(userId, "atmosphere", atmospherePrefs, 0.8f);
        UserPreference budgetPref = new UserPreference(userId, "budget", budget, 0.7f);

        String finalBudget = budget;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.userPreferenceDao().insert(preference);
            database.userPreferenceDao().insert(atmospherePref);
            database.userPreferenceDao().insert(budgetPref);

            // Mark profile as complete
            CityScapeApp.getInstance().setProfileComplete(true);

            runOnUiThread(() -> {
                navigateToMain();
            });
        });
    }

    private void skipSetup() {
        CityScapeApp.getInstance().setProfileComplete(true);
        navigateToMain();
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
