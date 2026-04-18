package com.cityscape.app.model;

import java.util.Date;

public class CalendarDate {
    public Date date;
    public boolean isSelected;
    public boolean hasEvents;

    public CalendarDate(Date date, boolean isSelected, boolean hasEvents) {
        this.date = date;
        this.isSelected = isSelected;
        this.hasEvents = hasEvents;
    }
}
