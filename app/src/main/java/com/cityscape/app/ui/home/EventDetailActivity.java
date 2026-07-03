package com.cityscape.app.ui.home;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.bumptech.glide.Glide;
import com.cityscape.app.R;
import com.cityscape.app.model.Event;
import com.cityscape.app.BaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;

public class EventDetailActivity extends BaseActivity {

    private Event event;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);

        // Get event data from intent
        String eventJson = getIntent().getStringExtra("event_json");
        if (eventJson != null) {
            event = new Gson().fromJson(eventJson, Event.class);
        }

        if (event == null) {
            finish();
            return;
        }

        setupUI();
    }

    private void setupUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        ImageView eventImage = findViewById(R.id.event_detail_image);
        TextView titleText = findViewById(R.id.event_detail_title);
        TextView timeText = findViewById(R.id.event_detail_time);
        TextView locationText = findViewById(R.id.event_detail_location);
        TextView descText = findViewById(R.id.event_detail_description);
        MaterialButton btnTickets = findViewById(R.id.btn_buy_tickets);
        MaterialButton btnShare = findViewById(R.id.btn_share_event);

        titleText.setText(event.title);

        String dateStr = event.date != null ? event.date : event.date_str;
        timeText.setText(dateStr != null && !dateStr.isEmpty() ? dateStr : (event.time != null ? event.time : ""));
        locationText.setText(event.location != null ? event.location : "");

        if (event.description != null && !event.description.isEmpty()) {
            descText.setText(event.description);
        }

        if (event.imageUrl != null && !event.imageUrl.isEmpty()) {
            Glide.with(this)
                .load(event.imageUrl)
                .placeholder(R.drawable.placeholder_place)
                .into(eventImage);
        }

        if (event.url == null || event.url.isEmpty()) {
            btnTickets.setVisibility(View.GONE);
        } else {
            btnTickets.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(event.url));
                startActivity(intent);
            });
        }

        // Explainable AI section
        android.view.View aiSection = findViewById(R.id.ai_section);
        TextView txtConfidence = findViewById(R.id.txt_event_confidence);
        TextView txtAiReason = findViewById(R.id.txt_ai_reason);
        android.widget.ProgressBar progressInterests = findViewById(R.id.progress_interests);
        android.widget.ProgressBar progressNovelty = findViewById(R.id.progress_novelty);
        android.widget.ProgressBar progressHistory = findViewById(R.id.progress_history);

        if (event.confidence > 0 && aiSection != null) {
            aiSection.setVisibility(android.view.View.VISIBLE);
            if (txtConfidence != null)
                txtConfidence.setText(event.confidence + "% Potrivire");
            if (txtAiReason != null && event.aiReason != null)
                txtAiReason.setText(event.aiReason);
            if (event.aiFactors != null) {
                if (progressInterests != null) progressInterests.setProgress(event.aiFactors.interestMatch);
                if (progressNovelty != null) progressNovelty.setProgress(event.aiFactors.novelty);
                if (progressHistory != null) progressHistory.setProgress(event.aiFactors.historyMatch);
            }
        }

        btnShare.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, event.title);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Uite un eveniment interesant: " + event.title + "\nLocație: " + event.location + "\nDată: " + event.time + (event.url != null ? ("\n\nDetalii: " + event.url) : ""));
            startActivity(Intent.createChooser(shareIntent, "Partajează Eveniment"));
        });
    }
}
