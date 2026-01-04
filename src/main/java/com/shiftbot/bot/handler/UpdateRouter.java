package com.shiftbot.bot.handler;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.bot.ui.CalendarKeyboardBuilder;
import com.shiftbot.model.Request;
import com.shiftbot.model.Shift;
import com.shiftbot.model.User;
import com.shiftbot.model.Location;
import com.shiftbot.model.Request;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.ShiftStatus;
import com.shiftbot.state.ConversationStateStore;
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
    private final UsersRepository usersRepository;
    private final ConversationStateStore stateStore;
    private final CalendarKeyboardBuilder calendarKeyboardBuilder;
    private final LocationsRepository locationsRepository;
    private final ConversationStateStore stateStore;
    private final CoverRequestFsm coverRequestFsm;
    private final AuditService auditService;
    private final ZoneId zoneId;
    private final ConversationStateStore conversationStateStore;
    private final CoverRequestConversationHandler coverRequestConversationHandler;

    public UpdateRouter(AuthService authService, ScheduleService scheduleService, RequestService requestService,
                        CalendarKeyboardBuilder calendarKeyboardBuilder, ZoneId zoneId, ConversationStateStore conversationStateStore) {
        this.authService = authService;
        this.scheduleService = scheduleService;
        this.requestService = requestService;
        this.usersRepository = usersRepository;
        this.stateStore = stateStore;
        this.calendarKeyboardBuilder = calendarKeyboardBuilder;
        this.locationsRepository = locationsRepository;
        this.stateStore = stateStore;
        this.coverRequestFsm = coverRequestFsm;
        this.auditService = auditService;
        this.zoneId = zoneId;
        this.conversationStateStore = conversationStateStore;
        this.coverRequestConversationHandler = new CoverRequestConversationHandler(conversationStateStore, requestService, zoneId);
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

        if (isCancel(text)) {
            coverRequestConversationHandler.handleUserInput(user, text, bot);
            return;
        }

        if (coverRequestConversationHandler.hasConversation(user.getUserId())) {
            coverRequestConversationHandler.handleUserInput(user, text, bot);
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
        } else if (data.startsWith("swapDate:")) {
            LocalDate date = LocalDate.parse(data.substring("swapDate:".length()));
            showSwapShifts(user, date, bot);
        } else if (data.startsWith("swapShift:")) {
            String[] parts = data.split(":", 3);
            if (parts.length == 3) {
                LocalDate date = LocalDate.parse(parts[1]);
                String shiftId = parts[2];
                handleSwapShift(user, date, shiftId, bot);
            }
        } else if (data.startsWith("swapPeer:")) {
            long peerId = Long.parseLong(data.substring("swapPeer:".length()));
            handleSwapPeer(user, peerId, bot);
        } else if (data.startsWith("swapTarget:")) {
            handleSwapTarget(user, data.substring("swapTarget:".length()), bot);
        } else if (data.startsWith("swapPeerAccept:")) {
            handlePeerDecision(user, data.substring("swapPeerAccept:".length()), true, bot);
        } else if (data.startsWith("swapPeerDecline:")) {
            handlePeerDecision(user, data.substring("swapPeerDecline:".length()), false, bot);
        } else if (data.startsWith("swapTmApprove:")) {
            handleTmDecision(data.substring("swapTmApprove:".length()), true, bot);
        } else if (data.startsWith("swapTmReject:")) {
            handleTmDecision(data.substring("swapTmReject:".length()), false, bot);
        } else if ("noop".equals(data)) {
            coverRequestConversationHandler.handleNoop(chatId, bot);
        } else if (data.startsWith("cover:")) {
            LocalDate date = LocalDate.parse(data.substring("cover:".length()));
            requestService.createCoverRequest(user.getUserId(), "unknown", date, TimeUtils.DEFAULT_START, TimeUtils.DEFAULT_END, "Авто створено з меню");
            bot.sendMarkdown(chatId, "Заявка на заміну створена та очікує ТМ", null);
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

    private void sendCoverRequestIntro(User user, BotNotificationPort bot) {
        coverRequestConversationHandler.start(user, bot);
    }

    private InlineKeyboardMarkup mainMenu(User user) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(buttonRow("📅 Мій графік", "M::my"));
        rows.add(buttonRow("🏪 Графік локації", "M::location"));
        rows.add(buttonRow("🔁 Підміни", "M::swap"));
        rows.add(buttonRow("🆘 Потрібна заміна", "M::cover"));
        if (user.getRole() == Role.TM || user.getRole() == Role.SENIOR) {
            rows.add(buttonRow("📥 Мої заявки", "M::requests"));
            rows.add(buttonRow("⏳ Нові користувачі", "M::pendingUsers"));
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
            case PENDING_SWAP -> "Очікує підміну";
            case DRAFT -> "Чернетка";
            case CANCELED -> "Скасовано";
        };
    }

    private void handleUserStatusChange(User actor, String data, boolean activate, BotNotificationPort bot) {
        if (actor.getRole() != Role.TM && actor.getRole() != Role.SENIOR) {
            bot.sendMarkdown(actor.getUserId(), "⛔ Недостатньо прав для цієї дії", null);
            return;
        }
        long targetId;
        try {
            targetId = Long.parseLong(data.substring(data.lastIndexOf(":") + 1));
        } catch (NumberFormatException e) {
            bot.sendMarkdown(actor.getUserId(), "Невірний формат запиту", null);
            return;
        }
        Optional<User> targetOpt = usersRepository.findById(targetId);
        if (targetOpt.isEmpty()) {
            bot.sendMarkdown(actor.getUserId(), "Користувача не знайдено", null);
            return;
        }
        User target = targetOpt.get();
        UserStatus newStatus = activate ? UserStatus.ACTIVE : UserStatus.BLOCKED;
        if (target.getStatus() == newStatus) {
            bot.sendMarkdown(actor.getUserId(), "Статус вже " + newStatus.name(), null);
            return;
        }
        User updated = new User(target.getUserId(), target.getUsername(), target.getFullName(), target.getPhone(), target.getRole(), newStatus, target.getCreatedAt(), target.getCreatedBy());
        usersRepository.updateRow(target.getUserId(), updated);
        auditService.logEvent(actor.getUserId(), activate ? "user_activated" : "user_rejected", "user", String.valueOf(target.getUserId()), Map.of("previousStatus", target.getStatus().name(), "newStatus", newStatus.name()), bot);
        bot.sendMarkdown(actor.getUserId(), MarkdownEscaper.escape("Статус " + target.getFullName() + " → " + newStatus.name()), null);
        bot.sendMarkdown(target.getUserId(), activate ? "✅ Ваш профіль активовано" : "⛔ Ваш профіль відхилено", null);
    }

    private void sendPendingUsers(User actor, BotNotificationPort bot) {
        if (actor.getRole() != Role.TM && actor.getRole() != Role.SENIOR) {
            bot.sendMarkdown(actor.getUserId(), "⛔ Недостатньо прав для цієї дії", null);
            return;
        }
        List<User> pending = usersRepository.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.PENDING)
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (pending.isEmpty()) {
            bot.sendMarkdown(actor.getUserId(), "Немає користувачів у статусі PENDING", null);
            return;
        }
        StringBuilder sb = new StringBuilder("⏳ Користувачі в статусі PENDING:\\n");
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        for (User pendingUser : pending) {
            String username = pendingUser.getUsername() == null ? "" : " " + MarkdownEscaper.escape("(@" + pendingUser.getUsername() + ")");
            sb.append("• ").append(MarkdownEscaper.escape(pendingUser.getFullName()))
                    .append(username).append("\\n");
            buttons.add(List.of(
                    InlineKeyboardButton.builder().text("✅ " + pendingUser.getFullName()).callbackData("user:activate:" + pendingUser.getUserId()).build(),
                    InlineKeyboardButton.builder().text("⛔").callbackData("user:reject:" + pendingUser.getUserId()).build()
            ));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(buttons);
        bot.sendMarkdown(actor.getUserId(), sb.toString(), markup);
    }

    private String buildFullName(Message message) {
        return buildFullName(message.getFrom().getFirstName(), message.getFrom().getLastName());
    }

    private String buildFullName(String first, String last) {
        return StringUtils.trimToEmpty(first + " " + (last == null ? "" : last));
    }

    private boolean isCancel(String text) {
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("cancel") || normalized.equals("/cancel");
    }
}
