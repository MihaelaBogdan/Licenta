package com.cityscape.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cityscape.app.R;
import com.cityscape.app.database.entities.Place;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for chat messages in chatbot
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_BOT = 1;
    private static final int TYPE_BOT_WITH_PLACES = 2;

    private final List<ChatMessage> messages;
    private final OnPlaceClickListener placeClickListener;

    public interface OnPlaceClickListener {
        void onPlaceClick(Place place);
    }

    public ChatAdapter(List<ChatMessage> messages, OnPlaceClickListener listener) {
        this.messages = messages;
        this.placeClickListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messages.get(position);
        if (message.isUser) {
            return TYPE_USER;
        } else if (message.places != null && !message.places.isEmpty()) {
            return TYPE_BOT_WITH_PLACES;
        }
        return TYPE_BOT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == TYPE_USER) {
            View view = inflater.inflate(R.layout.item_chat_user, parent, false);
            return new UserMessageViewHolder(view);
        } else if (viewType == TYPE_BOT_WITH_PLACES) {
            View view = inflater.inflate(R.layout.item_chat_bot_places, parent, false);
            return new BotPlacesViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_chat_bot, parent, false);
            return new BotMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);

        if (holder instanceof UserMessageViewHolder) {
            ((UserMessageViewHolder) holder).bind(message);
        } else if (holder instanceof BotPlacesViewHolder) {
            ((BotPlacesViewHolder) holder).bind(message);
        } else if (holder instanceof BotMessageViewHolder) {
            ((BotMessageViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // View Holders
    static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;

        UserMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }

        void bind(ChatMessage message) {
            messageText.setText(message.text);
        }
    }

    static class BotMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;

        BotMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }

        void bind(ChatMessage message) {
            messageText.setText(message.text);
        }
    }

    class BotPlacesViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;
        private final RecyclerView placesRecycler;

        BotPlacesViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            placesRecycler = itemView.findViewById(R.id.placesRecycler);
        }

        void bind(ChatMessage message) {
            messageText.setText(message.text);

            // Setup places recycler
            placesRecycler.setLayoutManager(
                    new LinearLayoutManager(itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
            PlaceCardAdapter adapter = new PlaceCardAdapter(message.places, placeClickListener::onPlaceClick);
            placesRecycler.setAdapter(adapter);
        }
    }

    // Data class
    public static class ChatMessage {
        public final String text;
        public final boolean isUser;
        public final List<Place> places;

        public ChatMessage(String text, boolean isUser, List<Place> places) {
            this.text = text;
            this.isUser = isUser;
            this.places = places;
        }
    }
}
