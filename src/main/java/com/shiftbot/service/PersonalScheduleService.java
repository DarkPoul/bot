package com.shiftbot.service;

import com.shiftbot.model.ScheduleEntry;
import com.shiftbot.repository.SchedulesRepository;
import com.shiftbot.util.TimeUtils;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class PersonalScheduleService {
    private final SchedulesRepository schedulesRepository;
    private final ZoneId zoneId;
    private final Clock clock;

    public PersonalScheduleService(SchedulesRepository schedulesRepository, ZoneId zoneId) {
        this(schedulesRepository, zoneId, Clock.system(zoneId));
    }

    public PersonalScheduleService(SchedulesRepository schedulesRepository, ZoneId zoneId, Clock clock) {
        this.schedulesRepository = schedulesRepository;
        this.zoneId = zoneId;
        this.clock = clock;
    }

    public Optional<ScheduleEntry> findByUserAndMonth(long userId, YearMonth month) {
        return schedulesRepository.findByUserAndMonth(userId, month.getYear(), month.getMonthValue());
    }

    public ScheduleEntry saveOrUpdate(long userId, YearMonth month, Set<Integer> workDays) {
        ScheduleEntry entry = schedulesRepository.findByUserAndMonth(userId, month.getYear(), month.getMonthValue())
                .orElseGet(ScheduleEntry::new);
        entry.setUserId(userId);
        entry.setYear(month.getYear());
        entry.setMonth(month.getMonthValue());
        entry.setWorkDaysCsv(toCsv(workDays));
        entry.setUpdatedAt(TimeUtils.nowInstant(zoneId));
        schedulesRepository.saveMonthlySchedule(userId, null, month.getYear(), month.getMonthValue(), workDays, entry.getUpdatedAt());
        return entry;
    }

    public ParseResult parseWorkDays(String input, YearMonth month) {
        if (input == null || input.isBlank()) {
            return ParseResult.empty();
        }
        if (!input.matches("[0-9,\\s]+")) {
            return ParseResult.invalid("Допустимі лише цифри, коми та пробіли.");
        }
        Set<Integer> invalid = new LinkedHashSet<>();
        Set<Integer> workDays = new LinkedHashSet<>();
        int maxDay = month.lengthOfMonth();
        for (String part : input.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int day = Integer.parseInt(trimmed);
                if (day < 1 || day > maxDay) {
                    invalid.add(day);
                } else {
                    workDays.add(day);
                }
            } catch (NumberFormatException e) {
                invalid.add(-1);
            }
        }
        if (!invalid.isEmpty()) {
            return ParseResult.invalid("Невірні дати: " + invalid.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", ")) + ". Приклад: 1,2,3,5,6");
        }
        return ParseResult.valid(workDays);
    }

    public Set<Integer> workDaysFromCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Integer::parseInt)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public YearMonth currentMonth() {
        return YearMonth.now(clock);
    }

    public YearMonth nextMonth() {
        return currentMonth().plusMonths(1);
    }

    private String toCsv(Set<Integer> days) {
        if (days == null || days.isEmpty()) {
            return "";
        }
        return days.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    public record ParseResult(Set<Integer> workDays, String errorMessage, boolean isEmpty) {

        public static ParseResult empty() {
            return new ParseResult(Collections.emptySet(), null, true);
        }

        public static ParseResult valid(Set<Integer> days) {
            return new ParseResult(days, null, false);
        }

        public static ParseResult invalid(String message) {
            return new ParseResult(Collections.emptySet(), message, false);
        }

        public ParseResult {
            java.util.Objects.requireNonNull(workDays, "workDays cannot be null");
        }
    }

}
