package com.shiftbot.service;

import com.shiftbot.model.Request;
import com.shiftbot.model.Shift;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.ShiftSource;
import com.shiftbot.model.enums.ShiftStatus;
import com.shiftbot.repository.RequestsRepository;
import com.shiftbot.repository.ShiftsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RequestServiceSwapStateMachineTest {

    @Test
    void goesThroughSwapStates() {
        RequestsRepository requestsRepository = Mockito.mock(RequestsRepository.class);
        ShiftsRepository shiftsRepository = Mockito.mock(ShiftsRepository.class);
        RequestService service = new RequestService(requestsRepository, shiftsRepository, ZoneId.of("Europe/Kyiv"));

        Shift shift = new Shift("shift1", LocalDate.of(2024, 4, 10), LocalTime.of(9, 0), LocalTime.of(18, 0),
                "loc1", 100L, ShiftStatus.APPROVED, ShiftSource.MONTH_PLAN, null, null);
        AtomicReference<Request> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            stored.set(request);
            return null;
        }).when(requestsRepository).save(any(Request.class));
        doAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            stored.set(request);
            return null;
        }).when(requestsRepository).update(any(Request.class));
        when(requestsRepository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(shiftsRepository.findByUserAndSlot(anyLong(), any(), any(), any(), any())).thenReturn(Optional.of(shift));

        Request created = service.createSwapRequest(shift, 200L, 300L, "коментар", null);
        assertEquals(RequestStatus.WAIT_PEER, created.getStatus());
        assertEquals(shift.getUserId(), created.getFromUserId());
        assertEquals(300L, created.getToUserId());
        verify(shiftsRepository).updateStatusAndLink(eq(shift.getShiftId()), eq(ShiftStatus.PENDING_SWAP), anyString());

        Request waitTm = service.acceptByPeer(created.getRequestId());
        assertEquals(RequestStatus.WAIT_TM, waitTm.getStatus());

        Request approved = service.approveByTm(created.getRequestId());
        assertEquals(RequestStatus.APPROVED_TM, approved.getStatus());
        verify(shiftsRepository, atLeastOnce()).updateStatusAndLink(eq(shift.getShiftId()), eq(ShiftStatus.APPROVED), isNull());
    }
}
