package com.shiftbot.bot.handler;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.bot.ui.CalendarKeyboardBuilder;
import com.shiftbot.model.Location;
import com.shiftbot.model.Shift;
import com.shiftbot.model.User;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.ShiftStatus;
import com.shiftbot.model.enums.UserStatus;
import com.shiftbot.service.AuthService;
import com.shiftbot.service.RequestService;
import com.shiftbot.service.ScheduleService;
import com.shiftbot.service.AuditService;
import com.shiftbot.repository.UsersRepository;
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
    private final UsersRepository usersRepository;
    private final AuditService auditService;
    private final CalendarKeyboardBuilder calendarKeyboardBuilder;
    private final ZoneId zoneId;

    public UpdateRouter(AuthService authService, ScheduleService scheduleService, RequestService requestService,
                        UsersRepository usersRepository, AuditService auditService,
                        CalendarKeyboardBuilder calendarKeyboardBuilder, ZoneId zoneId) {
        this.authService = authService;
        this.scheduleService = scheduleService;
        this.requestService = requestService;
        this.usersRepository = usersRepository;
        this.auditService = auditService;
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
        AuthService.OnboardResult onboardResult = authService.onboard(chatId, message.getFrom().getUserName(), buildFullName(message));
        if (!onboardResult.allowed()) {
            bot.sendMarkdown(chatId, MarkdownEscaper.escape(onboardResult.message()), null);
            return;
        }
        User user = onboardResult.user();

        if (text.startsWith("/start")) {
            bot.sendMarkdown(chatId, "👋 Вітаємо, " + MarkdownEscaper.escape(user.getFullName()) + "!", mainMenu(user));
            return;
        }

        switch (text) {
            case "Мій графік", "📅 Мій графік" -> sendMySchedule(user, bot);
            case "Потрібна заміна", "🆘 Потрібна заміна" -> sendCoverRequestIntro(user, bot);
            case "⏳ Нові користувачі" -> sendPendingUsers(user, bot);
            default -> bot.sendMarkdown(chatId, "Оберіть дію з меню нижче", mainMenu(user));
        }
    }

    private void handleCallback(CallbackQuery callback, BotNotificationPort bot) {
        String data = callback.getData();
        Long chatId = callback.getMessage().getChatId();
        AuthService.OnboardResult onboardResult = authService.onboard(chatId, callback.getFrom().getUserName(), buildFullName(callback.getFrom().getFirstName(), callback.getFrom().getLastName()));
        if (!onboardResult.allowed()) {
            bot.sendMarkdown(chatId, MarkdownEscaper.escape(onboardResult.message()), null);
            return;
        }
        User user = onboardResult.user();

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
                case "location" -> sendLocationPicker(user, bot);
                case "cover" -> sendCoverRequestIntro(user, bot);
                case "pendingUsers" -> sendPendingUsers(user, bot);
                default -> bot.sendMarkdown(chatId, "Меню в розробці", null);
            }
        } else if (data.startsWith("user:activate:")) {
            handleUserStatusChange(user, data, true, bot);
        } else if (data.startsWith("user:reject:")) {
            handleUserStatusChange(user, data, false, bot);
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

    void sendLocationPicker(User user, BotNotificationPort bot) {
        List<Location> locations = locationsRepository.findAll().stream()
                .filter(Location::isActive)
                .toList();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Location location : locations) {
            rows.add(Collections.singletonList(InlineKeyboardButton.builder()
                    .text(location.getName())
                    .callbackData("location_pick:" + location.getLocationId())
                    .build()));
        }
        markup.setKeyboard(rows);
        String text = "🏪 Оберіть локацію для перегляду графіку";
        bot.sendMarkdown(user.getUserId(), MarkdownEscaper.escape(text), markup);
    }

    void sendLocationCalendar(User user, String locationId, BotNotificationPort bot) {
        LocalDate month = TimeUtils.today(zoneId).withDayOfMonth(1);
        Map<LocalDate, ShiftStatus> statuses = scheduleService.calendarStatusesForLocation(locationId, month);
        InlineKeyboardMarkup calendar = calendarKeyboardBuilder.buildMonth(month, statuses, "location:" + locationId + ":");
        String locationName = locationsRepository.findById(locationId).map(Location::getName).orElse(locationId);
        String text = "🏪 " + locationName + " — оберіть день";
        bot.sendMarkdown(user.getUserId(), MarkdownEscaper.escape(text), calendar);
    }

    void sendLocationSchedule(User user, String locationId, LocalDate date, BotNotificationPort bot) {
        List<Shift> shifts = scheduleService.shiftsForLocation(locationId, date);
        String locationName = locationsRepository.findById(locationId).map(Location::getName).orElse(locationId);
        if (shifts.isEmpty()) {
            String text = "⬜ Немає змін для " + locationName + " на " + TimeUtils.humanDate(date, zoneId);
            bot.sendMarkdown(user.getUserId(), MarkdownEscaper.escape(text), null);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🏪 ").append(locationName).append("\\n");
        sb.append("📅 ").append(TimeUtils.humanDate(date, zoneId)).append("\\n");
        for (Shift shift : shifts) {
            String seller = usersRepository.findById(shift.getUserId())
                    .map(User::getFullName)
                    .orElse("ID " + shift.getUserId());
            sb.append("• ")
                    .append(TimeUtils.humanTimeRange(shift.getStartTime(), shift.getEndTime()))
                    .append(" — ")
                    .append(seller)
                    .append(" (")
                    .append(statusLabel(shift.getStatus()))
                    .append(")")
                    .append("\\n");
        }
        bot.sendMarkdown(user.getUserId(), MarkdownEscaper.escape(sb.toString()), null);
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
}
