package com.shiftbot;

import com.shiftbot.bot.ShiftSchedulerBot;
import com.shiftbot.bot.handler.UpdateRouter;
import com.shiftbot.bot.ui.CalendarKeyboardBuilder;
import com.shiftbot.config.EnvironmentConfig;
import com.shiftbot.repository.*;
import com.shiftbot.service.*;
import com.shiftbot.state.ConversationStateStore;
import com.shiftbot.state.CoverRequestFsm;
import com.shiftbot.state.OnboardingFsm;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class Application {
    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        SheetsClient sheetsClient = new SheetsClient(config.getSpreadsheetId(), config.getCredentialsPath());

        UsersRepository usersRepository = new UsersRepository(sheetsClient, Duration.ofMinutes(5));
        LocationsRepository locationsRepository = new LocationsRepository(sheetsClient, Duration.ofMinutes(10));
        LocationAssignmentsRepository locationAssignmentsRepository = new LocationAssignmentsRepository(sheetsClient);
        ShiftsRepository shiftsRepository = new ShiftsRepository(sheetsClient);
        RequestsRepository requestsRepository = new RequestsRepository(sheetsClient);
        AuditRepository auditRepository = new AuditRepository(sheetsClient);
        ConversationStateStore stateStore = new ConversationStateStore(Duration.ofMinutes(15));

        AuditService auditService = new AuditService(auditRepository, Long.parseLong(config.getAuditGroupId()), config.getZoneId());
        AuthService authService = new AuthService(usersRepository, auditService, config.getZoneId());
        ScheduleService scheduleService = new ScheduleService(shiftsRepository, locationsRepository, config.getZoneId());
        RequestService requestService = new RequestService(requestsRepository, shiftsRepository, config.getZoneId());
        CalendarKeyboardBuilder calendarKeyboardBuilder = new CalendarKeyboardBuilder();

        UpdateRouter updateRouter = new UpdateRouter(authService, scheduleService, requestService, locationsRepository,
                usersRepository, locationAssignmentsRepository, calendarKeyboardBuilder, stateStore, new CoverRequestFsm(),
                new OnboardingFsm(), auditService, config.getZoneId());
        ShiftSchedulerBot bot = new ShiftSchedulerBot(config.getBotToken(), config.getBotUsername(), updateRouter);
        auditService.setBot(bot);

        ReminderService reminderService = new ReminderService(scheduleService, usersRepository, requestsRepository, bot, config.getZoneId());

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(bot);
        reminderService.start();
        Runtime.getRuntime().addShutdownHook(new Thread(reminderService::stop));
        log.info("Bot started with username {}", config.getBotUsername());
    }
}
