package com.shiftbot.repository;

import com.shiftbot.model.AccessRequest;

import java.util.List;
import java.util.Optional;

public interface AccessRequestsRepository {
    List<AccessRequest> findAll();

    Optional<AccessRequest> findById(String requestId);

    void save(AccessRequest request);

    void update(AccessRequest request);
}
