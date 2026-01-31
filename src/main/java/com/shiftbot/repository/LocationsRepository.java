package com.shiftbot.repository;

import com.shiftbot.model.Location;

import java.util.List;
import java.util.Optional;

public interface LocationsRepository {
    List<Location> findAll();

    List<Location> findActive();

    Optional<Location> findById(String id);
}
