package com.shiftbot.service;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.model.Request;
import com.shiftbot.model.Shift;
import com.shiftbot.model.User;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.ShiftSource;
import com.shiftbot.model.enums.ShiftStatus;
import com.shiftbot.model.enums.UserStatus;
import com.shiftbot.repository.RequestsRepository;
import com.shiftbot.repository.UsersRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderServiceTest {

    private final ZoneId zoneId = ZoneId.of("Europe/Kyiv");

    @Test
    void sendsTomorrowReminderForActiveSellers() {
        ScheduleService scheduleService = mock(ScheduleService.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        RequestsRepository requestsRepository = mock(RequestsRepository.class);
        BotNotificationPort bot = mock(BotNotificationPort.class);
        Clock clock = Clock.fixed(ZonedDateTime.of(2024, 3, 10, 10, 0, 0, 0, zoneId).toInstant(), zoneId);

        User seller = new User(100L, "seller", "Seller One", "", Role.SELLER, UserStatus.ACTIVE, null, null);
        when(usersRepository.findAll()).thenReturn(List.of(seller));

        Shift shift = new Shift("s1", LocalDate.of(2024, 3, 11), LocalTime.of(10, 0), LocalTime.of(18, 0), "loc1", 100L,
                ShiftStatus.APPROVED, ShiftSource.MONTH_PLAN, null, null);
        when(scheduleService.shiftsForDate(eq(100L), eq(LocalDate.of(2024, 3, 11)))).thenReturn(List.of(shift));

        ReminderService reminderService = new ReminderService(scheduleService, usersRepository, requestsRepository, bot, zoneId, clock);

        reminderService.sendTomorrowReminders();

        verify(bot).sendMarkdown(eq(100L), any(), eq(null));
    }

    @Test
    void notifiesTmAboutPendingRequests() {
        ScheduleService scheduleService = mock(ScheduleService.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        RequestsRepository requestsRepository = mock(RequestsRepository.class);
        BotNotificationPort bot = mock(BotNotificationPort.class);
        Clock clock = Clock.fixed(ZonedDateTime.of(2024, 3, 10, 8, 0, 0, 0, zoneId).toInstant(), zoneId);

        User tm = new User(200L, "tm", "Tm User", "", Role.TM, UserStatus.ACTIVE, null, null);
        when(usersRepository.findAll()).thenReturn(List.of(tm));

        Request request = new Request();
        request.setInitiatorUserId(300L);
        request.setStatus(RequestStatus.WAIT_TM);
        request.setDate(LocalDate.of(2024, 3, 10));
        request.setStartTime(LocalTime.of(12, 0));
        request.setEndTime(LocalTime.of(18, 0));
        request.setLocationId("loc-1");
        request.setUpdatedAt(Instant.now(clock));
        when(requestsRepository.findAll()).thenReturn(List.of(request));

        ReminderService reminderService = new ReminderService(scheduleService, usersRepository, requestsRepository, bot, zoneId, clock);

        reminderService.sendPendingTmReminders();

        verify(bot).sendMarkdown(eq(200L), any(), eq(null));
    }

    @Test
    void remindsInitiatorWhenRequestStale() {
        ScheduleService scheduleService = mock(ScheduleService.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        RequestsRepository requestsRepository = mock(RequestsRepository.class);
        BotNotificationPort bot = mock(BotNotificationPort.class);
        Clock clock = Clock.fixed(ZonedDateTime.of(2024, 3, 10, 18, 0, 0, 0, zoneId).toInstant(), zoneId);

        Request request = new Request();
        request.setInitiatorUserId(400L);
        request.setStatus(RequestStatus.WAIT_OTHER);
        request.setDate(LocalDate.of(2024, 3, 11));
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(15, 0));
        request.setLocationId("loc-2");
        request.setUpdatedAt(Instant.parse("2024-03-10T09:00:00Z"));
        when(requestsRepository.findAll()).thenReturn(List.of(request));

        ReminderService reminderService = new ReminderService(scheduleService, usersRepository, requestsRepository, bot, zoneId, clock);

        reminderService.sendInitiatorReminders();

        verify(bot).sendMarkdown(eq(400L), any(), eq(null));
    }
}
