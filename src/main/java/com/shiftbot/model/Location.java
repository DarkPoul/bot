package com.shiftbot.model;

import java.util.Objects;

public class Location {
    private String locationId;
    private String name;
    private String address;
    private boolean active;

    public Location() {
    }

    public Location(String locationId, String name, String address, boolean active) {
        this.locationId = locationId;
        this.name = name;
        this.address = address;
        this.active = active;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return Objects.equals(locationId, location.locationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationId);
    }
}
