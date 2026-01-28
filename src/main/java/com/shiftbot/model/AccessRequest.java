package com.shiftbot.model;

import com.shiftbot.model.enums.AccessRequestStatus;

import java.time.Instant;

public class AccessRequest {
    private String id;
    private long telegramUserId;
    private String username;
    private String fullName;
    private String comment;
    private AccessRequestStatus status;
    private Instant createdAt;
    private Long processedBy;
    private Instant processedAt;

    public AccessRequest() {
    }

    public AccessRequest(String id, long telegramUserId, String username, String fullName, String comment,
                         AccessRequestStatus status, Instant createdAt, Long processedBy, Instant processedAt) {
        this.id = id;
        this.telegramUserId = telegramUserId;
        this.username = username;
        this.fullName = fullName;
        this.comment = comment;
        this.status = status;
        this.createdAt = createdAt;
        this.processedBy = processedBy;
        this.processedAt = processedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getTelegramUserId() {
        return telegramUserId;
    }

    public void setTelegramUserId(long telegramUserId) {
        this.telegramUserId = telegramUserId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public AccessRequestStatus getStatus() {
        return status;
    }

    public void setStatus(AccessRequestStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(Long processedBy) {
        this.processedBy = processedBy;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
