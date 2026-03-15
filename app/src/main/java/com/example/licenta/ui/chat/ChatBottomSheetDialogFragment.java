package com.example.licenta.ui.chat;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.licenta.R;
import com.example.licenta.adapter.ChatAdapter;
import com.example.licenta.api.ApiClient;
import com.example.licenta.api.ApiService;
import com.example.licenta.api.ChatRequest;
import com.example.licenta.api.ChatResponse;
import com.example.licenta.data.LocaleHelper;
import com.example.licenta.model.ChatMessage;
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
    public void onAttach(@NonNull Context context) {
        // Apply saved locale to the fragment's context
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

        // Initial welcome message - using locale-aware string resource
        messages.add(new ChatMessage(getString(R.string.chatbot_welcome), false));
        adapter.notifyDataSetChanged();

        sendButton.setOnClickListener(v -> sendMessage());

        return view;
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

            // Send with current language
            String currentLanguage = LocaleHelper.getChatbotLanguage(requireContext());
            ChatRequest request = new ChatRequest(text, currentLanguage);
            Call<ChatResponse> call = apiService.chat(request);

            call.enqueue(new Callback<ChatResponse>() {
                @Override
                public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                    if (getContext() == null)
                        return; // Fragment detached
                    if (response.isSuccessful() && response.body() != null) {
                        String reply = response.body().answer;
                        messages.add(new ChatMessage(reply, false));
                        adapter.notifyItemInserted(messages.size() - 1);
                        recyclerView.scrollToPosition(messages.size() - 1);
                    } else {
                        Toast.makeText(getContext(), getString(R.string.chatbot_error), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<ChatResponse> call, Throwable t) {
                    if (getContext() == null)
                        return; // Fragment detached
                    Log.e(TAG, "Error sending message", t);
                    messages.add(new ChatMessage(getString(R.string.chatbot_connection_error), false));
                    adapter.notifyItemInserted(messages.size() - 1);
                    recyclerView.scrollToPosition(messages.size() - 1);
                }
            });
        }
    }
}
