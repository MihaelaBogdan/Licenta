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

        // Definim categoriile de interese
        String[] categories = {"Muzee", "Parcuri și natură", "Artă și design", "Restaurante", "Cultură și istorie", "Locuri interesante", "Sporte", "Shopping", "Viața de noapte", "Evenimente"};
        
        User tempUser = sessionManager.getCurrentUser();
        String activeInterests = (tempUser != null && tempUser.interests != null) ? tempUser.interests : "";

        // Creăm butoanele (Chips) vizual 
        for (String category : categories) {
            Chip chip = new Chip(this);
            chip.setText(category);
            chip.setCheckable(true);
            chip.setChecked(activeInterests.contains(category));
            chip.setTextColor(getResources().getColor(R.color.app_text_primary));
            chip.setChipBackgroundColorResource(R.color.app_card);
            chip.setChipStrokeColorResource(R.color.primary);
            chip.setChipStrokeWidth(2.0f);
            chipGroup.addView(chip);
        }
        
        boolean fromSettings = getIntent().getBooleanExtra("from_settings", false);
        if (fromSettings) {
            btnContinue.setText("Salvează Preferințele");
            android.widget.TextView btnSkip = findViewById(R.id.btn_skip);
            if (btnSkip != null) btnSkip.setText("Resetează la Simplu/Global");
        }

        btnContinue.setOnClickListener(v -> {
            List<String> selectedInterests = new ArrayList<>();
            // Căutăm printre toate variantele care au fost bifate
            for (int i = 0; i < chipGroup.getChildCount(); i++) {
                Chip chip = (Chip) chipGroup.getChildAt(i);
                if (chip.isChecked()) {
                    selectedInterests.add(chip.getText().toString());
                }
            }

            if (selectedInterests.isEmpty()) {
                Toast.makeText(this, "Te rugăm să alegi cel puțin un interes, sau apasă pe Sari Peste!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Actualizăm utilizatorul curent din baza de date internă Room
            User currentUser = sessionManager.getCurrentUser();
            if (currentUser != null) {
                // Generam String despărțit prin virgulă "Muzee,Parcuri și natură,Artă și design"
                currentUser.interests = android.text.TextUtils.join(",", selectedInterests);
                db.userDao().update(currentUser);
                sessionManager.createSession(currentUser); // re-salvăm sesiunea cu interesele noi
            }

            // Apoi, continuăm spre ecranul principal (MainActivity)
            Intent intent = new Intent(InterestsActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        // Configuram butonul Skip (Sari Peste). Astfel utilizatorul nu va mai fi forțat să aleagă.
        android.widget.TextView btnSkip = findViewById(R.id.btn_skip);
        btnSkip.setOnClickListener(v -> {
            User currentUser = sessionManager.getCurrentUser();
            if (currentUser != null) {
                // Dacă nu a ales nimic, îi punem "TRENDING" ca să știe aplicația că nu are interese și vrea doar recomandările globale
                currentUser.interests = "TRENDING";
                db.userDao().update(currentUser);
                sessionManager.createSession(currentUser);
            }
            startActivity(new Intent(InterestsActivity.this, MainActivity.class));
            finish();
        });
    }
}
