package com.shiftbot.service;

import com.shiftbot.model.Request;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import com.shiftbot.repository.RequestsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestServiceTest {

    @Test
    void createCoverRequestSetsStatusAndTimestamps() {
        RequestsRepository repository = mock(RequestsRepository.class);
        RequestService service = new RequestService(repository, ZoneId.of("Europe/Kyiv"));

        Request request = service.createCoverRequest(1L, "loc1", LocalDate.of(2024, 4, 1), LocalTime.of(10, 0), LocalTime.of(18, 0), "comment");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(repository).save(captor.capture());
        Request saved = captor.getValue();
        assertEquals(RequestStatus.WAIT_TM, saved.getStatus());
        assertEquals(RequestType.COVER, saved.getType());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(request.getRequestId(), saved.getRequestId());
    }

    @Test
    void approveByTmUpdatesStatusAndTimestamp() {
        RequestsRepository repository = mock(RequestsRepository.class);
        RequestService service = new RequestService(repository, ZoneId.of("Europe/Kyiv"));
        Request existing = new Request("req1", RequestType.COVER, 1L, null, null, LocalDate.now(), LocalTime.NOON, LocalTime.MIDNIGHT, "loc1", RequestStatus.WAIT_TM, "comment", Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));
        when(repository.findById("req1")).thenReturn(Optional.of(existing));

        service.approveByTm("req1");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(repository).update(captor.capture());
        Request updated = captor.getValue();
        assertEquals(RequestStatus.APPROVED_TM, updated.getStatus());
        assertTrue(updated.getUpdatedAt().isAfter(existing.getUpdatedAt()));
        assertEquals(existing.getCreatedAt(), updated.getCreatedAt());
    }
}
