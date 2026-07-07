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
        if (currentUser != null && (currentUser.interests == null || currentUser.interests.isEmpty()) && !sessionManager.isInterestsCompleted()) {
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

        if (!isNetworkAvailable()) {
            binding.navView.getMenu().removeItem(R.id.navigation_feed);
            binding.navView.getMenu().removeItem(R.id.navigation_calendar);
            
            binding.fabChat.setVisibility(android.view.View.GONE);
        }

        binding.fabChat.setOnClickListener(v -> {
            com.cityscape.app.ui.chat.ChatBottomSheetDialogFragment chatFragment = new com.cityscape.app.ui.chat.ChatBottomSheetDialogFragment();
            chatFragment.show(getSupportFragmentManager(), "ChatBot");
        });
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            android.net.NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
            if (capabilities != null) {
                return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                       capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                       capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET);
            }
        }
        return false;
    }

    public void navigateToTab(int menuItemId) {
        binding.navView.setSelectedItemId(menuItemId);
    }
}