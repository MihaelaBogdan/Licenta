package com.cityscape.app.model;

public class Badge {
    public String name;
    public String description;
    public int iconResId;
    public boolean isUnlocked;

    public String requirement;

    public Badge(String name, String description, String requirement, int iconResId, boolean isUnlocked) {
        this.name = name;
        this.description = description;
        this.requirement = requirement;
        this.iconResId = iconResId;
        this.isUnlocked = isUnlocked;
    }
}
