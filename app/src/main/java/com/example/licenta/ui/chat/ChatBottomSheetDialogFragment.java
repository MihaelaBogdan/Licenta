package com.example.licenta.ui.chat;

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
import com.example.licenta.model.ChatMessage;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private EditText inputField;
    private ImageButton sendButton;
    private ApiService apiService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_bottom_sheet, container, false);

        recyclerView = view.findViewById(R.id.chat_recycler_view);
        inputField = view.findViewById(R.id.chat_input);
        sendButton = view.findViewById(R.id.chat_send_button);

        apiService = ApiClient.getClient().create(ApiService.class);

        adapter = new ChatAdapter(messages);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        // Initial welcome message
        messages.add(new ChatMessage(
                "Hi! I'm your Bucharest Guide AI. I can recommend places and things to do. How can I help?", false));
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

            ChatRequest request = new ChatRequest(text);
            Call<ChatResponse> call = apiService.chat(request);

            call.enqueue(new Callback<ChatResponse>() {
                @Override
                public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        String reply = response.body().answer;
                        messages.add(new ChatMessage(reply, false));
                        adapter.notifyItemInserted(messages.size() - 1);
                        recyclerView.scrollToPosition(messages.size() - 1);
                    } else {
                        Toast.makeText(getContext(), "Failed to get response", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<ChatResponse> call, Throwable t) {
                    Log.e("ChatBot", "Error sending message", t);
                    messages.add(new ChatMessage("Sorry, I'm having trouble connecting to my brain right now.", false));
                    adapter.notifyItemInserted(messages.size() - 1);
                    recyclerView.scrollToPosition(messages.size() - 1);
                }
            });
        }
    }
}
