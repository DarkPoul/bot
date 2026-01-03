package com.shiftbot.util;

import com.shiftbot.model.Shift;

import java.time.LocalTime;
import java.util.List;

public final class OverlapChecker {
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
}
