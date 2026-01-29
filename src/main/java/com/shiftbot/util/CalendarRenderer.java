package com.shiftbot.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CalendarRenderer {
    private static final List<DayOfWeek> WEEK_DAYS = Arrays.asList(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY
    );
    private static final int CELL_WIDTH = 3;

    private CalendarRenderer() {
    }

    public static String renderMonth(YearMonth month, Set<Integer> workDays, Locale locale) {
        if (month == null) {
            return "";
        }
        Set<Integer> safeWorkDays = workDays == null ? Collections.emptySet() : workDays;
        StringBuilder sb = new StringBuilder();
        appendWeekHeader(sb, locale);
        sb.append("\n");

        LocalDate firstDay = month.atDay(1);
        int offset = firstDay.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        if (offset < 0) {
            offset += 7;
        }

        int column = 0;
        for (int i = 0; i < offset; i++) {
            appendCell(sb, "   ", column);
            column++;
        }

        int daysInMonth = month.lengthOfMonth();
        for (int day = 1; day <= daysInMonth; day++) {
            String marker = safeWorkDays.contains(day) ? "✅" : "❌";
            String cell = String.format("%2d%s", day, marker);
            appendCell(sb, cell, column);
            column++;
            if (column == 7) {
                sb.append("\n");
                column = 0;
            }
        }

        if (column != 0) {
            while (column < 7) {
                appendCell(sb, "   ", column);
                column++;
            }
            sb.append("\n");
        }

        return sb.toString().stripTrailing();
    }

    private static void appendWeekHeader(StringBuilder sb, Locale locale) {
        Locale effectiveLocale = locale == null ? new Locale("uk", "UA") : locale;
        for (int i = 0; i < WEEK_DAYS.size(); i++) {
            DayOfWeek day = WEEK_DAYS.get(i);
            String label = "uk".equalsIgnoreCase(effectiveLocale.getLanguage())
                    ? TimeUtils.ukrainianDayOfWeek(day)
                    : day.getDisplayName(TextStyle.SHORT, effectiveLocale);
            sb.append(String.format("%-3s", label));
            if (i < WEEK_DAYS.size() - 1) {
                sb.append(" ");
            }
        }
    }

    private static void appendCell(StringBuilder sb, String cell, int column) {
        sb.append(padCell(cell));
        if (column < 6) {
            sb.append(" ");
        }
    }

    private static String padCell(String cell) {
        if (cell == null) {
            return " ".repeat(CELL_WIDTH);
        }
        if (cell.length() >= CELL_WIDTH) {
            return cell;
        }
        return cell + " ".repeat(CELL_WIDTH - cell.length());
    }
}
