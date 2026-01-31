package com.shiftbot.repository.sqlite;

import com.shiftbot.model.Location;
import com.shiftbot.repository.LocationsRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteLocationsRepository implements LocationsRepository {
    private final DataSource dataSource;

    public SqliteLocationsRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Location> findAll() {
        String sql = "SELECT id, name, address, active FROM locations";
        List<Location> locations = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                locations.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load locations", e);
        }
        return locations;
    }

    @Override
    public List<Location> findActive() {
        String sql = "SELECT id, name, address, active FROM locations WHERE active = 1";
        List<Location> locations = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                locations.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load active locations", e);
        }
        return locations;
    }

    @Override
    public Optional<Location> findById(String id) {
        String sql = "SELECT id, name, address, active FROM locations WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load location", e);
        }
        return Optional.empty();
    }

    public void upsert(Location location) {
        String sql = "INSERT INTO locations (id, name, address, active) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET name = excluded.name, address = excluded.address, active = excluded.active";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, location.getLocationId());
            ps.setString(2, location.getName());
            ps.setString(3, location.getAddress());
            ps.setInt(4, location.isActive() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save location", e);
        }
    }

    private Location mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("name");
        String address = rs.getString("address");
        boolean active = rs.getInt("active") == 1;
        return new Location(id, name, address, active);
    }
}
