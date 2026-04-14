package com.cityscape.app;

import android.os.Bundle;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.cityscape.app.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        com.cityscape.app.data.SessionManager sessionManager = new com.cityscape.app.data.SessionManager(this);
        com.cityscape.app.model.User currentUser = sessionManager.getCurrentUser();
        if (currentUser != null && (currentUser.interests == null || currentUser.interests.isEmpty())) {
            startActivity(new android.content.Intent(this, InterestsActivity.class));
            finish();
            return;
        }

        try {
            com.cityscape.app.data.SupabaseSyncManager.getInstance(this).initialSync();
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

        binding.fabChat.setOnClickListener(v -> {
            com.cityscape.app.ui.chat.ChatBottomSheetDialogFragment chatFragment = new com.cityscape.app.ui.chat.ChatBottomSheetDialogFragment();
            chatFragment.show(getSupportFragmentManager(), "ChatBot");
        });
    }
}