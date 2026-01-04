package com.shiftbot.service;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.model.Request;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import com.shiftbot.repository.RequestsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class RequestServiceTest {

    private RequestsRepository requestsRepository;
    private AuditService auditService;
    private BotNotificationPort bot;
    private RequestService requestService;
    private final ZoneId zoneId = ZoneId.of("UTC");

    @BeforeEach
    void setUp() {
        requestsRepository = mock(RequestsRepository.class);
        auditService = mock(AuditService.class);
        bot = mock(BotNotificationPort.class);
        requestService = new RequestService(requestsRepository, auditService, zoneId);
    }

    @Test
    void createCoverRequest_logsAudit() {
        LocalDate date = LocalDate.of(2024, 1, 1);
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(18, 0);

        Request result = requestService.createCoverRequest(1L, "loc", date, start, end, "comment");

        assertEquals(RequestStatus.WAIT_TM, result.getStatus());
        verify(requestsRepository).save(result);
        verify(auditService).logEvent(eq(1L), eq("request_created"), eq("request"), eq(result.getRequestId()), any(Map.class));
    }

    @Test
    void approve_updatesStatus_notifiesAndAudits() {
        Request request = new Request();
        request.setRequestId("req-1");
        request.setType(RequestType.COVER);
        request.setInitiatorUserId(10L);
        request.setFromUserId(11L);
        request.setToUserId(12L);
        request.setDate(LocalDate.of(2024, 1, 2));
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(17, 0));
        request.setLocationId("L1");
        when(requestsRepository.findAll()).thenReturn(List.of(request));

        Request result = requestService.approve("req-1", 99L, bot);

        assertEquals(RequestStatus.APPROVED, result.getStatus());
        verify(requestsRepository).save(request);
        verify(auditService).logEvent(eq(99L), eq("request_approved"), eq("request"), eq("req-1"), any(Map.class));
        ArgumentCaptor<Long> recipientCaptor = ArgumentCaptor.forClass(Long.class);
        verify(bot, times(4)).sendMarkdown(recipientCaptor.capture(), contains("Запит підтверджено"), isNull());
        List<Long> recipients = recipientCaptor.getAllValues();
        assertEquals(4, recipients.size());
        assertTrue(recipients.containsAll(List.of(10L, 11L, 12L, 99L)));
    }

    @Test
    void reject_updatesStatus_notifiesAndAudits() {
        Request request = buildRequest();
        when(requestsRepository.findAll()).thenReturn(List.of(request));

        Request result = requestService.reject("req-1", 100L, bot);

        assertEquals(RequestStatus.REJECTED, result.getStatus());
        verify(auditService).logEvent(eq(100L), eq("request_rejected"), eq("request"), eq("req-1"), any(Map.class));
        verify(bot, times(4)).sendMarkdown(anyLong(), contains("Запит відхилено"), isNull());
    }

    @Test
    void cancel_updatesStatus_notifiesAndAudits() {
        Request request = buildRequest();
        when(requestsRepository.findAll()).thenReturn(List.of(request));

        Request result = requestService.cancel("req-1", 101L, bot);

        assertEquals(RequestStatus.CANCELED, result.getStatus());
        verify(auditService).logEvent(eq(101L), eq("request_canceled"), eq("request"), eq("req-1"), any(Map.class));
        verify(bot, times(4)).sendMarkdown(anyLong(), contains("Запит скасовано"), isNull());
    }

    @Test
    void updateStatus_throwsWhenRequestMissing() {
        when(requestsRepository.findAll()).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> requestService.approve("missing", 1L, bot));
        verifyNoInteractions(auditService, bot);
    }

    private Request buildRequest() {
        Request request = new Request();
        request.setRequestId("req-1");
        request.setType(RequestType.COVER);
        request.setInitiatorUserId(10L);
        request.setFromUserId(11L);
        request.setToUserId(12L);
        request.setDate(LocalDate.of(2024, 1, 2));
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(17, 0));
        request.setLocationId("L1");
        return request;
    }
}
