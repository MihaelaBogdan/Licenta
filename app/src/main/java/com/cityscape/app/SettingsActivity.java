package com.cityscape.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.cityscape.app.data.LocaleHelper;
import com.cityscape.app.data.SessionManager;
import com.cityscape.app.data.SupabaseAuthManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingsActivity extends BaseActivity {

    private LinearLayout languageItem;
    private TextView currentLanguageText;
    private Button btnLogout;
    private SessionManager sessionManager;
    private de.hdodenhof.circleimageview.CircleImageView currentEditProfileImage;
    private String currentEditProfileAvatarPath = null;
    
    private final androidx.activity.result.ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try {
                        java.io.InputStream is = getContentResolver().openInputStream(uri);
                        java.io.File file = new java.io.File(getFilesDir(), "avatar_" + System.currentTimeMillis() + ".jpg");
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                        fos.close();
                        is.close();
                        currentEditProfileAvatarPath = file.getAbsolutePath();
                        if (currentEditProfileImage != null) {
                            com.bumptech.glide.Glide.with(this).load(file).into(currentEditProfileImage);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Eroare la încărcarea imaginii", Toast.LENGTH_SHORT).show();
                    }
                }
            });

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

        // City selection
        LinearLayout changeCityItem = findViewById(R.id.changeCityItem);
        TextView currentCityLabel = new TextView(this); // Just for reference
        changeCityItem.setOnClickListener(v -> showCitySelectionDialog());

        // Logout button
        btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> showLogoutDialog());
        
        // Edit Profile
        LinearLayout editProfileItem = findViewById(R.id.editProfileItem);
        if (editProfileItem != null) {
            editProfileItem.setOnClickListener(v -> showEditProfileDialog());
        }

        // Terms and Conditions
        LinearLayout termsItem = findViewById(R.id.termsItem);
        if (termsItem != null) {
            termsItem.setOnClickListener(v -> showTermsDialog());
        }
        
        // Privacy Policy
        LinearLayout privacyItem = findViewById(R.id.privacyItem);
        if (privacyItem != null) {
            privacyItem.setOnClickListener(v -> showPrivacyPolicyDialog());
        }
        
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

    private void showCitySelectionDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Ex: Cluj-Napoca, London...");
        input.setPadding(40, 40, 40, 40);
        String currentCity = sessionManager.getPreferredCity();
        if (currentCity != null) input.setText(currentCity);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Selectează Orașul")
                .setMessage("Introdu orașul unde vrei să explorezi locuri:")
                .setView(input)
                .setPositiveButton("Salvează", (dialog, which) -> {
                    String city = input.getText().toString().trim();
                    if (!city.isEmpty()) {
                        sessionManager.setPreferredCity(city);
                        Toast.makeText(this, "Oraș salvat: " + city, Toast.LENGTH_SHORT).show();
                        
                        // Go back to feed
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    }
                })
                .setNegativeButton("Anulează", null)
                .show();
    }

    private void showEditProfileDialog() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_profile, null);
        com.google.android.material.textfield.TextInputEditText nameInput = dialogView.findViewById(R.id.editNameInput);
        com.google.android.material.textfield.TextInputEditText interestsInput = dialogView.findViewById(R.id.editInterestsInput);
        currentEditProfileImage = dialogView.findViewById(R.id.editProfileImage);
        currentEditProfileAvatarPath = null;
        
        com.cityscape.app.model.User currentUser = sessionManager.getCurrentUser();
        if (currentUser != null) {
            if (currentUser.name != null) nameInput.setText(currentUser.name);
            if (currentUser.interests != null) interestsInput.setText(currentUser.interests);
            if (currentUser.avatar != null && currentEditProfileImage != null) {
                com.bumptech.glide.Glide.with(this).load(currentUser.avatar).into(currentEditProfileImage);
            }
        }
        
        if (currentEditProfileImage != null) {
            currentEditProfileImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.DarkDialogTheme)
                .setView(dialogView)
                .create();
        
        // Face fundalul dialogului transparent pentru a pastra design-ul curbat din XML
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.btnCancelEdit).setOnClickListener(v -> dialog.dismiss());
        
        dialogView.findViewById(R.id.btnSaveEdit).setOnClickListener(v -> {
            String newName = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
            String newInterests = interestsInput.getText() != null ? interestsInput.getText().toString().trim() : "";
            
            if (!newName.isEmpty() && currentUser != null) {
                currentUser.name = newName;
                currentUser.interests = newInterests;
                if (currentEditProfileAvatarPath != null) {
                    currentUser.avatar = currentEditProfileAvatarPath;
                }
                sessionManager.updateUserName(newName);
                
                new Thread(() -> {
                    com.cityscape.app.data.AppDatabase.getInstance(this).userDao().update(currentUser);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Profil actualizat cu succes!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        recreate(); // Auto-refresh the activity
                    });
                }).start();
            }
        });
        
        dialog.show();
    }
    
    private void showPrivacyPolicyDialog() {
        String privacyText = "Politica de Confidențialitate - CityScape AI\n\n" +
                "1. Colectarea Datelor: Colectăm istoricul locațiilor vizitate și interesele tale pentru a oferi o experiență personalizată.\n" +
                "2. Transparență AI: Fiecare recomandare generată de AI îți explică procentual de ce a fost aleasă pe baza datelor tale.\n" +
                "3. Nu vindem datele: Profilul tău este procesat exclusiv pentru funcționalitățile CityScape.\n\n" +
                "Securitate garantată.";

        new MaterialAlertDialogBuilder(this, R.style.DarkDialogTheme)
                .setTitle(getString(R.string.privacy_policy))
                .setMessage(privacyText)
                .setPositiveButton("Am Înțeles", null)
                .show();
    }

    private void showTermsDialog() {
        String termsText = "Termeni și Condiții - CityScape\n\n" +
                "1. Prin utilizarea aplicației, ești de acord să respecți regulile comunității.\n" +
                "2. Datele tale (nume, preferințe) sunt folosite exclusiv pentru personalizarea recomandărilor prin AI.\n" +
                "3. Nu stocăm parole în clar, iar autentificarea este securizată via Supabase.\n" +
                "4. Ne rezervăm dreptul de a suspenda conturile care fac spam în modulul 'Hype Battle'.\n\n" +
                "Ultima actualizare: Astăzi.";

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.terms_of_service))
                .setMessage(termsText)
                .setPositiveButton("Am Înțeles", null)
                .show();
    }
}
