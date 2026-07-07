package com.cityscape.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.cityscape.app.BuildConfig;
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

public class SupabaseAuthManager {

    private static final String TAG = "SupabaseAuthManager";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String PREF_NAME = "SupabaseAuth";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_CONFIRMED = "user_confirmed";

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

    public interface VerificationCallback {
        void onSent();

        void onError(String errorMessage);
    }

    public interface EmailConfirmedCallback {
        void onConfirmed();
        void onNotConfirmed();
        void onError(String errorMessage);
    }

    public interface PasswordResetCallback {
        void onSent();
        void onError(String errorMessage);
    }

    public interface EmailNotificationCallback {
        void onEmailSent(String email);

        void onEmailFailed(String error);
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

    
    public void signUpWithEmail(String email, String password, String displayName, AuthCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);

        JsonObject data = new JsonObject();
        data.addProperty("display_name", displayName);
        body.add("data", data);

        RequestBody requestBody = RequestBody.create(body.toString(), JSON);

        Request request = new Request.Builder()
                .url(supabaseUrl + "/auth/v1/signup")
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer " + supabaseAnonKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Sign up request failed", e);
                callback.onFailure("Eroare de conexiune: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                        handleAuthResponse(json, displayName);
                        String userEmail = getStoredEmail();

                        sendEmailVerification(userEmail, new VerificationCallback() {
                            @Override
                            public void onSent() {
                                Log.d(TAG, "Verification email sent successfully to: " + userEmail);
                            }

                            @Override
                            public void onError(String error) {
                                Log.w(TAG, "Failed to send verification email: " + error);
                            }
                        });

                        callback.onSuccess(userEmail, displayName);
                        return;
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing sign up response", e);
                        callback.onFailure("Eroare la parsare răspuns: " + e.getMessage());
                        return;
                    }
                }
                Log.e(TAG, "Sign up failed: " + responseBody);
                String errorMsg = parseErrorMessage(responseBody);
                if (errorMsg.contains("already registered")) {
                    callback.onFailure("Această adresă de email este deja înregistrată");
                } else {
                    callback.onFailure(errorMsg);
                }
            }
        });
    }

    
    public void resendVerificationEmail(String email, VerificationCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);

        RequestBody requestBody = RequestBody.create(body.toString(), JSON);

        Request request = new Request.Builder()
                .url(supabaseUrl + "/auth/v1/resend")
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer " + supabaseAnonKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Resend verification email failed", e);
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    callback.onSent();
                } else {
                    String body = response.body() != null ? response.body().string() : "Unknown error";
                    callback.onError("HTTP " + response.code() + ": " + body);
                }
            }
        });
    }

    
    private void sendEmailVerification(String email, VerificationCallback callback) {
        resendVerificationEmail(email, callback);
    }

    
    public void sendWelcomeEmail(String email, String userName, EmailNotificationCallback callback) {
        new Thread(() -> {
            try {
                String subject = "Bine ai venit în CityScape! 🚀";
                String message = String.format(
                    "Salut %s,\n\n" +
                    "Contul tău CityScape a fost creat cu succes!\n\n" +
                    "🌟 Următoarele aventuri te așteaptă:\n" +
                    "✨ Explorează mii de locuri din orașul tău\n" +
                    "✨ Descoperă recomandări personalizate bazate pe AI\n" +
                    "✨ Planifică itinerar perfect pentru fiecare zi\n" +
                    "✨ Conectează-te cu alți exploratori urbani\n\n" +
                    "Pentru a-ți activa complet contul, confirmă adresa de email.\n\n" +
                    "Distrează-te explorând!\n" +
                    "- Echipa CityScape",
                    userName
                );

                Log.d(TAG, "Welcome email notification triggered for: " + email);
                if (callback != null) {
                    callback.onEmailSent(email);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in sendWelcomeEmail: " + e.getMessage());
                if (callback != null) {
                    callback.onEmailFailed(e.getMessage());
                }
            }
        }).start();
    }

    
    public void signInWithEmail(String email, String password, AuthCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);

        RequestBody requestBody = RequestBody.create(body.toString(), JSON);

        Request request = new Request.Builder()
                .url(supabaseUrl + "/auth/v1/token?grant_type=password")
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer " + supabaseAnonKey)
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
                        return;
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing sign in response", e);
                        callback.onFailure("Error parsing response");
                        return;
                    }
                }
                Log.e(TAG, "Sign in failed: " + responseBody);
                callback.onFailure(parseErrorMessage(responseBody));
            }
        });
    }

    
    public void signInWithGoogle(String idToken, AuthCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("provider", "google");
        body.addProperty("id_token", idToken);

        RequestBody requestBody = RequestBody.create(body.toString(), JSON);

        Request request = new Request.Builder()
                .url(supabaseUrl + "/auth/v1/token?grant_type=id_token")
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer " + supabaseAnonKey)
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
                        return;
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing Google sign in response", e);
                        callback.onFailure("Error parsing response");
                        return;
                    }
                }
                Log.e(TAG, "Google sign in failed: " + responseBody);
                callback.onFailure(parseErrorMessage(responseBody));
            }
        });
    }

    
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

    
    public boolean isAuthenticated() {
        return getAccessToken() != null;
    }

    
    public String getStoredEmail() {
        return prefs.getString(KEY_USER_EMAIL, null);
    }

    
    public String getStoredName() {
        return prefs.getString(KEY_USER_NAME, null);
    }

    
    public String getStoredUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    
    public boolean isUserConfirmed() {
        return prefs.getBoolean(KEY_USER_CONFIRMED, false);
    }

    
    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    

    private void handleAuthResponse(JsonObject json, String displayName) {
        
        if (json.has("access_token")) {
            handleTokenResponse(json);
        }

        
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
            
            boolean confirmed = user.has("email_confirmed_at") && !user.get("email_confirmed_at").isJsonNull();
            editor.putBoolean(KEY_USER_CONFIRMED, confirmed);
            
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

        
        if (json.has("user")) {
            JsonObject user = json.getAsJsonObject("user");
            if (user.has("email")) {
                editor.putString(KEY_USER_EMAIL, user.get("email").getAsString());
            }
            if (user.has("id")) {
                editor.putString(KEY_USER_ID, user.get("id").getAsString());
            }

            
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
            
            boolean confirmed = user.has("email_confirmed_at") && !user.get("email_confirmed_at").isJsonNull();
            editor.putBoolean(KEY_USER_CONFIRMED, confirmed);
        }

        editor.apply();
    }

    
    public void checkEmailConfirmed(EmailConfirmedCallback callback) {
        String accessToken = getAccessToken();
        if (accessToken == null) {
            callback.onError("Nicio sesiune activă.");
            return;
        }

        Request request = new Request.Builder()
                .url(supabaseUrl + "/auth/v1/user")
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    callback.onError("HTTP " + response.code());
                    return;
                }
                try {
                    JsonObject user = JsonParser.parseString(body).getAsJsonObject();
                    boolean confirmed = user.has("email_confirmed_at")
                            && !user.get("email_confirmed_at").isJsonNull()
                            && !user.get("email_confirmed_at").getAsString().isEmpty();

                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(KEY_USER_CONFIRMED, confirmed);
                    editor.apply();

                    if (confirmed) callback.onConfirmed();
                    else callback.onNotConfirmed();
                } catch (Exception e) {
                    callback.onError("Parse error: " + e.getMessage());
                }
            }
        });
    }

    
    public void requestPasswordReset(String email, PasswordResetCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);

        RequestBody requestBody = RequestBody.create(body.toString(), JSON);
        Request request = new Request.Builder()
                .url(supabaseUrl + "/auth/v1/recover")
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer " + supabaseAnonKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    callback.onSent();
                } else {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    callback.onError("HTTP " + response.code() + ": " + parseErrorMessage(responseBody));
                }
            }
        });
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
