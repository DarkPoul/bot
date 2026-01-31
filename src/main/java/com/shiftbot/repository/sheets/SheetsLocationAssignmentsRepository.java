package com.shiftbot.repository.sheets;

import com.shiftbot.model.LocationAssignment;
import com.shiftbot.repository.LocationAssignmentsRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SheetsLocationAssignmentsRepository implements LocationAssignmentsRepository {
    private static final String RANGE = "location_assignments!A2:E";

    private final SheetsClient sheetsClient;

    public SheetsLocationAssignmentsRepository(SheetsClient sheetsClient) {
        this.sheetsClient = sheetsClient;
    }

    @Override
    public synchronized List<LocationAssignment> findAll() {
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        List<LocationAssignment> assignments = new ArrayList<>();
        if (rows != null) {
            for (List<Object> row : rows) {
                if (row.isEmpty() || get(row, 0).isBlank()) {
                    continue;
                }
                String locationId = get(row, 0);
                long userId = Long.parseLong(get(row, 1));
                boolean primary = Boolean.parseBoolean(get(row, 2));
                String activeFromValue = get(row, 3);
                String activeToValue = get(row, 4);
                assignments.add(new LocationAssignment(locationId, userId, primary,
                        activeFromValue.isEmpty() ? null : LocalDate.parse(activeFromValue),
                        activeToValue.isEmpty() ? null : LocalDate.parse(activeToValue)));
            }
        }
        return assignments;
    }

    @Override
    public synchronized void save(LocationAssignment assignment) {
        sheetsClient.appendRow(RANGE, buildRow(assignment));
    }

    private List<Object> buildRow(LocationAssignment assignment) {
        List<Object> row = new ArrayList<>();
        row.add(assignment.getLocationId());
        row.add(String.valueOf(assignment.getUserId()));
        row.add(String.valueOf(assignment.isPrimary()));
        row.add(assignment.getActiveFrom() != null ? assignment.getActiveFrom().toString() : "");
        row.add(assignment.getActiveTo() != null ? assignment.getActiveTo().toString() : "");
        return row;
    }

    private String get(List<Object> row, int idx) {
        if (row.size() > idx && row.get(idx) != null) {
            return row.get(idx).toString();
        }
        return "";
    }
}
