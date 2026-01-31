package com.shiftbot.repository.sqlite;

import com.shiftbot.model.AccessRequest;
import com.shiftbot.model.enums.AccessRequestStatus;
import com.shiftbot.repository.AccessRequestsRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteAccessRequestsRepository implements AccessRequestsRepository {
    private final DataSource dataSource;

    public SqliteAccessRequestsRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<AccessRequest> findAll() {
        String sql = "SELECT id, telegram_user_id, username, full_name, comment, status, created_at, processed_by, processed_at FROM access_requests";
        List<AccessRequest> requests = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                requests.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load access requests", e);
        }
        return requests;
    }

    @Override
    public Optional<AccessRequest> findById(String requestId) {
        String sql = "SELECT id, telegram_user_id, username, full_name, comment, status, created_at, processed_by, processed_at FROM access_requests WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load access request", e);
        }
        return Optional.empty();
    }

    @Override
    public void save(AccessRequest request) {
        if (request.getId() == null) {
            request.setId(java.util.UUID.randomUUID().toString());
        }
        String sql = "INSERT INTO access_requests (id, telegram_user_id, username, full_name, comment, status, created_at, processed_by, processed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET telegram_user_id = excluded.telegram_user_id, username = excluded.username, " +
                "full_name = excluded.full_name, comment = excluded.comment, status = excluded.status, created_at = excluded.created_at, " +
                "processed_by = excluded.processed_by, processed_at = excluded.processed_at";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, request);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save access request", e);
        }
    }

    @Override
    public void update(AccessRequest request) {
        String sql = "UPDATE access_requests SET telegram_user_id = ?, username = ?, full_name = ?, comment = ?, status = ?, created_at = ?, " +
                "processed_by = ?, processed_at = ? WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, request.getTelegramUserId());
            ps.setString(2, request.getUsername());
            ps.setString(3, request.getFullName());
            ps.setString(4, request.getComment());
            ps.setString(5, request.getStatus().name());
            ps.setString(6, request.getCreatedAt() != null ? request.getCreatedAt().toString() : null);
            setLong(ps, 7, request.getProcessedBy());
            ps.setString(8, request.getProcessedAt() != null ? request.getProcessedAt().toString() : null);
            ps.setString(9, request.getId());
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("Access request not found: " + request.getId());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update access request", e);
        }
    }

    private void bind(PreparedStatement ps, AccessRequest request) throws SQLException {
        ps.setString(1, request.getId());
        ps.setLong(2, request.getTelegramUserId());
        ps.setString(3, request.getUsername());
        ps.setString(4, request.getFullName());
        ps.setString(5, request.getComment());
        ps.setString(6, request.getStatus().name());
        ps.setString(7, request.getCreatedAt() != null ? request.getCreatedAt().toString() : Instant.now().toString());
        setLong(ps, 8, request.getProcessedBy());
        ps.setString(9, request.getProcessedAt() != null ? request.getProcessedAt().toString() : null);
    }

    private AccessRequest mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        long telegramUserId = rs.getLong("telegram_user_id");
        String username = rs.getString("username");
        String fullName = rs.getString("full_name");
        String comment = rs.getString("comment");
        AccessRequestStatus status = AccessRequestStatus.valueOf(rs.getString("status"));
        String createdAtValue = rs.getString("created_at");
        Instant createdAt = createdAtValue == null ? null : Instant.parse(createdAtValue);
        long processedByValue = rs.getLong("processed_by");
        Long processedBy = rs.wasNull() ? null : processedByValue;
        String processedAtValue = rs.getString("processed_at");
        Instant processedAt = processedAtValue == null ? null : Instant.parse(processedAtValue);
        return new AccessRequest(id, telegramUserId, username, fullName, comment, status, createdAt, processedBy, processedAt);
    }

    private void setLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setLong(index, value);
        }
    }
}
