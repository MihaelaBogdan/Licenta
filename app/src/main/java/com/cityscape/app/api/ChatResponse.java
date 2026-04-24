package com.cityscape.app.api;

import com.google.gson.annotations.SerializedName;

public class ChatResponse {
    @SerializedName("answer")
    public String answer;

    @SerializedName("suggestions")
    public java.util.List<String> suggestions;
}
