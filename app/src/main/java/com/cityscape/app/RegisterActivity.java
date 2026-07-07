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
    private android.widget.CheckBox chkTerms;
    private TextView txtTermsLink;
    private de.hdodenhof.circleimageview.CircleImageView imgLogo;
    private String selectedAvatarPath = null;

    
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

    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
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
                        selectedAvatarPath = file.getAbsolutePath();
                        if (imgLogo != null) {
                            com.bumptech.glide.Glide.with(this).load(file).into(imgLogo);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, getString(R.string.error_loading_image), Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        db = AppDatabase.getInstance(this);
        sessionManager = new SessionManager(this);
        supabaseAuth = SupabaseAuthManager.getInstance(this);

        
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
        chkTerms = findViewById(R.id.chkTerms);
        txtTermsLink = findViewById(R.id.txtTermsLink);
        imgLogo = findViewById(R.id.imgLogo);

        if (imgLogo != null) {
            imgLogo.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        
        txtTermsLink.setOnClickListener(v -> showTermsDialog());

        registerButton.setOnClickListener(v -> {
            String name = nameEditText.getText().toString().trim();
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show();
                return;
            }

            if (name.length() < 2) {
                Toast.makeText(this, getString(R.string.name_min_length), Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isValidEmail(email)) {
                Toast.makeText(this, getString(R.string.invalid_email_address), Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isPasswordStrong(password)) {
                String msg = getPasswordStrengthMessage(password);
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                return;
            }

            if (!chkTerms.isChecked()) {
                Toast.makeText(this, getString(R.string.must_accept_terms), Toast.LENGTH_LONG).show();
                return;
            }

            if (progressOverlay != null) progressOverlay.setVisibility(View.VISIBLE);

            new Thread(() -> {
                User existingLocalUser = db.userDao().getUserByEmail(email);
                if (existingLocalUser != null) {
                    runOnUiThread(() -> {
                        if (progressOverlay != null) progressOverlay.setVisibility(View.GONE);
                        Toast.makeText(this, getString(R.string.email_already_registered_long), Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                supabaseAuth.signUpWithEmail(email, password, name, new SupabaseAuthManager.AuthCallback() {
                    @Override
                    public void onSuccess(String userEmail, String displayName) {
                        runOnUiThread(() -> {
                            handleSupabaseUserSession(userEmail, displayName);
                            if (progressOverlay != null) progressOverlay.setVisibility(View.GONE);

                            supabaseAuth.sendWelcomeEmail(userEmail, displayName, new SupabaseAuthManager.EmailNotificationCallback() {
                                @Override
                                public void onEmailSent(String email) {
                                    Log.d(TAG, "Welcome email sent to: " + email);
                                }

                                @Override
                                public void onEmailFailed(String error) {
                                    Log.w(TAG, "Welcome email failed: " + error);
                                }
                            });

                            if (supabaseAuth.isUserConfirmed()) {
                                Toast.makeText(RegisterActivity.this,
                                        "Bine ai venit, " + displayName + "!",
                                        Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                                finish();
                            } else {
                                showVerificationNeededDialog(userEmail != null ? userEmail : email);
                            }
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        runOnUiThread(() -> {
                            if (progressOverlay != null) progressOverlay.setVisibility(View.GONE);
                            Log.w(TAG, "Registration failed: " + errorMessage);
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
                        Toast.makeText(this, getString(R.string.google_services_not_initialized), Toast.LENGTH_SHORT).show();
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

        
        Animation fadeInUp = AnimationUtils.loadAnimation(this, R.anim.fade_in_up);
        findViewById(R.id.txtTitle).startAnimation(fadeInUp);
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

    private android.os.Handler verifyPollHandler;
    private Runnable verifyPollRunnable;

    private void showVerificationNeededDialog(String email) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_email_verify, null);

        android.widget.TextView txtEmail = dialogView.findViewById(R.id.txt_verify_email);
        android.widget.TextView txtStatus = dialogView.findViewById(R.id.txt_verify_status);
        android.widget.Button btnCheck = dialogView.findViewById(R.id.btn_check_confirmed);
        android.widget.Button btnResend = dialogView.findViewById(R.id.btn_resend_verify);

        if (txtEmail != null) txtEmail.setText(email);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DarkDialogTheme)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        if (btnCheck != null) {
            btnCheck.setOnClickListener(v -> {
                btnCheck.setEnabled(false);
                btnCheck.setText("Se verifică...");
                if (txtStatus != null) txtStatus.setText("");

                supabaseAuth.checkEmailConfirmed(new SupabaseAuthManager.EmailConfirmedCallback() {
                    @Override
                    public void onConfirmed() {
                        stopVerifyPolling();
                        runOnUiThread(() -> {
                            dialog.dismiss();
                            String name = supabaseAuth.getStoredName();
                            Toast.makeText(RegisterActivity.this,
                                    "Email confirmat! Bine ai venit" + (name != null ? ", " + name : "") + "! 🎉",
                                    Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                            finish();
                        });
                    }

                    @Override
                    public void onNotConfirmed() {
                        runOnUiThread(() -> {
                            btnCheck.setEnabled(true);
                            btnCheck.setText("Am confirmat, verifică acum");
                            if (txtStatus != null) {
                                txtStatus.setText("⚠️ Email-ul nu a fost confirmat încă. Verifică inbox-ul (și spam-ul).");
                                txtStatus.setTextColor(0xFFFF9800);
                            }
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            btnCheck.setEnabled(true);
                            btnCheck.setText("Am confirmat, verifică acum");
                            if (txtStatus != null) {
                                txtStatus.setText("Eroare: " + errorMessage);
                                txtStatus.setTextColor(0xFFEF4444);
                            }
                        });
                    }
                });
            });
        }

        if (btnResend != null) {
            btnResend.setOnClickListener(v -> {
                btnResend.setEnabled(false);
                supabaseAuth.resendVerificationEmail(email, new SupabaseAuthManager.VerificationCallback() {
                    @Override
                    public void onSent() {
                        runOnUiThread(() -> {
                            btnResend.setEnabled(true);
                            if (txtStatus != null) {
                                txtStatus.setText("✅ Email retrimis! Verifică inbox-ul.");
                                txtStatus.setTextColor(0xFF10B981);
                            }
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            btnResend.setEnabled(true);
                            if (txtStatus != null) {
                                txtStatus.setText("Eroare la retrimitere: " + errorMessage);
                                txtStatus.setTextColor(0xFFEF4444);
                            }
                        });
                    }
                });
            });
        }

        dialog.show();

        
        verifyPollHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        verifyPollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!dialog.isShowing()) return;
                supabaseAuth.checkEmailConfirmed(new SupabaseAuthManager.EmailConfirmedCallback() {
                    @Override
                    public void onConfirmed() {
                        stopVerifyPolling();
                        runOnUiThread(() -> {
                            if (dialog.isShowing()) dialog.dismiss();
                            String name = supabaseAuth.getStoredName();
                            Toast.makeText(RegisterActivity.this,
                                    "Email confirmat automat! Bine ai venit" + (name != null ? ", " + name : "") + "! 🎉",
                                    Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                            finish();
                        });
                    }

                    @Override
                    public void onNotConfirmed() {
                        
                        if (verifyPollHandler != null) verifyPollHandler.postDelayed(verifyPollRunnable, 5000);
                    }

                    @Override
                    public void onError(String error) {
                        if (verifyPollHandler != null) verifyPollHandler.postDelayed(verifyPollRunnable, 8000);
                    }
                });
            }
        };
        verifyPollHandler.postDelayed(verifyPollRunnable, 5000);
    }

    private void stopVerifyPolling() {
        if (verifyPollHandler != null && verifyPollRunnable != null) {
            verifyPollHandler.removeCallbacks(verifyPollRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        stopVerifyPolling();
        super.onDestroy();
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isPasswordStrong(String password) {
        if (password == null || password.length() < 6) {
            return false;
        }
        boolean hasNumber = password.matches(".*\\d.*");
        return hasNumber;
    }

    private String getPasswordStrengthMessage(String password) {
        if (password == null || password.isEmpty()) {
            return getString(R.string.err_password_required);
        }
        if (password.length() < 6) {
            return getString(R.string.err_password_too_short);
        }
        if (!password.matches(".*\\d.*")) {
            return getString(R.string.err_password_digit);
        }
        return "";
    }

    private void showTermsDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DarkDialogTheme)
                .setTitle(getString(R.string.terms_of_service))
                .setMessage(getString(R.string.terms_of_service_text))
                .setPositiveButton(getString(R.string.dialog_understand), (dialog, which) -> {
                    if (chkTerms != null) chkTerms.setChecked(true);
                })
                .show();
    }
}
