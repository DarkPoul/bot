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
        sheetsClient.appendRow(RANGE, buildRow(user));
        invalidateCache();
    }

    public synchronized void updateRow(long userId, User user) {
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        if (rows == null) {
            throw new IllegalArgumentException("User not found for id: " + userId);
        }
        for (int i = 0; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            try {
                long rowUserId = Long.parseLong(get(row, 0));
                if (rowUserId == userId) {
                    int sheetRowNumber = i + 2; // RANGE starts at A2
                    sheetsClient.updateRange("users!A" + sheetRowNumber + ":H" + sheetRowNumber, List.of(buildRow(user)));
                    invalidateCache();
                    return;
                }
            } catch (NumberFormatException e) {
                log.warn("Skip invalid user row during update: {}", row, e);
            }
        }
        throw new IllegalArgumentException("User not found for id: " + userId);
    }

    public synchronized void invalidateCache() {
        cache = null;
        cacheUpdatedAt = null;
    }

    private List<Object> buildRow(User user) {
        List<Object> row = new ArrayList<>();
        row.add(String.valueOf(user.getUserId()));
        row.add(user.getUsername());
        row.add(user.getFullName());
        row.add(user.getPhone());
        row.add(user.getRole().name());
        row.add(user.getStatus().name());
        row.add(user.getCreatedAt() != null ? user.getCreatedAt().toString() : "");
        row.add(user.getCreatedBy() != null ? user.getCreatedBy().toString() : "");
        return row;
    }

    private User mapRow(List<Object> row) {
        long userId = Long.parseLong(get(row, 0));
        String username = get(row, 1);
        String fullName = get(row, 2);
        boolean phoneMissing = isRoleValue(get(row, 3)) && !isRoleValue(get(row, 4));
        String phone = phoneMissing ? "" : get(row, 3);
        String roleValue = phoneMissing ? get(row, 3) : get(row, 4);
        String statusValue = phoneMissing ? get(row, 4) : get(row, 5);
        String createdAtValue = phoneMissing ? get(row, 5) : get(row, 6);
        String createdByValue = phoneMissing ? get(row, 6) : get(row, 7);
        Role role = Role.valueOf(roleValue);
        UserStatus status = UserStatus.valueOf(statusValue);
        Instant createdAt = createdAtValue.isEmpty() ? null : Instant.parse(createdAtValue);
        Long createdBy = createdByValue.isEmpty() ? null : Long.parseLong(createdByValue);
        return new User(userId, username, fullName, phone, role, status, createdAt, createdBy);
    }

    private String get(List<Object> row, int idx) {
        if (row.size() > idx && row.get(idx) != null) {
            return row.get(idx).toString();
        }
        return "";
    }

    private boolean isRoleValue(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            Role.valueOf(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
