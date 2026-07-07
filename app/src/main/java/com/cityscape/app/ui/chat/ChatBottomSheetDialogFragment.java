package com.cityscape.app.ui.chat;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.cityscape.app.R;
import com.cityscape.app.adapter.ChatAdapter;
import com.cityscape.app.api.ApiClient;
import com.cityscape.app.api.ApiService;
import com.cityscape.app.api.ChatRequest;
import com.cityscape.app.api.ChatResponse;
import com.cityscape.app.data.LocaleHelper;
import com.cityscape.app.model.ChatMessage;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private static final String TAG = "ChatBotFragment";

    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private EditText inputField;
    private ImageButton sendButton;
    private ApiService apiService;

    @Override
    public int getTheme() {
        return R.style.TransparentBottomSheetDialogTheme;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
                if (getActivity() != null) {
                    getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                    int height = (int) (displayMetrics.heightPixels * 0.75);
                    
                    ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
                    params.height = height;
                    bottomSheet.setLayoutParams(params);
                    
                    BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                    behavior.setPeekHeight(height);
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    behavior.setSkipCollapsed(true);
                }
            }
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        
        super.onAttach(LocaleHelper.applyLocale(context));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_bottom_sheet, container, false);

        recyclerView = view.findViewById(R.id.chat_recycler_view);
        inputField = view.findViewById(R.id.chat_input);
        sendButton = view.findViewById(R.id.chat_send_button);

        try {
            apiService = ApiClient.getClient().create(ApiService.class);
        } catch (Exception e) {
            Log.e(TAG, "Error creating API service", e);
        }

        adapter = new ChatAdapter(messages);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        
        com.cityscape.app.data.SessionManager sessionManager = new com.cityscape.app.data.SessionManager(requireContext());
        String userName = sessionManager.getUserName();
        boolean isEn = "en".equals(java.util.Locale.getDefault().getLanguage());
        if (userName == null || userName.isEmpty()) userName = isEn ? "Friend" : "Prieten";
        
        String finalWelcome = isEn ? 
            "Hello, " + userName + "! How can I help you today?" :
            "Salut, " + userName + "! Cu ce te pot ajuta azi?";

        messages.add(new ChatMessage(finalWelcome, false));
        adapter.notifyDataSetChanged();

        
        List<String> welcomeSuggestions = new ArrayList<>();
        if (isEn) {
            welcomeSuggestions.add("🍽️ Fine Dining");
            welcomeSuggestions.add("☕ Cozy Cafe");
            welcomeSuggestions.add("🎭 What to visit?");
            welcomeSuggestions.add("🎬 Cinema");
            welcomeSuggestions.add("🎾 Tennis / Sports");
            welcomeSuggestions.add("🌙 Nightlife");
        } else {
            welcomeSuggestions.add("🍽️ Restaurant bun");
            welcomeSuggestions.add("☕ Cafenea cozy");
            welcomeSuggestions.add("🎭 Ce vizităm?");
            welcomeSuggestions.add("🎬 Cinema");
            welcomeSuggestions.add("🎾 Tenis / Sport");
            welcomeSuggestions.add("🌙 Viață de noapte");
        }
        showSuggestions(welcomeSuggestions);

        
        setupDiscussionThemes(view);

        sendButton.setOnClickListener(v -> sendMessage());

        View closeButton = view.findViewById(R.id.btn_close_chat);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dismiss());
        }

        return view;
    }

    private void setupDiscussionThemes(View view) {
        com.google.android.material.chip.ChipGroup themesGroup = view.findViewById(R.id.chat_themes_group);
        if (themesGroup == null) return;

        boolean isEn = "en".equals(java.util.Locale.getDefault().getLanguage());
        String[] themes;
        if (isEn) {
            themes = new String[]{
                "🍕 Where should we eat today?",
                "📍 What's nearby?",
                "🗺️ Plan my day",
                "💎 Hidden gems",
                "📸 Best photo spots",
                "💸 Budget-friendly options",
                "🎸 Live events"
            };
        } else {
            themes = new String[]{
                "🍕 Unde mâncăm azi?",
                "📍 Ce e în apropiere?",
                "🗺️ Planifică-mi ziua",
                "💎 Locuri ascunse",
                "📸 Cele mai bune poze",
                "💸 Opțiuni ieftine",
                "🎸 Evenimente live"
            };
        }

        for (String theme : themes) {
            com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_chat_suggestion_chip, themesGroup, false);
            chip.setText(theme);
            chip.setOnClickListener(v -> {
                inputField.setText(theme);
                sendMessage();
            });
            themesGroup.addView(chip);
        }
    }

    private void sendMessage() {
        String text = inputField.getText().toString().trim();
        if (!text.isEmpty()) {
            messages.add(new ChatMessage(text, true));
            adapter.notifyItemInserted(messages.size() - 1);
            recyclerView.scrollToPosition(messages.size() - 1);
            inputField.setText("");

            if (apiService == null) {
                messages.add(new ChatMessage(getString(R.string.chatbot_connection_error), false));
                adapter.notifyItemInserted(messages.size() - 1);
                recyclerView.scrollToPosition(messages.size() - 1);
                return;
            }

            
            com.cityscape.app.data.SessionManager sessionManager = new com.cityscape.app.data.SessionManager(requireContext());
            String userId = sessionManager.getUserId();
            double lat = sessionManager.getLastLat();
            double lng = sessionManager.getLastLng();
            String cityName = sessionManager.getPreferredCity();
            
            
            String interests = "";
            int userXp = 0;
            int userLevel = 1;
            int placesVisited = 0;
            
            com.cityscape.app.model.User currentUser = sessionManager.getCurrentUser();
            if (currentUser != null) {
                interests = currentUser.interests != null ? currentUser.interests : "";
                userXp = currentUser.totalXp;
                userLevel = currentUser.level;
                placesVisited = currentUser.placesVisited;
            }
            
            ChatRequest request = new ChatRequest(text, userId, lat, lng, cityName, interests, userXp, userLevel, placesVisited);
            request.language = com.cityscape.app.data.LocaleHelper.getLanguage(requireContext());
            com.cityscape.app.util.BadgeManager.addExperience(requireContext(), userId, 10);
            Call<ChatResponse> call = apiService.chat(request);

            call.enqueue(new Callback<ChatResponse>() {
                @Override
                public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                    if (getContext() == null)
                        return; 
                    if (response.isSuccessful() && response.body() != null) {
                        String reply = response.body().answer;
                        String itineraryJson = response.body().itineraryJson;
                        messages.add(new ChatMessage(reply, false, itineraryJson));
                        adapter.notifyItemInserted(messages.size() - 1);
                        recyclerView.scrollToPosition(messages.size() - 1);
                        
                        
                        showSuggestions(response.body().suggestions);
                    } else {
                        Toast.makeText(getContext(), getString(R.string.chatbot_error), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<ChatResponse> call, Throwable t) {
                    if (getContext() == null)
                        return; 
                    Log.e(TAG, "Error sending message", t);
                    messages.add(new ChatMessage(getString(R.string.chatbot_connection_error), false));
                    adapter.notifyItemInserted(messages.size() - 1);
                    recyclerView.scrollToPosition(messages.size() - 1);
                }
            });
        }
    }

    private void showSuggestions(List<String> suggestions) {
        View view = getView();
        if (view == null || suggestions == null || suggestions.isEmpty()) return;

        com.google.android.material.chip.ChipGroup group = view.findViewById(R.id.chat_suggestions_group);
        View scroll = view.findViewById(R.id.chat_suggestions_scroll);
        
        if (group == null || scroll == null) return;
        
        group.removeAllViews();
        for (String suggestion : suggestions) {
            com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_chat_suggestion_chip, group, false);
            chip.setText(suggestion);
            chip.setOnClickListener(v -> {
                inputField.setText(suggestion);
                sendMessage();
                scroll.setVisibility(View.GONE);
            });
            group.addView(chip);
        }
        scroll.setVisibility(View.VISIBLE);
    }
}
