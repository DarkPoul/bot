package com.shiftbot.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

public final class TimeUtils {
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM");
    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    public static final LocalTime DEFAULT_START = LocalTime.of(10, 0);
    public static final LocalTime DEFAULT_END = LocalTime.of(22, 0);

    private TimeUtils() {
    }

    public static String humanDate(LocalDate date, ZoneId zoneId) {
        DayOfWeek dow = date.getDayOfWeek();
        return date.format(DATE_FORMAT) + " (" + ukrainianDayOfWeek(dow) + ")";
    }

    public static String humanTimeRange(LocalTime start, LocalTime end) {
        return start.format(TIME_FORMAT) + "–" + end.format(TIME_FORMAT);
    }

    public static String humanMonthYear(YearMonth month) {
        if (month == null) {
            return "";
        }
        String monthName = month.getMonth().getDisplayName(TextStyle.FULL, new Locale("uk", "UA"));
        return capitalize(monthName) + " " + month.getYear();
    }

    public static String ukrainianDayOfWeek(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "Пн";
            case TUESDAY -> "Вт";
            case WEDNESDAY -> "Ср";
            case THURSDAY -> "Чт";
            case FRIDAY -> "Пт";
            case SATURDAY -> "Сб";
            case SUNDAY -> "Нд";
        };
    }

    public static LocalDate today(ZoneId zoneId) {
        return LocalDate.now(zoneId);
    }

    public static Instant nowInstant(ZoneId zoneId) {
        return ZonedDateTime.now(zoneId).toInstant();
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(new Locale("uk", "UA")) + value.substring(1);
    }
}
