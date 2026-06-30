package com.cityscape.app.adapter;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.cityscape.app.R;
import com.cityscape.app.model.ChatMessage;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private final List<ChatMessage> messages;

    // [MAPS:Place Name:lat:lng]
    private static final Pattern MAPS_TAG = Pattern.compile(
        "\\[MAPS:([^:]+):([\\d.\\-]+):([\\d.\\-]+)\\]"
    );

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    static class PlaceLink {
        String name;
        double lat, lng;
        PlaceLink(String name, double lat, double lng) {
            this.name = name; this.lat = lat; this.lng = lng;
        }
    }

    private static List<PlaceLink> extractPlaces(String text) {
        List<PlaceLink> places = new ArrayList<>();
        Matcher m = MAPS_TAG.matcher(text);
        while (m.find()) {
            try {
                String name = m.group(1).trim();
                double lat = Double.parseDouble(m.group(2));
                double lng = Double.parseDouble(m.group(3));
                places.add(new PlaceLink(name, lat, lng));
            } catch (Exception ignored) {}
        }
        return places;
    }

    private static String stripMapsTags(String text) {
        return MAPS_TAG.matcher(text).replaceAll("").trim();
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
        if (message == null) return;

        if (message.isUser) {
            if (holder.userMessageText != null) {
                holder.userMessageText.setVisibility(View.VISIBLE);
                holder.userMessageText.setText(message.message);
            }
            if (holder.botMessageContainer != null) {
                holder.botMessageContainer.setVisibility(View.GONE);
            }
        } else {
            if (holder.userMessageText != null) holder.userMessageText.setVisibility(View.GONE);
            if (holder.botMessageContainer != null) holder.botMessageContainer.setVisibility(View.VISIBLE);

            String rawText = message.message != null ? message.message : "";

            // Extract [MAPS:...] tags then strip them from displayed text
            List<PlaceLink> places = extractPlaces(rawText);
            String displayText = stripMapsTags(rawText);

            if (holder.botMessageText != null) {
                holder.botMessageText.setText(displayText);
            }

            // Build Maps buttons — only if the message contained [MAPS:...] tags
            if (holder.buttonsContainer != null) {
                holder.buttonsContainer.removeAllViews();

                if (!places.isEmpty()) {
                    holder.buttonsContainer.setVisibility(View.VISIBLE);
                    for (PlaceLink place : places) {
                        LinearLayout row = new LinearLayout(holder.buttonsContainer.getContext());
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ));
                        row.setPadding(0, 6, 0, 2);
                        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

                        TextView label = new TextView(holder.buttonsContainer.getContext());
                        label.setText("📍 " + place.name);
                        label.setTextColor(holder.buttonsContainer.getContext().getResources().getColor(R.color.primary));
                        label.setTextSize(12f);
                        label.setTypeface(null, android.graphics.Typeface.BOLD);
                        label.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ));

                        MaterialButton btn = new MaterialButton(holder.buttonsContainer.getContext());
                        btn.setText("Maps");
                        btn.setTextSize(9f);
                        btn.setCornerRadius(24);
                        btn.setMinWidth(0);
                        btn.setMinimumWidth(0);
                        btn.setPadding(20, 4, 20, 4);
                        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            holder.buttonsContainer.getContext().getResources().getColor(R.color.primary)
                        ));
                        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        btnParams.setMargins(16, 0, 0, 0);
                        btn.setLayoutParams(btnParams);

                        final PlaceLink p = place;
                        btn.setOnClickListener(v -> {
                            try {
                                Uri gmmUri = Uri.parse("geo:" + p.lat + "," + p.lng + "?q=" + Uri.encode(p.name));
                                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmUri);
                                mapIntent.setPackage("com.google.android.apps.maps");
                                v.getContext().startActivity(mapIntent);
                            } catch (ActivityNotFoundException e) {
                                Uri webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query="
                                    + Uri.encode(p.name) + "&query_place_id=" + p.lat + "," + p.lng);
                                v.getContext().startActivity(new Intent(Intent.ACTION_VIEW, webUri));
                            } catch (Exception ex) {
                                Toast.makeText(v.getContext(), "Nu s-a putut deschide Maps", Toast.LENGTH_SHORT).show();
                            }
                        });

                        row.addView(label);
                        row.addView(btn);
                        holder.buttonsContainer.addView(row);
                    }
                } else {
                    holder.buttonsContainer.setVisibility(View.GONE);
                }
            }

            // Itinerary card
            if (message.itineraryJson != null && !message.itineraryJson.isEmpty()) {
                if (holder.chatItineraryCard != null) holder.chatItineraryCard.setVisibility(View.VISIBLE);
                if (holder.chatItineraryTitle != null) holder.chatItineraryTitle.setText("🗺️ Traseu AI Personalizat");
                if (holder.chatItinerarySummary != null) holder.chatItinerarySummary.setText("Planul tău de explorare este gata!");
                if (holder.chatItineraryTrail != null) holder.chatItineraryTrail.setText("Apasă mai jos pentru a deschide!");

                try {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<com.cityscape.app.api.ItineraryItem>>() {}.getType();
                    List<com.cityscape.app.api.ItineraryItem> items = gson.fromJson(message.itineraryJson, listType);
                    if (items != null && !items.isEmpty()) {
                        double totalCost = 0;
                        List<String> names = new ArrayList<>();
                        for (com.cityscape.app.api.ItineraryItem item : items) {
                            totalCost += item.estimatedCost;
                            if (item.name != null && !item.name.isEmpty()) names.add(item.name);
                        }
                        String costText = totalCost > 0 ? " • ~" + (int) totalCost + " RON" : "";
                        if (holder.chatItinerarySummary != null)
                            holder.chatItinerarySummary.setText(items.size() + " opriri" + costText);
                        if (holder.chatItineraryTrail != null)
                            holder.chatItineraryTrail.setText(android.text.TextUtils.join(" ➜ ", names));
                    }
                } catch (Exception e) { e.printStackTrace(); }

                if (holder.chatBtnViewItinerary != null) {
                    holder.chatBtnViewItinerary.setOnClickListener(v -> {
                        try {
                            android.os.Bundle args = new android.os.Bundle();
                            args.putString("itinerary_json", message.itineraryJson);
                            args.putString("itinerary_type", "Traseu AI Chat");
                            androidx.navigation.NavController navController =
                                androidx.navigation.Navigation.findNavController(
                                    (android.app.Activity) v.getContext(), R.id.nav_host_fragment);
                            navController.navigate(R.id.navigation_itinerary, args);
                        } catch (Exception e) {
                            Toast.makeText(v.getContext(), "Traseul se încarcă...", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } else {
                if (holder.chatItineraryCard != null) holder.chatItineraryCard.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() { return messages.size(); }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        LinearLayout botMessageContainer, buttonsContainer;
        TextView botMessageText, userMessageText;
        androidx.cardview.widget.CardView chatItineraryCard;
        MaterialButton chatBtnViewItinerary;
        TextView chatItineraryTitle, chatItinerarySummary, chatItineraryTrail;

        ChatViewHolder(View itemView) {
            super(itemView);
            botMessageContainer = itemView.findViewById(R.id.bot_message_container);
            botMessageText = itemView.findViewById(R.id.bot_message_text);
            userMessageText = itemView.findViewById(R.id.user_message_text);
            buttonsContainer = itemView.findViewById(R.id.buttons_container);
            chatItineraryCard = itemView.findViewById(R.id.chat_itinerary_card);
            chatBtnViewItinerary = itemView.findViewById(R.id.chat_btn_view_itinerary);
            chatItineraryTitle = itemView.findViewById(R.id.chat_itinerary_title);
            chatItinerarySummary = itemView.findViewById(R.id.chat_itinerary_summary);
            chatItineraryTrail = itemView.findViewById(R.id.chat_itinerary_trail);
        }
    }
}
