package com.shiftbot.repository.sqlite;

import com.shiftbot.model.User;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import com.shiftbot.repository.UsersRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteUsersRepository implements UsersRepository {
    private final DataSource dataSource;

    public SqliteUsersRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT user_id, username, full_name, location_id, phone, role, status, created_at, created_by FROM users";
        List<User> users = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load users", e);
        }
        return users;
    }

    @Override
    public Optional<User> findById(long userId) {
        String sql = "SELECT user_id, username, full_name, location_id, phone, role, status, created_at, created_by FROM users WHERE user_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load user", e);
        }
        return Optional.empty();
    }

    @Override
    public void save(User user) {
        String sql = "INSERT INTO users (user_id, username, full_name, location_id, phone, role, status, created_at, created_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(user_id) DO UPDATE SET " +
                "username = excluded.username, full_name = excluded.full_name, location_id = excluded.location_id, " +
                "phone = excluded.phone, role = excluded.role, status = excluded.status, created_at = excluded.created_at, " +
                "created_by = excluded.created_by";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bindUser(ps, user);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save user", e);
        }
    }

    @Override
    public void update(User user) {
        String sql = "UPDATE users SET username = ?, full_name = ?, location_id = ?, phone = ?, role = ?, status = ?, " +
                "created_at = ?, created_by = ? WHERE user_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getFullName());
            ps.setString(3, user.getLocationId());
            ps.setString(4, user.getPhone());
            ps.setString(5, user.getRole().name());
            ps.setString(6, user.getStatus().name());
            ps.setString(7, user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
            if (user.getCreatedBy() == null) {
                ps.setObject(8, null);
            } else {
                ps.setLong(8, user.getCreatedBy());
            }
            ps.setLong(9, user.getUserId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update user", e);
        }
    }

    private void bindUser(PreparedStatement ps, User user) throws SQLException {
        ps.setLong(1, user.getUserId());
        ps.setString(2, user.getUsername());
        ps.setString(3, user.getFullName());
        ps.setString(4, user.getLocationId());
        ps.setString(5, user.getPhone());
        ps.setString(6, user.getRole().name());
        ps.setString(7, user.getStatus().name());
        ps.setString(8, user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        if (user.getCreatedBy() == null) {
            ps.setObject(9, null);
        } else {
            ps.setLong(9, user.getCreatedBy());
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        long userId = rs.getLong("user_id");
        String username = rs.getString("username");
        String fullName = rs.getString("full_name");
        String locationId = rs.getString("location_id");
        String phone = rs.getString("phone");
        Role role = Role.valueOf(rs.getString("role"));
        UserStatus status = UserStatus.valueOf(rs.getString("status"));
        String createdAtValue = rs.getString("created_at");
        Instant createdAt = createdAtValue == null ? null : Instant.parse(createdAtValue);
        long createdByValue = rs.getLong("created_by");
        Long createdBy = rs.wasNull() ? null : createdByValue;
        return new User(userId, username, fullName, locationId, phone, role, status, createdAt, createdBy);
    }
}
