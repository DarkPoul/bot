package com.shiftbot.repository;

import com.shiftbot.model.ScheduleEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SchedulesRepository {
    private static final String RANGE = "personal_schedules!A2:F";
    private final SheetsClient sheetsClient;

    public SchedulesRepository(SheetsClient sheetsClient) {
        this.sheetsClient = sheetsClient;
    }

    public Optional<ScheduleEntry> findByUserId(long userId) {
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        if (rows == null) {
            return Optional.empty();
        }
        for (List<Object> row : rows) {
            ScheduleEntry entry = mapRow(row);
            if (entry != null && entry.getUserId() == userId) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public Optional<ScheduleEntry> findByUserAndMonth(long userId, int year, int month) {
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        if (rows == null) {
            return Optional.empty();
        }
        for (List<Object> row : rows) {
            ScheduleEntry entry = mapRow(row);
            if (entry != null && entry.getUserId() == userId
                    && entry.getYear() != null && entry.getMonth() != null
                    && entry.getYear() == year && entry.getMonth() == month) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public void save(ScheduleEntry entry) {
        if (entry.getScheduleId() == null) {
            entry.setScheduleId(UUID.randomUUID().toString());
        }
        List<Object> row = buildRow(entry);
        sheetsClient.appendRow(RANGE, row);
    }

    public void upsert(ScheduleEntry entry) {
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        if (rows == null) {
            save(entry);
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            ScheduleEntry existing = mapRow(rows.get(i));
            if (existing != null && existing.getUserId() == entry.getUserId()
                    && existing.getYear() != null && existing.getMonth() != null
                    && existing.getYear().equals(entry.getYear())
                    && existing.getMonth().equals(entry.getMonth())) {
                if (entry.getScheduleId() == null) {
                    entry.setScheduleId(existing.getScheduleId());
                }
                sheetsClient.updateRow(RANGE, i, buildRow(entry));
                return;
            }
        }
        save(entry);
    }

    private List<Object> buildRow(ScheduleEntry entry) {
        List<Object> row = new ArrayList<>();
        row.add(entry.getScheduleId());
        row.add(String.valueOf(entry.getUserId()));
        row.add(entry.getYear() != null ? entry.getYear().toString() : "");
        row.add(entry.getMonth() != null ? entry.getMonth().toString() : "");
        row.add(entry.getWorkDaysCsv());
        row.add(entry.getUpdatedAt() != null ? entry.getUpdatedAt().toString() : Instant.now().toString());
        return row;
    }

    private ScheduleEntry mapRow(List<Object> row) {
        if (row == null || row.isEmpty() || row.get(0) == null || row.get(0).toString().isBlank()) {
            return null;
        }
        String scheduleId = row.get(0).toString();
        long userId = Long.parseLong(get(row, 1));
        Integer year = get(row, 2).isEmpty() ? null : Integer.parseInt(get(row, 2));
        Integer month = get(row, 3).isEmpty() ? null : Integer.parseInt(get(row, 3));
        String workDaysCsv = get(row, 4);
        Instant updatedAt = get(row, 5).isEmpty() ? Instant.now() : Instant.parse(get(row, 5));
        return new ScheduleEntry(scheduleId, userId, year, month, workDaysCsv, updatedAt);
    }

    private String get(List<Object> row, int idx) {
        if (row.size() > idx && row.get(idx) != null) {
            return row.get(idx).toString();
        }
        return "";
    }
}
