package com.cityscape.app.model;

public class ChatMessage {
    public String message;
    public boolean isUser;
    public long timestamp;

    public ChatMessage(String message, boolean isUser) {
        this.message = message;
        this.isUser = isUser;
        this.timestamp = System.currentTimeMillis();
    }
}
