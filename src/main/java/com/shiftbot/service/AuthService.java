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
    private final ZoneId zoneId;

    public AuthService(UsersRepository usersRepository, ZoneId zoneId) {
        this(usersRepository, null, zoneId);
    }

    public AuthService(UsersRepository usersRepository, AuditService auditService, ZoneId zoneId) {
        this.usersRepository = usersRepository;
        this.auditService = auditService;
        this.zoneId = zoneId;
    }

    public OnboardResult onboard(long userId, String username, String fullName) {
        Optional<User> existing = usersRepository.findById(userId);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getStatus() == UserStatus.BLOCKED) {
                return OnboardResult.blocked(user, "Ваш доступ заблоковано. Зверніться до ТМ/Сеньйора.");
            }
            if (user.getStatus() == UserStatus.PENDING) {
                return OnboardResult.pending(user, "Ваша анкета очікує підтвердження від ТМ/Сеньйора.");
            }
            return OnboardResult.allowed(user, null);
        }
        User user = new User();
        user.setUserId(userId);
        user.setUsername(username);
        user.setFullName(fullName);
        user.setRole(Role.SELLER);
        user.setStatus(UserStatus.PENDING);
        user.setCreatedAt(TimeUtils.nowInstant(zoneId));
        usersRepository.save(user);
        if (auditService != null) {
            auditService.logEvent(userId, "user_onboarded", "user", String.valueOf(userId), Map.of("status", user.getStatus().name()));
        }
        return OnboardResult.pending(user, "👋 Вітаємо, " + user.getFullName() + "! Ваша анкета створена та очікує підтвердження від ТМ/Сеньйора.");
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
