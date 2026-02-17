package com.example.licenta.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.licenta.R;
import com.example.licenta.LoginActivity;
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        sessionManager = new SessionManager(requireContext());
        db = AppDatabase.getInstance(requireContext());

        initViews(view);
        loadUserData();
        setupBadges();
        setupAchievements();

        // Logout on long press of avatar
        profileAvatar.setOnLongClickListener(v -> {
            sessionManager.logout();
            startActivity(new Intent(requireContext(), LoginActivity.class));
            requireActivity().finish();
            return true;
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserData();
        setupBadges();
        setupAchievements();
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
            profileTitle.setText("Explorer Level " + user.level);
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
