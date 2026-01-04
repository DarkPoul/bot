package com.shiftbot.service;

import com.shiftbot.model.Request;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import com.shiftbot.repository.RequestsRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class RequestServiceTest {

    @Test
    void updatesRequestStatusUsingRepositoryUpdate() {
        RequestsRepository requestsRepository = mock(RequestsRepository.class);
        Instant createdAt = Instant.parse("2024-03-01T00:00:00Z");
        Instant previousUpdate = Instant.parse("2024-03-02T00:00:00Z");
        Request existing = new Request("req-1", RequestType.COVER, 1L, null, null,
                LocalDate.of(2024, 3, 10), LocalTime.of(10, 0), LocalTime.of(12, 0),
                "loc", RequestStatus.WAIT_TM, "", createdAt, previousUpdate);
        when(requestsRepository.findAll()).thenReturn(List.of(existing));

        RequestService service = new RequestService(requestsRepository, ZoneId.of("Europe/Kyiv"));
        Request updated = service.updateStatus("req-1", RequestStatus.APPROVED);

        assertEquals(RequestStatus.APPROVED, updated.getStatus());
        assertNotNull(updated.getUpdatedAt());
        verify(requestsRepository).update(argThat(r -> r.getRequestId().equals("req-1") && r.getStatus() == RequestStatus.APPROVED && !r.getUpdatedAt().equals(previousUpdate)));
    }
}
