package com.shiftbot.repository;

import com.shiftbot.model.LocationAssignment;

public interface LocationAssignmentsRepository {
    java.util.List<LocationAssignment> findAll();

    void save(LocationAssignment assignment);
}
