package com.example.licenta.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.licenta.R;
import com.example.licenta.model.ChatMessage;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage message = messages.get(position);

        if (message.isUser) {
            holder.userMessageText.setVisibility(View.VISIBLE);
            holder.botMessageContainer.setVisibility(View.GONE);
            holder.userMessageText.setText(message.message);
        } else {
            holder.userMessageText.setVisibility(View.GONE);
            holder.botMessageContainer.setVisibility(View.VISIBLE);
            holder.botMessageText.setText(message.message);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        LinearLayout botMessageContainer;
        TextView botMessageText;
        TextView userMessageText;

        ChatViewHolder(View itemView) {
            super(itemView);
            botMessageContainer = itemView.findViewById(R.id.bot_message_container);
            botMessageText = itemView.findViewById(R.id.bot_message_text);
            userMessageText = itemView.findViewById(R.id.user_message_text);
        }
    }
}
