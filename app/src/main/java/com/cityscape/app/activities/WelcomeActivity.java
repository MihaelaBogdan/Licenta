package com.cityscape.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.appcompat.app.AppCompatActivity;

import com.cityscape.app.CityScapeApp;
import com.cityscape.app.R;
import com.cityscape.app.databinding.ActivityWelcomeBinding;

/**
 * Welcome/Splash screen activity
 * Shows app branding and navigates to appropriate screen
 */
public class WelcomeActivity extends AppCompatActivity {

    private ActivityWelcomeBinding binding;
    private static final int SPLASH_DELAY = 2000; // 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Setup animations
        setupAnimations();

        // Setup button listeners
        setupListeners();

        // Check if user is already logged in
        if (CityScapeApp.getInstance().isLoggedIn()) {
            // Navigate directly to main after splash
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                navigateToMain();
            }, SPLASH_DELAY);
        } else {
            // Show welcome content after splash
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                showWelcomeContent();
            }, SPLASH_DELAY);
        }
    }

    private void setupAnimations() {
        // Fade in logo
        binding.logoImage.setAlpha(0f);
        binding.logoImage.animate()
                .alpha(1f)
                .setDuration(1000)
                .start();

        // Initially hide welcome content
        binding.welcomeContent.setVisibility(View.INVISIBLE);
    }

    private void showWelcomeContent() {
        binding.welcomeContent.setVisibility(View.VISIBLE);

        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up);
        binding.welcomeContent.startAnimation(slideUp);
    }

    private void setupListeners() {
        binding.btnGetStarted.setOnClickListener(v -> {
            navigateToAuth();
        });

        binding.btnGoogle.setOnClickListener(v -> {
            // TODO: Implement Google Sign In
            navigateToAuth();
        });

        binding.btnFacebook.setOnClickListener(v -> {
            // TODO: Implement Facebook Sign In
            navigateToAuth();
        });
    }

    private void navigateToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
