package com.shiftbot.repository;

public record RepositoryBundle(
        UsersRepository usersRepository,
        LocationsRepository locationsRepository,
        LocationAssignmentsRepository locationAssignmentsRepository,
        ShiftsRepository shiftsRepository,
        RequestsRepository requestsRepository,
        SubstitutionRequestsRepository substitutionRequestsRepository,
        AccessRequestsRepository accessRequestsRepository,
        AuditRepository auditRepository,
        SchedulesRepository schedulesRepository
) {
}
