package com.shiftbot.service;

import com.shiftbot.model.Request;
import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import com.shiftbot.repository.RequestsRepository;
import com.shiftbot.util.TimeUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RequestService {
    private final RequestsRepository requestsRepository;
    private final AuditService auditService;
    private final ZoneId zoneId;

    public RequestService(RequestsRepository requestsRepository, AuditService auditService, ZoneId zoneId) {
        this.requestsRepository = requestsRepository;
        this.auditService = auditService;
        this.zoneId = zoneId;
    }

    public Request createCoverRequest(long initiator, String locationId, LocalDate date, LocalTime start, LocalTime end, String comment) {
        Request request = new Request();
        request.setType(RequestType.COVER);
        request.setInitiatorUserId(initiator);
        request.setLocationId(locationId);
        request.setDate(date);
        request.setStartTime(start);
        request.setEndTime(end);
        request.setStatus(RequestStatus.WAIT_TM);
        request.setComment(comment);
        request.setCreatedAt(TimeUtils.nowInstant(zoneId));
        request.setUpdatedAt(TimeUtils.nowInstant(zoneId));
        requestsRepository.save(request);
        Map<String, Object> details = new HashMap<>();
        details.put("type", request.getType().name());
        details.put("status", request.getStatus().name());
        details.put("date", request.getDate().toString());
        details.put("locationId", request.getLocationId());
        auditService.logEvent(initiator, "request_created", "request", request.getRequestId(), details);
        return request;
    }

    public Request updateStatus(String requestId, RequestStatus status) {
        Request request = requestsRepository.findAll().stream()
                .filter(r -> r.getRequestId().equals(requestId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));
        request.setStatus(status);
        request.setUpdatedAt(TimeUtils.nowInstant(zoneId));
        requestsRepository.update(request);
        return request;
    }

    public List<Request> requestsByUser(long userId) {
        return requestsRepository.findAll().stream()
                .filter(r -> r.getInitiatorUserId() == userId || (r.getFromUserId() != null && r.getFromUserId() == userId) || (r.getToUserId() != null && r.getToUserId() == userId))
                .collect(Collectors.toList());
    }

    public Request approve(String requestId, long actorUserId, BotNotificationPort bot) {
        return updateStatus(requestId, RequestStatus.APPROVED, actorUserId, bot);
    }

    public Request reject(String requestId, long actorUserId, BotNotificationPort bot) {
        return updateStatus(requestId, RequestStatus.REJECTED, actorUserId, bot);
    }

    public Request cancel(String requestId, long actorUserId, BotNotificationPort bot) {
        return updateStatus(requestId, RequestStatus.CANCELED, actorUserId, bot);
    }

    private Request updateStatus(String requestId, RequestStatus status, long actorUserId, BotNotificationPort bot) {
        Request request = requestsRepository.findAll().stream()
                .filter(r -> r.getRequestId().equals(requestId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        request.setStatus(status);
        request.setUpdatedAt(TimeUtils.nowInstant(zoneId));
        requestsRepository.save(request);

        Map<String, Object> details = new HashMap<>();
        details.put("status", status.name());
        details.put("requestId", requestId);
        auditService.logEvent(actorUserId, "request_" + status.name().toLowerCase(), "request", requestId, details);

        notifyParticipants(request, status, actorUserId, bot);
        return request;
    }

    private void notifyParticipants(Request request, RequestStatus status, long tmUserId, BotNotificationPort bot) {
        if (bot == null) {
            return;
        }
        Set<Long> recipients = new HashSet<>();
        recipients.add(request.getInitiatorUserId());
        if (request.getFromUserId() != null) {
            recipients.add(request.getFromUserId());
        }
        if (request.getToUserId() != null) {
            recipients.add(request.getToUserId());
        }
        recipients.add(tmUserId);

        String statusLabel = switch (status) {
            case APPROVED -> "✅ Запит підтверджено";
            case REJECTED -> "❌ Запит відхилено";
            case CANCELED -> "⚠️ Запит скасовано";
            default -> "";
        };
        String text = statusLabel + "\\n" +
                "Тип: " + request.getType() + "\\n" +
                "Дата: " + request.getDate() + " " + TimeUtils.humanTimeRange(request.getStartTime(), request.getEndTime()) + "\\n" +
                "Локація: " + request.getLocationId();

        for (Long recipient : recipients) {
            bot.sendMarkdown(recipient, text, null);
        }
    }
}
