package com.shiftbot.bot.handler;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.bot.ui.UiMessages;
import com.shiftbot.model.User;
import com.shiftbot.state.ConversationState;
import com.shiftbot.state.ConversationStateStore;
import com.shiftbot.service.RequestService;
import com.shiftbot.util.InputValidator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

public class CoverRequestConversationHandler {
    private final ConversationStateStore stateStore;
    private final RequestService requestService;
    private final ZoneId zoneId;

    public CoverRequestConversationHandler(ConversationStateStore stateStore, RequestService requestService, ZoneId zoneId) {
        this.stateStore = stateStore;
        this.requestService = requestService;
        this.zoneId = zoneId;
    }

    public boolean hasConversation(Long userId) {
        return stateStore.has(userId);
    }

    public void start(User user, BotNotificationPort bot) {
        stateStore.put(user.getUserId(), new ConversationState("awaiting_date"));
        bot.sendMarkdown(user.getUserId(), UiMessages.PROMPT_DATE, null);
    }

    public void handleUserInput(User user, String text, BotNotificationPort bot) {
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("cancel") || normalized.equals("/cancel")) {
            stateStore.clear(user.getUserId());
            bot.sendMarkdown(user.getUserId(), UiMessages.CONVERSATION_CANCELLED, null);
            return;
        }

        Optional<ConversationState> stateOptional = stateStore.get(user.getUserId());
        if (stateOptional.isEmpty()) {
            bot.sendMarkdown(user.getUserId(), UiMessages.NO_ACTIVE_CONVERSATION, null);
            return;
        }

        ConversationState state = stateOptional.get();
        if (ConversationState.STATE_TIMEOUT.equals(state.getName())) {
            bot.sendMarkdown(user.getUserId(), UiMessages.CONVERSATION_TIMEOUT, null);
            stateStore.clear(user.getUserId());
            return;
        }

        switch (state.getName()) {
            case ConversationState.STATE_NOOP -> {
                bot.sendMarkdown(user.getUserId(), UiMessages.NOOP_MESSAGE, null);
                stateStore.touch(user.getUserId());
            }
            case "awaiting_date" -> handleDate(user, text, state, bot);
            case "awaiting_start" -> handleStart(user, text, state, bot);
            case "awaiting_end" -> handleEnd(user, text, state, bot);
            default -> bot.sendMarkdown(user.getUserId(), UiMessages.NO_ACTIVE_CONVERSATION, null);
        }
    }

    public void handleNoop(Long userId, BotNotificationPort bot) {
        if (!stateStore.has(userId)) {
            return;
        }
        stateStore.touch(userId);
        bot.sendMarkdown(userId, UiMessages.NOOP_MESSAGE, null);
    }

    private void handleDate(User user, String text, ConversationState state, BotNotificationPort bot) {
        Optional<LocalDate> parsedDate = InputValidator.parseDate(text, zoneId);
        if (parsedDate.isEmpty()) {
            bot.sendMarkdown(user.getUserId(), UiMessages.INVALID_DATE_FORMAT, null);
            return;
        }
        state.getData().put("date", parsedDate.get().toString());
        stateStore.put(user.getUserId(), new ConversationState("awaiting_start", state.getData()));
        bot.sendMarkdown(user.getUserId(), UiMessages.PROMPT_START_TIME, null);
    }

    private void handleStart(User user, String text, ConversationState state, BotNotificationPort bot) {
        Optional<LocalTime> parsedTime = InputValidator.parseTime(text);
        if (parsedTime.isEmpty()) {
            bot.sendMarkdown(user.getUserId(), UiMessages.INVALID_TIME_FORMAT, null);
            return;
        }
        state.getData().put("start", parsedTime.get().toString());
        stateStore.put(user.getUserId(), new ConversationState("awaiting_end", state.getData()));
        bot.sendMarkdown(user.getUserId(), UiMessages.PROMPT_END_TIME, null);
    }

    private void handleEnd(User user, String text, ConversationState state, BotNotificationPort bot) {
        Optional<LocalTime> parsedTime = InputValidator.parseTime(text);
        if (parsedTime.isEmpty()) {
            bot.sendMarkdown(user.getUserId(), UiMessages.INVALID_TIME_FORMAT, null);
            return;
        }
        LocalTime startTime = LocalTime.parse(state.getData().get("start"));
        if (!parsedTime.get().isAfter(startTime)) {
            bot.sendMarkdown(user.getUserId(), UiMessages.INVALID_TIME_RANGE, null);
            return;
        }
        LocalDate date = LocalDate.parse(state.getData().get("date"));
        requestService.createCoverRequest(user.getUserId(), state.getData().getOrDefault("locationId", "unknown"),
                date, startTime, parsedTime.get(), "Заявка з бота");
        bot.sendMarkdown(user.getUserId(), UiMessages.REQUEST_CREATED, null);
        stateStore.clear(user.getUserId());
    }
}
