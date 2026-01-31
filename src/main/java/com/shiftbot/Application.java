package com.shiftbot;

import com.shiftbot.bot.ShiftSchedulerBot;
import com.shiftbot.bot.handler.UpdateRouter;
import com.shiftbot.bot.ui.CalendarKeyboardBuilder;
import com.shiftbot.config.EnvironmentConfig;
import com.shiftbot.config.StorageType;
import com.shiftbot.repository.AccessRequestsRepository;
import com.shiftbot.repository.AuditRepository;
import com.shiftbot.repository.LocationAssignmentsRepository;
import com.shiftbot.repository.LocationsRepository;
import com.shiftbot.repository.RepositoryBundle;
import com.shiftbot.repository.RequestsRepository;
import com.shiftbot.repository.SchedulesRepository;
import com.shiftbot.repository.ShiftsRepository;
import com.shiftbot.repository.SubstitutionRequestsRepository;
import com.shiftbot.repository.UsersRepository;
import com.shiftbot.repository.sheets.SheetsAccessRequestsRepository;
import com.shiftbot.repository.sheets.SheetsAuditRepository;
import com.shiftbot.repository.sheets.SheetsClient;
import com.shiftbot.repository.sheets.SheetsLocationAssignmentsRepository;
import com.shiftbot.repository.sheets.SheetsLocationsRepository;
import com.shiftbot.repository.sheets.SheetsRequestsRepository;
import com.shiftbot.repository.sheets.SheetsSchedulesRepository;
import com.shiftbot.repository.sheets.SheetsShiftsRepository;
import com.shiftbot.repository.sheets.SheetsSubstitutionRequestsRepository;
import com.shiftbot.repository.sheets.SheetsUsersRepository;
import com.shiftbot.repository.sqlite.SqliteAccessRequestsRepository;
import com.shiftbot.repository.sqlite.SqliteAuditRepository;
import com.shiftbot.repository.sqlite.SqliteDatabase;
import com.shiftbot.repository.sqlite.SqliteLocationAssignmentsRepository;
import com.shiftbot.repository.sqlite.SqliteLocationsRepository;
import com.shiftbot.repository.sqlite.SqliteRequestsRepository;
import com.shiftbot.repository.sqlite.SqliteSchedulesRepository;
import com.shiftbot.repository.sqlite.SqliteShiftsRepository;
import com.shiftbot.repository.sqlite.SqliteSubstitutionRequestsRepository;
import com.shiftbot.repository.sqlite.SqliteUsersRepository;
import com.shiftbot.service.AccessRequestService;
import com.shiftbot.service.AuditService;
import com.shiftbot.service.AuthService;
import com.shiftbot.service.PersonalScheduleService;
import com.shiftbot.service.ReminderService;
import com.shiftbot.service.RequestService;
import com.shiftbot.service.ScheduleService;
import com.shiftbot.service.SheetsToSqliteImporter;
import com.shiftbot.service.SubstitutionService;
import com.shiftbot.state.ConversationStateStore;
import com.shiftbot.state.CoverRequestFsm;
import com.shiftbot.state.OnboardingFsm;
import com.shiftbot.state.SubstitutionRequestFsm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.time.Duration;
import java.util.Arrays;

public class Application {
    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        boolean importFromSheets = Arrays.asList(args).contains("--import-from-sheets");
        boolean dryRun = Arrays.asList(args).contains("--dry-run");

        String jdbcUrl = config.getDbPath().startsWith("jdbc:sqlite:") ? config.getDbPath() : "jdbc:sqlite:" + config.getDbPath();
        SqliteDatabase sqliteDatabase = new SqliteDatabase(jdbcUrl);
        sqliteDatabase.initialize();

        RepositoryBundle sqliteRepositories = createSqliteRepositories(sqliteDatabase, config);

        if (importFromSheets) {
            RepositoryBundle sheetsRepositories = createSheetsRepositories(config);
            SheetsToSqliteImporter importer = new SheetsToSqliteImporter(sheetsRepositories, sqliteRepositories);
            importer.importAll(dryRun);
            log.info("Sheets import completed (dryRun={})", dryRun);
            return;
        }

        RepositoryBundle repositories = config.getStorageType() == StorageType.SHEETS
                ? createSheetsRepositories(config)
                : sqliteRepositories;

        UsersRepository usersRepository = repositories.usersRepository();
        LocationsRepository locationsRepository = repositories.locationsRepository();
        LocationAssignmentsRepository locationAssignmentsRepository = repositories.locationAssignmentsRepository();
        ShiftsRepository shiftsRepository = repositories.shiftsRepository();
        RequestsRepository requestsRepository = repositories.requestsRepository();
        SubstitutionRequestsRepository substitutionRequestsRepository = repositories.substitutionRequestsRepository();
        AccessRequestsRepository accessRequestsRepository = repositories.accessRequestsRepository();
        AuditRepository auditRepository = repositories.auditRepository();
        SchedulesRepository schedulesRepository = repositories.schedulesRepository();
        ConversationStateStore stateStore = new ConversationStateStore(Duration.ofMinutes(15));

