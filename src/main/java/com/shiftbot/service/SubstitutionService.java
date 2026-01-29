package com.shiftbot.service;

import com.shiftbot.model.SubstitutionRequest;
import com.shiftbot.model.User;
import com.shiftbot.model.enums.SubstitutionReasonCode;
import com.shiftbot.model.enums.SubstitutionStatus;
import com.shiftbot.repository.SubstitutionRequestsRepository;
import com.shiftbot.util.TimeUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SubstitutionService {
    private final SubstitutionRequestsRepository substitutionRequestsRepository;
    private final AuditService auditService;
    private final ZoneId zoneId;

    public SubstitutionService(SubstitutionRequestsRepository substitutionRequestsRepository, AuditService auditService, ZoneId zoneId) {
        this.substitutionRequestsRepository = substitutionRequestsRepository;
        this.auditService = auditService;
        this.zoneId = zoneId;
    }

    public SubstitutionRequest createRequest(User seller, LocalDate shiftDate, SubstitutionReasonCode reasonCode,
                                             String reasonText, String location) {
        SubstitutionRequest request = new SubstitutionRequest();
        request.setId(UUID.randomUUID().toString());
        request.setCreatedAt(TimeUtils.nowInstant(zoneId));
        request.setSellerTelegramId(seller.getUserId());
        request.setSellerName(seller.getFullName());
        request.setLocation(location);
        request.setShiftDate(shiftDate);
        request.setReasonCode(reasonCode);
        request.setReasonText(reasonText);
        request.setStatus(SubstitutionStatus.OPEN);
        substitutionRequestsRepository.save(request);
        if (auditService != null) {
            auditService.logEvent(seller.getUserId(), "substitution_request_created", "substitution_request",
                    request.getId(), Map.of("status", request.getStatus().name()));
        }
        return request;
    }

    public List<SubstitutionRequest> listBySeller(long sellerTelegramId) {
        return substitutionRequestsRepository.findAll().stream()
                .filter(req -> req.getSellerTelegramId() == sellerTelegramId)
                .sorted(Comparator.comparing(SubstitutionRequest::getCreatedAt).reversed())
                .toList();
    }

    public SubstitutionRequest markInProgress(String id, long seniorTelegramId) {
        return updateStatus(id, SubstitutionStatus.IN_PROGRESS, seniorTelegramId);
    }

    public SubstitutionRequest reject(String id, long seniorTelegramId) {
        return updateStatus(id, SubstitutionStatus.REJECTED, seniorTelegramId);
    }

    public SubstitutionRequest resolve(String id, long seniorTelegramId) {
        return updateStatus(id, SubstitutionStatus.RESOLVED, seniorTelegramId);
    }

    public SubstitutionRequest cancel(String id, long sellerTelegramId) {
        SubstitutionRequest request = load(id);
        if (request.getSellerTelegramId() != sellerTelegramId) {
            throw new IllegalArgumentException("Недостатньо прав для скасування");
        }
        if (request.getStatus() != SubstitutionStatus.OPEN) {
            throw new IllegalStateException("Запит вже оброблено");
        }
        request.setStatus(SubstitutionStatus.CANCELLED);
        request.setProcessedAt(TimeUtils.nowInstant(zoneId));
        substitutionRequestsRepository.update(request);
        if (auditService != null) {
            auditService.logEvent(sellerTelegramId, "substitution_request_cancelled", "substitution_request",
                    request.getId(), Map.of("status", request.getStatus().name()));
        }
        return request;
    }

    public SubstitutionRequest load(String id) {
        return substitutionRequestsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Substitution request not found: " + id));
    }

    private SubstitutionRequest updateStatus(String id, SubstitutionStatus status, long seniorTelegramId) {
        SubstitutionRequest request = load(id);
        request.setStatus(status);
        request.setProcessedBy(seniorTelegramId);
        request.setProcessedAt(TimeUtils.nowInstant(zoneId));
        substitutionRequestsRepository.update(request);
        if (auditService != null) {
            auditService.logEvent(seniorTelegramId, "substitution_request_status_updated", "substitution_request",
                    request.getId(), Map.of("status", status.name()));
        }
        return request;
    }
}
