package com.shiftbot.repository.sheets;

import com.shiftbot.model.Request;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import com.shiftbot.repository.RequestsRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class SheetsRequestsRepository implements RequestsRepository {
    private static final String RANGE = "requests!A2:N";
    private final SheetsClient sheetsClient;
    private Map<String, Integer> rowIndexCache;

    public SheetsRequestsRepository(SheetsClient sheetsClient) {
        this.sheetsClient = sheetsClient;
    }

    @Override
    public synchronized List<Request> findAll() {
        List<Request> result = new ArrayList<>();
        rowIndexCache = new HashMap<>();
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                List<Object> row = rows.get(i);
                Request request = mapRow(row);
                if (request != null) {
                    result.add(request);
                    rowIndexCache.put(request.getRequestId(), i);
                }
            }
        }
        return result;
    }

    @Override
    public Optional<Request> findById(String requestId) {
        return findAll().stream().filter(r -> r.getRequestId().equals(requestId)).findFirst();
    }

    @Override
    public synchronized void save(Request request) {
        if (request.getRequestId() == null) {
            request.setRequestId(UUID.randomUUID().toString());
        }
        sheetsClient.appendRow(RANGE, toRow(request));
    }

    @Override
    public synchronized void update(Request request) {
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        if (rows == null || rows.isEmpty()) {
            throw new IllegalStateException("No requests to update");
        }
        boolean updated = false;
        for (int i = 0; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (!row.isEmpty() && request.getRequestId().equals(row.get(0).toString())) {
                rows.set(i, toRow(request));
                updated = true;
                break;
            }
        }
        if (!updated) {
            throw new IllegalArgumentException("Request not found: " + request.getRequestId());
        }
        sheetsClient.updateRange(RANGE, rows);
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

    private List<Object> toRow(Request request) {
        List<Object> row = new ArrayList<>();
        row.add(request.getRequestId() == null ? UUID.randomUUID().toString() : request.getRequestId());
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

    private String get(List<Object> row, int idx) {
        if (row.size() > idx && row.get(idx) != null) {
            return row.get(idx).toString();
        }
        return "";
    }
}
