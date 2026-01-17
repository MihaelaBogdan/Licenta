package com.cityscape.app.activities;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cityscape.app.CityScapeApp;
import com.cityscape.app.R;
import com.cityscape.app.adapters.ChatAdapter;
import com.cityscape.app.ai.ChatbotService;
import com.cityscape.app.database.entities.Place;
import com.cityscape.app.databinding.ActivityChatbotBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Chatbot activity for conversational recommendations
 */
public class ChatbotActivity extends AppCompatActivity {

    private ActivityChatbotBinding binding;
    private ChatbotService chatbotService;
    private ChatAdapter chatAdapter;
    private List<ChatAdapter.ChatMessage> messages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatbotBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        chatbotService = new ChatbotService(this);
        String userId = CityScapeApp.getInstance().getCurrentUserId();
        String cityId = CityScapeApp.getInstance().getSelectedCityId();
        chatbotService.setContext(userId, cityId);

        setupRecyclerView();
        setupListeners();

        // Send initial greeting
        sendWelcomeMessage();
    }

    private void setupRecyclerView() {
        messages = new ArrayList<>();
        chatAdapter = new ChatAdapter(messages, place -> {
            // Open place details
            openPlaceDetails(place.getId());
        });

        binding.chatRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.chatRecycler.setAdapter(chatAdapter);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnSend.setOnClickListener(v -> sendMessage());

        binding.messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        // Quick suggestion chips
        binding.chipRestaurant.setOnClickListener(v -> sendQuickMessage("Find a good restaurant"));
        binding.chipCafe.setOnClickListener(v -> sendQuickMessage("Suggest a cozy cafe"));
        binding.chipSurprise.setOnClickListener(v -> sendQuickMessage("Surprise me with something random!"));
    }

    private void sendWelcomeMessage() {
        ChatbotService.ChatResponse response = chatbotService.processMessage("hello");
        addBotMessage(response.getMessage(), response.getPlaces());
    }

    private void sendMessage() {
        String messageText = binding.messageInput.getText().toString().trim();
        if (messageText.isEmpty())
            return;

        // Add user message
        addUserMessage(messageText);

        // Clear input
        binding.messageInput.setText("");

        // Show typing indicator
        binding.typingIndicator.setVisibility(View.VISIBLE);

        // Process message
        binding.chatRecycler.postDelayed(() -> {
            ChatbotService.ChatResponse response = chatbotService.processMessage(messageText);

            binding.typingIndicator.setVisibility(View.GONE);
            addBotMessage(response.getMessage(), response.getPlaces());
        }, 500);
    }

    private void sendQuickMessage(String message) {
        binding.messageInput.setText(message);
        sendMessage();
    }

    private void addUserMessage(String text) {
        ChatAdapter.ChatMessage message = new ChatAdapter.ChatMessage(
                text, true, null);
        messages.add(message);
        chatAdapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();
    }

    private void addBotMessage(String text, List<Place> places) {
        ChatAdapter.ChatMessage message = new ChatAdapter.ChatMessage(
                text, false, places);
        messages.add(message);
        chatAdapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();
    }

    private void scrollToBottom() {
        binding.chatRecycler.scrollToPosition(messages.size() - 1);
    }

    private void openPlaceDetails(String placeId) {
        android.content.Intent intent = new android.content.Intent(this, PlaceDetailActivity.class);
        intent.putExtra("placeId", placeId);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
