package com.shiftbot.bot.handler;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.bot.ui.CalendarKeyboardBuilder;
import com.shiftbot.model.Shift;
import com.shiftbot.model.User;
import com.shiftbot.model.Location;
import com.shiftbot.model.Request;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.ShiftStatus;
import com.shiftbot.service.AuditService;
import com.shiftbot.service.AuthService;
import com.shiftbot.service.RequestService;
import com.shiftbot.service.ScheduleService;
import com.shiftbot.repository.LocationsRepository;
import com.shiftbot.state.ConversationState;
import com.shiftbot.state.ConversationStateStore;
import com.shiftbot.state.CoverRequestFsm;
import com.shiftbot.util.MarkdownEscaper;
import com.shiftbot.util.TimeUtils;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

public class UpdateRouter {
    private final AuthService authService;
    private final ScheduleService scheduleService;
    private final RequestService requestService;
    private final CalendarKeyboardBuilder calendarKeyboardBuilder;
    private final LocationsRepository locationsRepository;
    private final ConversationStateStore stateStore;
    private final CoverRequestFsm coverRequestFsm;
    private final AuditService auditService;
    private final ZoneId zoneId;

    public UpdateRouter(AuthService authService, ScheduleService scheduleService, RequestService requestService,
                        CalendarKeyboardBuilder calendarKeyboardBuilder, LocationsRepository locationsRepository,
                        ConversationStateStore stateStore, CoverRequestFsm coverRequestFsm, AuditService auditService,
                        ZoneId zoneId) {
        this.authService = authService;
        this.scheduleService = scheduleService;
        this.requestService = requestService;
        this.calendarKeyboardBuilder = calendarKeyboardBuilder;
        this.locationsRepository = locationsRepository;
        this.stateStore = stateStore;
        this.coverRequestFsm = coverRequestFsm;
        this.auditService = auditService;
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
        Optional<ConversationState> state = stateStore.get(chatId);

        if (isAbortCommand(text)) {
            stateStore.clear(chatId);
            bot.sendMarkdown(chatId, "⏹️ Заявка скасована", mainMenu(user));
            return;
        }

        if (state.isPresent() && coverRequestFsm.supports(state.get())) {
            if (handleCoverMessage(user, message, state.get(), bot)) {
                return;
            }
        }

        if (text.startsWith("/start")) {
            bot.sendMarkdown(chatId, "👋 Вітаємо, " + MarkdownEscaper.escape(user.getFullName()) + "!", mainMenu(user));
            return;
        }

        switch (text) {
            case "Мій графік", "📅 Мій графік" -> sendMySchedule(user, bot);
            case "Потрібна заміна", "🆘 Потрібна заміна" -> startCoverFlow(user, bot);
            case "📥 Мої заявки" -> sendTmRequests(user, bot);
            default -> bot.sendMarkdown(chatId, "Оберіть дію з меню нижче", mainMenu(user));
        }
    }

    private void handleCallback(CallbackQuery callback, BotNotificationPort bot) {
        String data = callback.getData();
        Long chatId = callback.getMessage().getChatId();
        User user = authService.onboard(chatId, callback.getFrom().getUserName(), buildFullName(callback.getFrom().getFirstName(), callback.getFrom().getLastName()));
        Optional<ConversationState> state = stateStore.get(chatId);

        if (state.isPresent() && coverRequestFsm.supports(state.get()) && data.startsWith("cover:")) {
            handleCoverCallback(user, callback, state.get(), bot);
            return;
        }

        if (data.startsWith("calendar:")) {
            LocalDate date = LocalDate.parse(data.replace("calendar:", ""));
            List<Shift> shifts = scheduleService.shiftsForDate(user.getUserId(), date);
            if (shifts.isEmpty()) {
                bot.sendMarkdown(chatId, "⬜ Немає змін на " + TimeUtils.humanDate(date, zoneId), null);
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("📅 ").append(TimeUtils.humanDate(date, zoneId)).append("\n");
                for (Shift shift : shifts) {
                    sb.append("• ").append(TimeUtils.humanTimeRange(shift.getStartTime(), shift.getEndTime()))
                            .append(" | ").append(shift.getLocationId())
                            .append(" | ").append(statusLabel(shift.getStatus()))
                            .append("\n");
                }
                bot.sendMarkdown(chatId, MarkdownEscaper.escape(sb.toString()), null);
            }
        } else if ("noop".equals(data)) {
            // ignore
        } else if (data.startsWith("M::")) {
            String action = data.substring("M::".length());
            switch (action) {
                case "my" -> sendMySchedule(user, bot);
                case "cover" -> startCoverFlow(user, bot);
                case "requests" -> sendTmRequests(user, bot);
                default -> bot.sendMarkdown(chatId, "Меню в розробці", null);
            }
        } else if (data.startsWith("request:approve:")) {
            handleTmDecision(user, data.substring("request:approve:".length()), true, bot);
        } else if (data.startsWith("request:reject:")) {
            handleTmDecision(user, data.substring("request:reject:".length()), false, bot);
        } else if (data.startsWith("cover:")) {
            ConversationState newState = coverRequestFsm.start();
            stateStore.put(user.getUserId(), newState);
            handleCoverCallback(user, callback, newState, bot);
        }
    }

