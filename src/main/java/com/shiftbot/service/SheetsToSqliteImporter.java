package com.shiftbot.service;

import com.shiftbot.model.AccessRequest;
import com.shiftbot.model.Location;
import com.shiftbot.model.LocationAssignment;
import com.shiftbot.model.Request;
import com.shiftbot.model.ScheduleEntry;
import com.shiftbot.model.Shift;
import com.shiftbot.model.SubstitutionRequest;
import com.shiftbot.model.User;
import com.shiftbot.repository.RepositoryBundle;
import com.shiftbot.repository.sqlite.SqliteLocationsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SheetsToSqliteImporter {
    private static final Logger log = LoggerFactory.getLogger(SheetsToSqliteImporter.class);

    private final RepositoryBundle sheets;
    private final RepositoryBundle sqlite;

    public SheetsToSqliteImporter(RepositoryBundle sheets, RepositoryBundle sqlite) {
        this.sheets = sheets;
        this.sqlite = sqlite;
    }

    public void importAll(boolean dryRun) {
        importLocations(dryRun);
        importUsers(dryRun);
        importLocationAssignments(dryRun);
        importSchedules(dryRun);
        importShifts(dryRun);
        importRequests(dryRun);
        importAccessRequests(dryRun);
        importSubstitutionRequests(dryRun);
    }

    private void importLocations(boolean dryRun) {
        List<Location> locations = sheets.locationsRepository().findAll();
        log.info("Importing {} locations", locations.size());
        if (dryRun) {
            return;
        }
        if (sqlite.locationsRepository() instanceof SqliteLocationsRepository sqliteLocationsRepository) {
            for (Location location : locations) {
                sqliteLocationsRepository.upsert(location);
            }
        } else {
            log.warn("Skipping locations import: sqlite repository does not support upsert");
        }
    }

    private void importUsers(boolean dryRun) {
        List<User> users = sheets.usersRepository().findAll();
        log.info("Importing {} users", users.size());
        if (dryRun) {
            return;
        }
        for (User user : users) {
            sqlite.usersRepository().save(user);
        }
    }

    private void importLocationAssignments(boolean dryRun) {
        List<LocationAssignment> assignments = sheets.locationAssignmentsRepository().findAll();
        log.info("Importing {} location assignments", assignments.size());
        if (dryRun) {
            return;
        }
        for (LocationAssignment assignment : assignments) {
            sqlite.locationAssignmentsRepository().save(assignment);
        }
    }

    private void importSchedules(boolean dryRun) {
        List<ScheduleEntry> schedules = sheets.schedulesRepository().findAll();
        log.info("Importing {} personal schedules", schedules.size());
        if (dryRun) {
            return;
        }
        for (ScheduleEntry entry : schedules) {
            if (entry.getYear() == null || entry.getMonth() == null) {
                log.warn("Skipping schedule entry with missing month/year for user {}", entry.getUserId());
                continue;
            }
            Set<Integer> workDays = parseWorkDays(entry.getWorkDaysCsv());
            Instant updatedAt = entry.getUpdatedAt() != null ? entry.getUpdatedAt() : Instant.now();
            sqlite.schedulesRepository().saveMonthlySchedule(entry.getUserId(), null, entry.getYear(), entry.getMonth(), workDays, updatedAt);
        }
    }

    private void importShifts(boolean dryRun) {
        List<Shift> shifts = sheets.shiftsRepository().findAll();
        log.info("Importing {} shifts", shifts.size());
        if (dryRun) {
            return;
        }
        for (Shift shift : shifts) {
            sqlite.shiftsRepository().save(shift);
        }
    }

    private void importRequests(boolean dryRun) {
        List<Request> requests = sheets.requestsRepository().findAll();
        log.info("Importing {} requests", requests.size());
        if (dryRun) {
            return;
        }
        for (Request request : requests) {
            sqlite.requestsRepository().save(request);
        }
    }

    private void importAccessRequests(boolean dryRun) {
        List<AccessRequest> requests = sheets.accessRequestsRepository().findAll();
        log.info("Importing {} access requests", requests.size());
        if (dryRun) {
            return;
        }
        for (AccessRequest request : requests) {
            sqlite.accessRequestsRepository().save(request);
        }
    }

    private void importSubstitutionRequests(boolean dryRun) {
        List<SubstitutionRequest> requests = sheets.substitutionRequestsRepository().findAll();
        log.info("Importing {} substitution requests", requests.size());
        if (dryRun) {
            return;
        }
        for (SubstitutionRequest request : requests) {
            sqlite.substitutionRequestsRepository().save(request);
        }
    }

    private Set<Integer> parseWorkDays(String csv) {
        Set<Integer> workDays = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return workDays;
        }
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                workDays.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException e) {
                log.warn("Skipping invalid day value {}", trimmed);
            }
        }
        return workDays;
    }
}
