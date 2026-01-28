package com.shiftbot.bot.handler;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.model.User;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import com.shiftbot.repository.UsersRepository;
import com.shiftbot.service.AuditService;
import com.shiftbot.service.AuthService;
import com.shiftbot.service.RequestService;
import com.shiftbot.service.ScheduleService;
import com.shiftbot.bot.ui.CalendarKeyboardBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.isNull;

@ExtendWith(MockitoExtension.class)
class UpdateRouterTest {

    @Mock
    private AuthService authService;
    @Mock
    private ScheduleService scheduleService;
    @Mock
    private RequestService requestService;
    @Mock
    private UsersRepository usersRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private BotNotificationPort bot;

    private UpdateRouter router;
    private CalendarKeyboardBuilder calendarKeyboardBuilder = new CalendarKeyboardBuilder();

    @BeforeEach
    void setUp() {
        router = new UpdateRouter(authService, scheduleService, requestService, usersRepository, auditService, calendarKeyboardBuilder, ZoneId.of("UTC"));
    }

    @Test
    void handlesUserActivationCallback() {
        User actor = new User(10L, "tm", "TM User", "", Role.TM, UserStatus.ACTIVE, null, null);
        when(authService.findExisting(eq(1L))).thenReturn(Optional.of(actor));
        when(authService.evaluateExisting(actor)).thenReturn(new AuthService.OnboardResult(actor, true, null));
        User pendingUser = new User(2L, "pending", "Pending User", "", Role.SELLER, UserStatus.PENDING, null, null);
        when(usersRepository.findById(2L)).thenReturn(Optional.of(pendingUser));

        Update update = new Update();
        CallbackQuery callbackQuery = new CallbackQuery();
        callbackQuery.setData("user:activate:2");
        org.telegram.telegrambots.meta.api.objects.User telegramUser = new org.telegram.telegrambots.meta.api.objects.User();
        telegramUser.setId(1L);
        telegramUser.setUserName("tm");
        telegramUser.setFirstName("TM");
        telegramUser.setLastName("User");
        callbackQuery.setFrom(telegramUser);
        Message message = new Message();
        message.setChatId(1L);
        callbackQuery.setMessage(message);
        update.setCallbackQuery(callbackQuery);

        router.handle(update, bot);

        ArgumentCaptor<User> updatedUserCaptor = ArgumentCaptor.forClass(User.class);
        verify(usersRepository).updateRow(eq(2L), updatedUserCaptor.capture());
        assertEquals(UserStatus.ACTIVE, updatedUserCaptor.getValue().getStatus());
        verify(auditService).logEvent(eq(actor.getUserId()), eq("user_activated"), eq("user"), eq("2"), eq(Map.of("previousStatus", "PENDING", "newStatus", "ACTIVE")), eq(bot));
        verify(bot).sendMarkdown(eq(2L), startsWith("✅"), isNull());
    }
}
