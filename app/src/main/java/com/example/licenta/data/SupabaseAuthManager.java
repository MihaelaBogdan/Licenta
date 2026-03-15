package com.example.licenta.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.licenta.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Manages Supabase authentication via REST API (GoTrue).
 * Replaces Firebase Auth in a pure Java project.
 */
public class SupabaseAuthManager {

    private static final String TAG = "SupabaseAuthManager";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String PREF_NAME = "SupabaseAuth";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_ID = "user_id";

    private static volatile SupabaseAuthManager INSTANCE;

    private final String supabaseUrl;
    private final String supabaseAnonKey;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final SharedPreferences prefs;

    public interface AuthCallback {
        void onSuccess(String email, String displayName);

        void onFailure(String errorMessage);
    }

    private SupabaseAuthManager(Context context) {
        this.supabaseUrl = BuildConfig.SUPABASE_URL;
        this.supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY;
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static SupabaseAuthManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SupabaseAuthManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SupabaseAuthManager(context);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Sign up with email and password.
     */
    public void signUpWithEmail(String email, String password, String displayName, AuthCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);

        // Add user metadata with display name
        JsonObject data = new JsonObject();
        data.addProperty("display_name", displayName);
        body.add("data", data);

        RequestBody requestBody = RequestBody.create(body.toString(), JSON);

        Request request = new Request.Builder()
                .url(supabaseUrl + "/auth/v1/signup")
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Sign up request failed", e);
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                        handleAuthResponse(json, displayName);
                        String userEmail = getStoredEmail();
                        callback.onSuccess(userEmail, displayName);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing sign up response", e);
                        callback.onFailure("Error parsing response");
                    }
                } else {
                    Log.e(TAG, "Sign up failed: " + responseBody);
                    String errorMsg = parseErrorMessage(responseBody);
                    callback.onFailure(errorMsg);
                }
            }
        });
    }

    /**
     * Sign in with email and password.
     */
    public void signInWithEmail(String email, String password, AuthCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);

        RequestBody requestBody = RequestBody.create(body.toString(), JSON);

        Request request = new Request.Builder()
                .url(supabaseUrl + "/auth/v1/token?grant_type=password")
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Sign in request failed", e);
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                        handleTokenResponse(json);
                        String userEmail = getStoredEmail();
                        String userName = getStoredName();
                        callback.onSuccess(userEmail, userName);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing sign in response", e);
                        callback.onFailure("Error parsing response");
                    }
                } else {
                    Log.e(TAG, "Sign in failed: " + responseBody);
                    String errorMsg = parseErrorMessage(responseBody);
                    callback.onFailure(errorMsg);
                }
            }
        });
    }

    /**
     * Sign in with Google ID token via Supabase.
     * Uses the id_token flow: POST /auth/v1/token?grant_type=id_token
     */
    public void signInWithGoogle(String idToken, AuthCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("provider", "google");
        body.addProperty("id_token", idToken);

        RequestBody requestBody = RequestBody.create(body.toString(), JSON);

        Request request = new Request.Builder()
                .url(supabaseUrl + "/auth/v1/token?grant_type=id_token")
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Google sign in request failed", e);
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                        handleTokenResponse(json);
                        String userEmail = getStoredEmail();
                        String userName = getStoredName();
                        callback.onSuccess(userEmail, userName != null ? userName : "User");
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing Google sign in response", e);
                        callback.onFailure("Error parsing response");
                    }
                } else {
                    Log.e(TAG, "Google sign in failed: " + responseBody);
                    String errorMsg = parseErrorMessage(responseBody);
                    callback.onFailure(errorMsg);
                }
            }
        });
    }

    /**
     * Sign out - revoke session and clear local tokens.
     */
    public void signOut() {
        String accessToken = getAccessToken();
        if (accessToken != null) {
            Request request = new Request.Builder()
                    .url(supabaseUrl + "/auth/v1/logout")
                    .addHeader("apikey", supabaseAnonKey)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .post(RequestBody.create("", JSON))
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Logout request failed", e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    Log.d(TAG, "Logout response: " + response.code());
                }
            });
        }
        clearSession();
    }

    /**
     * Check if there is a stored session (access token).
     */
    public boolean isAuthenticated() {
        return getAccessToken() != null;
    }

    /**
     * Retrieve stored user email.
     */
    public String getStoredEmail() {
        return prefs.getString(KEY_USER_EMAIL, null);
    }

    /**
     * Retrieve stored user name.
     */
    public String getStoredName() {
        return prefs.getString(KEY_USER_NAME, null);
    }

    /**
     * Retrieve stored Supabase user ID.
     */
    public String getStoredUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    /**
     * Retrieve stored access token.
     */
    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    // --- Internal helpers ---

    private void handleAuthResponse(JsonObject json, String displayName) {
        // Signup response has a different structure - may include session directly
        if (json.has("access_token")) {
            handleTokenResponse(json);
        }

        // Extract user info
        if (json.has("user")) {
            JsonObject user = json.getAsJsonObject("user");
            String email = user.has("email") ? user.get("email").getAsString() : null;
            String id = user.has("id") ? user.get("id").getAsString() : null;

            SharedPreferences.Editor editor = prefs.edit();
            if (email != null)
                editor.putString(KEY_USER_EMAIL, email);
            if (id != null)
                editor.putString(KEY_USER_ID, id);
            if (displayName != null)
                editor.putString(KEY_USER_NAME, displayName);
            editor.apply();
        }
    }

    private void handleTokenResponse(JsonObject json) {
        SharedPreferences.Editor editor = prefs.edit();

        if (json.has("access_token")) {
            editor.putString(KEY_ACCESS_TOKEN, json.get("access_token").getAsString());
        }
        if (json.has("refresh_token")) {
            editor.putString(KEY_REFRESH_TOKEN, json.get("refresh_token").getAsString());
        }

        // Extract user info from token response
        if (json.has("user")) {
            JsonObject user = json.getAsJsonObject("user");
            if (user.has("email")) {
                editor.putString(KEY_USER_EMAIL, user.get("email").getAsString());
            }
            if (user.has("id")) {
                editor.putString(KEY_USER_ID, user.get("id").getAsString());
            }

            // Try to get display name from user_metadata
            if (user.has("user_metadata")) {
                JsonObject meta = user.getAsJsonObject("user_metadata");
                String name = null;
                if (meta.has("display_name")) {
                    name = meta.get("display_name").getAsString();
                } else if (meta.has("full_name")) {
                    name = meta.get("full_name").getAsString();
                } else if (meta.has("name")) {
                    name = meta.get("name").getAsString();
                }
                if (name != null) {
                    editor.putString(KEY_USER_NAME, name);
                }
            }
        }

        editor.apply();
    }

    private void clearSession() {
        prefs.edit().clear().apply();
    }

    private String parseErrorMessage(String responseBody) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("error_description")) {
                return json.get("error_description").getAsString();
            }
            if (json.has("msg")) {
                return json.get("msg").getAsString();
            }
            if (json.has("message")) {
                return json.get("message").getAsString();
            }
            if (json.has("error")) {
                return json.get("error").getAsString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing error message", e);
        }
        return "Authentication failed";
    }
}
