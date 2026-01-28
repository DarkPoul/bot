package com.shiftbot.model;

import java.time.Instant;
import java.time.LocalDate;

public class ScheduleEntry {
    private String scheduleId;
    private long userId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String scheduleText;
    private Instant updatedAt;

    public ScheduleEntry() {
    }

    public ScheduleEntry(String scheduleId, long userId, LocalDate periodStart, LocalDate periodEnd, String scheduleText, Instant updatedAt) {
        this.scheduleId = scheduleId;
        this.userId = userId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.scheduleText = scheduleText;
        this.updatedAt = updatedAt;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(String scheduleId) {
        this.scheduleId = scheduleId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public String getScheduleText() {
        return scheduleText;
    }

    public void setScheduleText(String scheduleText) {
        this.scheduleText = scheduleText;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
