package com.cityscape.app.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import com.google.android.material.button.MaterialButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private android.content.Context appContext;

    // Segmented profile tabs
    private MaterialButton btnTabDiscoveries, btnTabLiked, btnTabBookmarked;
    private View layoutProfileTabs;
    private String currentCollectionTab = "discoveries"; // discoveries, liked, bookmarked
    private int secretClickCount = 0;

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
        appContext = requireContext().getApplicationContext();

        // Get containers
        guestContainer = view.findViewById(R.id.guest_container);
        profileContent = view.findViewById(R.id.profile_content);

        if (sessionManager.isLoggedIn()) {
            // Show profile
            guestContainer.setVisibility(View.GONE);
            profileContent.setVisibility(View.VISIBLE);

            initViews(view);
            loadUserData();
            setupProfileTabs();
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

            // Report Buttons
            View btnReport = view.findViewById(R.id.btn_generate_report);
            if (btnReport != null) btnReport.setOnClickListener(v -> showUserReport());

            View btnAdmin = view.findViewById(R.id.btn_admin_stats);
            if (btnAdmin != null) {
                // Show only for admins
                if (sessionManager.getEmail() != null && sessionManager.getEmail().contains("admin")) {
                    btnAdmin.setVisibility(View.VISIBLE);
                } else {
                    btnAdmin.setVisibility(View.GONE);
                }
                btnAdmin.setOnClickListener(v -> showAdminStats());
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
        
        btnTabDiscoveries = view.findViewById(R.id.btn_tab_discoveries);
        btnTabLiked = view.findViewById(R.id.btn_tab_liked);
        btnTabBookmarked = view.findViewById(R.id.btn_tab_bookmarked);
        layoutProfileTabs = view.findViewById(R.id.layout_profile_tabs);
        
        apiService = com.cityscape.app.api.ApiClient.getClient().create(com.cityscape.app.api.ApiService.class);

        // Easter Egg Listener
        if (profileAvatar != null) {
            profileAvatar.setOnClickListener(v -> {
                secretClickCount++;
                if (secretClickCount == 5) {
                    secretClickCount = 0;
                    User user = sessionManager.getCurrentUser();
                    if (user != null && getContext() != null) {
                        com.cityscape.app.util.BadgeManager.addExperience(getContext().getApplicationContext(), user.id, 1000);
                        com.cityscape.app.util.BadgeManager.awardBadge(getContext().getApplicationContext(), user.id, 
                                "licenta10", "Licență de Nota 10 🎓", 
                                "Felicitări! Ai deblocat secretul din codul aplicației pentru evaluare academică de elită!", 
                                "Apasă de 5 ori pe avatarul de profil", 
                                "ic_badge_generic");
                        
                        boolean isEn = "en".equals(java.util.Locale.getDefault().getLanguage());
                        new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                                .setTitle(isEn ? "🎓 EASTER EGG UNLOCKED!" : "🎓 EASTER EGG DEBLOCAT!")
                                .setMessage(isEn ? "Congratulations! You discovered the secret hidden in the CityScape app code!\n\n🎒 You received +1000 XP of academic excellence and the elite badge 'Thesis with Honors'! 🏆\n\nBest of luck with your thesis presentation!" : "Felicitări! Ai descoperit secretul ascuns în codul aplicației CityScape!\n\n🎒 Ai primit +1000 XP de excelență academică și insigna de elită 'Licență de Nota 10'! 🏆\n\nSucces maxim la prezentarea tezei!")
                                .setPositiveButton(isEn ? "Amazing!" : "Senzațional!", null)
                                .show();
                    }
                } else {
                    v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).withEndAction(() -> v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120)).start();
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                }
            });
        }
    }

    private void loadUserData() {
        User user = sessionManager.getCurrentUser();
        if (user != null && profileName != null) {
            profileName.setText(user.name);
            profileTitle.setText(String.format(getString(R.string.explorer_level), user.level).toUpperCase());
            levelBadge.setText(String.valueOf(user.level));
            
            if (user.avatar != null && !user.avatar.isEmpty() && profileAvatar != null && isAdded()) {
                com.bumptech.glide.Glide.with(this)
                        .load(user.avatar)
                        .circleCrop()
                        .placeholder(R.drawable.ic_profile)
                        .into(profileAvatar);
            }

            int xpNeeded = user.getXpForNextLevel();
            xpText.setText(String.format("%,d / %,d XP", user.currentXp, xpNeeded));
            xpProgress.setMax(100);
            xpProgress.setProgress(user.getProgressPercentage());

            // Handle the new 'XP needed' text
            if (xpNeededText != null) {
                int remaining = xpNeeded - user.currentXp;
                boolean isEn = "en".equals(java.util.Locale.getDefault().getLanguage());
                if (isEn) {
                    xpNeededText.setText(String.format("You need %d more XP for level %d", remaining, user.level + 1));
                } else {
                    xpNeededText.setText(String.format("Mai ai nevoie de %d XP pentru nivelul %d", remaining, user.level + 1));
                }
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
            badges.add(new Badge(ub.name, ub.description, ub.requirement, iconRes, ub.isUnlocked));
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

    private void setupProfileTabs() {
        if (layoutProfileTabs == null) return;
        
        layoutProfileTabs.setVisibility(View.VISIBLE);
        if (titleMyPosts != null) titleMyPosts.setVisibility(View.VISIBLE);
        
        btnTabDiscoveries.setOnClickListener(v -> switchTab("discoveries"));
        btnTabLiked.setOnClickListener(v -> switchTab("liked"));
        btnTabBookmarked.setOnClickListener(v -> switchTab("bookmarked"));
        
        switchTab("discoveries"); // Default active
    }
    
    private void switchTab(String tab) {
        currentCollectionTab = tab;
        if (getContext() == null) return;
        
        // Tab button visual states
        if (tab.equals("discoveries")) {
            btnTabDiscoveries.setTextColor(getContext().getColor(R.color.white));
            btnTabDiscoveries.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getContext().getColor(R.color.primary)));
            btnTabDiscoveries.setStrokeWidth(0);
            
            btnTabLiked.setTextColor(getContext().getColor(R.color.app_text_secondary));
            btnTabLiked.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            btnTabLiked.setStrokeWidth(1);
            
            btnTabBookmarked.setTextColor(getContext().getColor(R.color.app_text_secondary));
            btnTabBookmarked.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            btnTabBookmarked.setStrokeWidth(1);
            
            loadUserPosts();
        } else if (tab.equals("liked")) {
            btnTabDiscoveries.setTextColor(getContext().getColor(R.color.app_text_secondary));
            btnTabDiscoveries.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            btnTabDiscoveries.setStrokeWidth(1);
            
            btnTabLiked.setTextColor(getContext().getColor(R.color.white));
            btnTabLiked.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getContext().getColor(R.color.primary)));
            btnTabLiked.setStrokeWidth(0);
            
            btnTabBookmarked.setTextColor(getContext().getColor(R.color.app_text_secondary));
            btnTabBookmarked.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            btnTabBookmarked.setStrokeWidth(1);
            
            loadLikedPosts();
        } else if (tab.equals("bookmarked")) {
            btnTabDiscoveries.setTextColor(getContext().getColor(R.color.app_text_secondary));
            btnTabDiscoveries.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            btnTabDiscoveries.setStrokeWidth(1);
            
            btnTabLiked.setTextColor(getContext().getColor(R.color.app_text_secondary));
            btnTabLiked.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            btnTabLiked.setStrokeWidth(1);
            
            btnTabBookmarked.setTextColor(getContext().getColor(R.color.white));
            btnTabBookmarked.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getContext().getColor(R.color.primary)));
            btnTabBookmarked.setStrokeWidth(0);
            
            loadBookmarkedPosts();
        }
    }

    private void loadUserPosts() {
        if (recyclerUserPosts == null) return;
        
        apiService.getUserPosts(sessionManager.getUserId()).enqueue(new retrofit2.Callback<List<com.cityscape.app.model.FeedPost>>() {
            @Override
            public void onResponse(retrofit2.Call<List<com.cityscape.app.model.FeedPost>> call, retrofit2.Response<List<com.cityscape.app.model.FeedPost>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<com.cityscape.app.model.FeedPost> userPostsList = response.body();
                    List<com.cityscape.app.model.Place> postsAsPlaces = new ArrayList<>();
                    for (com.cityscape.app.model.FeedPost post : userPostsList) {
                        com.cityscape.app.model.Place p = new com.cityscape.app.model.Place();
                        p.id = post.id;
                        p.name = post.placeName;
                        p.imageUrl = post.imageUrl;
                        p.type = "Postare";
                        p.rating = (float) post.rating;
                        postsAsPlaces.add(p);
                    }
                    
                    if (postsAsPlaces.isEmpty()) {
                        recyclerUserPosts.setVisibility(View.GONE);
                        return;
                    }
                    
                    com.cityscape.app.adapter.PlaceAdapter.OnPlaceClickListener postClickListener = new com.cityscape.app.adapter.PlaceAdapter.OnPlaceClickListener() {
                        @Override
                        public void onPlaceClick(com.cityscape.app.model.Place place) {
                            for (com.cityscape.app.model.FeedPost fp : userPostsList) {
                                if (fp.id.equals(place.id)) {
                                    showPostDetailDialog(fp);
                                    break;
                                }
                            }
                        }
                        @Override public void onFavoriteClick(com.cityscape.app.model.Place place) {}
                        @Override public void onVisitedClick(com.cityscape.app.model.Place place) {}
                        @Override public void onPlanClick(com.cityscape.app.model.Place place) {}
                    };
                    
                    com.cityscape.app.adapter.PlaceAdapter adapter = new com.cityscape.app.adapter.PlaceAdapter(getContext(), postsAsPlaces, true, postClickListener);
                    recyclerUserPosts.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                    recyclerUserPosts.setAdapter(adapter);
                    recyclerUserPosts.setVisibility(View.VISIBLE);
                } else {
                    recyclerUserPosts.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<List<com.cityscape.app.model.FeedPost>> call, Throwable t) {
                recyclerUserPosts.setVisibility(View.GONE);
            }
        });
    }

    private void loadLikedPosts() {
        if (recyclerUserPosts == null) return;
        
        apiService.getLikedPosts(sessionManager.getUserId()).enqueue(new retrofit2.Callback<List<com.cityscape.app.model.FeedPost>>() {
            @Override
            public void onResponse(retrofit2.Call<List<com.cityscape.app.model.FeedPost>> call, retrofit2.Response<List<com.cityscape.app.model.FeedPost>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<com.cityscape.app.model.FeedPost> likedPostsList = response.body();
                    List<com.cityscape.app.model.Place> postsAsPlaces = new ArrayList<>();
                    for (com.cityscape.app.model.FeedPost post : likedPostsList) {
                        com.cityscape.app.model.Place p = new com.cityscape.app.model.Place();
                        p.id = post.id;
                        p.name = post.placeName;
                        p.imageUrl = post.imageUrl;
                        p.type = "Postare";
                        p.rating = (float) post.rating;
                        postsAsPlaces.add(p);
                    }
                    
                    if (postsAsPlaces.isEmpty()) {
                        recyclerUserPosts.setVisibility(View.GONE);
                        return;
                    }
                    
                    com.cityscape.app.adapter.PlaceAdapter.OnPlaceClickListener postClickListener = new com.cityscape.app.adapter.PlaceAdapter.OnPlaceClickListener() {
                        @Override
                        public void onPlaceClick(com.cityscape.app.model.Place place) {
                            for (com.cityscape.app.model.FeedPost fp : likedPostsList) {
                                if (fp.id.equals(place.id)) {
                                    showPostDetailDialog(fp);
                                    break;
                                }
                            }
                        }
                        @Override public void onFavoriteClick(com.cityscape.app.model.Place place) {}
                        @Override public void onVisitedClick(com.cityscape.app.model.Place place) {}
                        @Override public void onPlanClick(com.cityscape.app.model.Place place) {}
                    };
                    
                    com.cityscape.app.adapter.PlaceAdapter adapter = new com.cityscape.app.adapter.PlaceAdapter(getContext(), postsAsPlaces, true, postClickListener);
                    recyclerUserPosts.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                    recyclerUserPosts.setAdapter(adapter);
                    recyclerUserPosts.setVisibility(View.VISIBLE);
                } else {
                    recyclerUserPosts.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<List<com.cityscape.app.model.FeedPost>> call, Throwable t) {
                recyclerUserPosts.setVisibility(View.GONE);
            }
        });
    }

    private void loadBookmarkedPosts() {
        if (recyclerUserPosts == null) return;
        
        new Thread(() -> {
            List<String> bookmarkedIds = db.bookmarkDao().getBookmarkedPostIds(sessionManager.getUserId());
            if (bookmarkedIds.isEmpty()) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> recyclerUserPosts.setVisibility(View.GONE));
                }
                return;
            }
            
            String ids_str = String.join(",", bookmarkedIds);
            apiService.getFeedByIds(ids_str, sessionManager.getUserId()).enqueue(new retrofit2.Callback<List<com.cityscape.app.model.FeedPost>>() {
                @Override
                public void onResponse(retrofit2.Call<List<com.cityscape.app.model.FeedPost>> call, retrofit2.Response<List<com.cityscape.app.model.FeedPost>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        List<com.cityscape.app.model.FeedPost> bookmarkedPostsList = response.body();
                        List<com.cityscape.app.model.Place> postsAsPlaces = new ArrayList<>();
                        for (com.cityscape.app.model.FeedPost post : bookmarkedPostsList) {
                            com.cityscape.app.model.Place p = new com.cityscape.app.model.Place();
                            p.id = post.id;
                            p.name = post.placeName;
                            p.imageUrl = post.imageUrl;
                            p.type = "Postare";
                            p.rating = (float) post.rating;
                            postsAsPlaces.add(p);
                        }
                        
                        if (postsAsPlaces.isEmpty()) {
                            recyclerUserPosts.setVisibility(View.GONE);
                            return;
                        }
                        
                        com.cityscape.app.adapter.PlaceAdapter.OnPlaceClickListener postClickListener = new com.cityscape.app.adapter.PlaceAdapter.OnPlaceClickListener() {
                            @Override
                            public void onPlaceClick(com.cityscape.app.model.Place place) {
                                for (com.cityscape.app.model.FeedPost fp : bookmarkedPostsList) {
                                    if (fp.id.equals(place.id)) {
                                        showPostDetailDialog(fp);
                                        break;
                                    }
                                }
                            }
                            @Override public void onFavoriteClick(com.cityscape.app.model.Place place) {}
                            @Override public void onVisitedClick(com.cityscape.app.model.Place place) {}
                            @Override public void onPlanClick(com.cityscape.app.model.Place place) {}
                        };
                        
                        com.cityscape.app.adapter.PlaceAdapter adapter = new com.cityscape.app.adapter.PlaceAdapter(getContext(), postsAsPlaces, true, postClickListener);
                        recyclerUserPosts.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                        recyclerUserPosts.setAdapter(adapter);
                        recyclerUserPosts.setVisibility(View.VISIBLE);
                    } else {
                        recyclerUserPosts.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<List<com.cityscape.app.model.FeedPost>> call, Throwable t) {
                    recyclerUserPosts.setVisibility(View.GONE);
                }
            });
        }).start();
    }

    private void showPostDetailDialog(com.cityscape.app.model.FeedPost post) {
        if (!isAdded()) return;
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme);
        View v = getLayoutInflater().inflate(R.layout.dialog_post_detail, null);
        builder.setView(v);
        
        ImageView image = v.findViewById(R.id.detail_post_image);
        TextView txtType = v.findViewById(R.id.detail_post_type);
        TextView txtRating = v.findViewById(R.id.detail_post_rating);
        TextView txtName = v.findViewById(R.id.detail_post_place_name);
        TextView txtCaption = v.findViewById(R.id.detail_post_caption);
        Button btnClose = v.findViewById(R.id.btn_close_detail);
        
        if (txtName != null) txtName.setText(post.placeName);
        if (txtCaption != null) txtCaption.setText(post.userName + ": \"" + post.caption + "\"");
        if (txtRating != null) txtRating.setText(String.format("%.1f", post.rating));
        
        if (image != null && post.imageUrl != null && !post.imageUrl.isEmpty()) {
            com.bumptech.glide.Glide.with(this)
                .load(post.imageUrl)
                .centerCrop()
                .placeholder(R.drawable.placeholder_place)
                .into(image);
        }
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        
        if (btnClose != null) {
            btnClose.setOnClickListener(view -> dialog.dismiss());
        }
        
        dialog.show();
    }



    private void showUserReport() {
        if (!isAdded()) return;
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setTitle("Generare Raport...")
                .setMessage("Se preiau datele de la server...")
                .show();

        apiService.getUserReport(sessionManager.getUserId()).enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
            @Override
            public void onResponse(retrofit2.Call<com.google.gson.JsonObject> call, retrofit2.Response<com.google.gson.JsonObject> response) {
                if (!isAdded()) return;
                dialog.dismiss();
                if (response.isSuccessful() && response.body() != null) {
                    com.google.gson.JsonObject r = response.body();
                    
                    boolean isEn = "en".equals(java.util.Locale.getDefault().getLanguage());
                    StringBuilder sb = new StringBuilder();
                    sb.append(isEn ? "📊 CITYSCAPE ACTIVITY REPORT\n\n" : "📊 RAPORT ACTIVITATE CITYSCAPE\n\n");
                    sb.append(isEn ? "👤 User: " : "👤 Utilizator: ").append(r.get("user_name").getAsString()).append("\n");
                    sb.append(isEn ? "🏅 Level: " : "🏅 Nivel: ").append(r.get("level").getAsInt()).append("\n");
                    sb.append(isEn ? "⭐ Total XP: " : "⭐ Total XP: ").append(r.get("total_xp").getAsInt()).append("\n\n");
                    
                    sb.append(isEn ? "📍 Places visited: " : "📍 Locații vizitate: ").append(r.get("places_visited_count").getAsInt()).append("\n");
                    sb.append(isEn ? "❤️ Favorite category: " : "❤️ Categoria preferată: ").append(r.get("favorite_category").getAsString()).append("\n");
                    sb.append(isEn ? "📝 Posts created: " : "📝 Postări create: ").append(r.get("posts_created").getAsInt()).append("\n");
                    sb.append(isEn ? "💎 Badges earned: " : "💎 Insigne câștigate: ").append(r.get("badges_count").getAsInt()).append("\n\n");
                    
                    sb.append(isEn ? "📅 Member since: " : "📅 Membru din: ").append(r.get("join_date").getAsString()).append("\n\n");
                    
                    if (r.has("recent_visits") && r.get("recent_visits").getAsJsonArray().size() > 0) {
                        sb.append(isEn ? "Recent adventures:\n" : "Ultimile aventuri:\n");
                        for (com.google.gson.JsonElement e : r.get("recent_visits").getAsJsonArray()) {
                            sb.append(" • ").append(e.getAsString()).append("\n");
                        }
                    }

                    new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                            .setTitle(isEn ? "Your CityScape Report" : "Raportul Tău CityScape")
                            .setMessage(sb.toString())
                            .setPositiveButton(isEn ? "Close" : "Închide", null)
                            .setNeutralButton(isEn ? "Share" : "Partajează", (d, w) -> {
                                String[] shareOptions;
                                if (isEn) {
                                    shareOptions = new String[]{
                                        "Send to friends in group chat 💬",
                                        "Share externally (WhatsApp, Instagram, etc.) 📤"
                                    };
                                } else {
                                    shareOptions = new String[]{
                                        "Trimite prietenilor în chat-ul de grup 💬",
                                        "Partajează extern (WhatsApp, Instagram, etc.) 📤"
                                    };
                                }
                                new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                                        .setTitle(isEn ? "How would you like to share?" : "Cum dorești să partajezi?")
                                        .setItems(shareOptions, (subDialog, optionIndex) -> {
                                            if (optionIndex == 0) {
                                                // Load groups on background thread
                                                new Thread(() -> {
                                                    List<com.cityscape.app.model.ActivityGroup> groups = db.groupDao().getGroupsForUser(sessionManager.getUserId());
                                                    if (groups == null || groups.isEmpty()) {
                                                        // Fallback to sending to a friend (users list)
                                                        List<com.cityscape.app.model.User> friends = db.userDao().getAllUsers();
                                                        if (getActivity() == null) return;
                                                        getActivity().runOnUiThread(() -> {
                                                            if (friends == null || friends.isEmpty()) {
                                                                Toast.makeText(getContext(), getString(R.string.no_active_friends), Toast.LENGTH_SHORT).show();
                                                                return;
                                                            }
                                                            String[] friendNames = new String[friends.size()];
                                                            for (int i = 0; i < friends.size(); i++) {
                                                                friendNames[i] = friends.get(i).name;
                                                            }
                                                            new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                                                                    .setTitle(getString(R.string.enter_place_name_dialog))
                                                                    .setItems(friendNames, (fDialog, fIndex) -> {
                                                                        com.cityscape.app.model.User chosenFriend = friends.get(fIndex);
                                                                        com.cityscape.app.util.BadgeManager.addExperience(appContext, sessionManager.getUserId(), 100);
                                                                        Toast.makeText(getContext(), getString(R.string.report_sent) + " " + chosenFriend.name + getString(R.string.ai_error), Toast.LENGTH_LONG).show();
                                                                    })
                                                                    .show();
                                                        });
                                                    } else {
                                                        // List user's groups to share to
                                                        if (getActivity() == null) return;
                                                        getActivity().runOnUiThread(() -> {
                                                            String[] groupNames = new String[groups.size()];
                                                            for (int i = 0; i < groups.size(); i++) {
                                                                groupNames[i] = groups.get(i).groupName;
                                                            }
                                                            new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                                                                    .setTitle(getString(R.string.choose))
                                                                    .setItems(groupNames, (gDialog, gIndex) -> {
                                                                        com.cityscape.app.model.ActivityGroup chosenGroup = groups.get(gIndex);
                                                                        
                                                                        // Post message to the group chat
                                                                        new Thread(() -> {
                                                                            com.cityscape.app.model.GroupMessage sharedMsg = new com.cityscape.app.model.GroupMessage(
                                                                                chosenGroup.id, 
                                                                                sessionManager.getUserId(), 
                                                                                sessionManager.getCurrentUser() != null ? sessionManager.getCurrentUser().name : "Eu",
                                                                                "📊 " + (isEn ? "MY ACTIVITY REPORT:\n\n" : "RAPORTUL MEU ACTIVITATE:\n\n") + sb.toString()
                                                                            );
                                                                            db.groupMessageDao().insert(sharedMsg);
                                                                            com.cityscape.app.util.BadgeManager.addExperience(appContext, sessionManager.getUserId(), 150);
                                                                            
                                                                            if (getActivity() != null) {
                                                                                getActivity().runOnUiThread(() -> {
                                                                                    Toast.makeText(getContext(), getString(R.string.story_sent_to_group) + " '" + chosenGroup.groupName + getString(R.string.ai_error), Toast.LENGTH_LONG).show();
                                                                                });
                                                                            }
                                                                        }).start();
                                                                    })
                                                                    .show();
                                                        });
                                                    }
                                                }).start();
                                            } else {
                                                // External share
                                                Intent s = new Intent(Intent.ACTION_SEND); 
                                                s.setType("text/plain");
                                                s.putExtra(Intent.EXTRA_TEXT, sb.toString());
                                                startActivity(Intent.createChooser(s, isEn ? "Send Report" : "Trimite Raport"));
                                            }
                                        })
                                        .show();
                            })
                            .show();
                } else {
                    Toast.makeText(getContext(), getString(R.string.report_generation_failed), Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(retrofit2.Call<com.google.gson.JsonObject> call, Throwable t) {
                if (isAdded()) {
                    dialog.dismiss();
                    Toast.makeText(getContext(), getString(R.string.server_error), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showAdminStats() {
        if (!isAdded()) return;
        apiService.getAdminStats().enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
            @Override
            public void onResponse(retrofit2.Call<com.google.gson.JsonObject> call, retrofit2.Response<com.google.gson.JsonObject> response) {
                if (isAdded() && response.isSuccessful() && response.body() != null) {
                    com.google.gson.JsonObject s = response.body();
                    boolean isEn = "en".equals(java.util.Locale.getDefault().getLanguage());
                    StringBuilder sb = new StringBuilder();
                    sb.append("🛡️ ADMIN DASHBOARD STATS\n\n");
                    sb.append(isEn ? "👥 Total Users: " : "👥 Total Utilizatori: ").append(s.get("total_users").getAsInt()).append("\n");
                    sb.append(isEn ? "📮 Total Posts: " : "📮 Total Postări: ").append(s.get("total_posts").getAsInt()).append("\n");
                    sb.append(isEn ? "👣 Total Visits: " : "👣 Total Vizite: ").append(s.get("total_visits").getAsInt()).append("\n");
                    sb.append(isEn ? "⚠️ Total Reports: " : "⚠️ Total Raportări: ").append(s.get("total_reports").getAsInt()).append("\n\n");
                    
                    sb.append(isEn ? "🔴 Pending reports: " : "🔴 Raportări în așteptare: ").append(s.get("pending_reports_count").getAsInt()).append("\n");
                    sb.append(isEn ? "✅ System Status: " : "✅ Status Sistem: ").append(s.get("system_health").getAsString()).append("\n");

                    new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                            .setTitle(isEn ? "Global Statistics" : "Statistici Globale")
                            .setMessage(sb.toString())
                            .setPositiveButton("OK", null)
                            .show();
                }
            }
            @Override public void onFailure(retrofit2.Call<com.google.gson.JsonObject> call, Throwable t) {}
        });
    }

    private void showMysticalTravelStory() {
        if (!isAdded()) return;
        boolean isEn = "en".equals(java.util.Locale.getDefault().getLanguage());
        AlertDialog progressDialog = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setTitle(isEn ? "AI Chronicler..." : "Cronicarul AI...")
                .setMessage(isEn ? "Compiling your adventures into a mystical manuscript..." : "Se compilează aventurile tale într-un manuscris mistic...")
                .show();

        new Thread(() -> {
            List<String> visitedNames = new ArrayList<>();
            try {
                List<com.cityscape.app.model.PlannedActivity> list = db.activityDao().getActivitiesForUser(sessionManager.getUserId());
                if (list != null) {
                    for (com.cityscape.app.model.PlannedActivity act : list) {
                        if (act.placeName != null && !act.placeName.isEmpty()) {
                            visitedNames.add(act.placeName.replace("🏆 ", ""));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("ProfileFragment", "Error fetching visited activities", e);
            }

            if (visitedNames.isEmpty()) {
                visitedNames.add("Sala Palatului");
                visitedNames.add("Teatrul Național");
                visitedNames.add("Art Safari");
            }

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("places", visitedNames);
            payload.put("user_name", sessionManager.getCurrentUser() != null ? sessionManager.getCurrentUser().name : "Mihaela Bogdan");

            apiService.generateTravelStory(payload).enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
                @Override
                public void onResponse(retrofit2.Call<com.google.gson.JsonObject> call, retrofit2.Response<com.google.gson.JsonObject> response) {
                    if (!isAdded()) return;
                    progressDialog.dismiss();

                    if (response.isSuccessful() && response.body() != null) {
                        com.google.gson.JsonObject res = response.body();
                        String story = res.get("story").getAsString();

                        new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                                .setTitle(isEn ? "📖 AI Mystical Diary" : "📖 Jurnalul Mistic AI")
                                .setMessage(story)
                                .setPositiveButton(isEn ? "Close" : "Închide", null)
                                .setNeutralButton(isEn ? "Share" : "Partajează", (dialog, which) -> {
                                    String[] shareOptions;
                                    if (isEn) {
                                        shareOptions = new String[]{
                                            "Send to friends in group chat 💬",
                                            "Copy text 📋"
                                        };
                                    } else {
                                        shareOptions = new String[]{
                                            "Trimite prietenilor în chat-ul de grup 💬",
                                            "Copiază textul 📋"
                                        };
                                    }
                                    new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                                            .setTitle(getString(R.string.choose))
                                            .setItems(shareOptions, (subD, index) -> {
                                                if (index == 0) {
                                                    new Thread(() -> {
                                                        List<com.cityscape.app.model.ActivityGroup> groups = db.groupDao().getGroupsForUser(sessionManager.getUserId());
                                                        if (groups == null || groups.isEmpty()) {
                                                            if (getActivity() == null) return;
                                                            getActivity().runOnUiThread(() -> {
                                                                Toast.makeText(getContext(), getString(R.string.no_active_groups), Toast.LENGTH_SHORT).show();
                                                            });
                                                        } else {
                                                            if (getActivity() == null) return;
                                                            getActivity().runOnUiThread(() -> {
                                                                String[] groupNames = new String[groups.size()];
                                                                for (int i = 0; i < groups.size(); i++) {
                                                                    groupNames[i] = groups.get(i).groupName;
                                                                }
                                                                new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                                                                        .setTitle(getString(R.string.choose))
                                                                        .setItems(groupNames, (gDialog, gIndex) -> {
                                                                            com.cityscape.app.model.ActivityGroup chosenGroup = groups.get(gIndex);
                                                                            new Thread(() -> {
                                                                                com.cityscape.app.model.GroupMessage sharedMsg = new com.cityscape.app.model.GroupMessage(
                                                                                    chosenGroup.id, 
                                                                                    sessionManager.getUserId(), 
                                                                                    sessionManager.getCurrentUser() != null ? sessionManager.getCurrentUser().name : "Eu",
                                                                                    "📖 AI TRAVEL DIARY ENTRY:\n\n" + story
                                                                                );
                                                                                db.groupMessageDao().insert(sharedMsg);
                                                                                com.cityscape.app.util.BadgeManager.addExperience(appContext, sessionManager.getUserId(), 200);
                                                                                
                                                                                if (getActivity() != null) {
                                                                                    getActivity().runOnUiThread(() -> {
                                                                                        Toast.makeText(getContext(), getString(R.string.story_sent_to_group), Toast.LENGTH_LONG).show();
                                                                                    });
                                                                                }
                                                                            }).start();
                                                                        })
                                                                        .show();
                                                            });
                                                        }
                                                    }).start();
                                                } else {
                                                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                                    android.content.ClipData clip = android.content.ClipData.newPlainText("CityScape Mystical Story", story);
                                                    clipboard.setPrimaryClip(clip);
                                                    Toast.makeText(getContext(), getString(R.string.code_copied), Toast.LENGTH_SHORT).show();
                                                }
                                            })
                                            .show();
                                })
                                .show();
                    } else {
                        Toast.makeText(getContext(), getString(R.string.ai_chronicle_blocked), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<com.google.gson.JsonObject> call, Throwable t) {
                    if (isAdded()) {
                        progressDialog.dismiss();
                        Toast.makeText(getContext(), getString(R.string.ai_error), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }).start();
    }
}
