package com.shiftbot.bot.handler;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.bot.ui.CalendarKeyboardBuilder;
import com.shiftbot.model.Location;
import com.shiftbot.model.Request;
import com.shiftbot.model.User;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import com.shiftbot.model.enums.Role;
import com.shiftbot.service.AuditService;
import com.shiftbot.service.AuthService;
import com.shiftbot.service.RequestService;
import com.shiftbot.service.ScheduleService;
import com.shiftbot.repository.LocationsRepository;
import com.shiftbot.state.ConversationStateStore;
import com.shiftbot.state.CoverRequestFsm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UpdateRouterCoverFlowTest {

    private final ZoneId zoneId = ZoneId.of("Europe/Kyiv");
    private ConversationStateStore stateStore;
    private CoverRequestFsm coverRequestFsm;
    private AuthService authService;
    private ScheduleService scheduleService;
    private RequestService requestService;
    private LocationsRepository locationsRepository;
    private AuditService auditService;
    private UpdateRouter router;
    private BotNotificationPort bot;
    private User domainUser;

    @BeforeEach
    void setUp() {
        stateStore = new ConversationStateStore(Duration.ofMinutes(5));
        coverRequestFsm = new CoverRequestFsm();
        authService = mock(AuthService.class);
        scheduleService = mock(ScheduleService.class);
        requestService = mock(RequestService.class);
        locationsRepository = mock(LocationsRepository.class);
        auditService = mock(AuditService.class);
        router = new UpdateRouter(authService, scheduleService, requestService, new CalendarKeyboardBuilder(), locationsRepository, stateStore, coverRequestFsm, auditService, zoneId);
        bot = mock(BotNotificationPort.class);
        domainUser = new User();
        domainUser.setUserId(123L);
        domainUser.setRole(Role.SELLER);
        domainUser.setFullName("Test User");
        when(authService.findExisting(anyLong())).thenReturn(java.util.Optional.of(domainUser));
        when(authService.evaluateExisting(domainUser)).thenReturn(new AuthService.OnboardResult(domainUser, true, null));
    }

    @Test
    void coverFlowHappyPath() {
        when(locationsRepository.findActive()).thenReturn(List.of(new Location("loc1", "Store 1", "", true)));
        Request created = new Request("req1", RequestType.COVER, domainUser.getUserId(), null, null, LocalDate.of(2024, 4, 2),
                LocalTime.of(10, 0), LocalTime.of(18, 0), "loc1", RequestStatus.WAIT_TM, "comment", null, null);
        when(requestService.createCoverRequest(eq(domainUser.getUserId()), eq("loc1"), any(), any(), any(), any())).thenReturn(created);

        router.handle(messageUpdate(domainUser.getUserId(), "🆘 Потрібна заміна"), bot);
        router.handle(callbackUpdate(domainUser.getUserId(), "cover:date:2024-04-02"), bot);
        router.handle(messageUpdate(domainUser.getUserId(), "10:00-18:00"), bot);
        router.handle(callbackUpdate(domainUser.getUserId(), "cover:loc:loc1"), bot);
        router.handle(messageUpdate(domainUser.getUserId(), "Будь ласка"), bot);

        InOrder order = inOrder(bot);
        order.verify(bot).sendMarkdown(eq(domainUser.getUserId()), contains("Оберіть дату"), any());
        order.verify(bot).sendMarkdown(eq(domainUser.getUserId()), contains("Вкажіть час"), any());
        order.verify(bot, atLeastOnce()).sendMarkdown(eq(domainUser.getUserId()), contains("локац"), any());
        order.verify(bot, atLeastOnce()).sendMarkdown(eq(domainUser.getUserId()), contains("коментар"), any());

        verify(requestService).createCoverRequest(eq(domainUser.getUserId()), eq("loc1"),
                eq(LocalDate.parse("2024-04-02")), eq(LocalTime.of(10, 0)), eq(LocalTime.of(18, 0)), eq("Будь ласка"));
        assertTrue(stateStore.get(domainUser.getUserId()).isEmpty());
    }

    @Test
    void abortsCoverFlow() {
        router.handle(messageUpdate(domainUser.getUserId(), "🆘 Потрібна заміна"), bot);
        router.handle(messageUpdate(domainUser.getUserId(), "/stop"), bot);

        verify(bot, atLeastOnce()).sendMarkdown(eq(domainUser.getUserId()), contains("скасована"), any());
        assertTrue(stateStore.get(domainUser.getUserId()).isEmpty());
    }

    private Update messageUpdate(long chatId, String text) {
        Message message = new Message();
        message.setMessageId(1);
        message.setText(text);
        org.telegram.telegrambots.meta.api.objects.User from = new org.telegram.telegrambots.meta.api.objects.User();
        from.setId(chatId);
        from.setUserName("tester");
        from.setFirstName("Test");
        message.setFrom(from);
        Chat chat = new Chat();
        chat.setId(chatId);
        message.setChat(chat);

        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    private Update callbackUpdate(long chatId, String data) {
        CallbackQuery callbackQuery = new CallbackQuery();
        callbackQuery.setId("1");
        callbackQuery.setData(data);
        org.telegram.telegrambots.meta.api.objects.User from = new org.telegram.telegrambots.meta.api.objects.User();
        from.setId(chatId);
        from.setUserName("tester");
        from.setFirstName("Test");
        callbackQuery.setFrom(from);

        Message message = new Message();
        message.setMessageId(2);
        Chat chat = new Chat();
        chat.setId(chatId);
        message.setChat(chat);
        message.setFrom(from);

        callbackQuery.setMessage(message);

        Update update = new Update();
        update.setCallbackQuery(callbackQuery);
        return update;
    }
}
