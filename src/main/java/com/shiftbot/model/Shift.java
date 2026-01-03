package com.shiftbot.model;

import com.shiftbot.model.enums.ShiftSource;
import com.shiftbot.model.enums.ShiftStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

public class Shift {
    private String shiftId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private String locationId;
    private long userId;
    private ShiftStatus status;
    private ShiftSource source;
    private String linkedRequestId;
    private Instant updatedAt;

    public Shift() {
        this.shiftId = UUID.randomUUID().toString();
    }

    public Shift(String shiftId, LocalDate date, LocalTime startTime, LocalTime endTime, String locationId, long userId,
                 ShiftStatus status, ShiftSource source, String linkedRequestId, Instant updatedAt) {
        this.shiftId = shiftId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.locationId = locationId;
        this.userId = userId;
        this.status = status;
        this.source = source;
        this.linkedRequestId = linkedRequestId;
        this.updatedAt = updatedAt;
    }

    public String getShiftId() {
        return shiftId;
    }

    public void setShiftId(String shiftId) {
        this.shiftId = shiftId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
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

    public ShiftStatus getStatus() {
        return status;
    }

    public void setStatus(ShiftStatus status) {
        this.status = status;
    }

    public ShiftSource getSource() {
        return source;
    }

    public void setSource(ShiftSource source) {
        this.source = source;
    }

    public String getLinkedRequestId() {
        return linkedRequestId;
    }

    public void setLinkedRequestId(String linkedRequestId) {
        this.linkedRequestId = linkedRequestId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Shift shift = (Shift) o;
        return Objects.equals(shiftId, shift.shiftId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shiftId);
    }
}
