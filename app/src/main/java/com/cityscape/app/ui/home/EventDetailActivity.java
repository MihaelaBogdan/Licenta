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
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;

public class EventDetailActivity extends AppCompatActivity {

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
        MaterialButton btnTickets = findViewById(R.id.btn_buy_tickets);
        MaterialButton btnShare = findViewById(R.id.btn_share_event);

        titleText.setText(event.title);
        timeText.setText(event.time);
        locationText.setText(event.location);

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

        btnShare.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, event.title);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Uite un eveniment interesant: " + event.title + "\nLocație: " + event.location + "\nDată: " + event.time + (event.url != null ? ("\n\nDetalii: " + event.url) : ""));
            startActivity(Intent.createChooser(shareIntent, "Partajează Eveniment"));
        });
    }
}
