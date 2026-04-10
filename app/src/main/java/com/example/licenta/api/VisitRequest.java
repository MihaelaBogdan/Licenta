package com.example.licenta.api;

public class VisitRequest {
    public String user_id;
    public String place_id;
    public String google_place_id;
    public String place_name;
    public String place_type;

    public VisitRequest(String user_id, String place_id, String google_place_id, String place_name, String place_type) {
        this.user_id = user_id;
        this.place_id = place_id;
        this.google_place_id = google_place_id;
        this.place_name = place_name;
        this.place_type = place_type;
    }
}
