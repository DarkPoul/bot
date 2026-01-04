package com.shiftbot.service;

import com.shiftbot.model.Location;
import com.shiftbot.model.Shift;
import com.shiftbot.model.User;
import com.shiftbot.model.enums.ShiftSource;
import com.shiftbot.model.enums.ShiftStatus;
import com.shiftbot.repository.LocationsRepository;
import com.shiftbot.repository.ShiftsRepository;
import com.shiftbot.util.OverlapChecker;
import com.shiftbot.util.TimeUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class ScheduleService {
    private final ShiftsRepository shiftsRepository;
    private final LocationsRepository locationsRepository;
    private final ZoneId zoneId;

    public ScheduleService(ShiftsRepository shiftsRepository, LocationsRepository locationsRepository, ZoneId zoneId) {
        this.shiftsRepository = shiftsRepository;
        this.locationsRepository = locationsRepository;
        this.zoneId = zoneId;
    }

    public Map<LocalDate, ShiftStatus> calendarStatuses(long userId, LocalDate month) {
        Map<LocalDate, ShiftStatus> map = new HashMap<>();
        List<Shift> shifts = shiftsRepository.findByUser(userId);
        for (Shift shift : shifts) {
            if (shift.getDate().getMonth() == month.getMonth() && shift.getDate().getYear() == month.getYear()) {
                map.put(shift.getDate(), shift.getStatus());
            }
        }
        return map;
    }

    public Map<LocalDate, ShiftStatus> calendarStatusesForLocation(String locationId, LocalDate month) {
        Map<LocalDate, ShiftStatus> map = new HashMap<>();
        List<Shift> shifts = shiftsRepository.findByLocation(locationId);
        for (Shift shift : shifts) {
            if (shift.getDate().getMonth() == month.getMonth() && shift.getDate().getYear() == month.getYear()) {
                ShiftStatus existing = map.get(shift.getDate());
                map.put(shift.getDate(), mergeStatus(existing, shift.getStatus()));
            }
        }
        return map;
    }

    public List<Shift> shiftsForDate(long userId, LocalDate date) {
        return shiftsRepository.findByUser(userId).stream()
                .filter(s -> s.getDate().equals(date))
                .sorted(Comparator.comparing(Shift::getStartTime))
                .collect(Collectors.toList());
    }

    public List<Shift> shiftsForLocation(String locationId, LocalDate date) {
        return shiftsRepository.findByLocation(locationId).stream()
                .filter(s -> s.getDate().equals(date))
                .sorted(Comparator.comparing(Shift::getStartTime))
                .collect(Collectors.toList());
    }

    public List<Shift> locationSchedule(String locationId, LocalDate from, LocalDate to) {
        return shiftsRepository.findByLocation(locationId).stream()
                .filter(s -> !s.getDate().isBefore(from) && !s.getDate().isAfter(to))
                .collect(Collectors.toList());
    }

    public Shift createMonthlyPlanShift(long userId, String locationId, LocalDate date, LocalTime start, LocalTime end) {
        Shift shift = new Shift();
        shift.setUserId(userId);
        shift.setLocationId(locationId);
        shift.setDate(date);
        shift.setStartTime(start);
        shift.setEndTime(end);
        shift.setStatus(ShiftStatus.PENDING_TM);
        shift.setSource(ShiftSource.MONTH_PLAN);
        shift.setUpdatedAt(TimeUtils.nowInstant(zoneId));

        List<Shift> existing = shiftsRepository.findByUser(userId);
        if (OverlapChecker.conflictsWith(existing, shift)) {
            throw new IllegalArgumentException("Є конфлікт з існуючою зміною");
        }
        shiftsRepository.save(shift);
        return shift;
    }

    public String formatShift(Shift shift) {
        Optional<Location> location = locationsRepository.findById(shift.getLocationId());
        return "📍 " + location.map(Location::getName).orElse("Локація") + "\\n" +
                TimeUtils.humanDate(shift.getDate(), zoneId) + " " + TimeUtils.humanTimeRange(shift.getStartTime(), shift.getEndTime()) + "\\n" +
                "Статус: " + statusLabel(shift.getStatus());
    }

    public List<User> freeSellers(List<User> sellers, LocalDate date, LocalTime start, LocalTime end) {
        List<User> result = new ArrayList<>();
        for (User seller : sellers) {
            Shift candidate = new Shift();
            candidate.setDate(date);
            candidate.setStartTime(start);
            candidate.setEndTime(end);
            List<Shift> existing = shiftsRepository.findByUser(seller.getUserId());
            if (!OverlapChecker.conflictsWith(existing, candidate)) {
                result.add(seller);
            }
        }
        return result;
    }

    private String statusLabel(ShiftStatus status) {
        return switch (status) {
            case APPROVED -> "Затверджено";
            case PENDING_TM -> "Очікує ТМ";
            case DRAFT -> "Чернетка";
            case CANCELED -> "Скасовано";
        };
    }

    private ShiftStatus mergeStatus(ShiftStatus existing, ShiftStatus candidate) {
        if (existing == null) {
            return candidate;
        }
        if (existing == ShiftStatus.APPROVED || candidate == ShiftStatus.APPROVED) {
            return ShiftStatus.APPROVED;
        }
        if (existing == ShiftStatus.PENDING_TM || candidate == ShiftStatus.PENDING_TM) {
            return ShiftStatus.PENDING_TM;
        }
        if (existing == ShiftStatus.DRAFT || candidate == ShiftStatus.DRAFT) {
            return ShiftStatus.DRAFT;
        }
        return candidate;
    }
}
