package com.example.licenta.model;

public class Event {
    public String title;
    public String time;
    public String location;
    public String imageUrl;
    public String url;
    public double latitude;
    public double longitude;

    public Event(String title, String time, String location, String imageUrl, String url) {
        this.title = title;
        this.time = time;
        this.location = location;
        this.imageUrl = imageUrl;
        this.url = url;
    }
}
