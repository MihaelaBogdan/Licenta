package com.cityscape.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import com.cityscape.app.data.AppDatabase;
import com.cityscape.app.data.SessionManager;
import com.cityscape.app.model.User;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class InterestsActivity extends BaseActivity {
    private ChipGroup chipGroup;
    private Button btnContinue;
    private AppDatabase db;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_interests);

        db = AppDatabase.getInstance(this);
        sessionManager = new SessionManager(this);
        
        chipGroup = findViewById(R.id.chipGroupInterests);
        btnContinue = findViewById(R.id.btn_continue);


        String[] categories = {"Muzee", "Parcuri și natură", "Artă și design", "Restaurante", "Cultură și istorie", "Locuri interesante", "Sporte", "Shopping", "Viața de noapte", "Evenimente"};
        
        User tempUser = sessionManager.getCurrentUser();
        String activeInterests = (tempUser != null && tempUser.interests != null) ? tempUser.interests : "";

        
        for (String category : categories) {
            Chip chip = new Chip(this);
            chip.setText(category);
            chip.setCheckable(true);
            boolean isChecked = activeInterests.contains(category);
            chip.setChecked(isChecked);
            
            
            if (isChecked) {
                chip.setChipBackgroundColorResource(R.color.primary);
                chip.setTextColor(getResources().getColor(R.color.app_background));
            } else {
                chip.setChipBackgroundColorResource(R.color.app_card);
                chip.setTextColor(getResources().getColor(R.color.app_text_primary));
            }
            
            chip.setChipStrokeColorResource(R.color.primary);
            chip.setChipStrokeWidth(2.0f);
            
            
            chip.setOnCheckedChangeListener((buttonView, isCheckedNow) -> {
                if (isCheckedNow) {
                    chip.setChipBackgroundColorResource(R.color.primary);
                    chip.setTextColor(getResources().getColor(R.color.app_background));
                } else {
                    chip.setChipBackgroundColorResource(R.color.app_card);
                    chip.setTextColor(getResources().getColor(R.color.app_text_primary));
                }
            });
            
            chipGroup.addView(chip);
        }
        
        boolean fromSettings = getIntent().getBooleanExtra("from_settings", false);
        if (fromSettings) {
            btnContinue.setText(getString(R.string.save_preferences));
            android.widget.TextView btnSkip = findViewById(R.id.btn_skip);
            if (btnSkip != null) btnSkip.setText(getString(R.string.reset_to_simple));
        }

        btnContinue.setOnClickListener(v -> {
            List<String> selectedInterests = new ArrayList<>();
            for (int i = 0; i < chipGroup.getChildCount(); i++) {
                Chip chip = (Chip) chipGroup.getChildAt(i);
                if (chip.isChecked()) {
                    selectedInterests.add(chip.getText().toString());
                }
            }

            if (selectedInterests.isEmpty()) {
                Toast.makeText(this, getString(R.string.at_least_one_interest), Toast.LENGTH_SHORT).show();
                return;
            }

            User currentUser = sessionManager.getCurrentUser();
            if (currentUser != null) {
                currentUser.interests = android.text.TextUtils.join(",", selectedInterests);
                db.userDao().update(currentUser);
                sessionManager.createSession(currentUser);
            }
            sessionManager.setInterestsCompleted(true); 

            Intent intent = new Intent(InterestsActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        android.widget.TextView btnSkip = findViewById(R.id.btn_skip);
        btnSkip.setOnClickListener(v -> {
            User currentUser = sessionManager.getCurrentUser();
            if (currentUser != null) {
                currentUser.interests = "TRENDING";
                db.userDao().update(currentUser);
                sessionManager.createSession(currentUser);
            }
            sessionManager.setInterestsCompleted(true); 
            startActivity(new Intent(InterestsActivity.this, MainActivity.class));
            finish();
        });
    }
}
