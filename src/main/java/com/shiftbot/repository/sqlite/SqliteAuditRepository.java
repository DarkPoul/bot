package com.shiftbot.repository.sqlite;

import com.shiftbot.model.AuditEvent;
import com.shiftbot.repository.AuditRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SqliteAuditRepository implements AuditRepository {
    private final DataSource dataSource;

    public SqliteAuditRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(AuditEvent event) {
        String sql = "INSERT INTO audit_log (event_id, timestamp, actor_user_id, action, entity_type, entity_id, details) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, event.getEventId());
            ps.setString(2, event.getTimestamp().toString());
            ps.setLong(3, event.getActorUserId());
            ps.setString(4, event.getAction());
            ps.setString(5, event.getEntityType());
            ps.setString(6, event.getEntityId());
            ps.setString(7, event.getDetails());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save audit event", e);
        }
    }
}
