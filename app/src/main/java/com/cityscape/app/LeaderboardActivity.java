package com.cityscape.app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.cityscape.app.adapter.LeaderboardAdapter;
import com.cityscape.app.data.AppDatabase;
import com.cityscape.app.model.User;
import java.util.List;

public class LeaderboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.rv_leaderboard);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadData(recyclerView);
    }

    private void loadData(RecyclerView recyclerView) {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            List<User> topUsers = db.userDao().getTopExplorers();
            
            runOnUiThread(() -> {
                LeaderboardAdapter adapter = new LeaderboardAdapter(topUsers);
                recyclerView.setAdapter(adapter);
            });
        }).start();
    }
}
