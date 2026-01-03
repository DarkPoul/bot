package com.shiftbot.repository;

import com.shiftbot.model.AuditEvent;

import java.util.ArrayList;
import java.util.List;

public class AuditRepository {
    private static final String RANGE = "audit_log!A2:G";
    private final SheetsClient sheetsClient;

    public AuditRepository(SheetsClient sheetsClient) {
        this.sheetsClient = sheetsClient;
    }

    public void save(AuditEvent event) {
        List<Object> row = new ArrayList<>();
        row.add(event.getEventId());
        row.add(event.getTimestamp().toString());
        row.add(String.valueOf(event.getActorUserId()));
        row.add(event.getAction());
        row.add(event.getEntityType());
        row.add(event.getEntityId());
        row.add(event.getDetails());
        sheetsClient.appendRow(RANGE, row);
    }
}
