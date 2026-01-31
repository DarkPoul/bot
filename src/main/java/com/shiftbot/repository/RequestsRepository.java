package com.shiftbot.repository;

import com.shiftbot.model.Request;

import java.util.List;
import java.util.Optional;

public interface RequestsRepository {
    List<Request> findAll();

    Optional<Request> findById(String requestId);

    void save(Request request);

    void update(Request request);
}
