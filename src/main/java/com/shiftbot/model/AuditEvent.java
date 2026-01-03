package com.shiftbot.model;

import java.time.Instant;
import java.util.UUID;

public class AuditEvent {
    private String eventId;
    private Instant timestamp;
    private long actorUserId;
    private String action;
    private String entityType;
    private String entityId;
    private String details;

    public AuditEvent() {
        this.eventId = UUID.randomUUID().toString();
    }

    public AuditEvent(String eventId, Instant timestamp, long actorUserId, String action, String entityType, String entityId, String details) {
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.actorUserId = actorUserId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public long getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(long actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
