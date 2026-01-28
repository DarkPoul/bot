package com.shiftbot.repository;

import com.shiftbot.model.LocationAssignment;

import java.util.ArrayList;
import java.util.List;

public class LocationAssignmentsRepository {
    private static final String RANGE = "location_assignments!A2:E";

    private final SheetsClient sheetsClient;

    public LocationAssignmentsRepository(SheetsClient sheetsClient) {
        this.sheetsClient = sheetsClient;
    }

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
}
