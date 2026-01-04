package com.shiftbot.util;

import com.shiftbot.model.Request;
import com.shiftbot.model.Shift;
import com.shiftbot.model.enums.RequestStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class OverlapChecker {
    private static final Set<RequestStatus> PENDING_REQUEST_STATUSES = Set.of(RequestStatus.WAIT_TM, RequestStatus.WAIT_OTHER);

    private OverlapChecker() {
    }

    public static boolean overlaps(LocalTime startA, LocalTime endA, LocalTime startB, LocalTime endB) {
        return startA.isBefore(endB) && startB.isBefore(endA);
    }

    public static boolean conflictsWith(List<Shift> shifts, Shift candidate) {
        return shifts.stream()
                .filter(s -> s.getDate().equals(candidate.getDate()))
                .anyMatch(s -> overlaps(s.getStartTime(), s.getEndTime(), candidate.getStartTime(), candidate.getEndTime()));
    }

    public static ConflictResult conflictsFor(long userId, String locationId, LocalDate date, LocalTime start, LocalTime end,
                                              List<Shift> userShifts, List<Shift> locationShifts, List<Request> requests) {
        List<ConflictSlot> userConflicts = new ArrayList<>();
        for (Shift shift : userShifts) {
            if (date.equals(shift.getDate()) && overlaps(shift.getStartTime(), shift.getEndTime(), start, end)) {
                userConflicts.add(new ConflictSlot(date, shift.getStartTime(), shift.getEndTime(), "shift"));
            }
        }

        List<ConflictSlot> locationConflicts = new ArrayList<>();
        for (Shift shift : locationShifts) {
            if (locationId.equals(shift.getLocationId()) && date.equals(shift.getDate()) &&
                    overlaps(shift.getStartTime(), shift.getEndTime(), start, end)) {
                locationConflicts.add(new ConflictSlot(date, shift.getStartTime(), shift.getEndTime(), "shift"));
            }
        }

        for (Request request : requests) {
            if (!isPending(request) || !date.equals(request.getDate()) ||
                    !overlaps(request.getStartTime(), request.getEndTime(), start, end)) {
                continue;
            }

            if (involvesUser(request, userId)) {
                userConflicts.add(new ConflictSlot(date, request.getStartTime(), request.getEndTime(), "request"));
            }
            if (locationId.equals(request.getLocationId())) {
                locationConflicts.add(new ConflictSlot(date, request.getStartTime(), request.getEndTime(), "request"));
            }
        }

        return new ConflictResult(distinct(userConflicts), distinct(locationConflicts));
    }

    private static boolean isPending(Request request) {
        return PENDING_REQUEST_STATUSES.contains(request.getStatus());
    }

    private static boolean involvesUser(Request request, long userId) {
        return request.getInitiatorUserId() == userId ||
                (request.getFromUserId() != null && request.getFromUserId() == userId) ||
                (request.getToUserId() != null && request.getToUserId() == userId);
    }

    private static List<ConflictSlot> distinct(List<ConflictSlot> slots) {
        return slots.stream().distinct().collect(Collectors.toList());
    }

    public record ConflictSlot(LocalDate date, LocalTime startTime, LocalTime endTime, String source) {
    }

    public record ConflictResult(List<ConflictSlot> userConflicts, List<ConflictSlot> locationConflicts) {
        public ConflictResult {
            userConflicts = List.copyOf(userConflicts);
            locationConflicts = List.copyOf(locationConflicts);
        }

        public boolean hasUserConflicts() {
            return !userConflicts.isEmpty();
        }

        public boolean hasLocationConflicts() {
            return !locationConflicts.isEmpty();
        }

        public boolean hasAnyConflicts() {
            return hasUserConflicts() || hasLocationConflicts();
        }
    }
}
