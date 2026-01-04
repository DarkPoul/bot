package com.shiftbot.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public final class InputValidator {

    private InputValidator() {
    }

    public static Optional<LocalDate> parseDate(String input, ZoneId zoneId) {
        try {
            MonthDay monthDay = MonthDay.parse(input.trim(), TimeUtils.DATE_FORMAT);
            int year = LocalDate.now(zoneId).getYear();
            return Optional.of(monthDay.atYear(year));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    public static Optional<LocalTime> parseTime(String input) {
        try {
            return Optional.of(LocalTime.parse(input.trim(), TimeUtils.TIME_FORMAT));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
