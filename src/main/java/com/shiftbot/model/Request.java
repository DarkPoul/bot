package com.shiftbot.model;

import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public class Request {
    private String requestId;
    private RequestType type;
    private long initiatorUserId;
    private Long fromUserId;
    private Long toUserId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private String locationId;
    private RequestStatus status;
    private String comment;
    private Instant createdAt;
    private Instant updatedAt;

    public Request() {
        this.requestId = UUID.randomUUID().toString();
    }

    public Request(String requestId, RequestType type, long initiatorUserId, Long fromUserId, Long toUserId, LocalDate date,
                   LocalTime startTime, LocalTime endTime, String locationId, RequestStatus status, String comment,
                   Instant createdAt, Instant updatedAt) {
        this.requestId = requestId;
        this.type = type;
        this.initiatorUserId = initiatorUserId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.locationId = locationId;
        this.status = status;
        this.comment = comment;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public RequestType getType() {
        return type;
    }

    public void setType(RequestType type) {
        this.type = type;
    }

    public long getInitiatorUserId() {
        return initiatorUserId;
    }

    public void setInitiatorUserId(long initiatorUserId) {
        this.initiatorUserId = initiatorUserId;
    }

    public Long getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(Long fromUserId) {
        this.fromUserId = fromUserId;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public void setToUserId(Long toUserId) {
        this.toUserId = toUserId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
