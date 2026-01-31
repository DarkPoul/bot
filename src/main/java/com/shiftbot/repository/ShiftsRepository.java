package com.shiftbot.repository;

import com.shiftbot.model.Shift;
import com.shiftbot.model.enums.ShiftStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ShiftsRepository {
    List<Shift> findAll();

    List<Shift> findByUser(long userId);

    List<Shift> findByLocation(String locationId);

    void save(Shift shift);

    void updateStatusAndLink(String shiftId, ShiftStatus status, String linkedRequestId);

    Optional<Shift> findByUserAndSlot(long userId, LocalDate date, LocalTime startTime, LocalTime endTime, String locationId);
}
