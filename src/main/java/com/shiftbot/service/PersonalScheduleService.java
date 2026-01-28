package com.shiftbot.service;

import com.shiftbot.model.ScheduleEntry;
import com.shiftbot.repository.SchedulesRepository;
import com.shiftbot.util.TimeUtils;

import java.time.ZoneId;
import java.util.Optional;

public class PersonalScheduleService {
    private final SchedulesRepository schedulesRepository;
    private final ZoneId zoneId;

    public PersonalScheduleService(SchedulesRepository schedulesRepository, ZoneId zoneId) {
        this.schedulesRepository = schedulesRepository;
        this.zoneId = zoneId;
    }

    public Optional<ScheduleEntry> findByUser(long userId) {
        return schedulesRepository.findByUserId(userId);
    }

    public ScheduleEntry saveOrUpdate(long userId, String scheduleText) {
        ScheduleEntry entry = schedulesRepository.findByUserId(userId).orElseGet(ScheduleEntry::new);
        entry.setUserId(userId);
        entry.setScheduleText(scheduleText);
        entry.setUpdatedAt(TimeUtils.nowInstant(zoneId));
        schedulesRepository.upsert(entry);
        return entry;
    }
}
