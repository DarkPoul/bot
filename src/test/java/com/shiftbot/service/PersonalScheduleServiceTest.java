package com.shiftbot.service;

import com.shiftbot.model.ScheduleEntry;
import com.shiftbot.repository.SchedulesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalScheduleServiceTest {

    @Mock
    private SchedulesRepository schedulesRepository;

    @Test
    void savesNewScheduleWhenMissing() {
        when(schedulesRepository.findByUserAndMonth(10L, 2024, 3)).thenReturn(Optional.empty());
        PersonalScheduleService service = new PersonalScheduleService(schedulesRepository, ZoneId.of("UTC"));

        service.saveOrUpdate(10L, YearMonth.of(2024, 3), Set.of(1, 2, 3));

        ArgumentCaptor<ScheduleEntry> captor = ArgumentCaptor.forClass(ScheduleEntry.class);
        verify(schedulesRepository).upsert(captor.capture());
        assertEquals(10L, captor.getValue().getUserId());
        assertEquals(2024, captor.getValue().getYear());
        assertEquals(3, captor.getValue().getMonth());
        assertEquals("1,2,3", captor.getValue().getWorkDaysCsv());
    }

    @Test
    void updatesExistingSchedule() {
        ScheduleEntry existing = new ScheduleEntry();
        existing.setScheduleId("sch-1");
        existing.setUserId(20L);
        existing.setYear(2024);
        existing.setMonth(4);
        existing.setWorkDaysCsv("old");
        when(schedulesRepository.findByUserAndMonth(20L, 2024, 4)).thenReturn(Optional.of(existing));
        PersonalScheduleService service = new PersonalScheduleService(schedulesRepository, ZoneId.of("UTC"));

        service.saveOrUpdate(20L, YearMonth.of(2024, 4), Set.of(5, 6));

        ArgumentCaptor<ScheduleEntry> captor = ArgumentCaptor.forClass(ScheduleEntry.class);
        verify(schedulesRepository).upsert(captor.capture());
        assertEquals("sch-1", captor.getValue().getScheduleId());
        assertEquals("5,6", captor.getValue().getWorkDaysCsv());
    }

    @Test
    void parsesCommaSeparatedDaysAndRemovesDuplicates() {
        PersonalScheduleService service = new PersonalScheduleService(schedulesRepository, ZoneId.of("UTC"));

        PersonalScheduleService.ParseResult result = service.parseWorkDays("1, 2,3,2", YearMonth.of(2024, 3));

        assertTrue(result.errorMessage() == null);
        assertEquals(Set.of(1, 2, 3), result.workDays());
    }

    @Test
    void rejectsInvalidDaysOutsideMonth() {
        PersonalScheduleService service = new PersonalScheduleService(schedulesRepository, ZoneId.of("UTC"));

        PersonalScheduleService.ParseResult result = service.parseWorkDays("0, 35", YearMonth.of(2024, 2));

        assertTrue(result.errorMessage() != null);
    }

    @Test
    void usesYearMonthLengthForValidation() {
        PersonalScheduleService service = new PersonalScheduleService(schedulesRepository, ZoneId.of("UTC"));

        PersonalScheduleService.ParseResult result = service.parseWorkDays("29", YearMonth.of(2024, 2));

        assertTrue(result.errorMessage() == null);
        assertEquals(Set.of(29), result.workDays());
    }

    @Test
    void nextMonthCrossesYearInDecember() {
        Clock clock = Clock.fixed(Instant.parse("2024-12-15T10:00:00Z"), ZoneId.of("UTC"));
        PersonalScheduleService service = new PersonalScheduleService(schedulesRepository, ZoneId.of("UTC"), clock);

        YearMonth next = service.nextMonth();

        assertEquals(YearMonth.of(2025, 1), next);
    }
}
