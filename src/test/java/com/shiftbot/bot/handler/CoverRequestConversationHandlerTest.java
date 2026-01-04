package com.shiftbot.bot.handler;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.bot.ui.UiMessages;
import com.shiftbot.model.User;
import com.shiftbot.state.ConversationState;
import com.shiftbot.state.ConversationStateStore;
import com.shiftbot.service.RequestService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class CoverRequestConversationHandlerTest {

    @Test
    void cancelsConversation() {
        ConversationStateStore store = new ConversationStateStore(Duration.ofMinutes(5));
        RequestService requestService = mock(RequestService.class);
        BotNotificationPort bot = mock(BotNotificationPort.class);
        CoverRequestConversationHandler handler = new CoverRequestConversationHandler(store, requestService, ZoneId.of("Europe/Kyiv"));
        User user = new User();
        user.setUserId(1L);

        handler.start(user, bot);
        handler.handleUserInput(user, "cancel", bot);

        verify(bot).sendMarkdown(eq(1L), eq(UiMessages.CONVERSATION_CANCELLED), isNull());
        assertFalse(store.has(1L));
    }

    @Test
    void repeatsPromptOnInvalidDate() {
        ConversationStateStore store = new ConversationStateStore(Duration.ofMinutes(5));
        RequestService requestService = mock(RequestService.class);
        BotNotificationPort bot = mock(BotNotificationPort.class);
        CoverRequestConversationHandler handler = new CoverRequestConversationHandler(store, requestService, ZoneId.of("Europe/Kyiv"));
        User user = new User();
        user.setUserId(2L);

        handler.start(user, bot);
        handler.handleUserInput(user, "32.13", bot);

        verify(bot).sendMarkdown(eq(2L), eq(UiMessages.INVALID_DATE_FORMAT), isNull());
        ConversationState state = store.get(2L).orElseThrow();
        assertEquals("awaiting_date", state.getName());
    }

    @Test
    void warnsOnTimeout() {
        ConversationStateStore store = new ConversationStateStore(Duration.ofSeconds(1));
        RequestService requestService = mock(RequestService.class);
        BotNotificationPort bot = mock(BotNotificationPort.class);
        CoverRequestConversationHandler handler = new CoverRequestConversationHandler(store, requestService, ZoneId.of("Europe/Kyiv"));
        User user = new User();
        user.setUserId(3L);

        handler.start(user, bot);
        ConversationState state = store.get(3L).orElseThrow();
        state.setUpdatedAt(Instant.now().minus(Duration.ofMinutes(10)));

        handler.handleUserInput(user, "05.07", bot);

        verify(bot).sendMarkdown(eq(3L), eq(UiMessages.CONVERSATION_TIMEOUT), isNull());
        assertFalse(store.has(3L));
    }
}
