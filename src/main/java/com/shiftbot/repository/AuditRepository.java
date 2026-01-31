package com.shiftbot.repository;

import com.shiftbot.model.AuditEvent;

public interface AuditRepository {
    void save(AuditEvent event);
}
