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

        com.example.licenta.data.SessionManager sessionManager = new com.example.licenta.data.SessionManager(this);
        com.example.licenta.model.User currentUser = sessionManager.getCurrentUser();
        if (currentUser != null && (currentUser.interests == null || currentUser.interests.isEmpty())) {
            startActivity(new android.content.Intent(this, InterestsActivity.class));
            finish();
            return;
        }

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

        binding.fabChat.setOnClickListener(v -> {
            com.example.licenta.ui.chat.ChatBottomSheetDialogFragment chatFragment = new com.example.licenta.ui.chat.ChatBottomSheetDialogFragment();
            chatFragment.show(getSupportFragmentManager(), "ChatBot");
        });
    }
}