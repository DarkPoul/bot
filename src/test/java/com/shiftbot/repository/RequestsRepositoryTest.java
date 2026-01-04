package com.shiftbot.repository;

import com.shiftbot.model.Request;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestsRepositoryTest {

    @Test
    void updatesRequestRowById() {
        SheetsClient sheetsClient = mock(SheetsClient.class);
        RequestsRepository repository = new RequestsRepository(sheetsClient);

        List<List<Object>> rows = List.of(
                List.of(
                        "req-1",
                        RequestType.COVER.name(),
                        "1",
                        "",
                        "",
                        LocalDate.of(2024, 3, 10).toString(),
                        LocalTime.of(10, 0).toString(),
                        LocalTime.of(12, 0).toString(),
                        "loc",
                        RequestStatus.WAIT_TM.name(),
                        "comment",
                        Instant.parse("2024-03-01T00:00:00Z").toString(),
                        Instant.parse("2024-03-01T00:00:00Z").toString()
                )
        );
        when(sheetsClient.readRange("requests!A2:N")).thenReturn(rows);

        Request request = repository.findAll().get(0);
        request.setStatus(RequestStatus.APPROVED);
        Instant updatedAt = Instant.parse("2024-03-02T00:00:00Z");
        request.setUpdatedAt(updatedAt);

        repository.update(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object>> rowCaptor = ArgumentCaptor.forClass((Class<List<Object>>) (Class<?>) List.class);
        verify(sheetsClient).updateRow(eq("requests!A2:N"), eq(0), rowCaptor.capture());
        assertEquals(RequestStatus.APPROVED.name(), rowCaptor.getValue().get(9));
        assertEquals(updatedAt.toString(), rowCaptor.getValue().get(12));
    }
}
