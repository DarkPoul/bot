package com.shiftbot.bot.handler;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.bot.ui.CalendarKeyboardBuilder;
import com.shiftbot.model.Location;
import com.shiftbot.model.LocationAssignment;
import com.shiftbot.model.Request;
import com.shiftbot.model.Shift;
import com.shiftbot.model.User;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.ShiftStatus;
import com.shiftbot.model.enums.UserStatus;
import com.shiftbot.repository.LocationAssignmentsRepository;
import com.shiftbot.repository.LocationsRepository;
import com.shiftbot.repository.UsersRepository;
import com.shiftbot.service.AuditService;
import com.shiftbot.service.AuthService;
import com.shiftbot.service.RequestService;
import com.shiftbot.service.ScheduleService;
import com.shiftbot.state.ConversationState;
import com.shiftbot.state.ConversationStateStore;
import com.shiftbot.state.CoverRequestFsm;
import com.shiftbot.state.OnboardingFsm;
import com.shiftbot.util.MarkdownEscaper;
import com.shiftbot.util.TimeUtils;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

public class UpdateRouter {
    private final AuthService authService;
    private final ScheduleService scheduleService;
    private final RequestService requestService;
    private final LocationsRepository locationsRepository;
    private final UsersRepository usersRepository;
    private final LocationAssignmentsRepository locationAssignmentsRepository;
    private final CalendarKeyboardBuilder calendarKeyboardBuilder;
    private final ConversationStateStore stateStore;
    private final CoverRequestFsm coverRequestFsm;
    private final OnboardingFsm onboardingFsm;
    private final AuditService auditService;
    private final ZoneId zoneId;

    public UpdateRouter(AuthService authService,
                        ScheduleService scheduleService,
                        RequestService requestService,
                        CalendarKeyboardBuilder calendarKeyboardBuilder,
                        ZoneId zoneId) {
        this(authService, scheduleService, requestService, null, null, null, calendarKeyboardBuilder,
                new ConversationStateStore(Duration.ofMinutes(10)), new CoverRequestFsm(), new OnboardingFsm(), null, zoneId);
    }

    public UpdateRouter(AuthService authService,
                        ScheduleService scheduleService,
                        RequestService requestService,
                        UsersRepository usersRepository,
                        AuditService auditService,
                        CalendarKeyboardBuilder calendarKeyboardBuilder,
                        ZoneId zoneId) {
        this(authService, scheduleService, requestService, null, usersRepository, null, calendarKeyboardBuilder,
                new ConversationStateStore(Duration.ofMinutes(10)), new CoverRequestFsm(), new OnboardingFsm(), auditService, zoneId);
    }

    public UpdateRouter(AuthService authService,
                        ScheduleService scheduleService,
                        RequestService requestService,
                        UsersRepository usersRepository,
                        ConversationStateStore stateStore,
                        CalendarKeyboardBuilder calendarKeyboardBuilder,
                        ZoneId zoneId) {
        this(authService, scheduleService, requestService, null, usersRepository, null, calendarKeyboardBuilder,
                stateStore, new CoverRequestFsm(), new OnboardingFsm(), null, zoneId);
    }

    public UpdateRouter(AuthService authService,
                        ScheduleService scheduleService,
                        RequestService requestService,
                        LocationsRepository locationsRepository,
                        UsersRepository usersRepository,
                        CalendarKeyboardBuilder calendarKeyboardBuilder,
                        ZoneId zoneId) {
        this(authService, scheduleService, requestService, locationsRepository, usersRepository, null, calendarKeyboardBuilder,
                new ConversationStateStore(Duration.ofMinutes(10)), new CoverRequestFsm(), new OnboardingFsm(), null, zoneId);
    }

