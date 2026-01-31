package com.shiftbot.repository;

import com.shiftbot.model.ScheduleEntry;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public interface SchedulesRepository {
    Optional<ScheduleEntry> findByUserAndMonth(long userId, int year, int month);

    java.util.List<ScheduleEntry> findAll();

    void saveMonthlySchedule(long userId, String locationId, int year, int month, Set<Integer> workDays, Instant updatedAt);
}
