package com.cityscape.app.model;

import java.util.List;

public class LiveBattleResponse {
    public static class PlaceInfo {
        public String name;
        public String type;
        public String image_url;
        public int pct; 
        public String review_text;
        public String review_author;
    }
    public static class LeaderboardEntry {
        public int id;
        public String name;
        public int xp;
    }
    public PlaceInfo place_a;
    public PlaceInfo place_b;
    public String most_voted_name;
    public int most_voted_pct;
    public List<LeaderboardEntry> leaderboard;
    public boolean user_has_voted;
}
