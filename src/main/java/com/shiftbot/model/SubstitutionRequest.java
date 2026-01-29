package com.shiftbot.model;

import com.shiftbot.model.enums.SubstitutionReasonCode;
import com.shiftbot.model.enums.SubstitutionStatus;

import java.time.Instant;
import java.time.LocalDate;

public class SubstitutionRequest {
    private String id;
    private Instant createdAt;
    private long sellerTelegramId;
    private String sellerName;
    private String location;
    private LocalDate shiftDate;
    private SubstitutionReasonCode reasonCode;
    private String reasonText;
    private SubstitutionStatus status;
    private Long processedBy;
    private Instant processedAt;

    public SubstitutionRequest() {
    }

    public SubstitutionRequest(String id, Instant createdAt, long sellerTelegramId, String sellerName, String location,
                               LocalDate shiftDate, SubstitutionReasonCode reasonCode, String reasonText,
                               SubstitutionStatus status, Long processedBy, Instant processedAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.sellerTelegramId = sellerTelegramId;
        this.sellerName = sellerName;
        this.location = location;
        this.shiftDate = shiftDate;
        this.reasonCode = reasonCode;
        this.reasonText = reasonText;
        this.status = status;
        this.processedBy = processedBy;
        this.processedAt = processedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public long getSellerTelegramId() {
        return sellerTelegramId;
    }

    public void setSellerTelegramId(long sellerTelegramId) {
        this.sellerTelegramId = sellerTelegramId;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDate getShiftDate() {
        return shiftDate;
    }

    public void setShiftDate(LocalDate shiftDate) {
        this.shiftDate = shiftDate;
    }

    public SubstitutionReasonCode getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(SubstitutionReasonCode reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReasonText() {
        return reasonText;
    }

    public void setReasonText(String reasonText) {
        this.reasonText = reasonText;
    }

    public SubstitutionStatus getStatus() {
        return status;
    }

    public void setStatus(SubstitutionStatus status) {
        this.status = status;
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
