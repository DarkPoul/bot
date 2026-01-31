package com.shiftbot.repository.sqlite;

import com.shiftbot.model.AccessRequest;
import com.shiftbot.model.Request;
import com.shiftbot.model.ScheduleEntry;
import com.shiftbot.model.User;
import com.shiftbot.model.enums.RequestStatus;
import com.shiftbot.model.enums.RequestType;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import com.shiftbot.repository.AccessRequestsRepository;
import com.shiftbot.repository.RequestsRepository;
import com.shiftbot.repository.SchedulesRepository;
import com.shiftbot.repository.UsersRepository;
import com.shiftbot.service.AccessRequestService;
import com.shiftbot.service.AuthService;
import com.shiftbot.util.TimeUtils;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteRepositoryIntegrationTest {

    @Test
    void registersAndApprovesUser() throws Exception {
        TestContext context = new TestContext();
        UsersRepository usersRepository = new SqliteUsersRepository(context.dataSource());
        AccessRequestsRepository accessRequestsRepository = new SqliteAccessRequestsRepository(context.dataSource());
        ZoneId zoneId = ZoneId.of("Europe/Kyiv");

        AccessRequestService accessRequestService = new AccessRequestService(accessRequestsRepository, usersRepository, zoneId);
        AuthService authService = new AuthService(usersRepository, null, accessRequestService, zoneId);

        AuthService.OnboardResult result = authService.register(1001L, "user1", "User One", "loc-1");
        assertEquals(UserStatus.PENDING, result.user().getStatus());

        List<AccessRequest> pending = accessRequestsRepository.findAll();
        assertEquals(1, pending.size());
        AccessRequest request = pending.get(0);
        accessRequestService.approve(request.getId(), 2001L);

        User updated = usersRepository.findById(1001L).orElseThrow();
        assertEquals(UserStatus.APPROVED, updated.getStatus());
        assertEquals(Role.SELLER, updated.getRole());
    }

    @Test
    void savesAndLoadsMonthlySchedule() throws Exception {
        TestContext context = new TestContext();
        SchedulesRepository schedulesRepository = new SqliteSchedulesRepository(context.dataSource());
        ZoneId zoneId = ZoneId.of("Europe/Kyiv");
        YearMonth month = YearMonth.of(2024, 5);

        Set<Integer> workDays = Set.of(1, 2, 5, 10);
        Instant updatedAt = TimeUtils.nowInstant(zoneId);
        schedulesRepository.saveMonthlySchedule(101L, "loc-1", month.getYear(), month.getMonthValue(), workDays, updatedAt);

        ScheduleEntry entry = schedulesRepository.findByUserAndMonth(101L, 2024, 5).orElseThrow();
        assertEquals("1,2,5,10", entry.getWorkDaysCsv());
        assertNotNull(entry.getUpdatedAt());
    }

    @Test
    void createsAndUpdatesSwapRequest() throws Exception {
        TestContext context = new TestContext();
        RequestsRepository requestsRepository = new SqliteRequestsRepository(context.dataSource());

        Request request = new Request();
        request.setRequestId(UUID.randomUUID().toString());
        request.setType(RequestType.SWAP);
        request.setInitiatorUserId(10L);
        request.setFromUserId(10L);
        request.setToUserId(20L);
        request.setDate(LocalDate.of(2024, 6, 1));
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(18, 0));
        request.setLocationId("loc-1");
        request.setStatus(RequestStatus.WAIT_TM);
        request.setComment("swap");
        request.setCreatedAt(Instant.now());
        request.setUpdatedAt(Instant.now());

        requestsRepository.save(request);

        Request loaded = requestsRepository.findById(request.getRequestId()).orElseThrow();
        assertEquals(RequestStatus.WAIT_TM, loaded.getStatus());

        loaded.setStatus(RequestStatus.APPROVED_TM);
        loaded.setUpdatedAt(Instant.now());
        requestsRepository.update(loaded);

        Request updated = requestsRepository.findById(request.getRequestId()).orElseThrow();
        assertEquals(RequestStatus.APPROVED_TM, updated.getStatus());
    }

    @Test
    void savesScheduleConcurrently() throws Exception {
        TestContext context = new TestContext();
        SchedulesRepository schedulesRepository = new SqliteSchedulesRepository(context.dataSource());
        YearMonth month = YearMonth.of(2024, 7);
        int threads = 8;
        var pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int i = 0; i < threads; i++) {
            int offset = i;
            pool.submit(() -> {
                try {
                    Set<Integer> workDays = new HashSet<>();
                    workDays.add(1 + offset);
                    workDays.add(2 + offset);
                    schedulesRepository.saveMonthlySchedule(200L, "loc-1", month.getYear(), month.getMonthValue(), workDays, Instant.now());
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        pool.shutdownNow();
        if (failure.get() != null) {
            throw new AssertionError("Concurrency failure", failure.get());
        }
    }

    private static class TestContext {
        private final DataSource dataSource;

        TestContext() throws Exception {
            Path dbPath = Files.createTempFile("shiftbot", ".db");
            String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
            SqliteDatabase sqliteDatabase = new SqliteDatabase(jdbcUrl);
            sqliteDatabase.initialize();
            this.dataSource = sqliteDatabase.getDataSource();
        }

        DataSource dataSource() {
            return dataSource;
        }
    }
}
