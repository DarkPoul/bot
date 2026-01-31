package com.shiftbot.repository.sqlite;

import com.shiftbot.model.LocationAssignment;
import com.shiftbot.repository.LocationAssignmentsRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SqliteLocationAssignmentsRepository implements LocationAssignmentsRepository {
    private final DataSource dataSource;

    public SqliteLocationAssignmentsRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<LocationAssignment> findAll() {
        String sql = "SELECT location_id, user_id, is_primary, active_from, active_to FROM location_assignments";
        List<LocationAssignment> assignments = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String activeFromValue = rs.getString("active_from");
                String activeToValue = rs.getString("active_to");
                assignments.add(new LocationAssignment(
                        rs.getString("location_id"),
                        rs.getLong("user_id"),
                        rs.getInt("is_primary") == 1,
                        activeFromValue == null ? null : LocalDate.parse(activeFromValue),
                        activeToValue == null ? null : LocalDate.parse(activeToValue)
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load location assignments", e);
        }
        return assignments;
    }

    @Override
    public void save(LocationAssignment assignment) {
        String sql = "INSERT INTO location_assignments (location_id, user_id, is_primary, active_from, active_to) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, assignment.getLocationId());
            ps.setLong(2, assignment.getUserId());
            ps.setInt(3, assignment.isPrimary() ? 1 : 0);
            ps.setString(4, assignment.getActiveFrom() != null ? assignment.getActiveFrom().toString() : null);
            ps.setString(5, assignment.getActiveTo() != null ? assignment.getActiveTo().toString() : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save location assignment", e);
        }
    }
}
