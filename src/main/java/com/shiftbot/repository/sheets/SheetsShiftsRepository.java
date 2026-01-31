package com.shiftbot.repository.sheets;

import com.shiftbot.model.Shift;
import com.shiftbot.model.enums.ShiftSource;
import com.shiftbot.model.enums.ShiftStatus;
import com.shiftbot.repository.ShiftsRepository;
import com.shiftbot.util.TimeUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SheetsShiftsRepository implements ShiftsRepository {
    private static final String RANGE = "shifts!A2:J";
    private final SheetsClient sheetsClient;

    public SheetsShiftsRepository(SheetsClient sheetsClient) {
        this.sheetsClient = sheetsClient;
    }

    @Override
    public List<Shift> findAll() {
        List<Shift> result = new ArrayList<>();
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        if (rows != null) {
            for (List<Object> row : rows) {
                Shift shift = mapRow(row);
                if (shift != null) {
                    result.add(shift);
                }
            }
        }
        return result;
    }

    @Override
    public List<Shift> findByUser(long userId) {
        List<Shift> result = new ArrayList<>();
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        if (rows != null) {
            for (List<Object> row : rows) {
                Shift shift = mapRow(row);
                if (shift != null && shift.getUserId() == userId) {
                    result.add(shift);
                }
            }
        }
        return result;
    }

    @Override
    public List<Shift> findByLocation(String locationId) {
        List<Shift> result = new ArrayList<>();
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        if (rows != null) {
            for (List<Object> row : rows) {
                Shift shift = mapRow(row);
                if (shift != null && locationId.equals(shift.getLocationId())) {
                    result.add(shift);
                }
            }
        }
        return result;
    }

    @Override
    public void save(Shift shift) {
        if (shift.getShiftId() == null) {
            shift.setShiftId(UUID.randomUUID().toString());
        }
        List<Object> row = new ArrayList<>();
        row.add(shift.getShiftId());
        row.add(shift.getDate().toString());
        row.add(shift.getStartTime().toString());
        row.add(shift.getEndTime().toString());
        row.add(shift.getLocationId());
        row.add(String.valueOf(shift.getUserId()));
        row.add(shift.getStatus().name());
        row.add(shift.getSource().name());
        row.add(shift.getLinkedRequestId());
        row.add(shift.getUpdatedAt() != null ? shift.getUpdatedAt().toString() : Instant.now().toString());
        sheetsClient.appendRow(RANGE, row);
    }

    @Override
    public void updateStatusAndLink(String shiftId, ShiftStatus status, String linkedRequestId) {
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        List<List<Object>> updatedRows = new ArrayList<>();
        boolean replaced = false;
        if (rows != null) {
            for (List<Object> row : rows) {
                Shift shift = mapRow(row);
                if (shift != null && shift.getShiftId().equals(shiftId)) {
                    ensureSize(row, 10);
                    row.set(6, status.name());
                    row.set(8, linkedRequestId);
                    row.set(9, Instant.now().toString());
                    replaced = true;
                }
                updatedRows.add(row);
            }
        }
        if (replaced) {
            sheetsClient.updateRange(RANGE, updatedRows);
        } else {
            throw new IllegalArgumentException("Shift not found: " + shiftId);
        }
    }

    @Override
    public Optional<Shift> findByUserAndSlot(long userId, LocalDate date, LocalTime startTime, LocalTime endTime, String locationId) {
        return findByUser(userId).stream()
                .filter(s -> s.getDate().equals(date)
                        && s.getStartTime().equals(startTime)
                        && s.getEndTime().equals(endTime)
                        && s.getLocationId().equals(locationId))
                .findFirst();
    }

    private Shift mapRow(List<Object> row) {
        if (row.isEmpty() || row.get(0) == null || row.get(0).toString().isBlank()) {
            return null;
        }
        String shiftId = row.get(0).toString();
        LocalDate date = LocalDate.parse(get(row, 1));
        LocalTime start = LocalTime.parse(get(row, 2));
        LocalTime end = LocalTime.parse(get(row, 3));
        String locationId = get(row, 4);
        long userId = Long.parseLong(get(row, 5));
        ShiftStatus status = ShiftStatus.valueOf(get(row, 6));
        ShiftSource source = ShiftSource.valueOf(get(row, 7));
        String linkedRequestId = get(row, 8).isEmpty() ? null : get(row, 8);
        Instant updatedAt = get(row, 9).isEmpty() ? TimeUtils.nowInstant(ZoneId.of("Europe/Kyiv")) : Instant.parse(get(row, 9));
        return new Shift(shiftId, date, start, end, locationId, userId, status, source, linkedRequestId, updatedAt);
    }

    private String get(List<Object> row, int idx) {
        if (row.size() > idx && row.get(idx) != null) {
            return row.get(idx).toString();
        }
        return "";
    }

    private void ensureSize(List<Object> row, int size) {
        while (row.size() < size) {
            row.add("");
        }
    }
}
