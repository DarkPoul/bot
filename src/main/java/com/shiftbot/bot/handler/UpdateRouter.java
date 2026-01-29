package com.shiftbot.bot.handler;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.bot.ui.CalendarKeyboardBuilder;
import com.shiftbot.model.AccessRequest;
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
import com.shiftbot.service.AccessRequestService;
import com.shiftbot.service.AuditService;
import com.shiftbot.service.AuthService;
import com.shiftbot.service.PersonalScheduleService;
import com.shiftbot.service.RequestService;
import com.shiftbot.service.ScheduleService;
import com.shiftbot.state.ConversationState;
import com.shiftbot.state.ConversationStateStore;
import com.shiftbot.state.CoverRequestFsm;
import com.shiftbot.state.OnboardingFsm;
import com.shiftbot.state.PersonalScheduleFsm;
import com.shiftbot.util.CalendarRenderer;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class UpdateRouter {
    private static final DateTimeFormatter ACCESS_REQUEST_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private final AuthService authService;
    private final ScheduleService scheduleService;
    private final RequestService requestService;
    private final AccessRequestService accessRequestService;
    private final LocationsRepository locationsRepository;
    private final UsersRepository usersRepository;
    private final LocationAssignmentsRepository locationAssignmentsRepository;
    private final PersonalScheduleService personalScheduleService;
    private final CalendarKeyboardBuilder calendarKeyboardBuilder;
    private final ConversationStateStore stateStore;
    private final CoverRequestFsm coverRequestFsm;
    private final OnboardingFsm onboardingFsm;
    private final PersonalScheduleFsm personalScheduleFsm;
    private final AuditService auditService;
    private final ZoneId zoneId;
    private final Long adminTelegramId;

    public UpdateRouter(AuthService authService,
                        ScheduleService scheduleService,
                        RequestService requestService,
                        CalendarKeyboardBuilder calendarKeyboardBuilder,
                        ZoneId zoneId) {
        this(authService, scheduleService, requestService, null, null, null, null, null, calendarKeyboardBuilder,
                new ConversationStateStore(Duration.ofMinutes(10)), new CoverRequestFsm(), new OnboardingFsm(),
                new PersonalScheduleFsm(), null, zoneId, null);
    }

    public UpdateRouter(AuthService authService,
                        ScheduleService scheduleService,
                        RequestService requestService,
                        UsersRepository usersRepository,
                        AuditService auditService,
                        CalendarKeyboardBuilder calendarKeyboardBuilder,
                        ZoneId zoneId) {
        this(authService, scheduleService, requestService, null, null, usersRepository, null, null, calendarKeyboardBuilder,
                new ConversationStateStore(Duration.ofMinutes(10)), new CoverRequestFsm(), new OnboardingFsm(),
                new PersonalScheduleFsm(), auditService, zoneId, null);
    }

    public UpdateRouter(AuthService authService,
                        ScheduleService scheduleService,
                        RequestService requestService,
                        UsersRepository usersRepository,
                        ConversationStateStore stateStore,
                        CalendarKeyboardBuilder calendarKeyboardBuilder,
                        ZoneId zoneId) {
        this(authService, scheduleService, requestService, null, null, usersRepository, null, null, calendarKeyboardBuilder,
                stateStore, new CoverRequestFsm(), new OnboardingFsm(), new PersonalScheduleFsm(), null, zoneId, null);
    }

    public UpdateRouter(AuthService authService,
                        ScheduleService scheduleService,
                        RequestService requestService,
                        LocationsRepository locationsRepository,
                        UsersRepository usersRepository,
                        CalendarKeyboardBuilder calendarKeyboardBuilder,
                        ZoneId zoneId) {
        this(authService, scheduleService, requestService, null, locationsRepository, usersRepository, null, null, calendarKeyboardBuilder,
                new ConversationStateStore(Duration.ofMinutes(10)), new CoverRequestFsm(), new OnboardingFsm(),
                new PersonalScheduleFsm(), null, zoneId, null);
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
        this(authService, scheduleService, requestService, null, locationsRepository, null, null, null, calendarKeyboardBuilder,
                stateStore, coverRequestFsm, new OnboardingFsm(), new PersonalScheduleFsm(), auditService, zoneId, null);
    }

    public UpdateRouter(AuthService authService,
                        ScheduleService scheduleService,
                        RequestService requestService,
                        AccessRequestService accessRequestService,
                        LocationsRepository locationsRepository,
                        UsersRepository usersRepository,
                        LocationAssignmentsRepository locationAssignmentsRepository,
                        PersonalScheduleService personalScheduleService,
                        CalendarKeyboardBuilder calendarKeyboardBuilder,
                        ConversationStateStore stateStore,
                        CoverRequestFsm coverRequestFsm,
                        OnboardingFsm onboardingFsm,
                        PersonalScheduleFsm personalScheduleFsm,
                        AuditService auditService,
                        ZoneId zoneId,
                        Long adminTelegramId) {
        this.authService = authService;
        this.scheduleService = scheduleService;
        this.requestService = requestService;
        this.accessRequestService = accessRequestService;
        this.locationsRepository = locationsRepository;
        this.usersRepository = usersRepository;
        this.locationAssignmentsRepository = locationAssignmentsRepository;
        this.personalScheduleService = personalScheduleService;
        this.calendarKeyboardBuilder = calendarKeyboardBuilder;
        this.stateStore = stateStore;
        this.coverRequestFsm = coverRequestFsm;
        this.onboardingFsm = onboardingFsm;
        this.personalScheduleFsm = personalScheduleFsm;
        this.auditService = auditService;
        this.zoneId = zoneId;
        this.adminTelegramId = adminTelegramId;
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

        if (text.startsWith("/start")) {
            handleStartCommand(user, onboard, bot);
            return;
        }

        if ("🔄 Перевірити статус".equals(text)) {
            handleStatusCheck(user, bot);
            return;
        }

        if (!onboard.allowed()) {
            handleBlockedUser(user, bot);
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

        if (stateOpt.isPresent() && personalScheduleFsm.supports(stateOpt.get())) {
            if (handleScheduleMessage(user, text, stateOpt.get(), bot)) {
                return;
            }
        }

        switch (text) {
            case "🗓 Створити/Оновити мій графік" -> startScheduleFlow(user, bot);
            case "👀 Переглянути мій графік" -> sendPersonalSchedule(user, bot);
            case "Потрібна заміна", "🆘 Потрібна заміна" -> startCoverFlow(user, bot);
            case "📥 Мої заявки" -> sendTmRequests(user, bot);
            case "📨 Заявки" -> sendAccessRequests(user, bot);
            case "🔄 Перевірити статус" -> handleStatusCheck(user, bot);
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

        if (data.startsWith("admin:") && adminTelegramId != null) {
            handleAdminDecision(chatId, data, bot);
            return;
        }

        Optional<User> existing = authService.findExisting(chatId);
        if (existing.isEmpty()) {
            startOnboarding(chatId, bot);
            return;
        }
        AuthService.OnboardResult onboard = authService.evaluateExisting(existing.get());
        User user = onboard.user();

        if ("status:check".equals(data)) {
            handleStatusCheck(user, bot);
            return;
        }

        if (!onboard.allowed()) {
            handleBlockedUser(user, bot);
            return;
        }

        if (stateOpt.isPresent() && coverRequestFsm.supports(stateOpt.get()) && data.startsWith("cover:")) {
            handleCoverCallback(user, callback, stateOpt.get(), bot);
            return;
        }

        if (data.startsWith("schedule:")) {
            handlePersonalScheduleCallback(user, data, stateOpt.orElse(personalScheduleFsm.start()), bot);
            return;
        }

        if (data.startsWith("requests:list")) {
            sendAccessRequests(user, bot);
            return;
        }

        if (data.startsWith("requests:approve:")) {
            handleAccessRequestDecision(user, data.substring("requests:approve:".length()), true, bot);
            return;
        }

        if (data.startsWith("requests:reject:")) {
            handleAccessRequestDecision(user, data.substring("requests:reject:".length()), false, bot);
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
                case "schedule_edit" -> startScheduleFlow(user, bot);
                case "schedule_view" -> sendPersonalSchedule(user, bot);
                case "cover" -> startCoverFlow(user, bot);
                case "requests" -> sendTmRequests(user, bot);
                case "main_menu" -> bot.sendMarkdown(chatId, "Оберіть дію з меню нижче", mainMenu(user));
                default -> bot.sendMarkdown(chatId, "Меню в розробці", null);
            }
        } else if (data.startsWith("location:")) {
            handleLocationCallback(user, data, bot);
        }
    }

    private void handleAdminDecision(Long chatId, String data, BotNotificationPort bot) {
        if (adminTelegramId == null || !adminTelegramId.equals(chatId)) {
            bot.sendMarkdown(chatId, "⛔ Недостатньо прав для цієї дії", null);
            return;
        }
        if (usersRepository == null) {
            bot.sendMarkdown(chatId, "⛔ Репозиторій користувачів недоступний", null);
            return;
        }
        String[] parts = data.split(":");
        if (parts.length != 3) {
            bot.sendMarkdown(chatId, "Невірний формат запиту", null);
            return;
        }
        boolean approve = "approve".equals(parts[1]);
        long targetId;
        try {
            targetId = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            bot.sendMarkdown(chatId, "Невірний формат ID користувача", null);
            return;
        }
        Optional<User> targetOpt = usersRepository.findById(targetId);
        if (targetOpt.isEmpty()) {
            bot.sendMarkdown(chatId, "Користувача не знайдено", null);
            return;
        }
        User target = targetOpt.get();
        UserStatus newStatus = approve ? UserStatus.APPROVED : UserStatus.REJECTED;
        User updated = new User(target.getUserId(), target.getUsername(), target.getFullName(), target.getLocationId(),
                target.getPhone(), target.getRole(), newStatus, target.getCreatedAt(), target.getCreatedBy());
        usersRepository.updateRow(target.getUserId(), updated);
        bot.sendMarkdown(chatId, approve ? "✅ Користувача підтверджено" : "❌ Заявку відхилено", null);
        bot.sendMarkdown(target.getUserId(), approve ? "Вас підтверджено, доступ відкрито" : "Заявку відхилено", null);
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

    private void sendAccessRequests(User user, BotNotificationPort bot) {
        if (user.getRole() != Role.SENIOR) {
            bot.sendMarkdown(user.getUserId(), "Недостатньо прав", null);
            return;
        }
        if (accessRequestService == null) {
            bot.sendMarkdown(user.getUserId(), "Сервіс заявок недоступний", null);
            return;
        }
        List<AccessRequest> pending = accessRequestService.listPendingRequests();
        if (pending.isEmpty()) {
            bot.sendMarkdown(user.getUserId(), "Немає нових заявок.", null);
            return;
        }
        for (AccessRequest request : pending) {
            sendAccessRequestDetail(user.getUserId(), request, bot);
        }
    }

    private void handleAccessRequestDecision(User user, String requestId, boolean approve, BotNotificationPort bot) {
        if (user.getRole() != Role.SENIOR) {
            bot.sendMarkdown(user.getUserId(), "Недостатньо прав", null);
            return;
        }
        if (accessRequestService == null) {
            bot.sendMarkdown(user.getUserId(), "Сервіс заявок недоступний", null);
            return;
        }
        try {
            AccessRequest updated = approve
                    ? accessRequestService.approveRequest(requestId, user.getUserId())
                    : accessRequestService.rejectRequest(requestId, user.getUserId());
            String confirmation = approve ? "✅ Заявку схвалено" : "❌ Заявку відхилено";
            bot.sendMarkdown(user.getUserId(), confirmation, null);
            String userMessage = approve ? "Доступ надано ✅" : "Заявку відхилено ❌";
            bot.sendMarkdown(updated.getTelegramUserId(), userMessage, null);
            List<AccessRequest> pending = accessRequestService.listPendingRequests();
            if (pending.isEmpty()) {
                bot.sendMarkdown(user.getUserId(), "Все опрацьовано.", null);
            } else {
                sendAccessRequestDetail(user.getUserId(), pending.get(0), bot);
            }
        } catch (Exception e) {
            bot.sendMarkdown(user.getUserId(), "⚠️ Помилка: " + MarkdownEscaper.escape(e.getMessage()), null);
        }
    }

    private void sendAccessRequestDetail(Long chatId, AccessRequest request, BotNotificationPort bot) {
        String text = formatAccessRequest(request);
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Arrays.asList(
                InlineKeyboardButton.builder()
                        .text("✅ Схвалити")
                        .callbackData("requests:approve:" + request.getId())
                        .build(),
                InlineKeyboardButton.builder()
                        .text("❌ Відхилити")
                        .callbackData("requests:reject:" + request.getId())
                        .build()
        ));
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        bot.sendMarkdown(chatId, MarkdownEscaper.escape(text), markup);
    }

    private String formatAccessRequest(AccessRequest request) {
        StringBuilder sb = new StringBuilder("📨 Заявка на доступ\n");
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            sb.append("ПІБ: ").append(request.getFullName()).append("\n");
        }
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            sb.append("Username: @").append(request.getUsername()).append("\n");
        }
        sb.append("Telegram ID: ").append(request.getTelegramUserId()).append("\n");
        sb.append("Дата: ").append(formatAccessRequestDate(request.getCreatedAt())).append("\n");
        if (request.getComment() != null && !request.getComment().isBlank()) {
            sb.append("Коментар: ").append(request.getComment()).append("\n");
        }
        sb.append("Статус: ").append(accessRequestStatusLabel(request));
        return sb.toString();
    }

    private String accessRequestStatusLabel(AccessRequest request) {
        return switch (request.getStatus()) {
            case PENDING -> "Очікує";
            case APPROVED -> "Схвалено";
            case REJECTED -> "Відхилено";
        };
    }

    private String formatAccessRequestDate(java.time.Instant createdAt) {
        if (createdAt == null) {
            return "невідомо";
        }
        LocalDateTime localDateTime = LocalDateTime.ofInstant(createdAt, zoneId);
        return localDateTime.format(ACCESS_REQUEST_DATE_FORMAT);
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

    private void handleStartCommand(User user, AuthService.OnboardResult onboard, BotNotificationPort bot) {
        if (onboard.allowed()) {
            String welcome = "👋 Вітаємо, " + MarkdownEscaper.escape(user.getFullName()) + "!";
            bot.sendMarkdown(user.getUserId(), welcome, mainMenu(user));
            return;
        }
        handlePendingAccess(user, bot);
    }

    private void handlePendingAccess(User user, BotNotificationPort bot) {
        if (accessRequestService == null) {
            bot.sendMarkdown(user.getUserId(), "Доступ ще не підтверджено. Очікуйте рішення старшого.", pendingMenu());
            return;
        }
        Optional<AccessRequest> pending = accessRequestService.getPendingByTelegramUserId(user.getUserId());
        if (pending.isPresent()) {
            bot.sendMarkdown(user.getUserId(), "Ви вже очікуєте підтвердження. Статус: Очікує ✅", pendingMenu());
            return;
        }
        accessRequestService.createPendingIfAbsent(user, null);
        bot.sendMarkdown(user.getUserId(), "Заявку на доступ створено ✅ Очікуйте підтвердження старшого.", pendingMenu());
    }

    private void handleBlockedUser(User user, BotNotificationPort bot) {
        bot.sendMarkdown(user.getUserId(), "Доступ ще не підтверджено. Очікуйте рішення старшого.", pendingMenu());
    }

    private void handleStatusCheck(User user, BotNotificationPort bot) {
        Optional<User> refreshed = authService.findExisting(user.getUserId());
        User current = refreshed.orElse(user);
        if (current.getStatus() == UserStatus.APPROVED) {
            bot.sendMarkdown(current.getUserId(), "✅ Доступ активовано", mainMenu(current));
            return;
        }
        handlePendingAccess(current, bot);
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
        Optional<Location> locationOpt = locationsRepository.findById(locationId);
        if (locationOpt.isEmpty()) {
            bot.sendMarkdown(chatId, "Локацію не знайдено", null);
            return;
        }
        String fullName = state.getData().get(OnboardingFsm.FULL_NAME_KEY);
        if (fullName == null || fullName.isBlank()) {
            bot.sendMarkdown(chatId, "Почнімо з ПІБ", null);
            stateStore.put(chatId, onboardingFsm.start());
            return;
        }
        AuthService.OnboardResult onboard = authService.register(chatId, callback.getFrom().getUserName(), fullName, locationId);
        if (locationAssignmentsRepository != null) {
            LocationAssignment assignment = new LocationAssignment(locationId, chatId, true, TimeUtils.today(zoneId), null);
            locationAssignmentsRepository.save(assignment);
        }
        stateStore.clear(chatId);
        String message = onboard.message() != null ? onboard.message() : "Реєстрація завершена.";
        bot.sendMarkdown(chatId, MarkdownEscaper.escape(message), pendingMenu());
        String locationName = locationOpt.map(Location::getName).orElse(locationId);
        notifyAdminAboutRegistration(chatId, callback.getFrom().getUserName(), fullName, locationName, bot);
    }

    private void notifyAdminAboutRegistration(Long chatId, String username, String fullName, String locationName, BotNotificationPort bot) {
        if (adminTelegramId == null) {
            return;
        }
        StringBuilder text = new StringBuilder("🆕 Нова заявка на реєстрацію:\n");
        text.append("ПІБ: ").append(MarkdownEscaper.escape(fullName)).append("\n");
        text.append("Telegram ID: ").append(chatId).append("\n");
        if (username != null && !username.isBlank()) {
            text.append("Username: @").append(MarkdownEscaper.escape(username)).append("\n");
        }
        text.append("Локація: ").append(MarkdownEscaper.escape(locationName));
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Arrays.asList(
                InlineKeyboardButton.builder()
                        .text("✅ Підтвердити")
                        .callbackData("admin:approve:" + chatId)
                        .build(),
                InlineKeyboardButton.builder()
                        .text("❌ Відхилити")
                        .callbackData("admin:reject:" + chatId)
                        .build()
        ));
        markup.setKeyboard(rows);
        bot.sendMarkdown(adminTelegramId, text.toString(), markup);
    }

    private void startScheduleFlow(User user, BotNotificationPort bot) {
        if (personalScheduleService == null) {
            bot.sendMarkdown(user.getUserId(), "Сервіс графіків недоступний", null);
            return;
        }
        ConversationState state = personalScheduleFsm.start();
        stateStore.put(user.getUserId(), state);
        bot.sendMarkdown(user.getUserId(), "Оберіть місяць для графіку:", monthPickerMarkup("schedule:edit"));
    }

    private boolean handleScheduleMessage(User user, String text, ConversationState state, BotNotificationPort bot) {
        PersonalScheduleFsm.Step step = personalScheduleFsm.currentStep(state);
        if (step == PersonalScheduleFsm.Step.WAIT_MONTH_PICK) {
            bot.sendMarkdown(user.getUserId(), "Оберіть місяць кнопками нижче.", monthPickerMarkup("schedule:edit"));
            return true;
        }
        if (step == PersonalScheduleFsm.Step.WAIT_CONFIRM_ALL_OFF) {
            bot.sendMarkdown(user.getUserId(), "Підтвердіть збереження порожнього графіка кнопками нижче.", confirmAllOffMarkup(state));
            return true;
        }
        YearMonth month = monthFromState(state);
        if (month == null) {
            bot.sendMarkdown(user.getUserId(), "Оберіть місяць для графіку:", monthPickerMarkup("schedule:edit"));
            return true;
        }
        PersonalScheduleService.ParseResult parseResult = personalScheduleService.parseWorkDays(text, month);
        if (parseResult.errorMessage() != null) {
            bot.sendMarkdown(user.getUserId(), parseResult.errorMessage(), null);
            return true;
        }
        if (parseResult.isEmpty()) {
            state.getData().put(PersonalScheduleFsm.STEP_KEY, PersonalScheduleFsm.Step.WAIT_CONFIRM_ALL_OFF.name());
            state.getData().put(PersonalScheduleFsm.MONTH_KEY, month.toString());
            bot.sendMarkdown(user.getUserId(), "Ви не ввели жодного робочого дня. Зберегти весь місяць як OFF?", confirmAllOffMarkup(state));
            return true;
        }
        personalScheduleService.saveOrUpdate(user.getUserId(), month, parseResult.workDays());
        stateStore.clear(user.getUserId());
        bot.sendMarkdown(user.getUserId(), summaryMessage(month, parseResult.workDays()), savedScheduleMarkup());
        return true;
    }

    private void sendPersonalSchedule(User user, BotNotificationPort bot) {
        if (personalScheduleService == null) {
            bot.sendMarkdown(user.getUserId(), "Сервіс графіків недоступний", null);
            return;
        }
        sendPersonalScheduleMonth(user, personalScheduleService.currentMonth(), bot);
    }

    private void handlePersonalScheduleCallback(User user, String data, ConversationState state, BotNotificationPort bot) {
        if (personalScheduleService == null) {
            bot.sendMarkdown(user.getUserId(), "Сервіс графіків недоступний", null);
            return;
        }
        if (data.startsWith("schedule:edit:")) {
            YearMonth month = monthFromCallback(data);
            if (month == null) {
                bot.sendMarkdown(user.getUserId(), "Оберіть місяць для графіку:", monthPickerMarkup("schedule:edit"));
                return;
            }
            state.getData().put(PersonalScheduleFsm.STEP_KEY, PersonalScheduleFsm.Step.WAIT_DAYS_INPUT.name());
            state.getData().put(PersonalScheduleFsm.MONTH_KEY, month.toString());
            stateStore.put(user.getUserId(), state);
            bot.sendMarkdown(user.getUserId(), "Введіть числа дат через кому, наприклад: 1,2,3,5,6\nЦе робочі дні (WORK). Усі інші дні місяця будуть вихідні (OFF).", null);
            return;
        }
        if (data.startsWith("schedule:view:")) {
            YearMonth month = monthFromScheduleViewCallback(data);
            if (month == null) {
                bot.sendMarkdown(user.getUserId(), "Оберіть місяць для перегляду:", monthPickerMarkup("schedule:view"));
                return;
            }
            sendPersonalScheduleMonth(user, month, bot);
            return;
        }
        if (data.startsWith("schedule:confirm_off:")) {
            YearMonth month = YearMonth.parse(data.substring("schedule:confirm_off:".length()));
            personalScheduleService.saveOrUpdate(user.getUserId(), month, Collections.emptySet());
            stateStore.clear(user.getUserId());
            bot.sendMarkdown(user.getUserId(), summaryMessage(month, Collections.emptySet()), savedScheduleMarkup());
            return;
        }
        if (data.startsWith("schedule:cancel")) {
            stateStore.clear(user.getUserId());
            bot.sendMarkdown(user.getUserId(), "Дію скасовано.", mainMenu(user));
        }
    }

    private InlineKeyboardMarkup savedScheduleMarkup() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(buttonRow("👀 Переглянути", "M::schedule_view"));
        rows.add(buttonRow("🗓 Змінити ще раз", "M::schedule_edit"));
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup confirmAllOffMarkup(ConversationState state) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        String month = state.getData().get(PersonalScheduleFsm.MONTH_KEY);
        rows.add(buttonRow("Так, зберегти все OFF", "schedule:confirm_off:" + month));
        rows.add(buttonRow("Скасувати", "schedule:cancel"));
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup monthPickerMarkup(String prefix) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        YearMonth current = personalScheduleService.currentMonth();
        YearMonth next = personalScheduleService.nextMonth();
        rows.add(buttonRow("Поточний місяць: " + TimeUtils.humanMonthYear(current), prefix + ":current"));
        rows.add(buttonRow("Наступний місяць: " + TimeUtils.humanMonthYear(next), prefix + ":next"));
        markup.setKeyboard(rows);
        return markup;
    }

    private YearMonth monthFromCallback(String data) {
        if (data.endsWith(":current")) {
            return personalScheduleService.currentMonth();
        }
        if (data.endsWith(":next")) {
            return personalScheduleService.nextMonth();
        }
        return null;
    }

    private YearMonth monthFromScheduleViewCallback(String data) {
        String value = data.substring("schedule:view:".length());
        if ("current".equals(value)) {
            return personalScheduleService.currentMonth();
        }
        if ("next".equals(value)) {
            return personalScheduleService.nextMonth();
        }
        try {
            return YearMonth.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private YearMonth monthFromState(ConversationState state) {
        String value = state.getData().get(PersonalScheduleFsm.MONTH_KEY);
        if (value == null || value.isBlank()) {
            return null;
        }
        return YearMonth.parse(value);
    }

    private void sendPersonalScheduleMonth(User user, YearMonth month, BotNotificationPort bot) {
        if (month == null) {
            bot.sendMarkdown(user.getUserId(), "Оберіть місяць для перегляду:", monthPickerMarkup("schedule:view"));
            return;
        }
        Optional<com.shiftbot.model.ScheduleEntry> entry = personalScheduleService.findByUserAndMonth(user.getUserId(), month);
        if (entry.isEmpty() || entry.get().getWorkDaysCsv() == null) {
            bot.sendMarkdown(user.getUserId(),
                    "Графік не створено. Натисніть «🗓 Створити графік», щоб заповнити календар.",
                    scheduleMissingMarkup(month));
            return;
        }
        Set<Integer> workDays = personalScheduleService.workDaysFromCsv(entry.get().getWorkDaysCsv());
        String calendar = CalendarRenderer.renderMonth(month, workDays, new Locale("uk", "UA"));
        String message = "Графік на " + TimeUtils.humanMonthYear(month) + "\n<pre>" + calendar + "</pre>";
        bot.sendHtml(user.getUserId(), message, scheduleViewMarkup(month));
    }

    private InlineKeyboardMarkup scheduleViewMarkup(YearMonth month) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        YearMonth prev = month.minusMonths(1);
        YearMonth next = month.plusMonths(1);
        List<InlineKeyboardButton> navRow = new ArrayList<>();
        navRow.add(InlineKeyboardButton.builder()
                .text("◀️ Попередній")
                .callbackData("schedule:view:" + prev)
                .build());
        navRow.add(InlineKeyboardButton.builder()
                .text("Наступний ▶️")
                .callbackData("schedule:view:" + next)
                .build());
        rows.add(navRow);
        rows.add(buttonRow("🔙 Назад", "M::main_menu"));
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup scheduleMissingMarkup(YearMonth month) {
        InlineKeyboardMarkup markup = scheduleViewMarkup(month);
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(buttonRow("🗓 Створити графік", "M::schedule_edit"));
        rows.addAll(markup.getKeyboard());
        markup.setKeyboard(rows);
        return markup;
    }

    private String summaryMessage(YearMonth month, Set<Integer> workDays) {
        int totalDays = month.lengthOfMonth();
        int workCount = workDays.size();
        int offCount = totalDays - workCount;
        return "Збережено для " + TimeUtils.humanMonthYear(month) + ": WORK=" + workCount + ", OFF=" + offCount;
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
        rows.add(buttonRow("🗓 Створити/Оновити мій графік", "M::schedule_edit"));
        rows.add(buttonRow("👀 Переглянути мій графік", "M::schedule_view"));
        if (user.getRole() == Role.SENIOR) {
            rows.add(buttonRow("📨 Заявки", "requests:list"));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup pendingMenu() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(buttonRow("🔄 Перевірити статус", "status:check"));
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
