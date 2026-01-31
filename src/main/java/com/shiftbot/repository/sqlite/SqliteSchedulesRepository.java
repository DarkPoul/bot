package com.shiftbot.repository.sqlite;

import com.shiftbot.model.ScheduleEntry;
import com.shiftbot.repository.SchedulesRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SqliteSchedulesRepository implements SchedulesRepository {
    private final DataSource dataSource;

    public SqliteSchedulesRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<ScheduleEntry> findByUserAndMonth(long userId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();
        String sql = "SELECT work_date, is_working, created_at FROM schedules WHERE user_id = ? AND work_date >= ? AND work_date <= ? ORDER BY work_date";
        List<LocalDate> days = new ArrayList<>();
        Instant updatedAt = null;
        boolean hasRows = false;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, start.toString());
            ps.setString(3, end.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    hasRows = true;
                    if (rs.getInt("is_working") == 1) {
                        days.add(LocalDate.parse(rs.getString("work_date")));
                    }
                    String createdAtValue = rs.getString("created_at");
                    if (createdAtValue != null) {
                        Instant candidate = Instant.parse(createdAtValue);
                        if (updatedAt == null || candidate.isAfter(updatedAt)) {
                            updatedAt = candidate;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load schedule", e);
        }
        if (!hasRows) {
            return Optional.empty();
        }
        String csv = days.stream().map(LocalDate::getDayOfMonth).map(String::valueOf).collect(Collectors.joining(","));
        ScheduleEntry entry = new ScheduleEntry(UUID.randomUUID().toString(), userId, year, month, csv, updatedAt);
        return Optional.of(entry);
    }

    @Override
    public List<ScheduleEntry> findAll() {
        String sql = "SELECT user_id, work_date, is_working, created_at FROM schedules ORDER BY user_id, work_date";
        Map<String, List<Integer>> daysByKey = new HashMap<>();
        Map<String, Instant> updatedByKey = new HashMap<>();
        Map<String, YearMonth> monthByKey = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long userId = rs.getLong("user_id");
                LocalDate workDate = LocalDate.parse(rs.getString("work_date"));
                YearMonth month = YearMonth.from(workDate);
                String key = userId + ":" + month;
                monthByKey.putIfAbsent(key, month);
                if (rs.getInt("is_working") == 1) {
                    daysByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(workDate.getDayOfMonth());
                } else {
                    daysByKey.computeIfAbsent(key, ignored -> new ArrayList<>());
                }
                String createdAtValue = rs.getString("created_at");
                if (createdAtValue != null) {
                    Instant candidate = Instant.parse(createdAtValue);
                    Instant current = updatedByKey.get(key);
                    if (current == null || candidate.isAfter(current)) {
                        updatedByKey.put(key, candidate);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load schedules", e);
        }
        List<ScheduleEntry> entries = new ArrayList<>();
        for (Map.Entry<String, YearMonth> entry : monthByKey.entrySet()) {
            String key = entry.getKey();
            YearMonth month = entry.getValue();
            String[] parts = key.split(":");
            long userId = Long.parseLong(parts[0]);
            List<Integer> days = daysByKey.getOrDefault(key, List.of());
            String csv = days.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
            entries.add(new ScheduleEntry(UUID.randomUUID().toString(), userId, month.getYear(), month.getMonthValue(), csv, updatedByKey.get(key)));
        }
        return entries;
    }

    @Override
    public void saveMonthlySchedule(long userId, String locationId, int year, int month, Set<Integer> workDays, Instant updatedAt) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();
        String deleteSql = "DELETE FROM schedules WHERE user_id = ? AND work_date >= ? AND work_date <= ?";
        String insertSql = "INSERT INTO schedules (user_id, location_id, work_date, is_working, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(deleteSql)) {
                delete.setLong(1, userId);
                delete.setString(2, start.toString());
                delete.setString(3, end.toString());
                delete.executeUpdate();
            }
            if (workDays != null && !workDays.isEmpty()) {
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    for (Integer day : workDays) {
                        LocalDate date = yearMonth.atDay(day);
                        insert.setLong(1, userId);
                        insert.setString(2, locationId);
                        insert.setString(3, date.toString());
                        insert.setInt(4, 1);
                        insert.setString(5, updatedAt != null ? updatedAt.toString() : Instant.now().toString());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            } else {
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    insert.setLong(1, userId);
                    insert.setString(2, locationId);
                    insert.setString(3, start.toString());
                    insert.setInt(4, 0);
                    insert.setString(5, updatedAt != null ? updatedAt.toString() : Instant.now().toString());
                    insert.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save monthly schedule", e);
        }
    }
}
