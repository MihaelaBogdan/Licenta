package com.cityscape.app.ui.feed;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.bumptech.glide.Glide;
import com.cityscape.app.R;
import com.cityscape.app.adapter.FeedAdapter;
import com.cityscape.app.api.ApiClient;
import com.cityscape.app.api.ApiService;
import com.cityscape.app.data.SessionManager;
import com.cityscape.app.model.FeedComment;
import com.cityscape.app.model.FeedPost;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FeedFragment extends Fragment implements FeedAdapter.OnPostActionListener {

    private static final String TAG = "FeedFragment";
    private RecyclerView rvFeed;
    private View emptyState, progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private com.google.android.material.tabs.TabLayout tabLayout;
    private FeedAdapter adapter;
    private ApiService apiService;
    private SessionManager sessionManager;
    private String currentTab = "foryou";

    private Uri selectedImageUri;
    private ImageView dialogImagePreview;

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && isAdded()) {
                selectedImageUri = result.getData().getData();
                if (dialogImagePreview != null && selectedImageUri != null) {
                    dialogImagePreview.setVisibility(View.VISIBLE);
                    Glide.with(requireContext()).load(selectedImageUri).centerCrop().into(dialogImagePreview);
                }
            }
        });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvFeed = view.findViewById(R.id.rv_feed);
        emptyState = view.findViewById(R.id.feed_empty_state);
        progressBar = view.findViewById(R.id.feed_progress);
        tabLayout = view.findViewById(R.id.feed_tabs);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);

        adapter = new FeedAdapter(this);
        if (rvFeed != null) {
            rvFeed.setLayoutManager(new LinearLayoutManager(getContext()));
            rvFeed.setAdapter(adapter);
        }

        try {
            apiService = ApiClient.getClient().create(ApiService.class);
            sessionManager = new SessionManager(requireContext());
        } catch (Exception e) {
            Log.e(TAG, "Init error", e);
        }

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::loadFeed);
            swipeRefresh.setColorSchemeResources(R.color.primary);
        }

        View fab = view.findViewById(R.id.fab_new_post);
        if (fab != null) fab.setOnClickListener(v -> showNewPostDialog());

        View btnSearch = view.findViewById(R.id.btn_search_users);
        if (btnSearch != null) btnSearch.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), com.cityscape.app.UserSearchActivity.class));
        });

        if (tabLayout != null) {
            tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                    currentTab = (tab.getPosition() == 0) ? "foryou" : "friends";
                    loadFeed();
                }
                @Override public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
                @Override public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) { loadFeed(); }
            });
        }

        loadFeed();
    }

    private void loadFeed() {
        if (!isAdded() || apiService == null) {
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            return;
        }

        if (swipeRefresh != null && !swipeRefresh.isRefreshing()) progressBar.setVisibility(View.VISIBLE);
        if (emptyState != null) emptyState.setVisibility(View.GONE);

        String uId = (sessionManager != null) ? sessionManager.getUserId() : "";
        apiService.getFeed(currentTab, uId != null ? uId : "").enqueue(new Callback<List<FeedPost>>() {
            @Override
            public void onResponse(Call<List<FeedPost>> call, Response<List<FeedPost>> res) {
                if (!isAdded()) return;
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);

                if (res.isSuccessful() && res.body() != null) {
                    if (res.body().isEmpty()) {
                        if (rvFeed != null) rvFeed.setVisibility(View.GONE);
                        if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
                    } else {
                        adapter.setPosts(res.body());
                        if (rvFeed != null) rvFeed.setVisibility(View.VISIBLE);
                        if (emptyState != null) emptyState.setVisibility(View.GONE);
                    }
                } else {
                    if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
                }
            }
            @Override
            public void onFailure(Call<List<FeedPost>> call, Throwable t) {
                if (!isAdded()) return;
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showNewPostDialog() {
        if (!isAdded()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme);
        View v = getLayoutInflater().inflate(R.layout.dialog_new_post, null);
        builder.setView(v);

        AutoCompleteTextView etPlace = v.findViewById(R.id.et_place_name);
        EditText etCapt = v.findViewById(R.id.et_caption);
        RatingBar rb = v.findViewById(R.id.rating_input);
        dialogImagePreview = v.findViewById(R.id.img_preview);
        View btnImg = v.findViewById(R.id.btn_pick_image);

        if (etPlace != null) {
            final List<String> sug = new ArrayList<>();
            final ArrayAdapter<String> ad = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, sug);
            etPlace.setAdapter(ad);
            etPlace.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int be, int c) {
                    if (s.length() > 2 && apiService != null) {
                        apiService.getAutocomplete(s.toString(), sessionManager.getLastLat(), sessionManager.getLastLng()).enqueue(new Callback<List<Map<String, String>>>() {
                            @Override public void onResponse(Call<List<Map<String, String>>> call, Response<List<Map<String, String>>> res) {
                                if (isAdded() && res.isSuccessful() && res.body() != null) {
                                    sug.clear(); for (Map<String, String> m : res.body()) sug.add(m.get("name")); ad.notifyDataSetChanged();
                                }
                            }
                            @Override public void onFailure(Call<List<Map<String, String>>> call, Throwable t) {}
                        });
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        selectedImageUri = null;
        if (btnImg != null) btnImg.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_PICK); intent.setType("image/*"); imagePickerLauncher.launch(intent);
        });

        builder.setPositiveButton("Publică", (dialog, which) -> {
            if (etPlace == null) return;
            String name = etPlace.getText().toString().trim();
            if (name.isEmpty()) return;

            String img = (selectedImageUri != null) ? selectedImageUri.toString() : 
                "https://source.unsplash.com/featured/800x600/?" + Uri.encode(name);

            Map<String, Object> body = new HashMap<>();
            body.put("user_id", sessionManager.getUserId());
            body.put("user_name", sessionManager.getUserName());
            body.put("place_name", name);
            body.put("image_url", img);
            body.put("caption", (etCapt != null) ? etCapt.getText().toString().trim() : "");
            body.put("rating", (rb != null) ? rb.getRating() : 0);
            body.put("latitude", sessionManager.getLastLat());
            body.put("longitude", sessionManager.getLastLng());

            if (apiService != null) {
                apiService.createPost(body).enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(Call<JsonObject> call, Response<JsonObject> res) {
                        if (isAdded()) {
                            if (res.isSuccessful()) {
                                Toast.makeText(getContext(), "✅ Postat!", Toast.LENGTH_SHORT).show();
                                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> loadFeed(), 500);
                            } else {
                                Toast.makeText(getContext(), "Server error: " + res.code(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                    @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                        if (isAdded()) Toast.makeText(getContext(), "Network error", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        builder.setNegativeButton("Anulează", null);
        builder.show();
    }

    @Override
    public void onLikeClicked(FeedPost post, int pos) {
        if (!isAdded() || apiService == null) return;
        Map<String, String> d = new HashMap<>(); d.put("user_id", sessionManager.getUserId());
        apiService.toggleLike(post.id, d).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> res) {
                if (isAdded()) { post.isLiked = !post.isLiked; post.likesCount += post.isLiked ? 1 : -1; adapter.notifyItemChanged(pos); }
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable t) {}
        });
    }

    @Override public void onCommentClicked(FeedPost post) { showCommentsDialog(post); }
    @Override public void onShareClicked(FeedPost post) {
        Intent s = new Intent(Intent.ACTION_SEND); s.setType("text/plain");
        s.putExtra(Intent.EXTRA_TEXT, "🌍 " + post.userName + " recomandă " + post.placeName);
        startActivity(Intent.createChooser(s, "Share"));
    }

    private void showCommentsDialog(FeedPost post) {
        if (!isAdded()) return;
        AlertDialog.Builder b = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme);
        View v = getLayoutInflater().inflate(R.layout.dialog_comments, null); b.setView(v);
        RecyclerView rv = v.findViewById(R.id.rv_comments);
        EditText et = v.findViewById(R.id.et_comment);
        View btn = v.findViewById(R.id.btn_send_comment);
        if (rv != null) rv.setLayoutManager(new LinearLayoutManager(getContext()));
        AlertDialog d = b.create(); d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        if (apiService != null) {
            apiService.getComments(post.id).enqueue(new Callback<List<FeedComment>>() {
                @Override public void onResponse(Call<List<FeedComment>> call, Response<List<FeedComment>> res) {
                    if (isAdded() && rv != null && res.body() != null) rv.setAdapter(new CommentAdapter(res.body()));
                }
                @Override public void onFailure(Call<List<FeedComment>> call, Throwable t) {}
            });
        }
        if (btn != null) btn.setOnClickListener(view -> {
            if (et == null) return; String txt = et.getText().toString().trim(); if (txt.isEmpty()) return;
            Map<String, String> c = new HashMap<>(); c.put("user_id", sessionManager.getUserId());
            c.put("user_name", sessionManager.getUserName()); c.put("comment_text", txt);
            if (apiService != null) {
                apiService.addComment(post.id, c).enqueue(new Callback<JsonObject>() {
                    @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> res) {
                        if (isAdded()) { et.setText("");
                            apiService.getComments(post.id).enqueue(new Callback<List<FeedComment>>() {
                                @Override public void onResponse(Call<List<FeedComment>> call, Response<List<FeedComment>> res2) {
                                    if (isAdded() && rv != null && res2.body() != null) { rv.setAdapter(new CommentAdapter(res2.body())); post.commentsCount = res2.body().size(); }
                                }
                                @Override public void onFailure(Call<List<FeedComment>> call, Throwable t) {}
                            });
                        }
                    }
                    @Override public void onFailure(Call<JsonObject> call, Throwable t) {}
                });
            }
        });
        d.show();
    }

    private static class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.VH> {
        private final List<FeedComment> comments;
        CommentAdapter(List<FeedComment> c) { this.comments = c; }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_comment, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int p) {
            FeedComment c = comments.get(p); h.n.setText(c.userName); h.t.setText(c.commentText);
        }
        @Override public int getItemCount() { return (comments != null) ? comments.size() : 0; }
        static class VH extends RecyclerView.ViewHolder {
            TextView n, t; VH(@NonNull View v) { super(v); n = v.findViewById(R.id.comment_user_name); t = v.findViewById(R.id.comment_text); }
        }
    }
}
