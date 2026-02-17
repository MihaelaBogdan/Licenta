package com.example.licenta.model;

public class Badge {
    public String name;
    public String description;
    public int iconResId;
    public boolean isUnlocked;

    public Badge(String name, String description, int iconResId, boolean isUnlocked) {
        this.name = name;
        this.description = description;
        this.iconResId = iconResId;
        this.isUnlocked = isUnlocked;
    }
}
