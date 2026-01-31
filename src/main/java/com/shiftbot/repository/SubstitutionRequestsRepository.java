package com.shiftbot.repository;

import com.shiftbot.model.SubstitutionRequest;

import java.util.List;
import java.util.Optional;

public interface SubstitutionRequestsRepository {
    List<SubstitutionRequest> findAll();

    Optional<SubstitutionRequest> findById(String requestId);

    void save(SubstitutionRequest request);

    void update(SubstitutionRequest request);
}
