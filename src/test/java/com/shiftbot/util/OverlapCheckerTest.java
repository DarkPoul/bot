package com.shiftbot.util;

import com.shiftbot.model.Shift;
import com.shiftbot.model.enums.ShiftSource;
import com.shiftbot.model.enums.ShiftStatus;
import com.shiftbot.model.Request;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OverlapCheckerTest {

    @Test
    void detectsOverlap() {
        LocalTime startA = LocalTime.of(10, 0);
        LocalTime endA = LocalTime.of(14, 0);
        LocalTime startB = LocalTime.of(13, 0);
        LocalTime endB = LocalTime.of(18, 0);
        assertTrue(OverlapChecker.overlaps(startA, endA, startB, endB));
    }

    @Test
    void detectsNoOverlapWhenSeparated() {
        LocalTime startA = LocalTime.of(8, 0);
        LocalTime endA = LocalTime.of(10, 0);
        LocalTime startB = LocalTime.of(10, 0);
        LocalTime endB = LocalTime.of(12, 0);
        assertFalse(OverlapChecker.overlaps(startA, endA, startB, endB));
    }

    @Test
    void conflictsWithExistingShifts() {
        Shift existing = new Shift("1", LocalDate.now(), LocalTime.of(10, 0), LocalTime.of(14, 0), "loc1", 1L, ShiftStatus.APPROVED, ShiftSource.MONTH_PLAN, null, null);
        Shift candidate = new Shift("2", LocalDate.now(), LocalTime.of(13, 0), LocalTime.of(15, 0), "loc1", 1L, ShiftStatus.DRAFT, ShiftSource.MONTH_PLAN, null, null);
        assertTrue(OverlapChecker.conflictsWith(List.of(existing), candidate));
    }

    @Test
    void detectsPendingRequestConflictsForUserAndLocation() {
        LocalDate date = LocalDate.of(2024, 5, 1);
        Request request = new Request("r1", RequestType.COVER, 10L, null, null, date,
                LocalTime.of(9, 0), LocalTime.of(12, 0), "loc1", RequestStatus.WAIT_TM, null, null, null);

        OverlapChecker.ConflictResult result = OverlapChecker.conflictsFor(
                10L, "loc1", date, LocalTime.of(10, 0), LocalTime.of(11, 0),
                List.of(), List.of(), List.of(request));

        assertTrue(result.hasUserConflicts());
        assertTrue(result.hasLocationConflicts());
        assertEquals(1, result.userConflicts().size());
        assertEquals(1, result.locationConflicts().size());
    }

    @Test
    void ignoresNonPendingRequestsInConflictsFor() {
        LocalDate date = LocalDate.of(2024, 5, 1);
        Request approvedRequest = new Request("r1", RequestType.COVER, 10L, null, null, date,
                LocalTime.of(9, 0), LocalTime.of(12, 0), "loc1", RequestStatus.APPROVED, null, null, null);

        OverlapChecker.ConflictResult result = OverlapChecker.conflictsFor(
                10L, "loc1", date, LocalTime.of(10, 0), LocalTime.of(11, 0),
                List.of(), List.of(), List.of(approvedRequest));

        assertFalse(result.hasAnyConflicts());
    }
}
