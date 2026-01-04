package com.shiftbot.service;

import com.shiftbot.model.User;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import com.shiftbot.repository.UsersRepository;
import com.shiftbot.util.TimeUtils;

import java.time.ZoneId;
import java.util.Optional;
import java.util.Map;

public class AuthService {
    private final UsersRepository usersRepository;
    private final AuditService auditService;
    private final ZoneId zoneId;

    public AuthService(UsersRepository usersRepository, AuditService auditService, ZoneId zoneId) {
        this.usersRepository = usersRepository;
        this.auditService = auditService;
        this.zoneId = zoneId;
    }

    public User onboard(long userId, String username, String fullName) {
        Optional<User> existing = usersRepository.findById(userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        User user = new User();
        user.setUserId(userId);
        user.setUsername(username);
        user.setFullName(fullName);
        user.setRole(Role.SELLER);
        user.setStatus(UserStatus.PENDING);
        user.setCreatedAt(TimeUtils.nowInstant(zoneId));
        usersRepository.save(user);
        auditService.logEvent(userId, "user_onboard", "user", String.valueOf(userId), Map.of("username", username));
        return user;
    }
}
