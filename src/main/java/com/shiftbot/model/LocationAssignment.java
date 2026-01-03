package com.shiftbot.model;

import java.time.LocalDate;

public class LocationAssignment {
    private String locationId;
    private long userId;
    private boolean primary;
    private LocalDate activeFrom;
    private LocalDate activeTo;

    public LocationAssignment() {
    }

    public LocationAssignment(String locationId, long userId, boolean primary, LocalDate activeFrom, LocalDate activeTo) {
        this.locationId = locationId;
        this.userId = userId;
        this.primary = primary;
        this.activeFrom = activeFrom;
        this.activeTo = activeTo;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public LocalDate getActiveFrom() {
        return activeFrom;
    }

    public void setActiveFrom(LocalDate activeFrom) {
        this.activeFrom = activeFrom;
    }

    public LocalDate getActiveTo() {
        return activeTo;
    }

    public void setActiveTo(LocalDate activeTo) {
        this.activeTo = activeTo;
    }
}
