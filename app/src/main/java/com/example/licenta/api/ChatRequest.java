package com.example.licenta.api;

public class ChatRequest {
    public String message;
    public String language;

    public ChatRequest(String message) {
        this.message = message;
        this.language = "ro"; // default Romanian
    }

    public ChatRequest(String message, String language) {
        this.message = message;
        this.language = language;
    }
}
