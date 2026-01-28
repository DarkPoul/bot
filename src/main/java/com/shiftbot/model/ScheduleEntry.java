package com.shiftbot.model;

import java.time.Instant;

public class ScheduleEntry {
    private String scheduleId;
    private long userId;
    private Integer year;
    private Integer month;
    private String workDaysCsv;
    private Instant updatedAt;

    public ScheduleEntry() {
    }

    public ScheduleEntry(String scheduleId, long userId, Integer year, Integer month, String workDaysCsv, Instant updatedAt) {
        this.scheduleId = scheduleId;
        this.userId = userId;
        this.year = year;
        this.month = month;
        this.workDaysCsv = workDaysCsv;
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

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }

    public String getWorkDaysCsv() {
        return workDaysCsv;
    }

    public void setWorkDaysCsv(String workDaysCsv) {
        this.workDaysCsv = workDaysCsv;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
