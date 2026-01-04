package com.shiftbot.service;

import com.shiftbot.model.Request;
import com.shiftbot.model.Shift;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import com.shiftbot.model.enums.ShiftStatus;
import com.shiftbot.repository.RequestsRepository;
import com.shiftbot.repository.ShiftsRepository;
import com.shiftbot.util.TimeUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RequestService {
    private final RequestsRepository requestsRepository;
    private final ShiftsRepository shiftsRepository;
    private final ZoneId zoneId;

    public RequestService(RequestsRepository requestsRepository, ShiftsRepository shiftsRepository, ZoneId zoneId) {
        this.requestsRepository = requestsRepository;
        this.shiftsRepository = shiftsRepository;
        this.zoneId = zoneId;
    }

    public Request createCoverRequest(long initiator, String locationId, LocalDate date, LocalTime start, LocalTime end, String comment) {
        checkForConflicts(initiator, locationId, date, start, end);

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

    public Request createSwapRequest(long initiator, long fromUserId, long toUserId, String locationId, LocalDate date,
                                     LocalTime start, LocalTime end, String comment) {
        checkForConflicts(fromUserId, locationId, date, start, end);
        checkForConflicts(toUserId, locationId, date, start, end);

        Request request = new Request();
        request.setType(RequestType.SWAP);
        request.setInitiatorUserId(initiator);
        request.setFromUserId(fromUserId);
        request.setToUserId(toUserId);
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

    public Request createSwapRequest(Shift fromShift, long initiatorId, long peerUserId, String comment, Shift targetShift) {
        Request request = new Request();
        request.setType(RequestType.SWAP);
        request.setInitiatorUserId(initiatorId);
        request.setFromUserId(fromShift.getUserId());
        request.setToUserId(peerUserId);
        request.setDate(fromShift.getDate());
        request.setStartTime(fromShift.getStartTime());
        request.setEndTime(fromShift.getEndTime());
        request.setLocationId(fromShift.getLocationId());
        request.setStatus(RequestStatus.INITIATED);
        request.setComment(comment);
        request.setCreatedAt(TimeUtils.nowInstant(zoneId));
        request.setUpdatedAt(TimeUtils.nowInstant(zoneId));
        requestsRepository.save(request);
        request.setStatus(RequestStatus.WAIT_PEER);
        request.setUpdatedAt(TimeUtils.nowInstant(zoneId));
        requestsRepository.update(request);
        shiftsRepository.updateStatusAndLink(fromShift.getShiftId(), ShiftStatus.PENDING_SWAP, request.getRequestId());
        return request;
    }

    public Optional<Request> findById(String requestId) {
        return requestsRepository.findById(requestId);
    }

    public Request acceptByPeer(String requestId) {
        Request request = require(requestId);
        request.setStatus(RequestStatus.WAIT_TM);
        request.setUpdatedAt(TimeUtils.nowInstant(zoneId));
        requestsRepository.update(request);
        return request;
    }

    public Request declineByPeer(String requestId) {
        Request request = require(requestId);
        request.setStatus(RequestStatus.REJECTED_TM);
        request.setUpdatedAt(TimeUtils.nowInstant(zoneId));
        requestsRepository.update(request);
        revertPendingShift(request);
        return request;
    }

    public Request approveByTm(String requestId) {
        Request request = require(requestId);
        request.setStatus(RequestStatus.APPROVED_TM);
        request.setUpdatedAt(TimeUtils.nowInstant(zoneId));
        requestsRepository.update(request);
        finalizePendingShift(request);
        return request;
    }

    public Request rejectByTm(String requestId) {
        Request request = require(requestId);
        request.setStatus(RequestStatus.REJECTED_TM);
        request.setUpdatedAt(TimeUtils.nowInstant(zoneId));
        requestsRepository.update(request);
        revertPendingShift(request);
        return request;
    }

    public List<Request> requestsByUser(long userId) {
        return requestsRepository.findAll().stream()
                .filter(r -> r.getInitiatorUserId() == userId || (r.getFromUserId() != null && r.getFromUserId() == userId) || (r.getToUserId() != null && r.getToUserId() == userId))
                .collect(Collectors.toList());
    }

    private Request require(String requestId) {
        return requestsRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));
    }

    private void revertPendingShift(Request request) {
        findLinkedShift(request).ifPresent(shift -> shiftsRepository.updateStatusAndLink(shift.getShiftId(), ShiftStatus.APPROVED, null));
    }

    private void finalizePendingShift(Request request) {
        findLinkedShift(request).ifPresent(shift -> shiftsRepository.updateStatusAndLink(shift.getShiftId(), ShiftStatus.APPROVED, null));
    }

    private Optional<Shift> findLinkedShift(Request request) {
        if (request.getFromUserId() == null) {
            return Optional.empty();
        }
        return shiftsRepository.findByUserAndSlot(request.getFromUserId(), request.getDate(), request.getStartTime(), request.getEndTime(), request.getLocationId());
    }
}
