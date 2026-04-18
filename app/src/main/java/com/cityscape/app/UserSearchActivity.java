package com.cityscape.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.cityscape.app.adapter.UserAdapter;
import com.cityscape.app.api.ApiClient;
import com.cityscape.app.api.ApiService;
import com.cityscape.app.data.SessionManager;
import com.cityscape.app.model.User;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserSearchActivity extends AppCompatActivity implements UserAdapter.OnUserActionListener {

    private EditText etSearch;
    private RecyclerView rvUsers;
    private UserAdapter adapter;
    private View emptyState, progressBar;
    private ApiService apiService;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_search);

        etSearch = findViewById(R.id.et_search_query);
        rvUsers = findViewById(R.id.rv_users);
        emptyState = findViewById(R.id.empty_state);
        progressBar = findViewById(R.id.search_progress);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        sessionManager = new SessionManager(this);
        apiService = ApiClient.getClient().create(ApiService.class);

        adapter = new UserAdapter(this);
        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        rvUsers.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() >= 2) searchUsers(s.toString());
                else {
                    adapter.setUsers(new java.util.ArrayList<>());
                    emptyState.setVisibility(View.GONE);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void searchUsers(String query) {
        progressBar.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);

        apiService.searchUsers(query, sessionManager.getUserId()).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> res) {
                progressBar.setVisibility(View.GONE);
                if (res.isSuccessful() && res.body() != null) {
                    adapter.setUsers(res.body());
                    emptyState.setVisibility(res.body().isEmpty() ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(UserSearchActivity.this, "Eroare la căutare", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onFollowClicked(User user, int position) {
        Map<String, String> data = new HashMap<>();
        data.put("follower_id", sessionManager.getUserId());
        data.put("following_id", user.id);

        apiService.followUser(data).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> res) {
                if (res.isSuccessful()) {
                    user.isFollowing = !user.isFollowing;
                    adapter.notifyItemChanged(position);
                    String msg = user.isFollowing ? "Acum îl urmărești pe " + user.name : "Nu îl mai urmărești pe " + user.name;
                    Toast.makeText(UserSearchActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(UserSearchActivity.this, "Eroare rețea", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onUserClicked(User user) {
        // Optionale: Deschide profilul utilizatorului
    }
}
