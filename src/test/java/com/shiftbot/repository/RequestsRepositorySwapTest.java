package com.shiftbot.repository;

import com.shiftbot.model.Request;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestsRepositorySwapTest {

    @Test
    void mapsSwapRequest() {
        SheetsClient sheetsClient = Mockito.mock(SheetsClient.class);
        RequestsRepository repository = new RequestsRepository(sheetsClient);

        List<Object> row = List.of("req1", "SWAP", "10", "10", "20", "2024-04-10", "09:00", "18:00", "loc1", "WAIT_PEER", "comment", "2024-04-01T10:00:00Z", "2024-04-01T10:00:00Z");
        when(sheetsClient.readRange(anyString())).thenReturn(List.of(row));

        Request request = repository.findAll().get(0);
        assertEquals(RequestType.SWAP, request.getType());
        assertEquals(10L, request.getInitiatorUserId());
        assertEquals(20L, request.getToUserId());
        assertEquals(RequestStatus.WAIT_PEER, request.getStatus());
    }

    @Test
    void savesSwapRequest() {
        SheetsClient sheetsClient = Mockito.mock(SheetsClient.class);
        RequestsRepository repository = new RequestsRepository(sheetsClient);

        Request request = new Request();
        request.setRequestId("req-new");
        request.setType(RequestType.SWAP);
        request.setInitiatorUserId(10L);
        request.setFromUserId(10L);
        request.setToUserId(20L);
        request.setDate(LocalDate.of(2024, 4, 10));
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(18, 0));
        request.setLocationId("loc1");
        request.setStatus(RequestStatus.WAIT_PEER);
        request.setComment("comment");
        request.setCreatedAt(Instant.parse("2024-04-01T10:00:00Z"));
        request.setUpdatedAt(Instant.parse("2024-04-01T10:00:00Z"));

        repository.save(request);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(sheetsClient).appendRow(anyString(), captor.capture());
        List<?> row = captor.getValue();
        assertEquals("SWAP", row.get(1));
        assertEquals("WAIT_PEER", row.get(9));
    }
}
