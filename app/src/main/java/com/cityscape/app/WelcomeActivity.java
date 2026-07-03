package com.cityscape.app;

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

import com.cityscape.app.data.AppDatabase;
import com.cityscape.app.data.SessionManager;
import com.cityscape.app.data.SupabaseAuthManager;
import com.cityscape.app.model.User;

public class WelcomeActivity extends BaseActivity {

    private static final String TAG = "WelcomeActivity";

    private SupabaseAuthManager supabaseAuth;
    private GoogleSignInClient googleSignInClient;
    private AppDatabase db;
    private SessionManager sessionManager;

    private String getSigningCertificateSHA1() {
        try {
            android.content.pm.Signature[] signatures;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.content.pm.PackageInfo packageInfo = getPackageManager().getPackageInfo(
                        getPackageName(), android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                if (packageInfo.signingInfo != null) {
                    if (packageInfo.signingInfo.hasMultipleSigners()) {
                        signatures = packageInfo.signingInfo.getApkContentsSigners();
                    } else {
                        signatures = packageInfo.signingInfo.getSigningCertificateHistory();
                    }
                } else {
                    return null;
                }
            } else {
                android.content.pm.PackageInfo packageInfo = getPackageManager().getPackageInfo(
                        getPackageName(), android.content.pm.PackageManager.GET_SIGNATURES);
                signatures = packageInfo.signatures;
            }

            if (signatures != null && signatures.length > 0) {
                byte[] cert = signatures[0].toByteArray();
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
                byte[] publicKey = md.digest(cert);
                StringBuilder hexString = new StringBuilder();
                for (byte b : publicKey) {
                    String appendString = Integer.toHexString(0xFF & b).toUpperCase();
                    if (appendString.length() == 1) hexString.append("0");
                    hexString.append(appendString).append(":");
                }
                if (hexString.length() > 0) {
                    hexString.setLength(hexString.length() - 1);
                }
                return hexString.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting signing SHA-1", e);
        }
        return null;
    }

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
                    String sha1 = getSigningCertificateSHA1();
                    Log.e(TAG, "Google sign-in failed, code: " + e.getStatusCode() + ". Actual SHA-1: " + sha1, e);

                    new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DarkDialogTheme)
                        .setTitle(getString(R.string.google_connection_error))
                        .setMessage(getString(R.string.sha1_instruction) + (sha1 != null ? sha1 : getString(R.string.sha1_not_generated)))
                        .setPositiveButton(getString(R.string.copy_sha1), (dialog, which) -> {
                            if (sha1 != null) {
                                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                android.content.ClipData clip = android.content.ClipData.newPlainText("SHA-1", sha1);
                                clipboard.setPrimaryClip(clip);
                                Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(getString(R.string.close), null)
                        .show();
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error during Google sign-in", e);
                    Toast.makeText(this, getString(R.string.unexpected_error) + e.getMessage(), Toast.LENGTH_SHORT).show();
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
