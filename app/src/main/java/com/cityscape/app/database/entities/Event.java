package com.cityscape.app.database.entities;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Event entity for events at places
 */
@Entity(tableName = "events", foreignKeys = @ForeignKey(entity = Place.class, parentColumns = "id", childColumns = "placeId", onDelete = ForeignKey.CASCADE), indices = {
        @Index("placeId"), @Index("startDate") })
public class Event {

    @PrimaryKey
    @NonNull
    private String id;

    @NonNull
    private String placeId;

    private String title;
    private String description;
    private String imageUrl;
    private long startDate;
    private long endDate;
    private String startTime;
    private String endTime;
    private boolean isRecurring;
    private String recurrencePattern; // weekly, monthly, etc.
    private int price; // 0 for free
    private String ticketUrl;
    private int interestedCount;
    private int goingCount;

    // Constructor
    public Event(@NonNull String id, @NonNull String placeId, String title,
            long startDate, long endDate) {
        this.id = id;
        this.placeId = placeId;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.isRecurring = false;
        this.price = 0;
        this.interestedCount = 0;
        this.goingCount = 0;
    }

    // Getters and Setters
    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public String getPlaceId() {
        return placeId;
    }

    public void setPlaceId(@NonNull String placeId) {
        this.placeId = placeId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public long getStartDate() {
        return startDate;
    }

    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }

    public long getEndDate() {
        return endDate;
    }

    public void setEndDate(long endDate) {
        this.endDate = endDate;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public boolean isRecurring() {
        return isRecurring;
    }

    public void setRecurring(boolean recurring) {
        isRecurring = recurring;
    }

    public String getRecurrencePattern() {
        return recurrencePattern;
    }

    public void setRecurrencePattern(String recurrencePattern) {
        this.recurrencePattern = recurrencePattern;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public String getTicketUrl() {
        return ticketUrl;
    }

    public void setTicketUrl(String ticketUrl) {
        this.ticketUrl = ticketUrl;
    }

    public int getInterestedCount() {
        return interestedCount;
    }

    public void setInterestedCount(int interestedCount) {
        this.interestedCount = interestedCount;
    }

    public int getGoingCount() {
        return goingCount;
    }

    public void setGoingCount(int goingCount) {
        this.goingCount = goingCount;
    }

    // Check if event is free
    public boolean isFree() {
        return price == 0;
    }
}
