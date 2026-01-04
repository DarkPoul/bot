package com.shiftbot.service;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.model.Request;
import com.shiftbot.model.Shift;
import com.shiftbot.model.User;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import com.shiftbot.repository.RequestsRepository;
import com.shiftbot.repository.UsersRepository;
import com.shiftbot.util.MarkdownEscaper;
import com.shiftbot.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ReminderService {
    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final Duration INITIATOR_STALE_THRESHOLD = Duration.ofHours(6);
    private static final Duration INITIATOR_REMINDER_INTERVAL = Duration.ofHours(1);
    private static final LocalTime SELLER_REMINDER_TIME = LocalTime.of(18, 0);
    private static final LocalTime TM_REMINDER_TIME = LocalTime.of(9, 0);

    private final ScheduleService scheduleService;
    private final UsersRepository usersRepository;
    private final RequestsRepository requestsRepository;
    private final BotNotificationPort botNotificationPort;
    private final ZoneId zoneId;
    private final Clock clock;
    private ScheduledExecutorService scheduler;

    public ReminderService(ScheduleService scheduleService, UsersRepository usersRepository,
                           RequestsRepository requestsRepository, BotNotificationPort botNotificationPort, ZoneId zoneId) {
        this(scheduleService, usersRepository, requestsRepository, botNotificationPort, zoneId, Clock.system(zoneId));
    }

    public ReminderService(ScheduleService scheduleService, UsersRepository usersRepository,
                           RequestsRepository requestsRepository, BotNotificationPort botNotificationPort,
                           ZoneId zoneId, Clock clock) {
        this.scheduleService = scheduleService;
        this.usersRepository = usersRepository;
        this.requestsRepository = requestsRepository;
        this.botNotificationPort = botNotificationPort;
        this.zoneId = zoneId;
        this.clock = clock;
    }

    public void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newScheduledThreadPool(2);
        scheduleDailyAt(SELLER_REMINDER_TIME, this::sendTomorrowReminders);
        scheduleDailyAt(TM_REMINDER_TIME, this::sendPendingTmReminders);
        scheduler.scheduleAtFixedRate(this::sendInitiatorReminders, INITIATOR_REMINDER_INTERVAL.toMinutes(),
                INITIATOR_REMINDER_INTERVAL.toMinutes(), TimeUnit.MINUTES);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public void sendTomorrowReminders(List<Long> userIds) {
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        for (Long userId : userIds) {
            List<Shift> shifts = scheduleService.shiftsForDate(userId, tomorrow);
            if (!shifts.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("🔔 Нагадування про зміну на ").append(TimeUtils.humanDate(tomorrow, zoneId)).append("\\n");
                for (Shift shift : shifts) {
                    sb.append("• ").append(TimeUtils.humanTimeRange(shift.getStartTime(), shift.getEndTime()))
                            .append(" | ").append(shift.getLocationId())
                            .append("\\n");
                }
                botNotificationPort.sendMarkdown(userId, sb.toString(), null);
            }
        }
    }

    public void sendTomorrowReminders() {
        List<Long> sellers = usersRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.SELLER && u.getStatus() == UserStatus.ACTIVE)
                .map(User::getUserId)
                .toList();
        sendTomorrowReminders(sellers);
    }

    public void sendConfirmationPrompt(Shift shift) {
        String text = "✅ Підтвердіть вихід: " + TimeUtils.humanDate(shift.getDate(), zoneId) + " " +
                TimeUtils.humanTimeRange(shift.getStartTime(), shift.getEndTime());
        botNotificationPort.sendMarkdown(shift.getUserId(), text, null);
    }

    void sendPendingTmReminders() {
        List<Request> pendingRequests = requestsRepository.findAll().stream()
                .filter(r -> r.getStatus() == RequestStatus.WAIT_TM)
                .filter(r -> !r.getDate().isBefore(LocalDate.now(clock)))
                .collect(Collectors.toList());
        if (pendingRequests.isEmpty()) {
            return;
        }
        List<Long> tmIds = usersRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.TM && u.getStatus() == UserStatus.ACTIVE)
                .map(User::getUserId)
                .toList();
        if (tmIds.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("🕒 Запити в очікуванні підтвердження ТМ:\\n");
        for (Request request : pendingRequests) {
            sb.append("• ").append(TimeUtils.humanDate(request.getDate(), zoneId))
                    .append(" ")
                    .append(TimeUtils.humanTimeRange(request.getStartTime(), request.getEndTime()))
                    .append(" | ").append(request.getLocationId())
                    .append("\\n");
        }
        for (Long tmId : tmIds) {
            botNotificationPort.sendMarkdown(tmId, MarkdownEscaper.escape(sb.toString()), null);
        }
    }

    void sendInitiatorReminders() {
        Instant now = Instant.now(clock);
        List<Request> staleRequests = requestsRepository.findAll().stream()
                .filter(r -> r.getStatus() == RequestStatus.WAIT_TM || r.getStatus() == RequestStatus.WAIT_OTHER)
                .filter(r -> r.getUpdatedAt() != null && r.getUpdatedAt().isBefore(now.minus(INITIATOR_STALE_THRESHOLD)))
                .filter(r -> !r.getDate().isBefore(LocalDate.now(clock)))
                .collect(Collectors.toList());
        for (Request request : staleRequests) {
            String text = "⏳ Ваш запит все ще очікує відповіді: " +
                    TimeUtils.humanDate(request.getDate(), zoneId) + " " +
                    TimeUtils.humanTimeRange(request.getStartTime(), request.getEndTime());
            botNotificationPort.sendMarkdown(request.getInitiatorUserId(), MarkdownEscaper.escape(text), null);
        }
    }

    private void scheduleDailyAt(LocalTime time, Runnable runnable) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime firstRun = now.with(time);
        if (now.toLocalTime().isAfter(time)) {
            firstRun = firstRun.plusDays(1);
        }
        long initialDelaySeconds = Duration.between(now, firstRun).toSeconds();
        scheduler.scheduleAtFixedRate(() -> safeExecute(runnable), initialDelaySeconds, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
    }

    private void safeExecute(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.warn("Reminder task failed", e);
        }
    }
}