        AuditService auditService = new AuditService(auditRepository, Long.parseLong(config.getAuditGroupId()), config.getZoneId());
        AccessRequestService accessRequestService = new AccessRequestService(accessRequestsRepository, usersRepository, config.getZoneId());
        AuthService authService = new AuthService(usersRepository, auditService, accessRequestService, config.getZoneId());
        ScheduleService scheduleService = new ScheduleService(shiftsRepository, locationsRepository, config.getZoneId());
        RequestService requestService = new RequestService(requestsRepository, shiftsRepository, config.getZoneId());
        SubstitutionService substitutionService = new SubstitutionService(substitutionRequestsRepository, auditService, config.getZoneId());
        PersonalScheduleService personalScheduleService = new PersonalScheduleService(schedulesRepository, config.getZoneId());
        CalendarKeyboardBuilder calendarKeyboardBuilder = new CalendarKeyboardBuilder();

        SubstitutionRequestFsm substitutionRequestFsm = new SubstitutionRequestFsm();

        UpdateRouter updateRouter = new UpdateRouter(authService, scheduleService, requestService, accessRequestService, locationsRepository,
                usersRepository, locationAssignmentsRepository, personalScheduleService, substitutionService, calendarKeyboardBuilder, stateStore,
                new CoverRequestFsm(), new OnboardingFsm(), new com.shiftbot.state.PersonalScheduleFsm(), substitutionRequestFsm, auditService,
                config.getZoneId(), Long.parseLong(config.getAdminTelegramId()));

        ShiftSchedulerBot bot = new ShiftSchedulerBot(config.getBotToken(), config.getBotUsername(), updateRouter);
        auditService.setBot(bot);

        ReminderService reminderService = new ReminderService(scheduleService, usersRepository, requestsRepository, bot, config.getZoneId());

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(bot);
        reminderService.start();
        Runtime.getRuntime().addShutdownHook(new Thread(reminderService::stop));
        log.info("Bot started with username {}", config.getBotUsername());
    }

    private static RepositoryBundle createSheetsRepositories(EnvironmentConfig config) {
        String spreadsheetId = config.getSpreadsheetId();
        String credentialsPath = config.getCredentialsPath();
        if (spreadsheetId == null || spreadsheetId.isBlank() || credentialsPath == null || credentialsPath.isBlank()) {
            throw new IllegalStateException("Sheets storage requires SPREADSHEET_ID and GOOGLE_APPLICATION_CREDENTIALS");
        }
        SheetsClient sheetsClient = new SheetsClient(spreadsheetId, credentialsPath);
        UsersRepository usersRepository = new SheetsUsersRepository(sheetsClient, Duration.ofMinutes(5));
        LocationsRepository locationsRepository = new SheetsLocationsRepository(sheetsClient, Duration.ofMinutes(10));
        LocationAssignmentsRepository locationAssignmentsRepository = new SheetsLocationAssignmentsRepository(sheetsClient);
        ShiftsRepository shiftsRepository = new SheetsShiftsRepository(sheetsClient);
        RequestsRepository requestsRepository = new SheetsRequestsRepository(sheetsClient);
        SubstitutionRequestsRepository substitutionRequestsRepository = new SheetsSubstitutionRequestsRepository(sheetsClient);
        AccessRequestsRepository accessRequestsRepository = new SheetsAccessRequestsRepository(sheetsClient);
        AuditRepository auditRepository = new SheetsAuditRepository(sheetsClient);
        SchedulesRepository schedulesRepository = new SheetsSchedulesRepository(sheetsClient);
        return new RepositoryBundle(usersRepository, locationsRepository, locationAssignmentsRepository, shiftsRepository,
                requestsRepository, substitutionRequestsRepository, accessRequestsRepository, auditRepository, schedulesRepository);
    }

    private static RepositoryBundle createSqliteRepositories(SqliteDatabase sqliteDatabase, EnvironmentConfig config) {
        UsersRepository usersRepository = new SqliteUsersRepository(sqliteDatabase.getDataSource());
        SqliteLocationsRepository locationsRepository = new SqliteLocationsRepository(sqliteDatabase.getDataSource());
        LocationAssignmentsRepository locationAssignmentsRepository = new SqliteLocationAssignmentsRepository(sqliteDatabase.getDataSource());
        ShiftsRepository shiftsRepository = new SqliteShiftsRepository(sqliteDatabase.getDataSource(), config.getZoneId());
        RequestsRepository requestsRepository = new SqliteRequestsRepository(sqliteDatabase.getDataSource());
        SubstitutionRequestsRepository substitutionRequestsRepository = new SqliteSubstitutionRequestsRepository(sqliteDatabase.getDataSource());
        AccessRequestsRepository accessRequestsRepository = new SqliteAccessRequestsRepository(sqliteDatabase.getDataSource());
        AuditRepository auditRepository = new SqliteAuditRepository(sqliteDatabase.getDataSource());
        SchedulesRepository schedulesRepository = new SqliteSchedulesRepository(sqliteDatabase.getDataSource());
        seedLocationsIfConfigured(locationsRepository, config.getLocationsSeed());
        return new RepositoryBundle(usersRepository, locationsRepository, locationAssignmentsRepository, shiftsRepository,
                requestsRepository, substitutionRequestsRepository, accessRequestsRepository, auditRepository, schedulesRepository);
    }

    private static void seedLocationsIfConfigured(SqliteLocationsRepository locationsRepository, String seedValue) {
        if (seedValue == null || seedValue.isBlank()) {
            return;
        }
        String[] entries = seedValue.split(";");
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\\|", -1);
            if (parts.length < 2) {
                continue;
            }
            String id = parts[0].trim();
            String name = parts[1].trim();
            String address = parts.length > 2 ? parts[2].trim() : "";
            boolean active = parts.length <= 3 || Boolean.parseBoolean(parts[3].trim());
            locationsRepository.upsert(new com.shiftbot.model.Location(id, name, address, active));
        }
    }
}
