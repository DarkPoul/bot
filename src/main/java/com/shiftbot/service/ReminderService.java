package com.shiftbot.service;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.model.Shift;
import com.shiftbot.service.ScheduleService;
import com.shiftbot.util.TimeUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

public class ReminderService {
    private final ScheduleService scheduleService;
    private final BotNotificationPort botNotificationPort;
    private final ZoneId zoneId;

    public ReminderService(ScheduleService scheduleService, BotNotificationPort botNotificationPort, ZoneId zoneId) {
        this.scheduleService = scheduleService;
        this.botNotificationPort = botNotificationPort;
        this.zoneId = zoneId;
    }

    public void sendTomorrowReminders(List<Long> userIds) {
        LocalDate tomorrow = TimeUtils.today(zoneId).plusDays(1);
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

    public void sendConfirmationPrompt(Shift shift) {
        String text = "✅ Підтвердіть вихід: " + TimeUtils.humanDate(shift.getDate(), zoneId) + " " +
                TimeUtils.humanTimeRange(shift.getStartTime(), shift.getEndTime());
        botNotificationPort.sendMarkdown(shift.getUserId(), text, null);
    }
}
