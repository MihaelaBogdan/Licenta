package com.example.licenta;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.licenta.data.LocaleHelper;
import com.example.licenta.data.SessionManager;
import com.example.licenta.data.SupabaseAuthManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingsActivity extends BaseActivity {

    private LinearLayout languageItem;
    private TextView currentLanguageText;
    private Button btnLogout;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sessionManager = new SessionManager(this);

        // Back button
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Language selection
        languageItem = findViewById(R.id.languageItem);
        currentLanguageText = findViewById(R.id.currentLanguageText);

        updateLanguageDisplay();

        languageItem.setOnClickListener(v -> showLanguageDialog());
        
        // Personalize button
        LinearLayout personalizeItem = findViewById(R.id.personalizeItem);
        if (personalizeItem != null) {
            personalizeItem.setOnClickListener(v -> {
                Intent intent = new Intent(this, InterestsActivity.class);
                intent.putExtra("from_settings", true);
                startActivity(intent);
            });
        }

        // Logout button
        btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> showLogoutDialog());
        
        // Dark mode toggle
        com.google.android.material.switchmaterial.SwitchMaterial darkModeSwitch = findViewById(R.id.darkModeSwitch);
        darkModeSwitch.setChecked(sessionManager.isDarkMode());
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setDarkMode(isChecked);
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                isChecked ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : 
                           androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            );
        });
    }

    private void updateLanguageDisplay() {
        String currentLang = LocaleHelper.getLanguage(this);
        if ("ro".equals(currentLang)) {
            currentLanguageText.setText(getString(R.string.language_romanian));
        } else {
            currentLanguageText.setText(getString(R.string.language_english));
        }
    }

    private void showLanguageDialog() {
        String currentLang = LocaleHelper.getLanguage(this);

        String[] languages = {
                getString(R.string.language_romanian),
                getString(R.string.language_english)
        };
        String[] languageCodes = { "ro", "en" };

        int checkedItem = "ro".equals(currentLang) ? 0 : 1;

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.select_language))
                .setSingleChoiceItems(languages, checkedItem, (dialog, which) -> {
                    String selectedCode = languageCodes[which];
                    if (!selectedCode.equals(currentLang)) {
                        LocaleHelper.setLanguage(this, selectedCode);
                        Toast.makeText(this, getString(R.string.language_changed), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();

                        // Restart the app to apply new locale
                        restartApp();
                    } else {
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void restartApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLogoutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.logout))
                .setMessage(getString(R.string.logout_confirm))
                .setPositiveButton(getString(R.string.yes), (dialog, which) -> {
                    // Logout from Supabase
                    SupabaseAuthManager.getInstance(this).signOut();
                    // Logout from local session
                    sessionManager.logout();
                    // Go to login
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton(getString(R.string.no), null)
                .show();
    }
}
