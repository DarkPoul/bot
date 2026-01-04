package com.shiftbot.repository;

import com.shiftbot.model.Request;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class RequestsRepository {
    private static final String RANGE = "requests!A2:N";
    private final SheetsClient sheetsClient;

    public RequestsRepository(SheetsClient sheetsClient) {
        this.sheetsClient = sheetsClient;
    }

    public List<Request> findAll() {
        List<Request> result = new ArrayList<>();
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        if (rows != null) {
            for (List<Object> row : rows) {
                Request request = mapRow(row);
                if (request != null) {
                    result.add(request);
                }
            }
        }
        return result;
    }

    public Optional<Request> findById(String requestId) {
        return findAll().stream()
                .filter(r -> r.getRequestId().equals(requestId))
                .findFirst();
    }

    public void save(Request request) {
        if (request.getRequestId() == null) {
            request.setRequestId(UUID.randomUUID().toString());
        }
        List<Object> row = buildRow(request);
        sheetsClient.appendRow(RANGE, row);
    }

    public void update(Request request) {
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        List<List<Object>> updatedRows = new ArrayList<>();
        boolean replaced = false;
        if (rows != null) {
            for (List<Object> row : rows) {
                Request existing = mapRow(row);
                if (existing != null && existing.getRequestId().equals(request.getRequestId())) {
                    updatedRows.add(buildRow(request));
                    replaced = true;
                } else {
                    updatedRows.add(row);
                }
            }
        }
        if (replaced) {
            sheetsClient.updateRange(RANGE, updatedRows);
        } else {
            throw new IllegalArgumentException("Request not found: " + request.getRequestId());
        }
    }

    private List<Object> buildRow(Request request) {
        List<Object> row = new ArrayList<>();
        row.add(request.getRequestId());
        row.add(request.getType().name());
        row.add(String.valueOf(request.getInitiatorUserId()));
        row.add(request.getFromUserId());
        row.add(request.getToUserId());
        row.add(request.getDate().toString());
        row.add(request.getStartTime().toString());
        row.add(request.getEndTime().toString());
        row.add(request.getLocationId());
        row.add(request.getStatus().name());
        row.add(request.getComment());
        row.add(request.getCreatedAt() != null ? request.getCreatedAt().toString() : Instant.now().toString());
        row.add(request.getUpdatedAt() != null ? request.getUpdatedAt().toString() : Instant.now().toString());
        return row;
    }

    private Request mapRow(List<Object> row) {
        if (row.isEmpty() || row.get(0) == null || row.get(0).toString().isBlank()) {
            return null;
        }
        String requestId = row.get(0).toString();
        RequestType type = RequestType.valueOf(get(row, 1));
        long initiator = Long.parseLong(get(row, 2));
        Long fromUserId = get(row, 3).isEmpty() ? null : Long.parseLong(get(row, 3));
        Long toUserId = get(row, 4).isEmpty() ? null : Long.parseLong(get(row, 4));
        LocalDate date = LocalDate.parse(get(row, 5));
        LocalTime start = LocalTime.parse(get(row, 6));
        LocalTime end = LocalTime.parse(get(row, 7));
        String locationId = get(row, 8);
        RequestStatus status = RequestStatus.valueOf(get(row, 9));
        String comment = get(row, 10);
        Instant createdAt = get(row, 11).isEmpty() ? null : Instant.parse(get(row, 11));
        Instant updatedAt = get(row, 12).isEmpty() ? null : Instant.parse(get(row, 12));
        return new Request(requestId, type, initiator, fromUserId, toUserId, date, start, end, locationId, status, comment, createdAt, updatedAt);
    }

    private String get(List<Object> row, int idx) {
        if (row.size() > idx && row.get(idx) != null) {
            return row.get(idx).toString();
        }
        return "";
    }
}
