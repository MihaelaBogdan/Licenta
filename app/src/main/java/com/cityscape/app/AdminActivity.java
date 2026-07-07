package com.cityscape.app;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.cityscape.app.BuildConfig;
import com.google.android.material.tabs.TabLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AdminActivity extends AppCompatActivity {

    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String SUPABASE_KEY = BuildConfig.SUPABASE_ANON_KEY;

    private OkHttpClient http = new OkHttpClient();

    private TabLayout tabLayout;
    private LinearLayout panelStats, panelUsers, panelReports;
    private android.widget.ProgressBar progressBar;

    private TextView statUsers, statPosts, statBadges, statLikes;
    private RecyclerView rvUsers, rvReports;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        tabLayout    = findViewById(R.id.admin_tabs);
        panelStats   = findViewById(R.id.panel_stats);
        panelUsers   = findViewById(R.id.panel_users);
        panelReports = findViewById(R.id.panel_reports);
        progressBar  = findViewById(R.id.admin_progress);

        statUsers  = findViewById(R.id.stat_users);
        statPosts  = findViewById(R.id.stat_posts);
        statBadges = findViewById(R.id.stat_badges);
        statLikes  = findViewById(R.id.stat_likes);

        rvUsers   = findViewById(R.id.rv_users);
        rvReports = findViewById(R.id.rv_reports);

        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        rvReports.setLayoutManager(new LinearLayoutManager(this));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { showPanel(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        View btnBack = findViewById(R.id.btn_back_admin);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        loadAll();
    }

    private void showPanel(int pos) {
        panelStats.setVisibility(pos == 0 ? View.VISIBLE : View.GONE);
        panelUsers.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
        panelReports.setVisibility(pos == 2 ? View.VISIBLE : View.GONE);
    }

    private void loadAll() {
        progressBar.setVisibility(View.VISIBLE);
        fetchUsers();
        fetchPosts();
        fetchBadges();
        fetchReports();
    }

    private okhttp3.Headers supaHeaders() {
        return new okhttp3.Headers.Builder()
                .add("apikey", SUPABASE_KEY)
                .add("Authorization", "Bearer " + SUPABASE_KEY)
                .build();
    }

    private void fetchUsers() {
        Request req = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/user_profiles?select=id,name,email,level,total_xp,created_at&order=created_at.desc")
                .headers(supaHeaders()).build();

        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { hideProgress(); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) { hideProgress(); return; }
                String body = response.body().string();
                runOnUiThread(() -> {
                    try {
                        JSONArray arr = new JSONArray(body);
                        statUsers.setText(String.valueOf(arr.length()));
                        List<String[]> rows = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject u = arr.getJSONObject(i);
                            rows.add(new String[]{
                                u.optString("name","—"),
                                u.optString("email","—"),
                                "Lv." + u.optInt("level",1),
                                u.optInt("total_xp",0) + " XP",
                                u.optString("created_at","").substring(0, Math.min(10, u.optString("created_at","").length()))
                            });
                        }
                        rvUsers.setAdapter(new SimpleTableAdapter(rows,
                            new String[]{"Nume","Email","Level","XP","Creat"}));
                    } catch (Exception ignored) {}
                    hideProgress();
                });
            }
        });
    }

    private void fetchPosts() {
        Request req = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/feed_posts?select=id,user_name,place_name,caption,created_at&order=created_at.desc&limit=200")
                .headers(supaHeaders()).build();

        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) return;
                String body = response.body().string();
                runOnUiThread(() -> {
                    try {
                        JSONArray arr = new JSONArray(body);
                        statPosts.setText(String.valueOf(arr.length()));
                    } catch (Exception ignored) {}
                });
            }
        });
    }

    private void fetchBadges() {
        Request req = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/user_badges?select=id&is_unlocked=eq.true&limit=1000")
                .headers(supaHeaders()).build();

        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) return;
                String body = response.body().string();
                runOnUiThread(() -> {
                    try {
                        JSONArray arr = new JSONArray(body);
                        statBadges.setText(String.valueOf(arr.length()));
                    } catch (Exception ignored) {}
                });
            }
        });
    }

    private void fetchReports() {
        Request req = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/content_reports?select=*&order=created_at.desc&limit=100")
                .headers(supaHeaders()).build();

        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        statLikes.setText("0");
                        TextView empty = findViewById(R.id.reports_empty);
                        if (empty != null) empty.setVisibility(View.VISIBLE);
                    });
                    return;
                }
                String body = response.body().string();
                runOnUiThread(() -> {
                    try {
                        JSONArray arr = new JSONArray(body);
                        statLikes.setText(String.valueOf(arr.length()));
                        TextView empty = findViewById(R.id.reports_empty);
                        if (arr.length() == 0) {
                            if (empty != null) empty.setVisibility(View.VISIBLE);
                            return;
                        }
                        if (empty != null) empty.setVisibility(View.GONE);
                        List<String[]> rows = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject r = arr.getJSONObject(i);
                            rows.add(new String[]{
                                r.optString("reporter_id","—").substring(0,8) + "…",
                                r.optString("reason", r.optString("report_type","necunoscut")),
                                r.optString("status","pending"),
                                r.optString("created_at","").substring(0, Math.min(10, r.optString("created_at","").length()))
                            });
                        }
                        rvReports.setAdapter(new SimpleTableAdapter(rows,
                            new String[]{"Reporter","Motiv","Status","Data"}));
                    } catch (Exception ignored) {}
                });
            }
        });
    }

    private void hideProgress() {
        runOnUiThread(() -> progressBar.setVisibility(View.GONE));
    }

    static class SimpleTableAdapter extends RecyclerView.Adapter<SimpleTableAdapter.VH> {
        private final List<String[]> rows;
        private final String[] headers;

        SimpleTableAdapter(List<String[]> rows, String[] headers) {
            this.rows = rows;
            this.headers = headers;
        }

        static class VH extends RecyclerView.ViewHolder {
            LinearLayout container;
            VH(View v) {
                super(v);
                container = (LinearLayout) v;
            }
        }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            row.setPadding(0, 0, 0, 0);
            return new VH(row);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            LinearLayout row = holder.container;
            row.removeAllViews();
            android.content.Context ctx = row.getContext();
            String[] data = rows.get(position);

            boolean isEven = position % 2 == 0;
            row.setBackgroundColor(isEven ? 0xFF0D1810 : 0xFF111B14);

            for (int i = 0; i < data.length; i++) {
                TextView tv = new TextView(ctx);
                tv.setText(data[i]);
                tv.setTextColor(i == 0 ? 0xFFF0FDF4 : 0xFF9CA3AF);
                tv.setTextSize(12f);
                tv.setPadding(16, 14, 16, 14);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                tv.setLayoutParams(lp);
                tv.setMaxLines(2);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(tv);
            }

            row.setOnLongClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(ctx)
                    .setTitle(data[0])
                    .setMessage(String.join("\n", data))
                    .setPositiveButton("OK", null)
                    .show();
                return true;
            });
        }

        @Override public int getItemCount() { return rows.size(); }
    }
}
