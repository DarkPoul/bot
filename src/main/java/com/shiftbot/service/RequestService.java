package com.shiftbot.service;

import com.shiftbot.model.Request;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import com.shiftbot.repository.RequestsRepository;
import com.shiftbot.util.TimeUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RequestService {
    private final RequestsRepository requestsRepository;
    private final ZoneId zoneId;

    public RequestService(RequestsRepository requestsRepository, ZoneId zoneId) {
        this.requestsRepository = requestsRepository;
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
        return request;
    }

    public List<Request> requestsByUser(long userId) {
        return requestsRepository.findAll().stream()
                .filter(r -> r.getInitiatorUserId() == userId || (r.getFromUserId() != null && r.getFromUserId() == userId) || (r.getToUserId() != null && r.getToUserId() == userId))
                .collect(Collectors.toList());
    }

    public List<Request> pendingForTm() {
        return requestsRepository.findAll().stream()
                .filter(r -> r.getStatus() == RequestStatus.WAIT_TM)
                .collect(Collectors.toList());
    }

    public Optional<Request> findById(String requestId) {
        return requestsRepository.findById(requestId);
    }

    public Request approveByTm(String requestId) {
        Request request = load(requestId);
        request.setStatus(RequestStatus.APPROVED_TM);
        request.setUpdatedAt(TimeUtils.nowInstant(zoneId));
        requestsRepository.update(request);
        return request;
    }

    public Request rejectByTm(String requestId) {
        Request request = load(requestId);
        request.setStatus(RequestStatus.REJECTED_TM);
        request.setUpdatedAt(TimeUtils.nowInstant(zoneId));
        requestsRepository.update(request);
        return request;
    }

    private Request load(String requestId) {
        return requestsRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));
    }
}
