package com.shiftbot.bot.handler;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.bot.ui.CalendarKeyboardBuilder;
import com.shiftbot.model.Request;
import com.shiftbot.model.User;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import com.shiftbot.repository.UsersRepository;
import com.shiftbot.service.AuthService;
import com.shiftbot.service.RequestService;
import com.shiftbot.service.ScheduleService;
import com.shiftbot.state.ConversationStateStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UpdateRouterSwapHandlerTest {

    @Test
    void peerAcceptTriggersTmNotification() {
        AuthService authService = Mockito.mock(AuthService.class);
        ScheduleService scheduleService = Mockito.mock(ScheduleService.class);
        RequestService requestService = Mockito.mock(RequestService.class);
        UsersRepository usersRepository = Mockito.mock(UsersRepository.class);
        ConversationStateStore stateStore = new ConversationStateStore(Duration.ofMinutes(5));
        UpdateRouter router = new UpdateRouter(authService, scheduleService, requestService, usersRepository, stateStore, new CalendarKeyboardBuilder(), ZoneId.of("Europe/Kyiv"));
        BotNotificationPort bot = Mockito.mock(BotNotificationPort.class);

        User peer = new User(20L, "peer", "Peer User", "", Role.SELLER, UserStatus.ACTIVE, null, null);
        when(authService.onboard(anyLong(), any(), any())).thenReturn(new AuthService.OnboardResult(peer, true, null));

        Request request = new Request();
        request.setRequestId("req1");
        request.setType(RequestType.SWAP);
        request.setInitiatorUserId(10L);
        request.setToUserId(20L);
        request.setDate(LocalDate.of(2024, 4, 10));
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(18, 0));
        request.setLocationId("loc1");
        request.setStatus(RequestStatus.WAIT_PEER);
        request.setComment("comment");

        Request accepted = new Request(request.getRequestId(), RequestType.SWAP, request.getInitiatorUserId(), request.getFromUserId(), request.getToUserId(),
                request.getDate(), request.getStartTime(), request.getEndTime(), request.getLocationId(), RequestStatus.WAIT_TM, request.getComment(), null, null);
        when(requestService.findById("req1")).thenReturn(Optional.of(request));
        when(requestService.acceptByPeer("req1")).thenReturn(accepted);

        User tmUser = new User(30L, "tm", "TM User", "", Role.TM, UserStatus.ACTIVE, null, null);
        when(usersRepository.findAll()).thenReturn(List.of(tmUser, peer));
        when(usersRepository.findById(10L)).thenReturn(Optional.of(new User(10L, "init", "Initiator", "", Role.SELLER, UserStatus.ACTIVE, null, null)));
        when(usersRepository.findById(20L)).thenReturn(Optional.of(peer));

        Update update = buildCallbackUpdate(20L, "swapPeerAccept:req1");
        router.handle(update, bot);

        verify(requestService).acceptByPeer("req1");
        verify(bot).sendMarkdown(eq(tmUser.getUserId()), ArgumentMatchers.contains("Підміна"), any());
    }

    @Test
    void peerDeclineNotifiesInitiator() {
        AuthService authService = Mockito.mock(AuthService.class);
        ScheduleService scheduleService = Mockito.mock(ScheduleService.class);
        RequestService requestService = Mockito.mock(RequestService.class);
        UsersRepository usersRepository = Mockito.mock(UsersRepository.class);
        ConversationStateStore stateStore = new ConversationStateStore(Duration.ofMinutes(5));
        UpdateRouter router = new UpdateRouter(authService, scheduleService, requestService, usersRepository, stateStore, new CalendarKeyboardBuilder(), ZoneId.of("Europe/Kyiv"));
        BotNotificationPort bot = Mockito.mock(BotNotificationPort.class);

        User peer = new User(20L, "peer", "Peer User", "", Role.SELLER, UserStatus.ACTIVE, null, null);
        when(authService.onboard(anyLong(), any(), any())).thenReturn(new AuthService.OnboardResult(peer, true, null));

        Request request = new Request();
        request.setRequestId("req1");
        request.setType(RequestType.SWAP);
        request.setInitiatorUserId(10L);
        request.setToUserId(20L);
        request.setDate(LocalDate.of(2024, 4, 10));
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(18, 0));
        request.setLocationId("loc1");
        request.setStatus(RequestStatus.WAIT_PEER);
        request.setComment("comment");

        Request declined = new Request(request.getRequestId(), RequestType.SWAP, request.getInitiatorUserId(), request.getFromUserId(), request.getToUserId(),
                request.getDate(), request.getStartTime(), request.getEndTime(), request.getLocationId(), RequestStatus.REJECTED_TM, request.getComment(), null, null);
        when(requestService.findById("req1")).thenReturn(Optional.of(request));
        when(requestService.declineByPeer("req1")).thenReturn(declined);

        User initiator = new User(10L, "init", "Initiator", "", Role.SELLER, UserStatus.ACTIVE, null, null);
        when(usersRepository.findById(10L)).thenReturn(Optional.of(initiator));
        when(usersRepository.findById(20L)).thenReturn(Optional.of(peer));

        Update update = buildCallbackUpdate(20L, "swapPeerDecline:req1");
        router.handle(update, bot);

        verify(requestService).declineByPeer("req1");
        verify(bot).sendMarkdown(eq(initiator.getUserId()), contains("відхилено"), isNull());
    }

    private Update buildCallbackUpdate(long chatId, String data) {
        Update update = new Update();
        CallbackQuery callback = new CallbackQuery();
        callback.setData(data);
        org.telegram.telegrambots.meta.api.objects.User tgUser = new org.telegram.telegrambots.meta.api.objects.User();
        tgUser.setId(chatId);
        callback.setFrom(tgUser);
        Message message = new Message();
        message.setChat(new Chat(chatId, "private"));
        callback.setMessage(message);
        update.setCallbackQuery(callback);
        return update;
    }
}
