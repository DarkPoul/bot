package com.shiftbot.repository;

import com.shiftbot.model.AccessRequest;
import com.shiftbot.model.enums.AccessRequestStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AccessRequestsRepository {
    private static final String RANGE = "access_requests!A2:I";
    private final SheetsClient sheetsClient;

    public AccessRequestsRepository(SheetsClient sheetsClient) {
        this.sheetsClient = sheetsClient;
    }

    public synchronized List<AccessRequest> findAll() {
        List<AccessRequest> result = new ArrayList<>();
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        if (rows != null) {
            for (List<Object> row : rows) {
                AccessRequest request = mapRow(row);
                if (request != null) {
                    result.add(request);
                }
            }
        }
        return result;
    }

    public Optional<AccessRequest> findById(String requestId) {
        return findAll().stream().filter(r -> r.getId().equals(requestId)).findFirst();
    }

    public synchronized void save(AccessRequest request) {
        if (request.getId() == null) {
            request.setId(UUID.randomUUID().toString());
        }
        sheetsClient.appendRow(RANGE, toRow(request));
    }

    public synchronized void update(AccessRequest request) {
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        if (rows == null || rows.isEmpty()) {
            throw new IllegalStateException("No access requests to update");
        }
        boolean updated = false;
        for (int i = 0; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (!row.isEmpty() && request.getId().equals(row.get(0).toString())) {
                rows.set(i, toRow(request));
                updated = true;
                break;
            }
        }
        if (!updated) {
            throw new IllegalArgumentException("Access request not found: " + request.getId());
        }
        sheetsClient.updateRange(RANGE, rows);
    }

    private AccessRequest mapRow(List<Object> row) {
        if (row.isEmpty() || row.get(0) == null || row.get(0).toString().isBlank()) {
            return null;
        }
        String id = get(row, 0);
        long telegramUserId = Long.parseLong(get(row, 1));
        String username = get(row, 2);
        String fullName = get(row, 3);
        String comment = get(row, 4);
        AccessRequestStatus status = AccessRequestStatus.valueOf(get(row, 5));
        Instant createdAt = get(row, 6).isEmpty() ? null : Instant.parse(get(row, 6));
        Long processedBy = get(row, 7).isEmpty() ? null : Long.parseLong(get(row, 7));
        Instant processedAt = get(row, 8).isEmpty() ? null : Instant.parse(get(row, 8));
        return new AccessRequest(id, telegramUserId, username, fullName, comment, status, createdAt, processedBy, processedAt);
    }

    private List<Object> toRow(AccessRequest request) {
        List<Object> row = new ArrayList<>();
        row.add(request.getId() == null ? UUID.randomUUID().toString() : request.getId());
        row.add(String.valueOf(request.getTelegramUserId()));
        row.add(request.getUsername());
        row.add(request.getFullName());
        row.add(request.getComment());
        row.add(request.getStatus().name());
        row.add(request.getCreatedAt() != null ? request.getCreatedAt().toString() : Instant.now().toString());
        row.add(request.getProcessedBy() != null ? request.getProcessedBy().toString() : "");
        row.add(request.getProcessedAt() != null ? request.getProcessedAt().toString() : "");
        return row;
    }

    private String get(List<Object> row, int idx) {
        if (row.size() > idx && row.get(idx) != null) {
            return row.get(idx).toString();
        }
        return "";
    }
}
