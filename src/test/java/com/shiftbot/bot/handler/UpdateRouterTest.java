package com.shiftbot.bot.handler;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.model.User;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import com.shiftbot.repository.UsersRepository;
import com.shiftbot.service.AuditService;
import com.shiftbot.service.AuthService;
import com.shiftbot.service.PersonalScheduleService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

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
    private PersonalScheduleService personalScheduleService;
    @Mock
    private BotNotificationPort bot;

    private UpdateRouter router;
    private CalendarKeyboardBuilder calendarKeyboardBuilder = new CalendarKeyboardBuilder();

    @BeforeEach
    void setUp() {
        router = new UpdateRouter(authService, scheduleService, requestService, null, usersRepository, null, personalScheduleService,
                calendarKeyboardBuilder, new com.shiftbot.state.ConversationStateStore(java.time.Duration.ofMinutes(5)),
                new com.shiftbot.state.CoverRequestFsm(), new com.shiftbot.state.OnboardingFsm(),
                new com.shiftbot.state.PersonalScheduleFsm(), auditService, ZoneId.of("UTC"), 99L);
    }

    @Test
    void handlesAdminApprovalCallback() {
        User admin = new User(99L, "admin", "Admin User", "", Role.SENIOR, UserStatus.APPROVED, null, null);
        when(authService.findExisting(eq(99L))).thenReturn(Optional.of(admin));
        when(authService.evaluateExisting(admin)).thenReturn(new AuthService.OnboardResult(admin, true, null));
        User pendingUser = new User(2L, "pending", "Pending User", "", Role.SELLER, UserStatus.PENDING, null, null);
        when(usersRepository.findById(2L)).thenReturn(Optional.of(pendingUser));

        Update update = new Update();
        CallbackQuery callbackQuery = new CallbackQuery();
        callbackQuery.setData("admin:approve:2");
        org.telegram.telegrambots.meta.api.objects.User telegramUser = new org.telegram.telegrambots.meta.api.objects.User();
        telegramUser.setId(99L);
        telegramUser.setUserName("admin");
        telegramUser.setFirstName("Admin");
        telegramUser.setLastName("User");
        callbackQuery.setFrom(telegramUser);
        Message message = new Message();
        message.setChatId(99L);
        callbackQuery.setMessage(message);
        update.setCallbackQuery(callbackQuery);

        router.handle(update, bot);

        ArgumentCaptor<User> updatedUserCaptor = ArgumentCaptor.forClass(User.class);
        verify(usersRepository).updateRow(eq(2L), updatedUserCaptor.capture());
        assertEquals(UserStatus.APPROVED, updatedUserCaptor.getValue().getStatus());
        verify(bot).sendMarkdown(eq(2L), eq("Вас підтверджено, доступ відкрито"), isNull());
    }
}
