package com.cityscape.app.model;

import java.util.List;

public class VoteResponse {
    public boolean success;
    public String message;
    public int votes_a;
    public double pct_a;
    public int votes_b;
    public double pct_b;
    public String user_choice;
    public String most_voted_name;
    public double most_voted_pct;
    public List<LeaderboardEntry> leaderboard;
    public boolean user_has_voted;

    public static class LeaderboardEntry {
        public String name;
        public int total_xp;
    }
}
