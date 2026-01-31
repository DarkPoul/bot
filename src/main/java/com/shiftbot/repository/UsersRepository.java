package com.shiftbot.repository;

import com.shiftbot.model.User;

import java.util.List;
import java.util.Optional;

public interface UsersRepository {
    List<User> findAll();

    Optional<User> findById(long userId);

    void save(User user);

    void update(User user);
}
