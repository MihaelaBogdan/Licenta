package com.cityscape.app;

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
import com.cityscape.app.data.SupabaseSyncManager;
import com.cityscape.app.model.User;

public class LoginActivity extends BaseActivity {

    private static final String TAG = "LoginActivity";

    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private Button loginButton;
    private Button btnGoogleSignIn;
    private TextView registerPrompt;
    private AppDatabase db;
    private SessionManager sessionManager;

    
    private SupabaseAuthManager supabaseAuth;
    private GoogleSignInClient googleSignInClient;

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
                    Toast.makeText(this, getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = AppDatabase.getInstance(this);
        sessionManager = new SessionManager(this);
        supabaseAuth = SupabaseAuthManager.getInstance(this);

        setContentView(R.layout.activity_login);

        
        try {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.google_web_client_id))
                    .requestEmail()
                    .build();
            googleSignInClient = GoogleSignIn.getClient(this, gso);
        } catch (Exception e) {
            Log.e(TAG, "Error configuring Google Sign-In", e);
        }

        emailEditText = findViewById(R.id.emailInput);
        passwordEditText = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.btnLogin);
        registerPrompt = findViewById(R.id.btnRegister);
        btnGoogleSignIn = findViewById(R.id.btn_google_sign_in);

        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        android.widget.TextView btnForgotPassword = findViewById(R.id.btnForgotPassword);
        if (btnForgotPassword != null) {
            btnForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
        }

        loginButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, getString(R.string.please_fill_all_fields), Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isValidEmail(email)) {
                Toast.makeText(this, getString(R.string.invalid_email), Toast.LENGTH_SHORT).show();
                return;
            }

            
            supabaseAuth.signInWithEmail(email, password, new SupabaseAuthManager.AuthCallback() {
                @Override
                public void onSuccess(String userEmail, String displayName) {
                    runOnUiThread(() -> {
                        try {
                            handleSupabaseUserSession(userEmail, displayName);
                            
                            if (supabaseAuth.isUserConfirmed()) {
                                String name = displayName != null ? displayName : userEmail;
                                if ("admin@cityscape.ro".equals(userEmail)) {
                                    startActivity(new Intent(LoginActivity.this, AdminActivity.class));
                                    finish();
                                    return;
                                }
                                Toast.makeText(LoginActivity.this,
                                        String.format(getString(R.string.welcome_user), name),
                                        Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                finish();
                            } else {
                                showVerificationNeededDialog(userEmail);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error after Supabase login success", e);
                            
                            tryLocalLogin(email, password);
                        }
                    });
                }

                @Override
                public void onFailure(String errorMessage) {
                    runOnUiThread(() -> {
                        Log.w(TAG, "Supabase login failed: " + errorMessage + ", trying local DB");
                        tryLocalLogin(email, password);
                    });
                }
            });
        });

        if (btnGoogleSignIn != null) {
            btnGoogleSignIn.setOnClickListener(v -> {
                Log.d(TAG, "Google Sign-In button clicked");
                try {
                    if (googleSignInClient != null) {
                        googleSignInClient.signOut().addOnCompleteListener(task -> {
                            Intent signInIntent = googleSignInClient.getSignInIntent();
                            googleSignInLauncher.launch(signInIntent);
                        });
                    } else {
                        Toast.makeText(this, getString(R.string.google_services_not_initialized), Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error launching Google sign-in", e);
                    Toast.makeText(this, getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show();
                }
            });
        }

        registerPrompt.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });

        View btnGuest = findViewById(R.id.btnGuest);
        if (btnGuest != null) {
            btnGuest.setOnClickListener(v -> {
                Log.d(TAG, "Guest mode activated");
                User guest = new User("Explorator", "guest@cityscape.app", "");
                guest.id = "guest_user";
                sessionManager.createSession(guest);
                sessionManager.setPreferredCity("London");

                Toast.makeText(this, getString(R.string.guest_mode_activated), Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, MainActivity.class));
                finish();
            });
        }

        
        Animation fadeInUp = AnimationUtils.loadAnimation(this, R.anim.fade_in_up);
        findViewById(R.id.imgLogo).startAnimation(fadeInUp);
        findViewById(R.id.txtTitle).startAnimation(fadeInUp);
        findViewById(R.id.loginCard).startAnimation(fadeInUp);
        findViewById(R.id.btnRegister).startAnimation(fadeInUp);
        if (btnGuest != null) btnGuest.startAnimation(fadeInUp);

        
    }

    private void tryLocalLogin(String email, String password) {
        try {
            User user = db.userDao().login(email, password);
            if (user != null) {
                sessionManager.createSession(user);
                Toast.makeText(this,
                        String.format(getString(R.string.welcome_user), user.name),
                        Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, MainActivity.class));
                finish();
            } else {
                Toast.makeText(this, getString(R.string.invalid_credentials), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during local DB login", e);
            Toast.makeText(this, getString(R.string.invalid_credentials), Toast.LENGTH_SHORT).show();
        }
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
                        Toast.makeText(LoginActivity.this,
                                String.format(getString(R.string.welcome_user), displayName),
                                Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling Google sign-in result", e);
                        Toast.makeText(LoginActivity.this, getString(R.string.google_sign_in_failed),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Supabase auth with Google failed: " + errorMessage);
                    Toast.makeText(LoginActivity.this, getString(R.string.google_sign_in_failed),
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

            
            SupabaseSyncManager.getInstance(this).syncUserProfile(supabaseId);
        } catch (Exception e) {
            Log.e(TAG, "Error creating session for user", e);
        }
    }

    private void showForgotPasswordDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_forgot_password, null);
        android.widget.TextView txtStatus = dialogView.findViewById(R.id.txt_reset_status);
        com.google.android.material.textfield.TextInputEditText inputEmail = dialogView.findViewById(R.id.input_reset_email);

        
        String currentEmail = emailEditText.getText().toString().trim();
        if (!currentEmail.isEmpty() && inputEmail != null) {
            inputEmail.setText(currentEmail);
        }

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DarkDialogTheme)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        android.widget.Button btnSendReset = dialogView.findViewById(R.id.btn_send_reset);
        android.widget.Button btnCancelReset = dialogView.findViewById(R.id.btn_cancel_reset);

        if (btnSendReset != null) {
            btnSendReset.setOnClickListener(v -> {
                String resetEmail = inputEmail != null ? inputEmail.getText().toString().trim() : "";
                if (resetEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(resetEmail).matches()) {
                    if (txtStatus != null) {
                        txtStatus.setText("⚠️ Introdu o adresă de email validă.");
                        txtStatus.setTextColor(0xFFFF9800);
                    }
                    return;
                }

                btnSendReset.setEnabled(false);
                btnSendReset.setText("Se trimite...");
                if (txtStatus != null) txtStatus.setText("");

                supabaseAuth.requestPasswordReset(resetEmail, new com.cityscape.app.data.SupabaseAuthManager.PasswordResetCallback() {
                    @Override
                    public void onSent() {
                        runOnUiThread(() -> {
                            dialog.dismiss();
                            Toast.makeText(LoginActivity.this,
                                    "✅ Link de resetare trimis la " + resetEmail + ". Verifică inbox-ul!",
                                    Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            btnSendReset.setEnabled(true);
                            btnSendReset.setText("Trimite link de resetare");
                            if (txtStatus != null) {
                                txtStatus.setText("❌ Eroare: " + errorMessage);
                                txtStatus.setTextColor(0xFFEF4444);
                            }
                        });
                    }
                });
            });
        }

        if (btnCancelReset != null) {
            btnCancelReset.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void showVerificationNeededDialog(String email) {
        String message = getString(R.string.email_not_confirmed) + email + getString(R.string.email_not_confirmed_suffix);
        new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DarkDialogTheme)
                .setTitle(getString(R.string.unconfirmed_email))
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    dialog.dismiss();
                    supabaseAuth.signOut();
                })
                .setCancelable(false)
                .show();
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }
}