    private void sendMySchedule(User user, BotNotificationPort bot) {
        LocalDate today = TimeUtils.today(zoneId);
        Map<LocalDate, ShiftStatus> statuses = scheduleService.calendarStatuses(user.getUserId(), today);
        InlineKeyboardMarkup calendar = calendarKeyboardBuilder.buildMonth(today, statuses, "calendar:");
        String text = "📅 Мій графік на " + today.getMonth() + ": оберіть день";
        bot.sendMarkdown(user.getUserId(), MarkdownEscaper.escape(text), calendar);
    }

    private void startCoverFlow(User user, BotNotificationPort bot) {
        ConversationState state = coverRequestFsm.start();
        stateStore.put(user.getUserId(), state);
        promptCoverDate(user, bot);
    }

    private void promptCoverDate(User user, BotNotificationPort bot) {
        LocalDate startMonth = TimeUtils.today(zoneId);
        InlineKeyboardMarkup calendar = calendarKeyboardBuilder.buildMonth(startMonth, Collections.emptyMap(), "cover:date:");
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>(calendar.getKeyboard());
        keyboard.add(Collections.singletonList(InlineKeyboardButton.builder().text("❌ Скасувати").callbackData("cover:abort").build()));
        calendar.setKeyboard(keyboard);
        bot.sendMarkdown(user.getUserId(), "🆘 Оберіть дату зміни (календар або формат YYYY-MM-DD)", calendar);
    }

    private void promptCoverTime(User user, ConversationState nextState, BotNotificationPort bot) {
        stateStore.put(user.getUserId(), nextState);
        bot.sendMarkdown(user.getUserId(), "⏱️ Вкажіть час у форматі HH:mm-HH:mm", null);
    }

    private void promptCoverLocation(User user, ConversationState current, BotNotificationPort bot) {
        stateStore.put(user.getUserId(), coverRequestFsm.advance(current, CoverRequestFsm.Step.LOCATION, null));
        List<Location> activeLocations = locationsRepository.findActive();
        if (activeLocations.isEmpty()) {
            bot.sendMarkdown(user.getUserId(), "⚠️ Немає активних локацій, введіть ID вручну", null);
            return;
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Location location : activeLocations) {
            rows.add(Collections.singletonList(InlineKeyboardButton.builder()
                    .text(location.getName())
                    .callbackData("cover:loc:" + location.getLocationId())
                    .build()));
        }
        rows.add(Collections.singletonList(InlineKeyboardButton.builder().text("❌ Скасувати").callbackData("cover:abort").build()));
        markup.setKeyboard(rows);
        bot.sendMarkdown(user.getUserId(), "📍 Оберіть локацію", markup);
    }

    private void promptCoverComment(User user, ConversationState current, BotNotificationPort bot) {
        stateStore.put(user.getUserId(), coverRequestFsm.advance(current, CoverRequestFsm.Step.COMMENT, null));
        bot.sendMarkdown(user.getUserId(), "💬 Додайте коментар або напишіть '-' щоб пропустити", null);
    }

