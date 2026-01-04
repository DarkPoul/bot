package com.shiftbot.repository;

import com.shiftbot.model.Request;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestsRepositoryTest {

    @Test
    void updatesExistingRow() {
        SheetsClient sheetsClient = mock(SheetsClient.class);
        RequestsRepository repository = new RequestsRepository(sheetsClient);
        List<List<Object>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(Arrays.asList(
                "req1", "COVER", "1", "", "", "2024-04-01", "10:00", "18:00", "loc1", "WAIT_TM", "old", Instant.EPOCH.toString(), Instant.EPOCH.toString()
        )));
        rows.add(new ArrayList<>(Arrays.asList(
                "req2", "COVER", "2", "", "", "2024-04-02", "08:00", "16:00", "loc2", "WAIT_TM", "comment", Instant.EPOCH.toString(), Instant.EPOCH.toString()
        )));
        when(sheetsClient.readRange("requests!A2:N")).thenReturn(rows);

        Request updated = new Request("req1", RequestType.COVER, 1L, null, null, LocalDate.of(2024, 4, 1),
                LocalTime.of(10, 0), LocalTime.of(18, 0), "loc1", RequestStatus.APPROVED_TM, "new comment", Instant.EPOCH, Instant.now());

        repository.update(updated);

        ArgumentCaptor<List<List<Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(sheetsClient).updateRange(eq("requests!A2:N"), captor.capture());
        List<List<Object>> updatedRows = captor.getValue();
        assertEquals("APPROVED_TM", updatedRows.get(0).get(9));
        assertEquals("new comment", updatedRows.get(0).get(10));
        assertEquals("req2", updatedRows.get(1).get(0));
    }
}
