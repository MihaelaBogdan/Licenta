package com.cityscape.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cityscape.app.CityScapeApp;
import com.cityscape.app.R;
import com.cityscape.app.database.AppDatabase;
import com.cityscape.app.database.entities.User;
import com.cityscape.app.databinding.ActivityAuthBinding;

import java.util.UUID;

/**
 * Authentication activity for login and registration
 */
public class AuthActivity extends AppCompatActivity {

    private ActivityAuthBinding binding;
    private boolean isLoginMode = true;
    private AppDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        database = CityScapeApp.getInstance().getDatabase();

        setupListeners();
        updateUI();
    }

    private void setupListeners() {
        // Toggle between login and signup
        binding.btnToggleMode.setOnClickListener(v -> {
            isLoginMode = !isLoginMode;
            updateUI();
        });

        // Submit button
        binding.btnSubmit.setOnClickListener(v -> {
            if (isLoginMode) {
                handleLogin();
            } else {
                handleSignup();
            }
        });

        // Social login
        binding.btnGoogle.setOnClickListener(v -> {
            // TODO: Implement Google Sign In
            Toast.makeText(this, "Google Sign In coming soon!", Toast.LENGTH_SHORT).show();
        });

        binding.btnFacebook.setOnClickListener(v -> {
            // TODO: Implement Facebook Sign In
            Toast.makeText(this, "Facebook Sign In coming soon!", Toast.LENGTH_SHORT).show();
        });

        // Back button
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void updateUI() {
        if (isLoginMode) {
            binding.titleText.setText(R.string.sign_in);
            binding.btnSubmit.setText(R.string.sign_in);
            binding.btnToggleMode.setText(R.string.dont_have_account);
            binding.nameInputLayout.setVisibility(View.GONE);
            binding.confirmPasswordLayout.setVisibility(View.GONE);
        } else {
            binding.titleText.setText(R.string.create_account);
            binding.btnSubmit.setText(R.string.sign_up);
            binding.btnToggleMode.setText(R.string.already_have_account);
            binding.nameInputLayout.setVisibility(View.VISIBLE);
            binding.confirmPasswordLayout.setVisibility(View.VISIBLE);
        }
    }

    private void handleLogin() {
        String email = binding.emailInput.getText().toString().trim();
        String password = binding.passwordInput.getText().toString();

        // Validate inputs
        if (!validateEmail(email))
            return;
        if (!validatePassword(password))
            return;

        // Check user in database
        AppDatabase.databaseWriteExecutor.execute(() -> {
            User user = database.userDao().getUserByEmail(email);

            runOnUiThread(() -> {
                if (user == null) {
                    // User not found - show error
                    binding.emailInputLayout.setError("Contul nu există. Creează un cont nou!");
                } else if (user.getPassword() == null || !user.getPassword().equals(password)) {
                    // Wrong password
                    binding.passwordInputLayout.setError("Parolă incorectă!");
                } else {
                    // Login successful
                    CityScapeApp.getInstance().setCurrentUser(user.getId());
                    database.userDao().updateLastLogin(user.getId(), System.currentTimeMillis());

                    navigateAfterAuth(user);
                }
            });
        });
    }

    private void handleSignup() {
        String name = binding.nameInput.getText().toString().trim();
        String email = binding.emailInput.getText().toString().trim();
        String password = binding.passwordInput.getText().toString();
        String confirmPassword = binding.confirmPasswordInput.getText().toString();

        // Validate inputs
        if (TextUtils.isEmpty(name)) {
            binding.nameInputLayout.setError("Please enter your name");
            return;
        }
        if (!validateEmail(email))
            return;
        if (!validatePassword(password))
            return;
        if (!password.equals(confirmPassword)) {
            binding.confirmPasswordLayout.setError(getString(R.string.error_passwords_mismatch));
            return;
        }

        // Create user
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // Check if user exists
            User existingUser = database.userDao().getUserByEmail(email);

            runOnUiThread(() -> {
                if (existingUser != null) {
                    binding.emailInputLayout.setError("Email already registered");
                } else {
                    createNewUser(name, email, password);
                }
            });
        });
    }

    private void createNewUser(String name, String email, String password) {
        String userId = UUID.randomUUID().toString();
        User newUser = new User(userId, email, name);
        newUser.setPassword(password); // Store password for authentication

        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.userDao().insert(newUser);

            runOnUiThread(() -> {
                Toast.makeText(this, "Cont creat cu succes!", Toast.LENGTH_SHORT).show();
                CityScapeApp.getInstance().setCurrentUser(userId);
                navigateAfterAuth(newUser);
            });
        });
    }

    private void navigateAfterAuth(User user) {
        Intent intent;
        if (!CityScapeApp.getInstance().isProfileComplete()) {
            // Go to profile setup
            intent = new Intent(this, ProfileSetupActivity.class);
        } else {
            // Go to main
            intent = new Intent(this, MainActivity.class);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private boolean validateEmail(String email) {
        if (TextUtils.isEmpty(email)) {
            binding.emailInputLayout.setError("Email is required");
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.setError(getString(R.string.error_invalid_email));
            return false;
        }
        binding.emailInputLayout.setError(null);
        return true;
    }

    private boolean validatePassword(String password) {
        if (TextUtils.isEmpty(password)) {
            binding.passwordInputLayout.setError("Password is required");
            return false;
        }
        if (password.length() < 6) {
            binding.passwordInputLayout.setError(getString(R.string.error_password_short));
            return false;
        }
        binding.passwordInputLayout.setError(null);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