    private boolean handleCoverMessage(User user, Message message, ConversationState state, BotNotificationPort bot) {
        String text = message.getText();
        switch (coverRequestFsm.currentStep(state)) {
            case DATE -> {
                Optional<LocalDate> parsed = parseDate(text);
                if (parsed.isEmpty()) {
                    bot.sendMarkdown(user.getUserId(), "⚠️ Дата має бути у форматі YYYY-MM-DD", null);
                    return true;
                }
                Map<String, String> data = new HashMap<>(state.getData());
                data.put(CoverRequestFsm.DATE_KEY, parsed.get().toString());
                ConversationState next = coverRequestFsm.advance(state, CoverRequestFsm.Step.TIME, data);
                promptCoverTime(user, next, bot);
                return true;
            }
            case TIME -> {
                Optional<LocalTime[]> range = parseTimeRange(text);
                if (range.isEmpty()) {
                    bot.sendMarkdown(user.getUserId(), "⚠️ Час має бути у форматі HH:mm-HH:mm", null);
                    return true;
                }
                Map<String, String> data = new HashMap<>(state.getData());
                data.put(CoverRequestFsm.START_KEY, range.get()[0].toString());
                data.put(CoverRequestFsm.END_KEY, range.get()[1].toString());
                ConversationState next = coverRequestFsm.advance(state, CoverRequestFsm.Step.LOCATION, data);
                stateStore.put(user.getUserId(), next);
                promptCoverLocation(user, next, bot);
                return true;
            }
            case LOCATION -> {
                String locationId = text.trim();
                Map<String, String> data = new HashMap<>(state.getData());
                data.put(CoverRequestFsm.LOCATION_KEY, locationId);
                ConversationState next = coverRequestFsm.advance(state, CoverRequestFsm.Step.COMMENT, data);
                stateStore.put(user.getUserId(), next);
                promptCoverComment(user, next, bot);
                return true;
            }
            case COMMENT -> {
                Map<String, String> data = new HashMap<>(state.getData());
                data.put(CoverRequestFsm.COMMENT_KEY, text);
                ConversationState next = coverRequestFsm.advance(state, CoverRequestFsm.Step.COMMENT, data);
                completeCoverRequest(user, next, bot);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void handleCoverCallback(User user, CallbackQuery callback, ConversationState state, BotNotificationPort bot) {
        String data = callback.getData();
        if (data.startsWith("cover:abort")) {
            stateStore.clear(user.getUserId());
            bot.sendMarkdown(callback.getMessage().getChatId(), "⏹️ Заявка скасована", mainMenu(user));
            return;
        }
        if (data.startsWith("cover:date:")) {
            LocalDate date = LocalDate.parse(data.substring("cover:date:".length()));
            Map<String, String> params = new HashMap<>(state.getData());
            params.put(CoverRequestFsm.DATE_KEY, date.toString());
            ConversationState next = coverRequestFsm.advance(state, CoverRequestFsm.Step.TIME, params);
            promptCoverTime(user, next, bot);
            return;
        }
        if (data.startsWith("cover:loc:")) {
            String locationId = data.substring("cover:loc:".length());
            Map<String, String> params = new HashMap<>(state.getData());
            params.put(CoverRequestFsm.LOCATION_KEY, locationId);
            ConversationState next = coverRequestFsm.advance(state, CoverRequestFsm.Step.COMMENT, params);
            stateStore.put(user.getUserId(), next);
            promptCoverComment(user, next, bot);
        }
    }

    private void completeCoverRequest(User user, ConversationState state, BotNotificationPort bot) {
        try {
            LocalDate date = LocalDate.parse(state.getData().get(CoverRequestFsm.DATE_KEY));
            LocalTime start = LocalTime.parse(state.getData().getOrDefault(CoverRequestFsm.START_KEY, TimeUtils.DEFAULT_START.toString()));
            LocalTime end = LocalTime.parse(state.getData().getOrDefault(CoverRequestFsm.END_KEY, TimeUtils.DEFAULT_END.toString()));
            String locationId = state.getData().getOrDefault(CoverRequestFsm.LOCATION_KEY, "unknown");
            String comment = state.getData().getOrDefault(CoverRequestFsm.COMMENT_KEY, "-");
            Request request = requestService.createCoverRequest(user.getUserId(), locationId, date, start, end, comment);
            stateStore.clear(user.getUserId());
            bot.sendMarkdown(user.getUserId(), "✅ Заявка створена та очікує підтвердження ТМ\n" + MarkdownEscaper.escape(formatRequest(request)), null);
            auditService.logEvent(user.getUserId(), "Створена заявка на заміну", "REQUEST", request.getRequestId(), Map.of(
                    "status", request.getStatus().name(),
                    "locationId", request.getLocationId()
            ));
        } catch (Exception e) {
            bot.sendMarkdown(user.getUserId(), "⚠️ Не вдалося створити заявку: " + MarkdownEscaper.escape(e.getMessage()), null);
        }
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

    private void sendTmRequests(User user, BotNotificationPort bot) {
        if (user.getRole() != Role.TM && user.getRole() != Role.SENIOR) {
            bot.sendMarkdown(user.getUserId(), "⛔ Недостатньо прав", null);
            return;
        }
        List<Request> pending = requestService.pendingForTm();
        if (pending.isEmpty()) {
            bot.sendMarkdown(user.getUserId(), "✅ Немає заявок, що очікують рішення", null);
            return;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        StringBuilder text = new StringBuilder("📥 Очікують на ТМ:\n");
        for (Request request : pending) {
            text.append("• ").append(formatRequest(request)).append("\n");
            rows.add(Arrays.asList(
                    InlineKeyboardButton.builder()
                            .text("✅ Апрув " + shortId(request.getRequestId()))
                            .callbackData("request:approve:" + request.getRequestId())
                            .build(),
                    InlineKeyboardButton.builder()
                            .text("❌ Відхилити " + shortId(request.getRequestId()))
                            .callbackData("request:reject:" + request.getRequestId())
                            .build()
            ));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        bot.sendMarkdown(user.getUserId(), MarkdownEscaper.escape(text.toString()), markup);
    }

    private void handleTmDecision(User user, String requestId, boolean approve, BotNotificationPort bot) {
        if (user.getRole() != Role.TM && user.getRole() != Role.SENIOR) {
            bot.sendMarkdown(user.getUserId(), "⛔ Недостатньо прав", null);
            return;
        }
        try {
            Request updated = approve ? requestService.approveByTm(requestId) : requestService.rejectByTm(requestId);
            String action = approve ? "✅ Заявка погоджена" : "❌ Заявка відхилена";
            String response = MarkdownEscaper.escape(action + "\n" + formatRequest(updated));
            bot.sendMarkdown(user.getUserId(), response, null);
            bot.sendMarkdown(updated.getInitiatorUserId(), MarkdownEscaper.escape("ℹ️ ТМ оновив вашу заявку\n" + formatRequest(updated)), null);
            auditService.logEvent(user.getUserId(), action, "REQUEST", updated.getRequestId(), Map.of(
                    "status", updated.getStatus().name(),
                    "initiator", updated.getInitiatorUserId()
            ));
        } catch (Exception e) {
            bot.sendMarkdown(user.getUserId(), "⚠️ Помилка: " + MarkdownEscaper.escape(e.getMessage()), null);
        }
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

    private Optional<LocalDate> parseDate(String text) {
        try {
            return Optional.of(LocalDate.parse(text.trim()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<LocalTime[]> parseTimeRange(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String sanitized = text.replace("–", "-").replace("—", "-");
        String[] parts = sanitized.split("-");
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            LocalTime start = LocalTime.parse(parts[0].trim());
            LocalTime end = LocalTime.parse(parts[1].trim());
            return Optional.of(new LocalTime[]{start, end});
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String formatRequest(Request request) {
        String locationName = locationsRepository.findById(request.getLocationId())
                .map(Location::getName)
                .orElse(request.getLocationId());
        return TimeUtils.humanDate(request.getDate(), zoneId) + " " +
                TimeUtils.humanTimeRange(request.getStartTime(), request.getEndTime()) + " | " +
                locationName + " | " + request.getStatus().name();
    }

    private String shortId(String requestId) {
        if (requestId == null || requestId.length() < 8) return requestId;
        return requestId.substring(0, 8);
    }

    private boolean isAbortCommand(String text) {
        return "/stop".equalsIgnoreCase(text) || "/cancel".equalsIgnoreCase(text) || "скасувати".equalsIgnoreCase(text);
    }
}
