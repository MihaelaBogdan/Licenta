package com.example.licenta.ui.profile;

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
import com.example.licenta.R;
import com.example.licenta.LoginActivity;
import com.example.licenta.RegisterActivity;
import com.example.licenta.SettingsActivity;
import com.example.licenta.adapter.BadgeAdapter;
import com.example.licenta.adapter.AchievementAdapter;
import com.example.licenta.data.AppDatabase;
import com.example.licenta.data.SessionManager;
import com.example.licenta.model.User;
import com.example.licenta.model.UserBadge;
import com.example.licenta.model.UserAchievement;
import com.example.licenta.model.Achievement;
import com.example.licenta.model.Badge;
import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment {

    private TextView profileName, profileTitle, xpText, levelBadge;
    private TextView statPlaces, statBadges;
    private ProgressBar xpProgress;
    private RecyclerView recyclerBadges, recyclerAchievements;
    private ImageView profileAvatar;
    private SessionManager sessionManager;
    private AppDatabase db;

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
        profileAvatar = view.findViewById(R.id.profile_avatar);
    }

    private void loadUserData() {
        User user = sessionManager.getCurrentUser();
        if (user != null) {
            profileName.setText(user.name);
            profileTitle.setText(String.format(getString(R.string.explorer_level), user.level));
            levelBadge.setText(String.valueOf(user.level));

            int xpNeeded = user.getXpForNextLevel();
            xpText.setText(String.format("%,d / %,d XP", user.currentXp, xpNeeded));
            xpProgress.setMax(100);
            xpProgress.setProgress(user.getProgressPercentage());

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
}
