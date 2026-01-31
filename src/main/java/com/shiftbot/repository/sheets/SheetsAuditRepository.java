package com.shiftbot.repository.sheets;

import com.shiftbot.model.AuditEvent;
import com.shiftbot.repository.AuditRepository;

import java.util.ArrayList;
import java.util.List;

public class SheetsAuditRepository implements AuditRepository {
    private static final String RANGE = "audit_log!A2:G";
    private final SheetsClient sheetsClient;

    public SheetsAuditRepository(SheetsClient sheetsClient) {
        this.sheetsClient = sheetsClient;
    }

    @Override
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