    public UpdateRouter(AuthService authService,
                        ScheduleService scheduleService,
                        RequestService requestService,
                        CalendarKeyboardBuilder calendarKeyboardBuilder,
                        LocationsRepository locationsRepository,
                        ConversationStateStore stateStore,
                        CoverRequestFsm coverRequestFsm,
                        AuditService auditService,
                        ZoneId zoneId) {
        this(authService, scheduleService, requestService, locationsRepository, null, null, calendarKeyboardBuilder,
                stateStore, coverRequestFsm, new OnboardingFsm(), auditService, zoneId);
    }

    public UpdateRouter(AuthService authService,
                        ScheduleService scheduleService,
                        RequestService requestService,
                        LocationsRepository locationsRepository,
                        UsersRepository usersRepository,
                        LocationAssignmentsRepository locationAssignmentsRepository,
                        CalendarKeyboardBuilder calendarKeyboardBuilder,
                        ConversationStateStore stateStore,
                        CoverRequestFsm coverRequestFsm,
                        OnboardingFsm onboardingFsm,
                        AuditService auditService,
                        ZoneId zoneId) {
        this.authService = authService;
        this.scheduleService = scheduleService;
        this.requestService = requestService;
        this.locationsRepository = locationsRepository;
        this.usersRepository = usersRepository;
        this.locationAssignmentsRepository = locationAssignmentsRepository;
        this.calendarKeyboardBuilder = calendarKeyboardBuilder;
        this.stateStore = stateStore;
        this.coverRequestFsm = coverRequestFsm;
        this.onboardingFsm = onboardingFsm;
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
        Optional<ConversationState> stateOpt = stateStore.get(chatId);
        if (stateOpt.isPresent() && onboardingFsm.supports(stateOpt.get())) {
            if (handleOnboardingMessage(chatId, text, stateOpt.get(), bot)) {
                return;
            }
        }

        Optional<User> existing = authService.findExisting(chatId);
        if (existing.isEmpty()) {
            startOnboarding(chatId, bot);
            return;
        }
        AuthService.OnboardResult onboard = authService.evaluateExisting(existing.get());
        User user = onboard.user();
        if (!onboard.allowed()) {
            bot.sendMarkdown(chatId, MarkdownEscaper.escape(onboard.message()), null);
            return;
        }

        if (isAbortCommand(text)) {
            stateStore.clear(chatId);
            bot.sendMarkdown(chatId, "⏹️ Заявка скасована", mainMenu(user));
            return;
        }

        if (stateOpt.isPresent() && coverRequestFsm.supports(stateOpt.get())) {
            if (handleCoverMessage(user, text, stateOpt.get(), bot)) {
                return;
            }
        }

        if (text.startsWith("/start")) {
            String welcome = "👋 Вітаємо, " + MarkdownEscaper.escape(user.getFullName()) + "!";
            bot.sendMarkdown(chatId, welcome, mainMenu(user));
            return;
        }

        switch (text) {
            case "Мій графік", "📅 Мій графік" -> sendMySchedule(user, bot);
            case "Потрібна заміна", "🆘 Потрібна заміна" -> startCoverFlow(user, bot);
            case "📥 Мої заявки" -> sendTmRequests(user, bot);
            case "🔁 Підміни" -> bot.sendMarkdown(chatId, "Оберіть дію з меню нижче", mainMenu(user));
            default -> bot.sendMarkdown(chatId, "Оберіть дію з меню нижче", mainMenu(user));
        }
    }

