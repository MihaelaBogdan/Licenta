package com.cityscape.app.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.cityscape.app.BuildConfig;
import com.cityscape.app.model.ActivityGroup;
import com.cityscape.app.model.GroupMember;
import com.cityscape.app.model.Invitation;
import com.cityscape.app.model.MemberSchedule;
import com.cityscape.app.model.PlannedActivity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseDataManager {
    private static final String TAG = "SupabaseDataManager";
    private static final String API_URL = BuildConfig.SUPABASE_URL + "/rest/v1/";
    private static final String API_KEY = BuildConfig.SUPABASE_ANON_KEY;

    private static SupabaseDataManager instance;
    private OkHttpClient client;
    private Gson gson;
    private ExecutorService executorService;
    private Handler mainHandler;
    private SupabaseAuthManager authManager;

    private SupabaseDataManager(Context context) {
        client = new OkHttpClient();
        gson = new Gson();
        executorService = Executors.newFixedThreadPool(4);
        mainHandler = new Handler(Looper.getMainLooper());
        authManager = SupabaseAuthManager.getInstance(context);
    }

    public static synchronized SupabaseDataManager getInstance(Context context) {
        if (instance == null) {
            instance = new SupabaseDataManager(context);
        }
        return instance;
    }

    private Request.Builder getAuthenticatedRequestBuilder(String endpoint) {
        String accessToken = authManager.getAccessToken();
        Request.Builder builder = new Request.Builder()
                .url(API_URL + endpoint)
                .addHeader("apikey", API_KEY)
                .addHeader("Content-Type", "application/json");

        if (accessToken != null) {
            builder.addHeader("Authorization", "Bearer " + accessToken);
        } else {
            builder.addHeader("Authorization", "Bearer " + API_KEY);
        }
        builder.addHeader("apikey", API_KEY);
        return builder;
    }

    public interface DataCallback<T> {
        void onSuccess(T result);

        void onError(String error);
    }

    // ==========================================================
    // ACTIVITY GROUPS
    // ==========================================================

    public void insertGroup(ActivityGroup group, DataCallback<Void> callback) {
        executorService.execute(() -> {
            try {
                String json = gson.toJson(group);
                RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
                Request request = getAuthenticatedRequestBuilder("activity_groups")
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        postSuccess(callback, null);
                    } else {
                        postError(callback,
                                "Eroare la creare grup: " + response.code() + " " + response.body().string());
                    }
                }
            } catch (IOException e) {
                postError(callback, "Eroare rețea: " + e.getMessage());
            }
        });
    }

    public void getGroupsForUser(String userId, DataCallback<List<ActivityGroup>> callback) {
        executorService.execute(() -> {
            try {
                // To get groups for a user, we query via group_members table join in REST API
                // Supabase syntax:
                // /rest/v1/activity_groups?select=*,group_members!inner(*)&group_members.user_id=eq.{userId}
                String url = "activity_groups?select=*,group_members!inner(*)&group_members.user_id=eq." + userId;
                Request request = getAuthenticatedRequestBuilder(url).get().build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String jsonResponse = response.body().string();
                        List<ActivityGroup> groups = gson.fromJson(jsonResponse, new TypeToken<List<ActivityGroup>>() {
                        }.getType());
                        postSuccess(callback, groups);
                    } else {
                        postError(callback, "Eroare la preluare grupuri: " + response.code());
                    }
                }
            } catch (Exception e) {
                postError(callback, "Eroare rețea: " + e.getMessage());
            }
        });
    }

    public void getGroupByCode(String code, DataCallback<List<ActivityGroup>> callback) {
        executorService.execute(() -> {
            try {
                String url = "activity_groups?groupCode=eq." + code + "&limit=1";
                Request request = getAuthenticatedRequestBuilder(url).get().build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String jsonResponse = response.body().string();
                        List<ActivityGroup> groups = gson.fromJson(jsonResponse, new TypeToken<List<ActivityGroup>>() {
                        }.getType());
                        postSuccess(callback, groups);
                    } else {
                        postError(callback, "Eroare la preluare grup: " + response.code());
                    }
                }
            } catch (Exception e) {
                postError(callback, "Eroare rețea: " + e.getMessage());
            }
        });
    }

    // ==========================================================
    // PLANNED ACTIVITIES
    // ==========================================================

    public void insertActivity(PlannedActivity activity, DataCallback<Void> callback) {
        executorService.execute(() -> {
            try {
                String json = gson.toJson(activity);
                RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
                Request request = getAuthenticatedRequestBuilder("planned_activities")
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        postSuccess(callback, null);
                    } else {
                        postError(callback, "Eroare la salvare activitate: " + response.body().string());
                    }
                }
            } catch (IOException e) {
                postError(callback, "Eroare rețea: " + e.getMessage());
            }
        });
    }

    public void getActivitiesForDate(String userId, long date, DataCallback<List<PlannedActivity>> callback) {
        executorService.execute(() -> {
            try {
                String url = "planned_activities?user_id=eq." + userId + "&scheduled_date=eq." + date
                        + "&order=scheduledTime.asc";
                Request request = getAuthenticatedRequestBuilder(url).get().build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String jsonResponse = response.body().string();
                        List<PlannedActivity> activities = gson.fromJson(jsonResponse,
                                new TypeToken<List<PlannedActivity>>() {
                                }.getType());
                        postSuccess(callback, activities);
                    } else {
                        postError(callback, "Eroare la preluare activități");
                    }
                }
            } catch (Exception e) {
                postError(callback, "Eroare rețea: " + e.getMessage());
            }
        });
    }

    public void updateActivity(PlannedActivity activity, DataCallback<Void> callback) {
        executorService.execute(() -> {
            try {
                String json = gson.toJson(activity);
                RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
                Request request = getAuthenticatedRequestBuilder("planned_activities?id=eq." + activity.id)
                        .patch(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        postSuccess(callback, null);
                    } else {
                        postError(callback, "Eroare la update activitate");
                    }
                }
            } catch (Exception e) {
                postError(callback, "Eroare rețea: " + e.getMessage());
            }
        });
    }

    // ==========================================================
    // USER PROFILES
    // ==========================================================

    public void getUserProfile(String userId, DataCallback<com.cityscape.app.model.User> callback) {
        executorService.execute(() -> {
            try {
                String url = "user_profiles?id=eq." + userId + "&limit=1";
                Request request = getAuthenticatedRequestBuilder(url).get().build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String jsonResponse = response.body().string();
                        List<com.cityscape.app.model.User> users = gson.fromJson(jsonResponse,
                                new TypeToken<List<com.cityscape.app.model.User>>() {
                                }.getType());
                        if (users != null && !users.isEmpty()) {
                            postSuccess(callback, users.get(0));
                        } else {
                            postError(callback, "Profil negăsit în cloud");
                        }
                    } else {
                        postError(callback, "Eroare la preluare profil: " + response.code());
                    }
                }
            } catch (Exception e) {
                postError(callback, "Eroare rețea: " + e.getMessage());
            }
        });
    }

    public void insertBadge(com.cityscape.app.model.UserBadge badge, DataCallback<Void> callback) {
        executorService.execute(() -> {
            try {
                String json = gson.toJson(badge);
                RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
                Request request = getAuthenticatedRequestBuilder("user_badges")
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        postSuccess(callback, null);
                    } else {
                        postError(callback, "Eroare la deblocare badge in cloud: " + response.code());
                    }
                }
            } catch (IOException e) {
                postError(callback, "Eroare rețea badge: " + e.getMessage());
            }
        });
    }

    // ==========================================================
    // UTILS
    // ==========================================================

    private <T> void postSuccess(DataCallback<T> callback, T result) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(result));
        }
    }

    private <T> void postError(DataCallback<T> callback, String error) {
        Log.e(TAG, "Supabase API Error: " + error);
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }
}
