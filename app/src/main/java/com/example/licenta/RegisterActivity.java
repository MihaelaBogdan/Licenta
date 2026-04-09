package com.example.licenta;

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
import com.google.android.material.textfield.TextInputEditText;

import com.example.licenta.data.AppDatabase;
import com.example.licenta.data.SessionManager;
import com.example.licenta.data.SupabaseAuthManager;
import com.example.licenta.model.User;

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

        nameEditText = findViewById(R.id.input_name);
        emailEditText = findViewById(R.id.input_email);
        passwordEditText = findViewById(R.id.input_password);
        registerButton = findViewById(R.id.btn_register);
        loginPrompt = findViewById(R.id.text_login_prompt);
        btnGoogle = findViewById(R.id.btn_google);

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

            // Register with Supabase
            new Thread(() -> {
                User existingLocalUser = db.userDao().getUserByEmail(email);
                if (existingLocalUser != null) {
                    runOnUiThread(() -> Toast
                            .makeText(this, getString(R.string.email_already_registered), Toast.LENGTH_SHORT).show());
                    return;
                }

                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.email_checking), Toast.LENGTH_SHORT).show();
                    supabaseAuth.signUpWithEmail(email, password, name, new SupabaseAuthManager.AuthCallback() {
                        @Override
                        public void onSuccess(String userEmail, String displayName) {
                            runOnUiThread(() -> {
                                Toast.makeText(RegisterActivity.this, getString(R.string.check_email_confirmation),
                                        Toast.LENGTH_LONG).show();
                                new android.os.Handler().postDelayed(() -> {
                                    startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                                    finish();
                                }, 3000);
                            });
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            runOnUiThread(() -> {
                                Log.w(TAG, "Supabase registration failed: " + errorMessage);
                                Toast.makeText(RegisterActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                });
            }).start();
        });

        btnGoogle.setOnClickListener(v -> {
            try {
                if (googleSignInClient != null) {
                    googleSignInClient.signOut().addOnCompleteListener(task -> {
                        Intent signInIntent = googleSignInClient.getSignInIntent();
                        googleSignInLauncher.launch(signInIntent);
                    });
                } else {
                    Toast.makeText(this, getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error launching Google sign-in", e);
                Toast.makeText(this, getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show();
            }
        });

        loginPrompt.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
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

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isPasswordStrong(String password) {
        // At least 8 characters, 1 uppercase, 1 digit, 1 special character
        String passwordPattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$";
        return password.matches(passwordPattern);
    }
}
