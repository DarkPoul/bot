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
    private final UsersRepository usersRepository;
    private final ConversationStateStore stateStore;
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
        this.usersRepository = usersRepository;
        this.stateStore = stateStore;
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

    private void startSwapFlow(User user, BotNotificationPort bot) {
        ConversationState state = new ConversationState("SWAP_SELECT_DATE");
        stateStore.put(user.getUserId(), state);
        LocalDate today = TimeUtils.today(zoneId);
        Map<LocalDate, ShiftStatus> statuses = scheduleService.calendarStatuses(user.getUserId(), today);
        InlineKeyboardMarkup calendar = calendarKeyboardBuilder.buildMonth(today, statuses, "swapDate:");
        bot.sendMarkdown(user.getUserId(), MarkdownEscaper.escape("🔁 Оберіть день своєї зміни для підміни"), calendar);
    }

    private void showSwapShifts(User user, LocalDate date, BotNotificationPort bot) {
        List<Shift> shifts = scheduleService.shiftsForDate(user.getUserId(), date);
        if (shifts.isEmpty()) {
            bot.sendMarkdown(user.getUserId(), "⬜ Немає змін на " + TimeUtils.humanDate(date, zoneId), null);
            return;
        }
        ConversationState state = new ConversationState("SWAP_SELECT_SHIFT");
        state.getData().put("date", date.toString());
        stateStore.put(user.getUserId(), state);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Shift shift : shifts) {
            String label = TimeUtils.humanTimeRange(shift.getStartTime(), shift.getEndTime()) + " | " + shift.getLocationId();
            rows.add(Collections.singletonList(InlineKeyboardButton.builder()
                    .text(label)
                    .callbackData("swapShift:" + date + ":" + shift.getShiftId())
                    .build()));
        }
        markup.setKeyboard(rows);
        bot.sendMarkdown(user.getUserId(), MarkdownEscaper.escape("Оберіть зміну для підміни"), markup);
    }

    private void handleSwapShift(User user, LocalDate date, String shiftId, BotNotificationPort bot) {
        Shift fromShift = findShift(scheduleService.shiftsForDate(user.getUserId(), date), shiftId);
        if (fromShift == null) {
            bot.sendMarkdown(user.getUserId(), "Зміну не знайдено, спробуйте ще раз", null);
            return;
        }
        ConversationState state = new ConversationState("SWAP_SELECT_PEER");
        state.getData().put("date", date.toString());
        state.getData().put("shiftId", shiftId);
        stateStore.put(user.getUserId(), state);
        promptSwapPeer(user, fromShift, date, bot);
    }

    private void promptSwapPeer(User user, Shift fromShift, LocalDate date, BotNotificationPort bot) {
        List<User> peers = usersRepository.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && u.getUserId() != user.getUserId())
                .toList();
        if (peers.isEmpty()) {
            bot.sendMarkdown(user.getUserId(), "Активних колег не знайдено", null);
            return;
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        for (User peer : peers) {
            currentRow.add(InlineKeyboardButton.builder()
                    .text(peer.getFullName())
                    .callbackData("swapPeer:" + peer.getUserId())
                    .build());
            if (currentRow.size() == 2) {
                rows.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }
        markup.setKeyboard(rows);
        String text = "Зміна " + TimeUtils.humanDate(date, zoneId) + " " +
                TimeUtils.humanTimeRange(fromShift.getStartTime(), fromShift.getEndTime()) +
                " (" + fromShift.getLocationId() + "). Оберіть отримувача підміни:";
        bot.sendMarkdown(user.getUserId(), MarkdownEscaper.escape(text), markup);
    }

    private void handleSwapPeer(User user, long peerId, BotNotificationPort bot) {
        Optional<ConversationState> stateOpt = stateStore.get(user.getUserId());
        if (stateOpt.isEmpty()) {
            startSwapFlow(user, bot);
            return;
        }
        ConversationState state = stateOpt.get();
        String dateStr = state.getData().get("date");
        String shiftId = state.getData().get("shiftId");
        if (dateStr == null || shiftId == null) {
            startSwapFlow(user, bot);
            return;
        }
        LocalDate date = LocalDate.parse(dateStr);
        Shift fromShift = findShift(scheduleService.shiftsForDate(user.getUserId(), date), shiftId);
        if (fromShift == null) {
            bot.sendMarkdown(user.getUserId(), "Зміну не знайдено, спробуйте знову", null);
            return;
        }
        ConversationState nextState = new ConversationState("SWAP_SELECT_TARGET");
        nextState.getData().put("date", date.toString());
        nextState.getData().put("shiftId", shiftId);
        nextState.getData().put("peerId", String.valueOf(peerId));
        stateStore.put(user.getUserId(), nextState);
        List<Shift> peerShifts = scheduleService.shiftsForDate(peerId, date);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Shift shift : peerShifts) {
            String text = "Обмін на " + TimeUtils.humanTimeRange(shift.getStartTime(), shift.getEndTime()) + " " + shift.getLocationId();
            rows.add(Collections.singletonList(InlineKeyboardButton.builder()
                    .text(text)
                    .callbackData("swapTarget:" + shift.getShiftId())
                    .build()));
        }
        rows.add(Collections.singletonList(InlineKeyboardButton.builder()
                .text("Передати мою зміну без обміну")
                .callbackData("swapTarget:none")
                .build()));
        markup.setKeyboard(rows);
        bot.sendMarkdown(user.getUserId(), MarkdownEscaper.escape("Виберіть зміну колеги або надішліть без обміну"), markup);
    }

    private void handleSwapTarget(User user, String targetId, BotNotificationPort bot) {
        Optional<ConversationState> stateOpt = stateStore.get(user.getUserId());
        if (stateOpt.isEmpty()) {
            startSwapFlow(user, bot);
            return;
        }
        ConversationState state = stateOpt.get();
        String dateStr = state.getData().get("date");
        String shiftId = state.getData().get("shiftId");
        String peerIdStr = state.getData().get("peerId");
        if (dateStr == null || shiftId == null || peerIdStr == null) {
            startSwapFlow(user, bot);
            return;
        }
        LocalDate date = LocalDate.parse(dateStr);
        long peerId = Long.parseLong(peerIdStr);
        Shift fromShift = findShift(scheduleService.shiftsForDate(user.getUserId(), date), shiftId);
        if (fromShift == null) {
            bot.sendMarkdown(user.getUserId(), "Зміну не знайдено, почніть спочатку", null);
            return;
        }
        Shift targetShift = null;
        if (!"none".equals(targetId)) {
            targetShift = findShift(scheduleService.shiftsForDate(peerId, date), targetId);
        }
        String comment = buildSwapComment(fromShift, targetShift);
        Request request = requestService.createSwapRequest(fromShift, user.getUserId(), peerId, comment, targetShift);
        stateStore.clear(user.getUserId());

        User peer = usersRepository.findById(peerId).orElse(null);
        bot.sendMarkdown(user.getUserId(), MarkdownEscaper.escape("Запит на підміну відправлено " + (peer != null ? peer.getFullName() : "колезі")), null);
        notifyPeer(request, user, peer, targetShift, bot);
    }

    private void handlePeerDecision(User peerUser, String requestId, boolean accepted, BotNotificationPort bot) {
        Optional<Request> requestOpt = requestService.findById(requestId);
        if (requestOpt.isEmpty()) {
            bot.sendMarkdown(peerUser.getUserId(), "Запит не знайдено або прострочений", null);
            return;
        }
        Request request;
        if (accepted) {
            request = requestService.acceptByPeer(requestId);
            bot.sendMarkdown(peerUser.getUserId(), "Ви підтвердили підміну. Очікує рішення ТМ.", null);
            notifyTm(request, bot);
        } else {
            request = requestService.declineByPeer(requestId);
            bot.sendMarkdown(peerUser.getUserId(), "Ви відхилили підміну.", null);
            notifyInitiatorResult(request, false, bot);
        }
    }

    private void handleTmDecision(String requestId, boolean approved, BotNotificationPort bot) {
        Optional<Request> requestOpt = requestService.findById(requestId);
        if (requestOpt.isEmpty()) {
            return;
        }
        Request request = approved ? requestService.approveByTm(requestId) : requestService.rejectByTm(requestId);
        notifyInitiatorResult(request, approved, bot);
    }

    private void notifyPeer(Request request, User initiator, User peer, Shift targetShift, BotNotificationPort bot) {
        if (peer == null) {
            return;
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Arrays.asList(
                InlineKeyboardButton.builder().text("✅ Прийняти").callbackData("swapPeerAccept:" + request.getRequestId()).build(),
                InlineKeyboardButton.builder().text("❌ Відхилити").callbackData("swapPeerDecline:" + request.getRequestId()).build()
        ));
        markup.setKeyboard(rows);

        StringBuilder text = new StringBuilder();
        text.append("🔁 Запит на підміну від ").append(initiator.getFullName()).append("\\n");
        text.append("Зміна: ").append(formatRequest(request)).append("\\n");
        if (targetShift != null) {
            text.append("Обмін на: ").append(formatShift(targetShift)).append("\\n");
        } else {
            text.append("Без зустрічної зміни.").append("\\n");
        }
        text.append("Коментар: ").append(request.getComment());
        bot.sendMarkdown(peer.getUserId(), MarkdownEscaper.escape(text.toString()), markup);
    }

    private void notifyTm(Request request, BotNotificationPort bot) {
        List<User> tms = usersRepository.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && (u.getRole() == Role.TM || u.getRole() == Role.SENIOR))
                .toList();
        if (tms.isEmpty()) {
            return;
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Arrays.asList(
                InlineKeyboardButton.builder().text("✅ Погодити").callbackData("swapTmApprove:" + request.getRequestId()).build(),
                InlineKeyboardButton.builder().text("❌ Відхилити").callbackData("swapTmReject:" + request.getRequestId()).build()
        ));
        markup.setKeyboard(rows);
        String initiatorName = usersRepository.findById(request.getInitiatorUserId()).map(User::getFullName).orElse("Працівник");
        String peerName = request.getToUserId() != null ? usersRepository.findById(request.getToUserId()).map(User::getFullName).orElse("Колега") : "Колега";
        String text = "🔁 Підміна очікує погодження ТМ\\n" +
                "Ініціатор: " + initiatorName + "\\n" +
                "Учасник: " + peerName + "\\n" +
                "Зміна: " + formatRequest(request) + "\\n" +
                "Коментар: " + request.getComment();
        for (User tm : tms) {
            bot.sendMarkdown(tm.getUserId(), MarkdownEscaper.escape(text), markup);
        }
    }

    private void notifyInitiatorResult(Request request, boolean approved, BotNotificationPort bot) {
        usersRepository.findById(request.getInitiatorUserId()).ifPresent(initiator -> {
            String text = approved ? "✅ ТМ погодив підміну" : "❌ Підміну відхилено";
            bot.sendMarkdown(initiator.getUserId(), text, null);
        });
        if (request.getToUserId() != null) {
            usersRepository.findById(request.getToUserId()).ifPresent(peer -> {
                String text = approved ? "✅ Підміна погоджена ТМ" : "ℹ️ Підміна відхилена ТМ";
                bot.sendMarkdown(peer.getUserId(), text, null);
            });
        }
    }

    private Shift findShift(List<Shift> shifts, String shiftId) {
        return shifts.stream().filter(s -> shiftId.equals(s.getShiftId())).findFirst().orElse(null);
    }

    private String buildSwapComment(Shift fromShift, Shift targetShift) {
        StringBuilder sb = new StringBuilder();
        sb.append("Підміна зміни ").append(TimeUtils.humanDate(fromShift.getDate(), zoneId)).append(" ")
                .append(TimeUtils.humanTimeRange(fromShift.getStartTime(), fromShift.getEndTime()))
                .append(" ").append(fromShift.getLocationId());
        if (targetShift != null) {
            sb.append(" на ").append(TimeUtils.humanDate(targetShift.getDate(), zoneId)).append(" ")
                    .append(TimeUtils.humanTimeRange(targetShift.getStartTime(), targetShift.getEndTime()))
                    .append(" ").append(targetShift.getLocationId());
        }
        return sb.toString();
    }

    private String formatShift(Shift shift) {
        return TimeUtils.humanDate(shift.getDate(), zoneId) + " " + TimeUtils.humanTimeRange(shift.getStartTime(), shift.getEndTime()) +
                " (" + shift.getLocationId() + ")";
    }

    private String formatRequest(Request request) {
        return TimeUtils.humanDate(request.getDate(), zoneId) + " " +
                TimeUtils.humanTimeRange(request.getStartTime(), request.getEndTime()) +
                " (" + request.getLocationId() + ")";
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
