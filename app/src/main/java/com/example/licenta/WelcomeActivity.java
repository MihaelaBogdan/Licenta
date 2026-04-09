package com.example.licenta;

import android.content.Intent;
import android.os.Bundle;
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

import com.example.licenta.data.AppDatabase;
import com.example.licenta.data.SessionManager;
import com.example.licenta.data.SupabaseAuthManager;
import com.example.licenta.model.User;

public class WelcomeActivity extends BaseActivity {

    private static final String TAG = "WelcomeActivity";

    private SupabaseAuthManager supabaseAuth;
    private GoogleSignInClient googleSignInClient;
    private AppDatabase db;
    private SessionManager sessionManager;

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                try {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    if (account != null && account.getIdToken() != null) {
                        Log.d(TAG, "Google sign-in successful, ID token present");
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

        supabaseAuth = SupabaseAuthManager.getInstance(this);
        db = AppDatabase.getInstance(this);
        sessionManager = new SessionManager(this);

        // If already logged in, skip to Home
        try {
            if (sessionManager.isLoggedIn()) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return;
            }
            // Check if Supabase session exists
            if (supabaseAuth.isAuthenticated()) {
                handleSupabaseUserSession(supabaseAuth.getStoredEmail(), supabaseAuth.getStoredName());
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking login status", e);
        }

        setContentView(R.layout.activity_welcome);

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

        Button btnGetStarted = findViewById(R.id.btn_get_started);
        Button btnGoogle = findViewById(R.id.btn_google);
        TextView textAlreadyAccount = findViewById(R.id.text_already_account);

        btnGetStarted.setOnClickListener(v -> {
            startActivity(new Intent(WelcomeActivity.this, RegisterActivity.class));
        });

        // Google Sign-In button
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

        textAlreadyAccount.setOnClickListener(v -> {
            startActivity(new Intent(WelcomeActivity.this, LoginActivity.class));
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
                        Toast.makeText(WelcomeActivity.this,
                                String.format(getString(R.string.welcome_user), displayName),
                                Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
                        finish();
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling Google sign-in result", e);
                        Toast.makeText(WelcomeActivity.this, getString(R.string.google_sign_in_failed),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Supabase auth with Google failed: " + errorMessage);
                    Toast.makeText(WelcomeActivity.this, getString(R.string.google_sign_in_failed),
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
}
