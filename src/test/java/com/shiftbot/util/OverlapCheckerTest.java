package com.shiftbot.util;

import com.shiftbot.model.Shift;
import com.shiftbot.model.enums.ShiftSource;
import com.shiftbot.model.enums.ShiftStatus;
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
}
