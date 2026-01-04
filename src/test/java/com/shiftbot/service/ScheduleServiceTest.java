package com.shiftbot.service;

import com.shiftbot.model.Shift;
import com.shiftbot.model.enums.ShiftSource;
import com.shiftbot.model.enums.ShiftStatus;
import com.shiftbot.repository.LocationsRepository;
import com.shiftbot.repository.ShiftsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class ScheduleServiceTest {

    @Test
    void buildsCalendarStatuses() {
        ShiftsRepository shiftsRepository = Mockito.mock(ShiftsRepository.class);
        LocationsRepository locationsRepository = Mockito.mock(LocationsRepository.class);
        ScheduleService service = new ScheduleService(shiftsRepository, locationsRepository, ZoneId.of("Europe/Kyiv"));

        Shift shift = new Shift("1", LocalDate.of(2024, 3, 10), LocalTime.of(10, 0), LocalTime.of(22, 0), "loc1", 1L, ShiftStatus.APPROVED, ShiftSource.MONTH_PLAN, null, null);
        when(shiftsRepository.findByUser(1L)).thenReturn(List.of(shift));

        Map<LocalDate, ShiftStatus> statuses = service.calendarStatuses(1L, LocalDate.of(2024, 3, 1));
        assertEquals(ShiftStatus.APPROVED, statuses.get(LocalDate.of(2024, 3, 10)));
    }

    @Test
    void mergesCalendarStatusesForLocation() {
        ShiftsRepository shiftsRepository = Mockito.mock(ShiftsRepository.class);
        LocationsRepository locationsRepository = Mockito.mock(LocationsRepository.class);
        ScheduleService service = new ScheduleService(shiftsRepository, locationsRepository, ZoneId.of("Europe/Kyiv"));

        Shift draft = new Shift("1", LocalDate.of(2024, 3, 10), LocalTime.of(10, 0), LocalTime.of(18, 0), "loc1", 1L, ShiftStatus.DRAFT, ShiftSource.MONTH_PLAN, null, null);
        Shift approved = new Shift("2", LocalDate.of(2024, 3, 10), LocalTime.of(18, 0), LocalTime.of(22, 0), "loc1", 2L, ShiftStatus.APPROVED, ShiftSource.MONTH_PLAN, null, null);
        when(shiftsRepository.findByLocation("loc1")).thenReturn(List.of(draft, approved));

        Map<LocalDate, ShiftStatus> statuses = service.calendarStatusesForLocation("loc1", LocalDate.of(2024, 3, 1));
        assertEquals(ShiftStatus.APPROVED, statuses.get(LocalDate.of(2024, 3, 10)));
    }

    @Test
    void sortsShiftsForLocationByTime() {
        ShiftsRepository shiftsRepository = Mockito.mock(ShiftsRepository.class);
        LocationsRepository locationsRepository = Mockito.mock(LocationsRepository.class);
        ScheduleService service = new ScheduleService(shiftsRepository, locationsRepository, ZoneId.of("Europe/Kyiv"));

        Shift late = new Shift("1", LocalDate.of(2024, 3, 10), LocalTime.of(14, 0), LocalTime.of(22, 0), "loc1", 1L, ShiftStatus.APPROVED, ShiftSource.MONTH_PLAN, null, null);
        Shift early = new Shift("2", LocalDate.of(2024, 3, 10), LocalTime.of(10, 0), LocalTime.of(14, 0), "loc1", 2L, ShiftStatus.APPROVED, ShiftSource.MONTH_PLAN, null, null);
        when(shiftsRepository.findByLocation("loc1")).thenReturn(List.of(late, early));

        List<Shift> shifts = service.shiftsForLocation("loc1", LocalDate.of(2024, 3, 10));
        assertEquals(List.of(early, late), shifts);
    }
}
