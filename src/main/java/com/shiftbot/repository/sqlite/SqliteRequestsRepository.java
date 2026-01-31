package com.shiftbot.repository.sqlite;

import com.shiftbot.model.Request;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import com.shiftbot.repository.RequestsRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteRequestsRepository implements RequestsRepository {
    private final DataSource dataSource;

    public SqliteRequestsRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Request> findAll() {
        String sql = "SELECT request_id, type, initiator_user_id, from_user_id, to_user_id, date, start_time, end_time, location_id, status, comment, created_at, updated_at FROM requests";
        List<Request> requests = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                requests.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load requests", e);
        }
        return requests;
    }

    @Override
    public Optional<Request> findById(String requestId) {
        String sql = "SELECT request_id, type, initiator_user_id, from_user_id, to_user_id, date, start_time, end_time, location_id, status, comment, created_at, updated_at FROM requests WHERE request_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load request", e);
        }
        return Optional.empty();
    }

    @Override
    public void save(Request request) {
        if (request.getRequestId() == null) {
            request.setRequestId(java.util.UUID.randomUUID().toString());
        }
        String sql = "INSERT INTO requests (request_id, type, initiator_user_id, from_user_id, to_user_id, date, start_time, end_time, location_id, status, comment, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(request_id) DO UPDATE SET type = excluded.type, initiator_user_id = excluded.initiator_user_id, " +
                "from_user_id = excluded.from_user_id, to_user_id = excluded.to_user_id, date = excluded.date, start_time = excluded.start_time, " +
                "end_time = excluded.end_time, location_id = excluded.location_id, status = excluded.status, comment = excluded.comment, " +
                "created_at = excluded.created_at, updated_at = excluded.updated_at";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, request);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save request", e);
        }
    }

    @Override
    public void update(Request request) {
        String sql = "UPDATE requests SET type = ?, initiator_user_id = ?, from_user_id = ?, to_user_id = ?, date = ?, start_time = ?, end_time = ?, " +
                "location_id = ?, status = ?, comment = ?, created_at = ?, updated_at = ? WHERE request_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, request.getType().name());
            ps.setLong(2, request.getInitiatorUserId());
            setLong(ps, 3, request.getFromUserId());
            setLong(ps, 4, request.getToUserId());
            ps.setString(5, request.getDate().toString());
            ps.setString(6, request.getStartTime().toString());
            ps.setString(7, request.getEndTime().toString());
            ps.setString(8, request.getLocationId());
            ps.setString(9, request.getStatus().name());
            ps.setString(10, request.getComment());
            ps.setString(11, request.getCreatedAt() != null ? request.getCreatedAt().toString() : null);
            ps.setString(12, request.getUpdatedAt() != null ? request.getUpdatedAt().toString() : null);
            ps.setString(13, request.getRequestId());
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("Request not found: " + request.getRequestId());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update request", e);
        }
    }

    private void bind(PreparedStatement ps, Request request) throws SQLException {
        ps.setString(1, request.getRequestId());
        ps.setString(2, request.getType().name());
        ps.setLong(3, request.getInitiatorUserId());
        setLong(ps, 4, request.getFromUserId());
        setLong(ps, 5, request.getToUserId());
        ps.setString(6, request.getDate().toString());
        ps.setString(7, request.getStartTime().toString());
        ps.setString(8, request.getEndTime().toString());
        ps.setString(9, request.getLocationId());
        ps.setString(10, request.getStatus().name());
        ps.setString(11, request.getComment());
        ps.setString(12, request.getCreatedAt() != null ? request.getCreatedAt().toString() : Instant.now().toString());
        ps.setString(13, request.getUpdatedAt() != null ? request.getUpdatedAt().toString() : Instant.now().toString());
    }

    private Request mapRow(ResultSet rs) throws SQLException {
        String requestId = rs.getString("request_id");
        RequestType type = RequestType.valueOf(rs.getString("type"));
        long initiator = rs.getLong("initiator_user_id");
        Long fromUserId = getLong(rs, "from_user_id");
        Long toUserId = getLong(rs, "to_user_id");
        LocalDate date = LocalDate.parse(rs.getString("date"));
        LocalTime start = LocalTime.parse(rs.getString("start_time"));
        LocalTime end = LocalTime.parse(rs.getString("end_time"));
        String locationId = rs.getString("location_id");
        RequestStatus status = RequestStatus.valueOf(rs.getString("status"));
        String comment = rs.getString("comment");
        String createdAtValue = rs.getString("created_at");
        Instant createdAt = createdAtValue == null ? null : Instant.parse(createdAtValue);
        String updatedAtValue = rs.getString("updated_at");
        Instant updatedAt = updatedAtValue == null ? null : Instant.parse(updatedAtValue);
        return new Request(requestId, type, initiator, fromUserId, toUserId, date, start, end, locationId, status, comment, createdAt, updatedAt);
    }

    private void setLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setLong(index, value);
        }
    }

    private Long getLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
