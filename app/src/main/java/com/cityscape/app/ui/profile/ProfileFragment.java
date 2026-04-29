package com.cityscape.app.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.cityscape.app.R;
import com.cityscape.app.LoginActivity;
import com.cityscape.app.RegisterActivity;
import com.cityscape.app.SettingsActivity;
import com.cityscape.app.adapter.BadgeAdapter;
import com.cityscape.app.adapter.AchievementAdapter;
import com.cityscape.app.data.AppDatabase;
import com.cityscape.app.data.SessionManager;
import com.cityscape.app.model.User;
import com.cityscape.app.model.UserBadge;
import com.cityscape.app.model.Achievement;
import com.cityscape.app.model.Badge;
import com.cityscape.app.model.UserAchievement;
import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment {

    private TextView profileName, profileTitle, xpText, levelBadge, titleMyPosts;
    private TextView statPlaces, statBadges;
    private ProgressBar xpProgress;
    private TextView xpNeededText;
    private RecyclerView recyclerBadges, recyclerAchievements, recyclerUserPosts;
    private ImageView profileAvatar;
    private SessionManager sessionManager;
    private AppDatabase db;
    private com.cityscape.app.api.ApiService apiService;

    // Containers
    private LinearLayout guestContainer;
    private LinearLayout profileContent;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        sessionManager = new SessionManager(requireContext());
        db = AppDatabase.getInstance(requireContext());

        // Get containers
        guestContainer = view.findViewById(R.id.guest_container);
        profileContent = view.findViewById(R.id.profile_content);

        if (sessionManager.isLoggedIn()) {
            // Show profile
            guestContainer.setVisibility(View.GONE);
            profileContent.setVisibility(View.VISIBLE);

            initViews(view);
            loadUserData();
            loadUserPosts();
            setupBadges();
            setupAchievements();

            // Settings button on long press of avatar
            profileAvatar.setOnLongClickListener(v -> {
                startActivity(new Intent(requireContext(), SettingsActivity.class));
                return true;
            });

            // Settings button
            View settingsButton = view.findViewById(R.id.btn_settings);
            if (settingsButton != null) {
                settingsButton.setOnClickListener(v -> {
                    startActivity(new Intent(requireContext(), SettingsActivity.class));
                });
            }
        } else {
            // Show guest state
            guestContainer.setVisibility(View.VISIBLE);
            profileContent.setVisibility(View.GONE);

            Button btnLogin = view.findViewById(R.id.btn_guest_login);
            Button btnRegister = view.findViewById(R.id.btn_guest_register);

            btnLogin.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), LoginActivity.class));
            });

            btnRegister.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), RegisterActivity.class));
            });
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Re-check login state on resume (user might have just logged in)
        if (sessionManager.isLoggedIn()) {
            if (guestContainer != null)
                guestContainer.setVisibility(View.GONE);
            if (profileContent != null) {
                profileContent.setVisibility(View.VISIBLE);
                if (profileName != null) {
                    loadUserData();
                    setupBadges();
                    setupAchievements();
                } else {
                    // Views not yet initialized, init them
                    initViews(profileContent);
                    loadUserData();
                    setupBadges();
                    setupAchievements();
                }
            }
        }
    }

    private void initViews(View view) {
        profileName = view.findViewById(R.id.profile_name);
        profileTitle = view.findViewById(R.id.profile_title);
        xpText = view.findViewById(R.id.xp_text);
        levelBadge = view.findViewById(R.id.level_badge);
        statPlaces = view.findViewById(R.id.stat_places);
        statBadges = view.findViewById(R.id.stat_badges);
        xpProgress = view.findViewById(R.id.xp_progress);
        recyclerBadges = view.findViewById(R.id.recycler_badges);
        recyclerAchievements = view.findViewById(R.id.recycler_achievements);
        recyclerUserPosts = view.findViewById(R.id.recycler_user_posts);
        titleMyPosts = view.findViewById(R.id.title_my_posts);
        profileAvatar = view.findViewById(R.id.profile_avatar);
        xpNeededText = view.findViewById(R.id.xp_needed_text);
        
        apiService = com.cityscape.app.api.ApiClient.getClient().create(com.cityscape.app.api.ApiService.class);
    }

    private void loadUserData() {
        User user = sessionManager.getCurrentUser();
        if (user != null && profileName != null) {
            profileName.setText(user.name);
            profileTitle.setText(String.format(getString(R.string.explorer_level), user.level).toUpperCase());
            levelBadge.setText(String.valueOf(user.level));

            int xpNeeded = user.getXpForNextLevel();
            xpText.setText(String.format("%,d / %,d XP", user.currentXp, xpNeeded));
            xpProgress.setMax(100);
            xpProgress.setProgress(user.getProgressPercentage());

            // Handle the new 'XP needed' text
            if (xpNeededText != null) {
                int remaining = xpNeeded - user.currentXp;
                xpNeededText.setText(String.format("Mai ai nevoie de %d XP pentru nivelul %d", remaining, user.level + 1));
            }

            statPlaces.setText(String.valueOf(user.placesVisited));
            statBadges.setText(String.valueOf(user.badgesEarned));
        }
    }

    private void setupBadges() {
        List<UserBadge> userBadges = db.badgeDao().getBadgesForUser(sessionManager.getUserId());
        List<Badge> badges = new ArrayList<>();

        for (UserBadge ub : userBadges) {
            int iconRes = getIconResource(ub.iconName);
            badges.add(new Badge(ub.name, ub.description, iconRes, ub.isUnlocked));
        }

        BadgeAdapter adapter = new BadgeAdapter(badges);
        recyclerBadges.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerBadges.setAdapter(adapter);
    }

    private int getIconResource(String iconName) {
        switch (iconName) {
            case "ic_badge_first":
                return R.drawable.ic_badge_first;
            case "ic_badge_coffee":
                return R.drawable.ic_badge_coffee;
            case "ic_badge_culture":
                return R.drawable.ic_badge_culture;
            case "ic_badge_night":
                return R.drawable.ic_badge_night;
            case "ic_badge_explorer":
                return R.drawable.ic_badge_explorer;
            case "ic_badge_social":
                return R.drawable.ic_badge_explorer; // Using explorer as fallback for social post badge
            default:
                return R.drawable.ic_badge_default;
        }
    }

    private void setupAchievements() {
        List<UserAchievement> userAchievements = db.achievementDao()
                .getRecentAchievements(sessionManager.getUserId(), 10);
        List<Achievement> achievements = new ArrayList<>();

        for (UserAchievement ua : userAchievements) {
            achievements.add(new Achievement(ua.title, "+" + ua.xpReward + " XP", ua.getTimeAgo()));
        }

        AchievementAdapter adapter = new AchievementAdapter(achievements);
        recyclerAchievements.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerAchievements.setAdapter(adapter);
    }

    private void loadUserPosts() {

        if (recyclerUserPosts == null) return;
        
        apiService.getUserPosts(sessionManager.getUserId()).enqueue(new retrofit2.Callback<List<com.cityscape.app.model.FeedPost>>() {
            @Override
            public void onResponse(retrofit2.Call<List<com.cityscape.app.model.FeedPost>> call, retrofit2.Response<List<com.cityscape.app.model.FeedPost>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    List<com.cityscape.app.model.Place> postsAsPlaces = new ArrayList<>();
                    for (com.cityscape.app.model.FeedPost post : response.body()) {
                        com.cityscape.app.model.Place p = new com.cityscape.app.model.Place();
                        p.id = post.id;
                        p.name = post.placeName;
                        p.imageUrl = post.imageUrl;
                        p.type = "Postare";
                        p.rating = (float) post.rating;
                        postsAsPlaces.add(p);
                    }
                    
                    com.cityscape.app.adapter.PlaceAdapter adapter = new com.cityscape.app.adapter.PlaceAdapter(getContext(), postsAsPlaces, true, null);
                    recyclerUserPosts.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                    recyclerUserPosts.setAdapter(adapter);
                    
                    recyclerUserPosts.setVisibility(View.VISIBLE);
                    if (titleMyPosts != null) titleMyPosts.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<List<com.cityscape.app.model.FeedPost>> call, Throwable t) { }
        });
    }
}
