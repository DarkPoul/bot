package com.shiftbot.repository.sheets;

import com.shiftbot.model.AccessRequest;
import com.shiftbot.model.enums.AccessRequestStatus;
import com.shiftbot.repository.AccessRequestsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SheetsAccessRequestsRepository implements AccessRequestsRepository {
    private static final Logger log = LoggerFactory.getLogger(SheetsAccessRequestsRepository.class);
    private static final String RANGE = "access_requests!A2:I";
    private static final String HEADER_RANGE = "access_requests!A1:I1";
    private final SheetsClient sheetsClient;

    public SheetsAccessRequestsRepository(SheetsClient sheetsClient) {
        this.sheetsClient = sheetsClient;
    }

    @Override
    public synchronized List<AccessRequest> findAll() {
        List<AccessRequest> result = new ArrayList<>();
        Map<String, Integer> headerIndexes = readHeaderIndexes();
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        if (rows != null) {
            for (List<Object> row : rows) {
                AccessRequest request = mapRow(row, headerIndexes);
                if (request != null) {
                    result.add(request);
                }
            }
        }
        return result;
    }

    @Override
    public Optional<AccessRequest> findById(String requestId) {
        return findAll().stream().filter(r -> r.getId().equals(requestId)).findFirst();
    }

    @Override
    public synchronized void save(AccessRequest request) {
        if (request.getId() == null) {
            request.setId(UUID.randomUUID().toString());
        }
        sheetsClient.appendRow(RANGE, toRow(request));
    }

    @Override
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

    private AccessRequest mapRow(List<Object> row, Map<String, Integer> headerIndexes) {
        if (row.isEmpty() || row.get(0) == null || row.get(0).toString().isBlank()) {
            return null;
        }
        int idIndex = resolveIndex(headerIndexes, "id", 0);
        int telegramUserIdIndex = resolveIndex(headerIndexes, "telegramuserid", 1);
        int usernameIndex = resolveIndex(headerIndexes, "username", 2);
        int fullNameIndex = resolveIndex(headerIndexes, "fullname", 3);
        int commentIndex = resolveIndex(headerIndexes, "comment", 4);
        int statusIndex = resolveIndex(headerIndexes, "status", 5);
        int createdAtIndex = resolveIndex(headerIndexes, "createdat", 6);
        int processedByIndex = resolveIndex(headerIndexes, "processedby", 7);
        int processedAtIndex = resolveIndex(headerIndexes, "processedat", 8);
        try {
            String id = get(row, idIndex);
            long telegramUserId = Long.parseLong(get(row, telegramUserIdIndex));
            String username = get(row, usernameIndex);
            String fullName = get(row, fullNameIndex);
            String comment = get(row, commentIndex);
            String statusValue = get(row, statusIndex);
            AccessRequestStatus status = AccessRequestStatus.valueOf(statusValue);
            String createdAtValue = get(row, createdAtIndex);
            Instant createdAt = createdAtValue.isEmpty() ? null : Instant.parse(createdAtValue);
            String processedByValue = get(row, processedByIndex);
            Long processedBy = processedByValue.isEmpty() ? null : Long.parseLong(processedByValue);
            String processedAtValue = get(row, processedAtIndex);
            Instant processedAt = processedAtValue.isEmpty() ? null : Instant.parse(processedAtValue);
            return new AccessRequest(id, telegramUserId, username, fullName, comment, status, createdAt, processedBy, processedAt);
        } catch (IllegalArgumentException e) {
            log.warn("Skip access request row with invalid status: {}", row, e);
            return null;
        } catch (Exception e) {
            log.warn("Skip invalid access request row: {}", row, e);
            return null;
        }
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

    private Map<String, Integer> readHeaderIndexes() {
        List<List<Object>> rows = sheetsClient.readRange(HEADER_RANGE);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        List<Object> headerRow = rows.get(0);
        Map<String, Integer> indexByHeader = new HashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String header = normalizeHeader(headerRow.get(i));
            if (!header.isEmpty()) {
                indexByHeader.put(header, i);
            }
        }
        return indexByHeader;
    }

    private int resolveIndex(Map<String, Integer> headerIndexes, String normalizedHeader, int defaultIndex) {
        return headerIndexes.getOrDefault(normalizedHeader, defaultIndex);
    }

    private String normalizeHeader(Object headerValue) {
        if (headerValue == null) {
            return "";
        }
        return headerValue.toString()
                .trim()
                .toLowerCase()
                .replace(" ", "")
                .replace("_", "");
    }
}