    private void handleCallback(CallbackQuery callback, BotNotificationPort bot) {
        String data = callback.getData();
        Long chatId = callback.getMessage().getChatId();
        Optional<ConversationState> stateOpt = stateStore.get(chatId);
        if (stateOpt.isPresent() && onboardingFsm.supports(stateOpt.get()) && data.startsWith("onboard:loc:")) {
            handleOnboardingLocation(chatId, data, stateOpt.get(), bot, callback);
            return;
        }

        Optional<User> existing = authService.findExisting(chatId);
        if (existing.isEmpty()) {
            startOnboarding(chatId, bot);
            return;
        }
        AuthService.OnboardResult onboard = authService.evaluateExisting(existing.get());
        User user = onboard.user();
        if (!onboard.allowed()) {
            bot.sendMarkdown(chatId, MarkdownEscaper.escape(onboard.message()), null);
            return;
        }

        if (stateOpt.isPresent() && coverRequestFsm.supports(stateOpt.get()) && data.startsWith("cover:")) {
            handleCoverCallback(user, callback, stateOpt.get(), bot);
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
        } else if (data.startsWith("swapPeerAccept:")) {
            handlePeerDecision(user, data.substring("swapPeerAccept:".length()), true, bot);
        } else if (data.startsWith("swapPeerDecline:")) {
            handlePeerDecision(user, data.substring("swapPeerDecline:".length()), false, bot);
        } else if (data.startsWith("swapTmApprove:")) {
            handleTmDecision(data.substring("swapTmApprove:".length()), true, bot);
        } else if (data.startsWith("swapTmReject:")) {
            handleTmDecision(data.substring("swapTmReject:".length()), false, bot);
        } else if ("noop".equals(data)) {
            stateStore.touch(chatId);
            bot.sendMarkdown(chatId, "⏳ Очікуємо на ваш ввід", null);
        } else if (data.startsWith("cover:date:")) {
            handleCoverCallback(user, callback, stateOpt.orElse(coverRequestFsm.start()), bot);
        } else if (data.startsWith("cover:loc:")) {
            handleCoverCallback(user, callback, stateOpt.orElse(coverRequestFsm.start()), bot);
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
        } else if (data.startsWith("user:activate:")) {
            handleUserStatusChange(user, data, true, bot);
        } else if (data.startsWith("user:reject:")) {
            handleUserStatusChange(user, data, false, bot);
        } else if (data.startsWith("location:")) {
            handleLocationCallback(user, data, bot);
        }
    }

    private void sendMySchedule(User user, BotNotificationPort bot) {
        LocalDate today = TimeUtils.today(zoneId);
        Map<LocalDate, ShiftStatus> statuses = scheduleService.calendarStatuses(user.getUserId(), today);
        InlineKeyboardMarkup calendar = calendarKeyboardBuilder.buildMonth(today, statuses, "calendar:");
        String text = "📅 Мій графік на " + today.getMonth() + ": оберіть день";
        bot.sendMarkdown(user.getUserId(), MarkdownEscaper.escape(text), calendar);
    }

    public void sendLocationPicker(User user, BotNotificationPort bot) {
        if (locationsRepository == null) {
            bot.sendMarkdown(user.getUserId(), "Локації недоступні", null);
            return;
        }
        List<Location> locations = locationsRepository.findAll();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Location location : locations) {
            rows.add(Collections.singletonList(
                    InlineKeyboardButton.builder()
                            .text(location.getName())
                            .callbackData("location_pick:" + location.getLocationId())
                            .build()
            ));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        bot.sendMarkdown(user.getUserId(), "Оберіть локацію", markup);
    }

