package com.shiftbot.repository.sqlite;

import com.shiftbot.model.Shift;
import com.shiftbot.model.enums.ShiftSource;
import com.shiftbot.model.enums.ShiftStatus;
import com.shiftbot.repository.ShiftsRepository;
import com.shiftbot.util.TimeUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteShiftsRepository implements ShiftsRepository {
    private final DataSource dataSource;
    private final ZoneId zoneId;

    public SqliteShiftsRepository(DataSource dataSource, ZoneId zoneId) {
        this.dataSource = dataSource;
        this.zoneId = zoneId;
    }

    @Override
    public List<Shift> findAll() {
        String sql = "SELECT shift_id, date, start_time, end_time, location_id, user_id, status, source, linked_request_id, updated_at FROM shifts";
        List<Shift> shifts = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                shifts.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load shifts", e);
        }
        return shifts;
    }

    @Override
    public List<Shift> findByUser(long userId) {
        String sql = "SELECT shift_id, date, start_time, end_time, location_id, user_id, status, source, linked_request_id, updated_at FROM shifts WHERE user_id = ?";
        List<Shift> shifts = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    shifts.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load shifts", e);
        }
        return shifts;
    }

    @Override
    public List<Shift> findByLocation(String locationId) {
        String sql = "SELECT shift_id, date, start_time, end_time, location_id, user_id, status, source, linked_request_id, updated_at FROM shifts WHERE location_id = ?";
        List<Shift> shifts = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    shifts.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load shifts", e);
        }
        return shifts;
    }

    @Override
    public void save(Shift shift) {
        if (shift.getShiftId() == null) {
            shift.setShiftId(java.util.UUID.randomUUID().toString());
        }
        String sql = "INSERT INTO shifts (shift_id, date, start_time, end_time, location_id, user_id, status, source, linked_request_id, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(shift_id) DO UPDATE SET date = excluded.date, start_time = excluded.start_time, end_time = excluded.end_time, " +
                "location_id = excluded.location_id, user_id = excluded.user_id, status = excluded.status, source = excluded.source, " +
                "linked_request_id = excluded.linked_request_id, updated_at = excluded.updated_at";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, shift.getShiftId());
            ps.setString(2, shift.getDate().toString());
            ps.setString(3, shift.getStartTime().toString());
            ps.setString(4, shift.getEndTime().toString());
            ps.setString(5, shift.getLocationId());
            ps.setLong(6, shift.getUserId());
            ps.setString(7, shift.getStatus().name());
            ps.setString(8, shift.getSource().name());
            ps.setString(9, shift.getLinkedRequestId());
            ps.setString(10, shift.getUpdatedAt() != null ? shift.getUpdatedAt().toString() : Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save shift", e);
        }
    }

    @Override
    public void updateStatusAndLink(String shiftId, ShiftStatus status, String linkedRequestId) {
        String sql = "UPDATE shifts SET status = ?, linked_request_id = ?, updated_at = ? WHERE shift_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, linkedRequestId);
            ps.setString(3, Instant.now().toString());
            ps.setString(4, shiftId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("Shift not found: " + shiftId);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update shift", e);
        }
    }

    @Override
    public Optional<Shift> findByUserAndSlot(long userId, LocalDate date, LocalTime startTime, LocalTime endTime, String locationId) {
        String sql = "SELECT shift_id, date, start_time, end_time, location_id, user_id, status, source, linked_request_id, updated_at " +
                "FROM shifts WHERE user_id = ? AND date = ? AND start_time = ? AND end_time = ? AND location_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, date.toString());
            ps.setString(3, startTime.toString());
            ps.setString(4, endTime.toString());
            ps.setString(5, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load shift", e);
        }
        return Optional.empty();
    }

    private Shift mapRow(ResultSet rs) throws SQLException {
        String shiftId = rs.getString("shift_id");
        LocalDate date = LocalDate.parse(rs.getString("date"));
        LocalTime start = LocalTime.parse(rs.getString("start_time"));
        LocalTime end = LocalTime.parse(rs.getString("end_time"));
        String locationId = rs.getString("location_id");
        long userId = rs.getLong("user_id");
        ShiftStatus status = ShiftStatus.valueOf(rs.getString("status"));
        ShiftSource source = ShiftSource.valueOf(rs.getString("source"));
        String linkedRequestId = rs.getString("linked_request_id");
        String updatedAtValue = rs.getString("updated_at");
        Instant updatedAt = updatedAtValue == null ? TimeUtils.nowInstant(zoneId) : Instant.parse(updatedAtValue);
        return new Shift(shiftId, date, start, end, locationId, userId, status, source, linkedRequestId, updatedAt);
    }
}
