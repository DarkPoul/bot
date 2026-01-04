package com.shiftbot.service;

import com.shiftbot.model.Request;
import com.shiftbot.model.Shift;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import com.shiftbot.repository.RequestsRepository;
import com.shiftbot.repository.ShiftsRepository;
import com.shiftbot.util.OverlapChecker;
import com.shiftbot.util.TimeUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
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

    public List<Request> requestsByUser(long userId) {
        return requestsRepository.findAll().stream()
                .filter(r -> r.getInitiatorUserId() == userId || (r.getFromUserId() != null && r.getFromUserId() == userId) || (r.getToUserId() != null && r.getToUserId() == userId))
                .collect(Collectors.toList());
    }

    private void checkForConflicts(long userId, String locationId, LocalDate date, LocalTime start, LocalTime end) {
        List<Shift> userShifts = shiftsRepository.findByUser(userId);
        List<Shift> locationShifts = shiftsRepository.findByLocation(locationId);
        List<Request> allRequests = requestsRepository.findAll();

        OverlapChecker.ConflictResult conflicts = OverlapChecker.conflictsFor(userId, locationId, date, start, end, userShifts, locationShifts, allRequests);
        if (conflicts.hasAnyConflicts()) {
            throw new IllegalArgumentException(buildConflictMessage(conflicts, date));
        }
    }

    private String buildConflictMessage(OverlapChecker.ConflictResult conflicts, LocalDate date) {
        StringBuilder sb = new StringBuilder("❗️ Є конфлікт для ")
                .append(TimeUtils.humanDate(date, zoneId))
                .append(":\n");
        if (conflicts.hasUserConflicts()) {
            sb.append("• Ваші зміни/заявки: ");
            sb.append(formatSlots(conflicts.userConflicts()));
            sb.append("\n");
        }
        if (conflicts.hasLocationConflicts()) {
            sb.append("• На локації вже є зміни/заявки: ");
            sb.append(formatSlots(conflicts.locationConflicts()));
        }
        return sb.toString().trim();
    }

    private String formatSlots(List<OverlapChecker.ConflictSlot> slots) {
        return slots.stream()
                .map(slot -> TimeUtils.humanTimeRange(slot.startTime(), slot.endTime()))
                .distinct()
                .collect(Collectors.joining(", "));
    }
}