    private void handleLocationCallback(User user, String data, BotNotificationPort bot) {
        if (locationsRepository == null) {
            return;
        }
        String[] parts = data.split(":");
        if (parts.length != 3) {
            return;
        }
        String locationId = parts[1];
        LocalDate date = LocalDate.parse(parts[2]);
        List<Shift> shifts = scheduleService.shiftsForLocation(locationId, date);
        if (shifts.isEmpty()) {
            bot.sendMarkdown(user.getUserId(), "Немає графіку для обраної дати", null);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("📍 ").append(locationId).append(" ").append(TimeUtils.humanDate(date, zoneId)).append("\\n");
        for (Shift shift : shifts) {
            String name = usersRepository != null
                    ? usersRepository.findById(shift.getUserId()).map(User::getFullName).orElse("Невідомо")
                    : String.valueOf(shift.getUserId());
            sb.append("• ").append(name).append(" — ").append(TimeUtils.humanTimeRange(shift.getStartTime(), shift.getEndTime())).append("\\n");
        }
        bot.sendMarkdown(user.getUserId(), sb.toString(), null);
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

    private void handleTmDecision(String requestId, boolean approve, BotNotificationPort bot) {
        try {
            Request updated = approve ? requestService.approveByTm(requestId) : requestService.rejectByTm(requestId);
            String action = approve ? "✅ Заявка погоджена" : "❌ Заявка відхилена";
            bot.sendMarkdown(updated.getInitiatorUserId(), MarkdownEscaper.escape(action + "\n" + formatRequest(updated)), null);
        } catch (Exception e) {
            bot.sendMarkdown(null, "⚠️ Помилка: " + MarkdownEscaper.escape(e.getMessage()), null);
        }
    }

    private void handlePeerDecision(User peer, String requestId, boolean accept, BotNotificationPort bot) {
        Optional<Request> requestOpt = requestService.findById(requestId);
        if (requestOpt.isEmpty()) {
            return;
        }
        Request request = requestOpt.get();
        if (request.getToUserId() == null || request.getToUserId() != peer.getUserId()) {
            bot.sendMarkdown(peer.getUserId(), "⛔ Недостатньо прав", null);
            return;
        }
        Request updated = accept ? requestService.acceptByPeer(requestId) : requestService.declineByPeer(requestId);
        if (accept) {
            notifyTmAboutSwap(updated, bot);
        } else {
            bot.sendMarkdown(updated.getInitiatorUserId(), "Підміна відхилено", null);
        }
    }

    private void notifyTmAboutSwap(Request request, BotNotificationPort bot) {
        if (usersRepository == null) {
            return;
        }
        List<User> tms = usersRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.TM || u.getRole() == Role.SENIOR)
                .toList();
        for (User tm : tms) {
            bot.sendMarkdown(tm.getUserId(), "Підміна очікує підтвердження", null);
        }
    }

    private void startCoverFlow(User user, BotNotificationPort bot) {
        ConversationState newState = coverRequestFsm.start();
        stateStore.put(user.getUserId(), newState);
        LocalDate today = TimeUtils.today(zoneId);
        InlineKeyboardMarkup markup = calendarKeyboardBuilder.buildMonth(today, Map.of(), "cover:date:");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>(markup.getKeyboard());
        rows.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("🚑 Попросити заміну на завтра")
                        .callbackData("cover:" + today.plusDays(1))
                        .build()
        ));
        markup.setKeyboard(rows);
        bot.sendMarkdown(user.getUserId(), "🆘 Потрібна заміна? Оберіть дату", markup);
    }

    private void handleCoverCallback(User user, CallbackQuery callback, ConversationState state, BotNotificationPort bot) {
        String data = callback.getData();
        if (data.startsWith("cover:date:")) {
            LocalDate date = LocalDate.parse(data.substring("cover:date:".length()));
            Map<String, String> extra = Map.of(CoverRequestFsm.DATE_KEY, date.toString());
            ConversationState next = coverRequestFsm.advance(state, CoverRequestFsm.Step.TIME, extra);
            stateStore.put(user.getUserId(), next);
            bot.sendMarkdown(user.getUserId(), "Вкажіть час у форматі HH:mm-HH:mm", null);
        } else if (data.startsWith("cover:loc:")) {
            Map<String, String> extra = Map.of(CoverRequestFsm.LOCATION_KEY, data.substring("cover:loc:".length()));
            ConversationState next = coverRequestFsm.advance(state, CoverRequestFsm.Step.COMMENT, extra);
            stateStore.put(user.getUserId(), next);
            bot.sendMarkdown(user.getUserId(), "Додайте коментар", null);
        }
    }

    private boolean handleCoverMessage(User user, String text, ConversationState state, BotNotificationPort bot) {
        CoverRequestFsm.Step step = coverRequestFsm.currentStep(state);
        switch (step) {
            case TIME -> {
                Optional<LocalTime[]> parsed = parseTimeRange(text);
                if (parsed.isEmpty()) {
                    bot.sendMarkdown(user.getUserId(), "Некоректний час, введіть у форматі HH:mm-HH:mm", null);
                    return true;
                }
                Map<String, String> extra = new HashMap<>();
                extra.put(CoverRequestFsm.START_KEY, parsed.get()[0].toString());
                extra.put(CoverRequestFsm.END_KEY, parsed.get()[1].toString());
                ConversationState next = coverRequestFsm.advance(state, CoverRequestFsm.Step.LOCATION, extra);
                stateStore.put(user.getUserId(), next);
                InlineKeyboardMarkup markup = buildLocationsKeyboard();
                bot.sendMarkdown(user.getUserId(), "Оберіть локацію", markup);
                return true;
            }
            case COMMENT -> {
                LocalDate date = LocalDate.parse(state.getData().get(CoverRequestFsm.DATE_KEY));
                LocalTime start = LocalTime.parse(state.getData().get(CoverRequestFsm.START_KEY));
                LocalTime end = LocalTime.parse(state.getData().get(CoverRequestFsm.END_KEY));
                String locationId = state.getData().getOrDefault(CoverRequestFsm.LOCATION_KEY, "unknown");
                requestService.createCoverRequest(user.getUserId(), locationId, date, start, end, text);
                bot.sendMarkdown(user.getUserId(), "Заявка на заміну створена та очікує ТМ", null);
                stateStore.clear(user.getUserId());
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private InlineKeyboardMarkup buildLocationsKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (locationsRepository != null) {
            for (Location location : locationsRepository.findActive()) {
                rows.add(Collections.singletonList(
                        InlineKeyboardButton.builder()
                                .text(location.getName())
                                .callbackData("cover:loc:" + location.getLocationId())
                                .build()
                ));
            }
        }
        markup.setKeyboard(rows);
        return markup;
    }

    private void startOnboarding(Long chatId, BotNotificationPort bot) {
        ConversationState state = onboardingFsm.start();
        stateStore.put(chatId, state);
        bot.sendMarkdown(chatId, "Для реєстрації введіть ПІБ", null);
    }

    private boolean handleOnboardingMessage(Long chatId, String text, ConversationState state, BotNotificationPort bot) {
        OnboardingFsm.Step step = onboardingFsm.currentStep(state);
        if (step == OnboardingFsm.Step.NAME) {
            String trimmed = text == null ? "" : text.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("/")) {
                bot.sendMarkdown(chatId, "Введіть ПІБ у текстовому вигляді", null);
                return true;
            }
            Map<String, String> extra = Map.of(OnboardingFsm.FULL_NAME_KEY, trimmed);
            ConversationState next = onboardingFsm.advance(state, OnboardingFsm.Step.LOCATION, extra);
            stateStore.put(chatId, next);
            sendOnboardingLocationPicker(chatId, bot);
            return true;
        }
        return false;
    }

    private void sendOnboardingLocationPicker(Long chatId, BotNotificationPort bot) {
        if (locationsRepository == null) {
            bot.sendMarkdown(chatId, "Локації недоступні, спробуйте пізніше", null);
            return;
        }
        List<Location> locations = locationsRepository.findActive();
        if (locations.isEmpty()) {
            bot.sendMarkdown(chatId, "Локації поки не налаштовані", null);
            return;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Location location : locations) {
            rows.add(Collections.singletonList(
                    InlineKeyboardButton.builder()
                            .text(location.getName())
                            .callbackData("onboard:loc:" + location.getLocationId())
                            .build()
            ));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        bot.sendMarkdown(chatId, "Оберіть локацію", markup);
    }

    private void handleOnboardingLocation(Long chatId, String data, ConversationState state, BotNotificationPort bot, CallbackQuery callback) {
        if (locationsRepository == null) {
            bot.sendMarkdown(chatId, "Локації недоступні, спробуйте пізніше", null);
            return;
        }
        String locationId = data.substring("onboard:loc:".length());
        if (locationsRepository.findById(locationId).isEmpty()) {
            bot.sendMarkdown(chatId, "Локацію не знайдено", null);
            return;
        }
        String fullName = state.getData().get(OnboardingFsm.FULL_NAME_KEY);
        if (fullName == null || fullName.isBlank()) {
            bot.sendMarkdown(chatId, "Почнімо з ПІБ", null);
            stateStore.put(chatId, onboardingFsm.start());
            return;
        }
        AuthService.OnboardResult onboard = authService.register(chatId, callback.getFrom().getUserName(), fullName);
        if (locationAssignmentsRepository != null) {
            LocationAssignment assignment = new LocationAssignment(locationId, chatId, true, TimeUtils.today(zoneId), null);
            locationAssignmentsRepository.save(assignment);
        }
        stateStore.clear(chatId);
        String message = onboard.message() != null ? onboard.message() : "Реєстрація завершена.";
        bot.sendMarkdown(chatId, MarkdownEscaper.escape(message), null);
    }

    private void handleUserStatusChange(User actor, String data, boolean activate, BotNotificationPort bot) {
        if (actor.getRole() != Role.TM && actor.getRole() != Role.SENIOR) {
            bot.sendMarkdown(actor.getUserId(), "⛔ Недостатньо прав для цієї дії", null);
            return;
        }
        if (usersRepository == null) {
            bot.sendMarkdown(actor.getUserId(), "⛔ Репозиторій користувачів недоступний", null);
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
        User updated = new User(target.getUserId(), target.getUsername(), target.getFullName(), target.getPhone(), target.getRole(), newStatus, target.getCreatedAt(), target.getCreatedBy());
        usersRepository.updateRow(target.getUserId(), updated);
        if (auditService != null) {
            auditService.logEvent(actor.getUserId(), activate ? "user_activated" : "user_rejected", "user", String.valueOf(target.getUserId()), Map.of("previousStatus", target.getStatus().name(), "newStatus", newStatus.name()), bot);
        }
        bot.sendMarkdown(actor.getUserId(), MarkdownEscaper.escape((activate ? "✅ " : "⛔ ") + target.getFullName()), null);
        bot.sendMarkdown(target.getUserId(), activate ? "✅ Ваш профіль активовано" : "⛔ Ваш профіль відхилено", null);
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

    private String formatRequest(Request request) {
        return TimeUtils.humanDate(request.getDate(), zoneId) + " " +
                TimeUtils.humanTimeRange(request.getStartTime(), request.getEndTime()) + " | " +
                request.getLocationId();
    }

    private String shortId(String requestId) {
        if (requestId == null || requestId.length() < 4) return requestId;
        return requestId.substring(0, 4);
    }

    private InlineKeyboardMarkup mainMenu(User user) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(buttonRow("📅 Мій графік", "M::my"));
        rows.add(buttonRow("🆘 Потрібна заміна", "M::cover"));
        if (user.getRole() == Role.TM || user.getRole() == Role.SENIOR) {
            rows.add(buttonRow("📥 Мої заявки", "M::requests"));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    private String buildFullName(Message message) {
        return buildFullName(message.getFrom().getFirstName(), message.getFrom().getLastName());
    }

    private String buildFullName(String first, String last) {
        return StringUtils.trimToEmpty(first + " " + (last == null ? "" : last));
    }

    private boolean isAbortCommand(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("cancel") || normalized.equals("/cancel") || normalized.equals("/stop");
    }

    private Optional<LocalTime[]> parseTimeRange(String text) {
        if (text == null || !text.contains("-")) {
            return Optional.empty();
        }
        String[] parts = text.split("-");
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            LocalTime start = LocalTime.parse(parts[0].trim());
            LocalTime end = LocalTime.parse(parts[1].trim());
            if (!end.isAfter(start)) {
                return Optional.empty();
            }
            return Optional.of(new LocalTime[]{start, end});
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
