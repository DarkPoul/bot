package com.shiftbot.service;

import com.shiftbot.model.User;
import com.shiftbot.model.enums.Role;
import com.shiftbot.model.enums.UserStatus;
import com.shiftbot.repository.UsersRepository;
import com.shiftbot.util.TimeUtils;

import java.time.ZoneId;
import java.util.Optional;

public class AuthService {
    private final UsersRepository usersRepository;
    private final ZoneId zoneId;

    public AuthService(UsersRepository usersRepository, ZoneId zoneId) {
        this.usersRepository = usersRepository;
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
        return user;
    }
}
