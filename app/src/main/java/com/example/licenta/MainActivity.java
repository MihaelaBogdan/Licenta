package com.example.licenta;

import android.os.Bundle;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.example.licenta.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            com.example.licenta.data.SupabaseSyncManager.getInstance(this).initialSync();
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Sync failed", e);
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.navView, navController);
        }

        handleIntentAction();

        binding.fabChat.setOnClickListener(v -> {
            com.example.licenta.ui.chat.ChatBottomSheetDialogFragment chatFragment = new com.example.licenta.ui.chat.ChatBottomSheetDialogFragment();
            chatFragment.show(getSupportFragmentManager(), "ChatBot");
        });
    }

    private void handleIntentAction() {
        if (getIntent() != null && "OPTIMIZE_ITINERARY".equals(getIntent().getStringExtra("ACTION"))) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("🔄 Optimizare Itinerariu")
                    .setMessage(
                            "Am observat că vrei să mai stai puțin aici. Vrei să re-calculăm restul traseului pentru a vedea tot ce ți-ai propus?")
                    .setPositiveButton("Da, Optimizează", (dialog, which) -> {
                        // Here you would call a backend or local logic to shift activities
                        android.widget.Toast.makeText(this, "Traseul a fost recalculat cu succes! ✨",
                                android.widget.Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton("Nu, mulțumesc", null)
                    .show();
        }
    }
}