package com.shiftbot.util;

import java.time.LocalDate;

public record DateRange(LocalDate start, LocalDate end) {
    public boolean includes(LocalDate date) {
        return (date.isEqual(start) || date.isAfter(start)) && (date.isEqual(end) || date.isBefore(end));
    }
}
