package com.shiftbot.repository;

import com.shiftbot.model.SubstitutionRequest;
import com.shiftbot.model.enums.SubstitutionReasonCode;
import com.shiftbot.model.enums.SubstitutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SubstitutionRequestsRepository {
    private static final Logger log = LoggerFactory.getLogger(SubstitutionRequestsRepository.class);
    private static final String SHEET_NAME = "substitution_requests";
    private static final String RANGE = SHEET_NAME + "!A2:K";
    private static final String HEADER_RANGE = SHEET_NAME + "!A1:K1";
    private static final List<String> EXPECTED_HEADERS = List.of(
            "id",
            "createdAt",
            "sellerTelegramId",
            "sellerName",
            "location",
            "shiftDate",
            "reasonCode",
            "reasonText",
            "status",
            "processedBy",
            "processedAt"
    );
    private final SheetsClient sheetsClient;

    public SubstitutionRequestsRepository(SheetsClient sheetsClient) {
        this.sheetsClient = sheetsClient;
    }

    public synchronized List<SubstitutionRequest> findAll() {
        List<SubstitutionRequest> result = new ArrayList<>();
        Map<String, Integer> headerIndexes = readHeaderIndexes();
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        if (rows != null) {
            for (List<Object> row : rows) {
                SubstitutionRequest request = mapRow(row, headerIndexes);
                if (request != null) {
                    result.add(request);
                }
            }
        }
        return result;
    }

    public Optional<SubstitutionRequest> findById(String requestId) {
        return findAll().stream().filter(r -> r.getId().equals(requestId)).findFirst();
    }

    public synchronized void save(SubstitutionRequest request) {
        if (request.getId() == null) {
            request.setId(UUID.randomUUID().toString());
        }
        sheetsClient.appendRow(RANGE, toRow(request));
    }

    public synchronized void update(SubstitutionRequest request) {
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        if (rows == null || rows.isEmpty()) {
            throw new IllegalStateException("No substitution requests to update");
        }
        Map<String, Integer> headerIndexes = readHeaderIndexes();
        int idIndex = resolveIndex(headerIndexes, "id", 0);
        for (int i = 0; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (!row.isEmpty() && request.getId().equals(get(row, idIndex))) {
                int sheetRowNumber = i + 2;
                List<Object> updatedRow = toRow(request);
                String endColumn = SheetsClient.columnIndexToLetter(updatedRow.size());
                sheetsClient.updateRange(SHEET_NAME + "!A" + sheetRowNumber + ":" + endColumn + sheetRowNumber,
                        List.of(updatedRow));
                return;
            }
        }
        throw new IllegalArgumentException("Substitution request not found: " + request.getId());
    }

    private SubstitutionRequest mapRow(List<Object> row, Map<String, Integer> headerIndexes) {
        if (row.isEmpty() || get(row, 0).isBlank()) {
            return null;
        }
        int idIndex = resolveIndex(headerIndexes, "id", 0);
        int createdAtIndex = resolveIndex(headerIndexes, "createdat", 1);
        int sellerTelegramIdIndex = resolveIndex(headerIndexes, "sellertelegramid", 2);
        int sellerNameIndex = resolveIndex(headerIndexes, "sellername", 3);
        int locationIndex = resolveIndex(headerIndexes, "location", 4);
        int shiftDateIndex = resolveIndex(headerIndexes, "shiftdate", 5);
        int reasonCodeIndex = resolveIndex(headerIndexes, "reasoncode", 6);
        int reasonTextIndex = resolveIndex(headerIndexes, "reasontext", 7);
        int statusIndex = resolveIndex(headerIndexes, "status", 8);
        int processedByIndex = resolveIndex(headerIndexes, "processedby", 9);
        int processedAtIndex = resolveIndex(headerIndexes, "processedat", 10);
        try {
            String id = get(row, idIndex);
            String createdAtValue = get(row, createdAtIndex);
            Instant createdAt = createdAtValue.isEmpty() ? null : Instant.parse(createdAtValue);
            long sellerTelegramId = Long.parseLong(get(row, sellerTelegramIdIndex));
            String sellerName = get(row, sellerNameIndex);
            String location = get(row, locationIndex);
            LocalDate shiftDate = LocalDate.parse(get(row, shiftDateIndex));
            String reasonCodeValue = get(row, reasonCodeIndex);
            SubstitutionReasonCode reasonCode = reasonCodeValue.isEmpty()
                    ? null
                    : SubstitutionReasonCode.valueOf(reasonCodeValue);
            String reasonText = get(row, reasonTextIndex);
            String statusValue = get(row, statusIndex);
            SubstitutionStatus status = SubstitutionStatus.valueOf(statusValue);
            String processedByValue = get(row, processedByIndex);
            Long processedBy = processedByValue.isEmpty() ? null : Long.parseLong(processedByValue);
            String processedAtValue = get(row, processedAtIndex);
            Instant processedAt = processedAtValue.isEmpty() ? null : Instant.parse(processedAtValue);
            return new SubstitutionRequest(id, createdAt, sellerTelegramId, sellerName, location, shiftDate,
                    reasonCode, reasonText, status, processedBy, processedAt);
        } catch (IllegalArgumentException e) {
            log.warn("Skip substitution request row with invalid enum: {}", row, e);
            return null;
        } catch (Exception e) {
            log.warn("Skip invalid substitution request row: {}", row, e);
            return null;
        }
    }

    private List<Object> toRow(SubstitutionRequest request) {
        int columnCount = EXPECTED_HEADERS.size();
        List<Object> row = new ArrayList<>();
        for (int i = 0; i < columnCount; i++) {
            row.add("");
        }
        put(row, "id", request.getId() == null ? UUID.randomUUID().toString() : request.getId());
        put(row, "createdAt", request.getCreatedAt() != null ? request.getCreatedAt().toString() : Instant.now().toString());
        put(row, "sellerTelegramId", String.valueOf(request.getSellerTelegramId()));
        put(row, "sellerName", request.getSellerName());
        put(row, "location", request.getLocation());
        put(row, "shiftDate", request.getShiftDate() != null ? request.getShiftDate().toString() : "");
        put(row, "reasonCode", request.getReasonCode() != null ? request.getReasonCode().name() : "");
        put(row, "reasonText", request.getReasonText());
        put(row, "status", request.getStatus() != null ? request.getStatus().name() : "");
        put(row, "processedBy", request.getProcessedBy() != null ? request.getProcessedBy().toString() : "");
        put(row, "processedAt", request.getProcessedAt() != null ? request.getProcessedAt().toString() : "");
        return row;
    }

    private void put(List<Object> row, String header, String value) {
        int index = EXPECTED_HEADERS.indexOf(header);
        if (index >= 0 && index < row.size()) {
            row.set(index, value == null ? "" : value);
        }
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
