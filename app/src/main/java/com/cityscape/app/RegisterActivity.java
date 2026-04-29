package com.cityscape.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import com.google.android.material.textfield.TextInputEditText;

import com.cityscape.app.data.AppDatabase;
import com.cityscape.app.data.SessionManager;
import com.cityscape.app.data.SupabaseAuthManager;
import com.cityscape.app.model.User;

public class RegisterActivity extends BaseActivity {

    private static final String TAG = "RegisterActivity";

    private TextInputEditText nameEditText;
    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private Button registerButton;
    private Button btnGoogle;
    private TextView loginPrompt;
    private AppDatabase db;
    private SessionManager sessionManager;
    private View progressOverlay;

    // Supabase
    private SupabaseAuthManager supabaseAuth;
    private GoogleSignInClient googleSignInClient;

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                try {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    if (account != null && account.getIdToken() != null) {
                        supabaseAuthWithGoogle(account);
                    } else {
                        Log.e(TAG, "Google sign-in: account or idToken is null");
                        Toast.makeText(this, getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show();
                    }
                } catch (ApiException e) {
                    Log.e(TAG, "Google sign-in failed, code: " + e.getStatusCode(), e);
                    Toast.makeText(this, getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error during Google sign-in", e);
                    Toast.makeText(this, getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        db = AppDatabase.getInstance(this);
        sessionManager = new SessionManager(this);
        supabaseAuth = SupabaseAuthManager.getInstance(this);

        // Configure Google Sign-In
        try {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.google_web_client_id))
                    .requestEmail()
                    .build();
            googleSignInClient = GoogleSignIn.getClient(this, gso);
        } catch (Exception e) {
            Log.e(TAG, "Error configuring Google Sign-In", e);
        }

        nameEditText = findViewById(R.id.nameInput);
        emailEditText = findViewById(R.id.emailInput);
        passwordEditText = findViewById(R.id.passwordInput);
        registerButton = findViewById(R.id.btnRegister);
        loginPrompt = findViewById(R.id.btnLogin);
        btnGoogle = findViewById(R.id.btn_google_sign_in);
        progressOverlay = findViewById(R.id.progressOverlay);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        registerButton.setOnClickListener(v -> {
            String name = nameEditText.getText().toString().trim();
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, getString(R.string.please_fill_all_fields), Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isValidEmail(email)) {
                Toast.makeText(this, getString(R.string.invalid_email), Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isPasswordStrong(password)) {
                Toast.makeText(this, getString(R.string.password_too_weak), Toast.LENGTH_LONG).show();
                return;
            }

            // Show progress
            if (progressOverlay != null) progressOverlay.setVisibility(View.VISIBLE);

            // Register with Supabase
            new Thread(() -> {
                User existingLocalUser = db.userDao().getUserByEmail(email);
                if (existingLocalUser != null) {
                    runOnUiThread(() -> {
                        if (progressOverlay != null) progressOverlay.setVisibility(View.GONE);
                        Toast.makeText(this, getString(R.string.email_already_registered), Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                supabaseAuth.signUpWithEmail(email, password, name, new SupabaseAuthManager.AuthCallback() {
                    @Override
                    public void onSuccess(String userEmail, String displayName) {
                        runOnUiThread(() -> {
                            handleSupabaseUserSession(userEmail, displayName);
                            
                            if (progressOverlay != null) progressOverlay.setVisibility(View.GONE);
                            
                            // Check if confirmed
                            if (supabaseAuth.isUserConfirmed()) {
                                // Already confirmed (could happen with some providers/backend settings)
                                Toast.makeText(RegisterActivity.this,
                                        String.format(getString(R.string.welcome_new_user), displayName != null ? displayName : name),
                                        Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                                finish();
                            } else {
                                // Needs confirmation - Show dialog instead of auto-logging in
                                showVerificationNeededDialog(userEmail != null ? userEmail : email);
                            }
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        runOnUiThread(() -> {
                            if (progressOverlay != null) progressOverlay.setVisibility(View.GONE);
                            Log.w(TAG, "Supabase registration failed: " + errorMessage);
                            Toast.makeText(RegisterActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            }).start();
        });

        if (btnGoogle != null) {
            btnGoogle.setOnClickListener(v -> {
                Log.d(TAG, "Google Sign-Up button clicked");
                try {
                    if (googleSignInClient != null) {
                        googleSignInClient.signOut().addOnCompleteListener(task -> {
                            Intent signInIntent = googleSignInClient.getSignInIntent();
                            googleSignInLauncher.launch(signInIntent);
                        });
                    } else {
                        Toast.makeText(this, "Google Services not initialized", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error launching Google sign-in", e);
                    Toast.makeText(this, getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show();
                }
            });
        }

        loginPrompt.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // Apply Entrance Animations
        Animation fadeInUp = AnimationUtils.loadAnimation(this, R.anim.fade_in_up);
        findViewById(R.id.txtTitle).startAnimation(fadeInUp);
        findViewById(R.id.txtSubtitle).startAnimation(fadeInUp);
        findViewById(R.id.registerCard).startAnimation(fadeInUp);
        findViewById(R.id.btnLogin).startAnimation(fadeInUp);
    }

    private void supabaseAuthWithGoogle(GoogleSignInAccount account) {
        String idToken = account.getIdToken();
        String displayName = account.getDisplayName() != null ? account.getDisplayName() : "User";

        supabaseAuth.signInWithGoogle(idToken, new SupabaseAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String email, String name) {
                runOnUiThread(() -> {
                    try {
                        handleSupabaseUserSession(email, displayName);
                        Toast.makeText(RegisterActivity.this,
                                String.format(getString(R.string.welcome_new_user), displayName),
                                Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                        finish();
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling Google sign-in result", e);
                        Toast.makeText(RegisterActivity.this, getString(R.string.google_sign_in_failed),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Supabase auth with Google failed: " + errorMessage);
                    Toast.makeText(RegisterActivity.this, getString(R.string.google_sign_in_failed),
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void handleSupabaseUserSession(String email, String name) {
        if (email == null || email.isEmpty()) {
            Log.e(TAG, "User email is null");
            return;
        }

        try {
            if (name == null || name.isEmpty())
                name = "User";

            User existingUser = db.userDao().getUserByEmail(email);
            String supabaseId = supabaseAuth.getStoredUserId();

            if (existingUser != null) {
                if (supabaseId != null && !existingUser.id.equals(supabaseId)) {
                    db.userDao().delete(existingUser);
                    existingUser.id = supabaseId;
                    db.userDao().insert(existingUser);
                }
                sessionManager.createSession(existingUser);
            } else {
                User newUser = new User(name, email, "supabase_auth");
                if (supabaseId != null) {
                    newUser.id = supabaseId;
                }
                db.userDao().insert(newUser);

                sessionManager.createSession(newUser);
                sessionManager.awardAchievement("Account Created", 100);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating session for user", e);
        }
    }

    private void showVerificationNeededDialog(String email) {
        new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DarkDialogTheme)
                .setTitle("Confirmă Adresa de Email")
                .setMessage("Un link de confirmare a fost trimis la: " + email + "\n\nTe rugăm să verifici inbox-ul (și folderul Spam) pentru a-ți activa contul CityScape.")
                .setPositiveButton("Am înțeles", (dialog, which) -> {
                    dialog.dismiss();
                    // Clear session since it's unconfirmed and we want them to login after confirming
                    supabaseAuth.signOut();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isPasswordStrong(String password) {
        // Just check for minimum length for testing
        return password != null && password.length() >= 6;
    }
}
