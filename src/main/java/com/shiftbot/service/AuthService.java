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
            User user = existing.get();
            boolean changed = false;
            if (!username.equals(user.getUsername())) {
                user.setUsername(username);
                changed = true;
            }
            if (!fullName.equals(user.getFullName())) {
                user.setFullName(fullName);
                changed = true;
            }
            if (changed) {
                usersRepository.update(user);
            }
            return user;
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
