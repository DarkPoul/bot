package com.shiftbot.service;

import com.shiftbot.model.User;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import com.shiftbot.repository.UsersRepository;
import com.shiftbot.util.TimeUtils;

import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

public class AuthService {
    private final UsersRepository usersRepository;
    private final AuditService auditService;
    private final AccessRequestService accessRequestService;
    private final ZoneId zoneId;

    public AuthService(UsersRepository usersRepository, ZoneId zoneId) {
        this(usersRepository, null, null, zoneId);
    }

    public AuthService(UsersRepository usersRepository, AuditService auditService, ZoneId zoneId) {
        this(usersRepository, auditService, null, zoneId);
    }

    public AuthService(UsersRepository usersRepository, AuditService auditService, AccessRequestService accessRequestService, ZoneId zoneId) {
        this.usersRepository = usersRepository;
        this.auditService = auditService;
        this.accessRequestService = accessRequestService;
        this.zoneId = zoneId;
    }

    public OnboardResult onboard(long userId, String username, String fullName, String locationId) {
        Optional<User> existing = usersRepository.findById(userId);
        if (existing.isPresent()) {
            return evaluateExisting(existing.get());
        }
        return register(userId, username, fullName, locationId);
    }

    public Optional<User> findExisting(long userId) {
        return usersRepository.findById(userId);
    }

    public OnboardResult evaluateExisting(User user) {
        if (user.getStatus() == UserStatus.REJECTED) {
            return OnboardResult.blocked(user, "Акаунт не підтверджено");
        }
        if (user.getStatus() == UserStatus.PENDING) {
            return OnboardResult.pending(user, "Акаунт не підтверджено");
        }
        return OnboardResult.allowed(user, null);
    }

    public OnboardResult register(long userId, String username, String fullName, String locationId) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername(username);
        user.setFullName(fullName);
        user.setLocationId(locationId);
        user.setRole(Role.SELLER);
        user.setStatus(UserStatus.PENDING);
        user.setCreatedAt(TimeUtils.nowInstant(zoneId));
        usersRepository.save(user);
        if (accessRequestService != null) {
            accessRequestService.createPendingIfAbsent(user, null);
        }
        if (auditService != null) {
            auditService.logEvent(userId, "user_onboarded", "user", String.valueOf(userId), Map.of("status", user.getStatus().name()));
        }
        return OnboardResult.pending(user, "Заявку на доступ створено ✅ Очікуйте підтвердження старшого.");
    }

    public record OnboardResult(User user, boolean allowed, String message) {
        private static OnboardResult allowed(User user, String message) {
            return new OnboardResult(user, true, message);
        }

        private static OnboardResult pending(User user, String message) {
            return new OnboardResult(user, false, message);
        }

        private static OnboardResult blocked(User user, String message) {
            return new OnboardResult(user, false, message);
        }
    }
}
