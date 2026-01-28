package com.shiftbot.bot.handler;

import com.shiftbot.bot.BotNotificationPort;
import com.shiftbot.bot.ui.CalendarKeyboardBuilder;
import com.shiftbot.model.Location;
import com.shiftbot.model.Shift;
import com.shiftbot.model.User;
import com.shiftbot.model.enums.ShiftSource;
import com.shiftbot.model.enums.ShiftStatus;
import com.shiftbot.repository.LocationsRepository;
import com.shiftbot.repository.UsersRepository;
import com.shiftbot.service.AuthService;
import com.shiftbot.service.RequestService;
import com.shiftbot.service.ScheduleService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UpdateRouterLocationFlowTest {

    @Test
    void buildsLocationPickerFromActiveLocations() {
        AuthService authService = mock(AuthService.class);
        ScheduleService scheduleService = mock(ScheduleService.class);
        RequestService requestService = mock(RequestService.class);
        LocationsRepository locationsRepository = mock(LocationsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        CalendarKeyboardBuilder builder = new CalendarKeyboardBuilder();
        UpdateRouter router = new UpdateRouter(authService, scheduleService, requestService, locationsRepository, usersRepository, builder, ZoneId.of("Europe/Kyiv"));

        User user = new User();
        user.setUserId(5L);
        when(locationsRepository.findAll()).thenReturn(List.of(new Location("loc-1", "Mall", "", true)));

        BotNotificationPort bot = mock(BotNotificationPort.class);
        router.sendLocationPicker(user, bot);

        ArgumentCaptor<InlineKeyboardMarkup> markupCaptor = ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
        verify(bot).sendMarkdown(eq(5L), anyString(), markupCaptor.capture());
        InlineKeyboardMarkup markup = markupCaptor.getValue();
        assertEquals("location_pick:loc-1", markup.getKeyboard().get(0).get(0).getCallbackData());
    }

    @Test
    void handlesLocationDateCallback() {
        AuthService authService = mock(AuthService.class);
        ScheduleService scheduleService = mock(ScheduleService.class);
        RequestService requestService = mock(RequestService.class);
        LocationsRepository locationsRepository = mock(LocationsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        CalendarKeyboardBuilder builder = new CalendarKeyboardBuilder();
        UpdateRouter router = new UpdateRouter(authService, scheduleService, requestService, locationsRepository, usersRepository, builder, ZoneId.of("Europe/Kyiv"));

        User onboarded = new User();
        onboarded.setUserId(100L);
        onboarded.setFullName("Tester");
        when(authService.findExisting(anyLong())).thenReturn(Optional.of(onboarded));
        when(authService.evaluateExisting(onboarded)).thenReturn(new AuthService.OnboardResult(onboarded, true, null));
        when(locationsRepository.findById("loc-1")).thenReturn(Optional.of(new Location("loc-1", "Mall", "", true)));

        com.shiftbot.model.User seller = new com.shiftbot.model.User();
        seller.setUserId(10L);
        seller.setFullName("Alice");
        when(usersRepository.findById(10L)).thenReturn(Optional.of(seller));

        Shift shift = new Shift("1", LocalDate.parse("2024-03-10"), LocalTime.of(10, 0), LocalTime.of(14, 0), "loc-1", 10L, ShiftStatus.APPROVED, ShiftSource.MONTH_PLAN, null, null);
        when(scheduleService.shiftsForLocation("loc-1", LocalDate.parse("2024-03-10"))).thenReturn(List.of(shift));

        BotNotificationPort bot = mock(BotNotificationPort.class);

        CallbackQuery callbackQuery = Mockito.mock(CallbackQuery.class, Mockito.RETURNS_DEEP_STUBS);
        when(callbackQuery.getData()).thenReturn("location:loc-1:2024-03-10");
        when(callbackQuery.getMessage().getChatId()).thenReturn(100L);
        org.telegram.telegrambots.meta.api.objects.User telegramUser = new org.telegram.telegrambots.meta.api.objects.User();
        telegramUser.setId(100L);
        when(callbackQuery.getFrom()).thenReturn(telegramUser);

        Update update = new Update();
        update.setCallbackQuery(callbackQuery);

        router.handle(update, bot);

        verify(bot).sendMarkdown(eq(100L), contains("Alice"), isNull());
        verify(scheduleService).shiftsForLocation("loc-1", LocalDate.parse("2024-03-10"));
    }
}
