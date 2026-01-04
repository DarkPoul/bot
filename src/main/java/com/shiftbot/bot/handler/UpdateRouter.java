package com.shiftbot.bot.handler;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.bot.ui.CalendarKeyboardBuilder;
import com.shiftbot.model.Shift;
import com.shiftbot.model.User;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.ShiftStatus;
import com.shiftbot.service.AuthService;
import com.shiftbot.service.RequestService;
import com.shiftbot.service.ScheduleService;
import com.shiftbot.util.MarkdownEscaper;
import com.shiftbot.util.TimeUtils;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class UpdateRouter {
    private final AuthService authService;
    private final ScheduleService scheduleService;
    private final RequestService requestService;
    private final CalendarKeyboardBuilder calendarKeyboardBuilder;
    private final ZoneId zoneId;

    public UpdateRouter(AuthService authService, ScheduleService scheduleService, RequestService requestService,
                        CalendarKeyboardBuilder calendarKeyboardBuilder, ZoneId zoneId) {
        this.authService = authService;
        this.scheduleService = scheduleService;
        this.requestService = requestService;
        this.calendarKeyboardBuilder = calendarKeyboardBuilder;
        this.zoneId = zoneId;
    }

    public void handle(Update update, BotNotificationPort bot) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleMessage(update.getMessage(), bot);
        } else if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery(), bot);
        }
    }

    private void handleMessage(Message message, BotNotificationPort bot) {
        Long chatId = message.getChatId();
        String text = message.getText();
        User user = authService.onboard(chatId, message.getFrom().getUserName(), buildFullName(message));

        if (text.startsWith("/start")) {
            bot.sendMarkdown(chatId, "👋 Вітаємо, " + MarkdownEscaper.escape(user.getFullName()) + "!", mainMenu(user));
            return;
        }

        switch (text) {
            case "Мій графік", "📅 Мій графік" -> sendMySchedule(user, bot);
            case "Потрібна заміна", "🆘 Потрібна заміна" -> sendCoverRequestIntro(user, bot);
            default -> bot.sendMarkdown(chatId, "Оберіть дію з меню нижче", mainMenu(user));
        }
    }

    private void handleCallback(CallbackQuery callback, BotNotificationPort bot) {
        String data = callback.getData();
        Long chatId = callback.getMessage().getChatId();
        User user = authService.onboard(chatId, callback.getFrom().getUserName(), buildFullName(callback.getFrom().getFirstName(), callback.getFrom().getLastName()));

        if (data.startsWith("calendar:")) {
            LocalDate date = LocalDate.parse(data.replace("calendar:", ""));
            List<Shift> shifts = scheduleService.shiftsForDate(user.getUserId(), date);
            if (shifts.isEmpty()) {
                bot.sendMarkdown(chatId, "⬜ Немає змін на " + TimeUtils.humanDate(date, zoneId), null);
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("📅 ").append(TimeUtils.humanDate(date, zoneId)).append("\\n");
                for (Shift shift : shifts) {
                    sb.append("• ").append(TimeUtils.humanTimeRange(shift.getStartTime(), shift.getEndTime()))
                            .append(" | ").append(shift.getLocationId())
                            .append(" | ").append(statusLabel(shift.getStatus()))
                            .append("\\n");
                }
                bot.sendMarkdown(chatId, MarkdownEscaper.escape(sb.toString()), null);
            }
        } else if ("noop".equals(data)) {
            // ignore
        } else if (data.startsWith("cover:")) {
            LocalDate date = LocalDate.parse(data.substring("cover:".length()));
            try {
                requestService.createCoverRequest(user.getUserId(), "unknown", date, TimeUtils.DEFAULT_START, TimeUtils.DEFAULT_END, "Авто створено з меню");
                bot.sendMarkdown(chatId, "Заявка на заміну створена та очікує ТМ", null);
            } catch (IllegalArgumentException ex) {
                bot.sendMarkdown(chatId, MarkdownEscaper.escape(ex.getMessage()), null);
            }
        } else if (data.startsWith("M::")) {
            String action = data.substring("M::".length());
            switch (action) {
                case "my" -> sendMySchedule(user, bot);
                case "cover" -> sendCoverRequestIntro(user, bot);
                default -> bot.sendMarkdown(chatId, "Меню в розробці", null);
            }
        }
    }

    private void sendMySchedule(User user, BotNotificationPort bot) {
        LocalDate today = TimeUtils.today(zoneId);
        Map<LocalDate, ShiftStatus> statuses = scheduleService.calendarStatuses(user.getUserId(), today);
        InlineKeyboardMarkup calendar = calendarKeyboardBuilder.buildMonth(today, statuses, "calendar:");
        String text = "📅 Мій графік на " + today.getMonth() + ": оберіть день";
        bot.sendMarkdown(user.getUserId(), MarkdownEscaper.escape(text), calendar);
    }

    private void sendCoverRequestIntro(User user, BotNotificationPort bot) {
        LocalDate tomorrow = TimeUtils.today(zoneId).plusDays(1);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Collections.singletonList(InlineKeyboardButton.builder()
                .text("🚑 Попросити заміну на завтра")
                .callbackData("cover:" + tomorrow)
                .build()));
        markup.setKeyboard(rows);
        String text = "🆘 Потрібна заміна? Оберіть дату";
        bot.sendMarkdown(user.getUserId(), MarkdownEscaper.escape(text), markup);
    }

    private InlineKeyboardMarkup mainMenu(User user) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(buttonRow("📅 Мій графік", "M::my"));
        rows.add(buttonRow("🏪 Графік локації", "M::location"));
        rows.add(buttonRow("🔁 Підміни", "M::swap"));
        rows.add(buttonRow("🆘 Потрібна заміна", "M::cover"));
        if (user.getRole() == Role.TM || user.getRole() == Role.SENIOR) {
            rows.add(buttonRow("📥 Мої заявки", "M::requests"));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    private List<InlineKeyboardButton> buttonRow(String text, String callback) {
        return Collections.singletonList(InlineKeyboardButton.builder().text(text).callbackData(callback).build());
    }

    private String statusLabel(ShiftStatus status) {
        return switch (status) {
            case APPROVED -> "Затверджено";
            case PENDING_TM -> "Очікує ТМ";
            case DRAFT -> "Чернетка";
            case CANCELED -> "Скасовано";
        };
    }

    private String buildFullName(Message message) {
        return buildFullName(message.getFrom().getFirstName(), message.getFrom().getLastName());
    }

    private String buildFullName(String first, String last) {
        return StringUtils.trimToEmpty(first + " " + (last == null ? "" : last));
    }
}
