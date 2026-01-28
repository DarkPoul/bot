package com.shiftbot.bot.handler;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.bot.ui.CalendarKeyboardBuilder;
import com.shiftbot.model.Location;
import com.shiftbot.model.Request;
import com.shiftbot.model.Shift;
import com.shiftbot.model.User;
import com.shiftbot.repository.LocationsRepository;
import com.shiftbot.repository.RequestsRepository;
import com.shiftbot.repository.ShiftsRepository;
import com.shiftbot.repository.UsersRepository;
import com.shiftbot.service.AuthService;
import com.shiftbot.service.RequestService;
import com.shiftbot.service.ScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

abstract class FlowTestSupport {
    protected ZoneId zoneId = ZoneId.of("UTC");
    protected InMemoryUsersRepository usersRepository;
    protected InMemoryRequestsRepository requestsRepository;
    protected InMemoryShiftsRepository shiftsRepository;
    protected InMemoryLocationsRepository locationsRepository;
    protected AuthService authService;
    protected ScheduleService scheduleService;
    protected RequestService requestService;
    protected CalendarKeyboardBuilder calendarKeyboardBuilder;
    protected UpdateRouter router;
    protected FakeBot bot;

    @BeforeEach
    void setUpBase() {
        usersRepository = new InMemoryUsersRepository();
        requestsRepository = new InMemoryRequestsRepository();
        shiftsRepository = new InMemoryShiftsRepository();
        locationsRepository = new InMemoryLocationsRepository();
        authService = new AuthService(usersRepository, zoneId);
        scheduleService = new ScheduleService(shiftsRepository, locationsRepository, zoneId);
        requestService = new RequestService(requestsRepository, zoneId);
        calendarKeyboardBuilder = new CalendarKeyboardBuilder();
        router = new UpdateRouter(authService, scheduleService, requestService, locationsRepository, usersRepository, calendarKeyboardBuilder, zoneId);
        bot = new FakeBot();
    }

    protected Update messageUpdate(long chatId, String username, String firstName, String lastName, String text) {
        Update update = new Update();
        Message message = new Message();
        message.setMessageId((int) (Math.random() * 10_000));
        message.setText(text);

        Chat chat = new Chat();
        chat.setId(chatId);
        chat.setType("private");
        message.setChat(chat);

        message.setFrom(telegramUser(chatId, username, firstName, lastName));
        update.setMessage(message);
        return update;
    }

    protected Update callbackUpdate(long chatId, String username, String firstName, String lastName, String data) {
        Update update = new Update();
        CallbackQuery callback = new CallbackQuery();
        callback.setId(UUID.randomUUID().toString());
        callback.setData(data);
        callback.setFrom(telegramUser(chatId, username, firstName, lastName));

        Message callbackMessage = new Message();
        Chat chat = new Chat();
        chat.setId(chatId);
        callbackMessage.setChat(chat);
        callback.setMessage(callbackMessage);

        update.setCallbackQuery(callback);
        return update;
    }

    protected boolean hasButtonWithText(InlineKeyboardMarkup markup, String text) {
        if (markup == null || markup.getKeyboard() == null) {
            return false;
        }
        return markup.getKeyboard().stream()
                .flatMap(List::stream)
                .anyMatch(btn -> text.equals(btn.getText()));
    }

    protected boolean hasCallbackWithPrefix(InlineKeyboardMarkup markup, String prefix) {
        if (markup == null || markup.getKeyboard() == null) {
            return false;
        }
        return markup.getKeyboard().stream()
                .flatMap(List::stream)
                .map(InlineKeyboardButton::getCallbackData)
                .anyMatch(data -> data != null && data.startsWith(prefix));
    }

    private org.telegram.telegrambots.meta.api.objects.User telegramUser(long id, String username, String firstName, String lastName) {
        org.telegram.telegrambots.meta.api.objects.User from = new org.telegram.telegrambots.meta.api.objects.User();
        from.setId(id);
        from.setIsBot(false);
        from.setUserName(username);
        from.setFirstName(firstName);
        from.setLastName(lastName);
        return from;
    }

    protected static class FakeBot implements BotNotificationPort {
        private final List<SentMessage> messages = new ArrayList<>();

        @Override
        public void sendMarkdown(Long chatId, String text, InlineKeyboardMarkup markup) {
            messages.add(new SentMessage(chatId, text, markup));
        }

        public List<SentMessage> getMessages() {
            return messages;
        }

        public SentMessage lastMessage() {
            return messages.get(messages.size() - 1);
        }
    }

    protected record SentMessage(Long chatId, String text, InlineKeyboardMarkup markup) {}

    protected static class InMemoryUsersRepository extends UsersRepository {
        private final Map<Long, User> storage = new LinkedHashMap<>();

        InMemoryUsersRepository() {
            super(null, Duration.ZERO);
        }

        @Override
        public synchronized List<User> findAll() {
            return new ArrayList<>(storage.values());
        }

        @Override
        public Optional<User> findById(long userId) {
            return Optional.ofNullable(storage.get(userId));
        }

        @Override
        public synchronized void save(User user) {
            storage.put(user.getUserId(), user);
        }
    }

    protected static class InMemoryRequestsRepository extends RequestsRepository {
        private final List<Request> storage = new ArrayList<>();

        InMemoryRequestsRepository() {
            super(null);
        }

        @Override
        public List<Request> findAll() {
            return new ArrayList<>(storage);
        }

        @Override
        public void save(Request request) {
            if (request.getRequestId() == null) {
                request.setRequestId(UUID.randomUUID().toString());
            }
            storage.add(request);
        }
    }

    protected static class InMemoryShiftsRepository extends ShiftsRepository {
        private final List<Shift> storage = new ArrayList<>();

        InMemoryShiftsRepository() {
            super(null);
        }

        @Override
        public List<Shift> findByUser(long userId) {
            return storage.stream()
                    .filter(shift -> shift.getUserId() == userId)
                    .collect(Collectors.toList());
        }

        @Override
        public List<Shift> findByLocation(String locationId) {
            return storage.stream()
                    .filter(shift -> locationId.equals(shift.getLocationId()))
                    .collect(Collectors.toList());
        }

        @Override
        public void save(Shift shift) {
            if (shift.getShiftId() == null) {
                shift.setShiftId(UUID.randomUUID().toString());
            }
            storage.add(shift);
        }
    }

    protected static class InMemoryLocationsRepository extends LocationsRepository {
        private final Map<String, Location> storage = new LinkedHashMap<>();

        InMemoryLocationsRepository() {
            super(null, Duration.ZERO);
        }

        @Override
        public synchronized List<Location> findAll() {
            return new ArrayList<>(storage.values());
        }

        @Override
        public Optional<Location> findById(String id) {
            return Optional.ofNullable(storage.get(id));
        }

        public void save(Location location) {
            storage.put(location.getLocationId(), location);
        }
    }
}
