package com.shiftbot.service;

import com.shiftbot.model.ScheduleEntry;
import com.shiftbot.repository.SchedulesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalScheduleServiceTest {

    @Mock
    private SchedulesRepository schedulesRepository;

    @Test
    void savesNewScheduleWhenMissing() {
        when(schedulesRepository.findByUserId(10L)).thenReturn(Optional.empty());
        PersonalScheduleService service = new PersonalScheduleService(schedulesRepository, ZoneId.of("UTC"));

        service.saveOrUpdate(10L, "Пн 10-18");

        ArgumentCaptor<ScheduleEntry> captor = ArgumentCaptor.forClass(ScheduleEntry.class);
        verify(schedulesRepository).upsert(captor.capture());
        assertEquals(10L, captor.getValue().getUserId());
        assertEquals("Пн 10-18", captor.getValue().getScheduleText());
    }

    @Test
    void updatesExistingSchedule() {
        ScheduleEntry existing = new ScheduleEntry();
        existing.setScheduleId("sch-1");
        existing.setUserId(20L);
        existing.setScheduleText("old");
        when(schedulesRepository.findByUserId(20L)).thenReturn(Optional.of(existing));
        PersonalScheduleService service = new PersonalScheduleService(schedulesRepository, ZoneId.of("UTC"));

        service.saveOrUpdate(20L, "Вт вихідний");

        ArgumentCaptor<ScheduleEntry> captor = ArgumentCaptor.forClass(ScheduleEntry.class);
        verify(schedulesRepository).upsert(captor.capture());
        assertEquals("sch-1", captor.getValue().getScheduleId());
        assertEquals("Вт вихідний", captor.getValue().getScheduleText());
    }
}
