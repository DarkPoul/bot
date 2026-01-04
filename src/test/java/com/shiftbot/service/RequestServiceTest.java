package com.shiftbot.service;

import com.shiftbot.model.Request;
import com.shiftbot.model.Shift;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import com.shiftbot.model.enums.ShiftSource;
import com.shiftbot.model.enums.ShiftStatus;
import com.shiftbot.repository.RequestsRepository;
import com.shiftbot.repository.ShiftsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {

    @Mock
    private RequestsRepository requestsRepository;
    @Mock
    private ShiftsRepository shiftsRepository;

    private RequestService requestService;

    @BeforeEach
    void setUp() {
        requestService = new RequestService(requestsRepository, shiftsRepository, ZoneId.of("Europe/Kyiv"));
    }

    @Test
    void createCoverRequestFailsWhenUserHasOverlap() {
        LocalDate date = LocalDate.of(2024, 6, 10);
        Shift existing = new Shift("1", date, LocalTime.of(10, 0), LocalTime.of(12, 0), "loc1", 10L, ShiftStatus.APPROVED, ShiftSource.MONTH_PLAN, null, null);

        when(shiftsRepository.findByUser(10L)).thenReturn(List.of(existing));
        when(shiftsRepository.findByLocation("loc1")).thenReturn(List.of());
        when(requestsRepository.findAll()).thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                requestService.createCoverRequest(10L, "loc1", date, LocalTime.of(11, 0), LocalTime.of(13, 0), "comment")
        );

        assertTrue(ex.getMessage().contains("10:00–12:00"));
        verify(requestsRepository, never()).save(org.mockito.Mockito.any(Request.class));
    }

    @Test
    void createSwapRequestFailsWhenPendingRequestOverlaps() {
        LocalDate date = LocalDate.of(2024, 6, 11);
        Request pending = new Request("r1", RequestType.SWAP, 99L, 20L, 21L, date,
                LocalTime.of(9, 0), LocalTime.of(11, 0), "loc2", RequestStatus.WAIT_TM, null, null, null);

        when(shiftsRepository.findByUser(20L)).thenReturn(List.of());
        when(shiftsRepository.findByUser(21L)).thenReturn(List.of());
        when(shiftsRepository.findByLocation("loc2")).thenReturn(List.of());
        when(requestsRepository.findAll()).thenReturn(List.of(pending));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                requestService.createSwapRequest(99L, 20L, 21L, "loc2", date, LocalTime.of(10, 0), LocalTime.of(12, 0), "swap")
        );

        assertTrue(ex.getMessage().contains("09:00–11:00"));
        verify(requestsRepository, never()).save(org.mockito.Mockito.any(Request.class));
    }
}
