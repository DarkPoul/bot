package com.shiftbot.repository.sqlite;

import com.shiftbot.model.SubstitutionRequest;
import com.shiftbot.model.enums.SubstitutionReasonCode;
import com.shiftbot.model.enums.SubstitutionStatus;
import com.shiftbot.repository.SubstitutionRequestsRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteSubstitutionRequestsRepository implements SubstitutionRequestsRepository {
    private final DataSource dataSource;

    public SqliteSubstitutionRequestsRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<SubstitutionRequest> findAll() {
        String sql = "SELECT id, created_at, seller_telegram_id, seller_name, location, shift_date, reason_code, reason_text, status, processed_by, processed_at FROM substitution_requests";
        List<SubstitutionRequest> requests = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                requests.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load substitution requests", e);
        }
        return requests;
    }

    @Override
    public Optional<SubstitutionRequest> findById(String requestId) {
        String sql = "SELECT id, created_at, seller_telegram_id, seller_name, location, shift_date, reason_code, reason_text, status, processed_by, processed_at FROM substitution_requests WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load substitution request", e);
        }
        return Optional.empty();
    }

    @Override
    public void save(SubstitutionRequest request) {
        if (request.getId() == null) {
            request.setId(java.util.UUID.randomUUID().toString());
        }
        String sql = "INSERT INTO substitution_requests (id, created_at, seller_telegram_id, seller_name, location, shift_date, reason_code, reason_text, status, processed_by, processed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET created_at = excluded.created_at, seller_telegram_id = excluded.seller_telegram_id, " +
                "seller_name = excluded.seller_name, location = excluded.location, shift_date = excluded.shift_date, reason_code = excluded.reason_code, " +
                "reason_text = excluded.reason_text, status = excluded.status, processed_by = excluded.processed_by, processed_at = excluded.processed_at";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, request);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save substitution request", e);
        }
    }

    @Override
    public void update(SubstitutionRequest request) {
        String sql = "UPDATE substitution_requests SET created_at = ?, seller_telegram_id = ?, seller_name = ?, location = ?, shift_date = ?, reason_code = ?, " +
                "reason_text = ?, status = ?, processed_by = ?, processed_at = ? WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, request.getCreatedAt() != null ? request.getCreatedAt().toString() : null);
            ps.setLong(2, request.getSellerTelegramId());
            ps.setString(3, request.getSellerName());
            ps.setString(4, request.getLocation());
            ps.setString(5, request.getShiftDate() != null ? request.getShiftDate().toString() : null);
            ps.setString(6, request.getReasonCode() != null ? request.getReasonCode().name() : null);
            ps.setString(7, request.getReasonText());
            ps.setString(8, request.getStatus().name());
            setLong(ps, 9, request.getProcessedBy());
            ps.setString(10, request.getProcessedAt() != null ? request.getProcessedAt().toString() : null);
            ps.setString(11, request.getId());
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("Substitution request not found: " + request.getId());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update substitution request", e);
        }
    }

    private void bind(PreparedStatement ps, SubstitutionRequest request) throws SQLException {
        ps.setString(1, request.getId());
        ps.setString(2, request.getCreatedAt() != null ? request.getCreatedAt().toString() : Instant.now().toString());
        ps.setLong(3, request.getSellerTelegramId());
        ps.setString(4, request.getSellerName());
        ps.setString(5, request.getLocation());
        ps.setString(6, request.getShiftDate() != null ? request.getShiftDate().toString() : null);
        ps.setString(7, request.getReasonCode() != null ? request.getReasonCode().name() : null);
        ps.setString(8, request.getReasonText());
        ps.setString(9, request.getStatus().name());
        setLong(ps, 10, request.getProcessedBy());
        ps.setString(11, request.getProcessedAt() != null ? request.getProcessedAt().toString() : null);
    }

    private SubstitutionRequest mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String createdAtValue = rs.getString("created_at");
        Instant createdAt = createdAtValue == null ? null : Instant.parse(createdAtValue);
        long sellerTelegramId = rs.getLong("seller_telegram_id");
        String sellerName = rs.getString("seller_name");
        String location = rs.getString("location");
        String shiftDateValue = rs.getString("shift_date");
        LocalDate shiftDate = shiftDateValue == null ? null : LocalDate.parse(shiftDateValue);
        String reasonCodeValue = rs.getString("reason_code");
        SubstitutionReasonCode reasonCode = reasonCodeValue == null ? null : SubstitutionReasonCode.valueOf(reasonCodeValue);
        String reasonText = rs.getString("reason_text");
        SubstitutionStatus status = SubstitutionStatus.valueOf(rs.getString("status"));
        long processedByValue = rs.getLong("processed_by");
        Long processedBy = rs.wasNull() ? null : processedByValue;
        String processedAtValue = rs.getString("processed_at");
        Instant processedAt = processedAtValue == null ? null : Instant.parse(processedAtValue);
        return new SubstitutionRequest(id, createdAt, sellerTelegramId, sellerName, location, shiftDate, reasonCode, reasonText, status, processedBy, processedAt);
    }

    private void setLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setLong(index, value);
        }
    }
}
