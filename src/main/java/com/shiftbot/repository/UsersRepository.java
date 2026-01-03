package com.shiftbot.repository;

import com.shiftbot.model.User;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UsersRepository {
    private static final Logger log = LoggerFactory.getLogger(UsersRepository.class);
    private static final String RANGE = "users!A2:H";

    private final SheetsClient sheetsClient;
    private final Duration cacheTtl;
    private List<User> cache;
    private Instant cacheUpdatedAt;

    public UsersRepository(SheetsClient sheetsClient, Duration cacheTtl) {
        this.sheetsClient = sheetsClient;
        this.cacheTtl = cacheTtl;
    }

    public synchronized List<User> findAll() {
        if (cache != null && cacheUpdatedAt != null && cacheUpdatedAt.isAfter(Instant.now().minus(cacheTtl))) {
            return cache;
        }
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        List<User> users = new ArrayList<>();
        if (rows != null) {
            for (List<Object> row : rows) {
                try {
                    users.add(mapRow(row));
                } catch (Exception e) {
                    log.warn("Skip invalid user row: {}", row, e);
                }
            }
        }
        cache = users;
        cacheUpdatedAt = Instant.now();
        return users;
    }

    public Optional<User> findById(long userId) {
        return findAll().stream().filter(u -> u.getUserId() == userId).findFirst();
    }

    public synchronized void save(User user) {
        List<Object> row = new ArrayList<>();
        row.add(String.valueOf(user.getUserId()));
        row.add(user.getUsername());
        row.add(user.getFullName());
        row.add(user.getPhone());
        row.add(user.getRole().name());
        row.add(user.getStatus().name());
        row.add(user.getCreatedAt() != null ? user.getCreatedAt().toString() : "");
        row.add(user.getCreatedBy() != null ? user.getCreatedBy().toString() : "");
        sheetsClient.appendRow(RANGE, row);
        invalidateCache();
    }

    public synchronized void invalidateCache() {
        cache = null;
        cacheUpdatedAt = null;
    }

    private User mapRow(List<Object> row) {
        long userId = Long.parseLong(get(row, 0));
        String username = get(row, 1);
        String fullName = get(row, 2);
        String phone = get(row, 3);
        Role role = Role.valueOf(get(row, 4));
        UserStatus status = UserStatus.valueOf(get(row, 5));
        Instant createdAt = get(row, 6).isEmpty() ? null : Instant.parse(get(row, 6));
        Long createdBy = get(row, 7).isEmpty() ? null : Long.parseLong(get(row, 7));
        return new User(userId, username, fullName, phone, role, status, createdAt, createdBy);
    }

    private String get(List<Object> row, int idx) {
        if (row.size() > idx && row.get(idx) != null) {
            return row.get(idx).toString();
        }
        return "";
    }
}
