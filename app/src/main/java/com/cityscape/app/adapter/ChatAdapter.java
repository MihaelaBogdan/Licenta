package com.cityscape.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.cityscape.app.R;
import com.cityscape.app.model.ChatMessage;
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

            // Handle dynamic AI itinerary card inside Chat
            if (message.itineraryJson != null && !message.itineraryJson.isEmpty()) {
                holder.chatItineraryCard.setVisibility(View.VISIBLE);
                
                // Set default texts in case parsing fails
                if (holder.chatItineraryTitle != null) {
                    holder.chatItineraryTitle.setText("🗺️ Traseu AI Personalizat");
                }
                if (holder.chatItinerarySummary != null) {
                    holder.chatItinerarySummary.setText("Planul tău de explorare este gata!");
                }
                if (holder.chatItineraryTrail != null) {
                    holder.chatItineraryTrail.setText("Apasă mai jos pentru a deschide!");
                }

                try {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<com.cityscape.app.api.ItineraryItem>>() {}.getType();
                    List<com.cityscape.app.api.ItineraryItem> items = gson.fromJson(message.itineraryJson, listType);

                    if (items != null && !items.isEmpty()) {
                        int count = items.size();
                        double totalCost = 0;
                        List<String> names = new java.util.ArrayList<>();
                        for (com.cityscape.app.api.ItineraryItem item : items) {
                            totalCost += item.estimatedCost;
                            if (item.name != null && !item.name.isEmpty()) {
                                names.add(item.name);
                            }
                        }

                        String countText = count == 1 ? "1 Opriri" : count + " Opriri";
                        String costText = totalCost > 0 ? " • Buget estimat: " + (int) totalCost + " RON" : "";
                        if (holder.chatItinerarySummary != null) {
                            holder.chatItinerarySummary.setText(countText + costText);
                        }

                        String trail = android.text.TextUtils.join(" ➜ ", names);
                        if (holder.chatItineraryTrail != null) {
                            holder.chatItineraryTrail.setText(trail);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                holder.chatBtnViewItinerary.setOnClickListener(v -> {
                    try {
                        android.os.Bundle args = new android.os.Bundle();
                        args.putString("itinerary_json", message.itineraryJson);
                        args.putString("itinerary_type", "Traseu AI Chat");
                        
                        // Navigate using the MainActivity NavController
                        androidx.navigation.NavController navController = androidx.navigation.Navigation.findNavController(
                            (android.app.Activity) v.getContext(), R.id.nav_host_fragment);
                        navController.navigate(R.id.navigation_itinerary, args);
                    } catch (Exception e) {
                        e.printStackTrace();
                        android.widget.Toast.makeText(v.getContext(), "Traseul se încarcă...", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                holder.chatItineraryCard.setVisibility(View.GONE);
            }
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
        androidx.cardview.widget.CardView chatItineraryCard;
        com.google.android.material.button.MaterialButton chatBtnViewItinerary;
        TextView chatItineraryTitle;
        TextView chatItinerarySummary;
        TextView chatItineraryTrail;

        ChatViewHolder(View itemView) {
            super(itemView);
            botMessageContainer = itemView.findViewById(R.id.bot_message_container);
            botMessageText = itemView.findViewById(R.id.bot_message_text);
            userMessageText = itemView.findViewById(R.id.user_message_text);
            chatItineraryCard = itemView.findViewById(R.id.chat_itinerary_card);
            chatBtnViewItinerary = itemView.findViewById(R.id.chat_btn_view_itinerary);
            chatItineraryTitle = itemView.findViewById(R.id.chat_itinerary_title);
            chatItinerarySummary = itemView.findViewById(R.id.chat_itinerary_summary);
            chatItineraryTrail = itemView.findViewById(R.id.chat_itinerary_trail);
        }
    }
}
