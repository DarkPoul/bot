package com.shiftbot.repository;

import com.shiftbot.model.Location;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LocationsRepository {
    private static final String RANGE = "locations!A2:D";

    private final SheetsClient sheetsClient;
    private final Duration cacheTtl;
    private List<Location> cache;
    private Instant cacheUpdatedAt;

    public LocationsRepository(SheetsClient sheetsClient, Duration cacheTtl) {
        this.sheetsClient = sheetsClient;
        this.cacheTtl = cacheTtl;
    }

    public synchronized List<Location> findAll() {
        if (cache != null && cacheUpdatedAt != null && cacheUpdatedAt.isAfter(Instant.now().minus(cacheTtl))) {
            return cache;
        }
        List<List<Object>> rows = sheetsClient.readRange(RANGE);
        List<Location> locations = new ArrayList<>();
        if (rows != null) {
            for (List<Object> row : rows) {
                String id = get(row, 0);
                if (id.isEmpty()) continue;
                String name = get(row, 1);
                String address = get(row, 2);
                boolean active = Boolean.parseBoolean(get(row, 3));
                locations.add(new Location(id, name, address, active));
            }
        }
        cache = locations;
        cacheUpdatedAt = Instant.now();
        return locations;
    }

    public List<Location> findActive() {
        return findAll().stream()
                .filter(Location::isActive)
                .toList();
    }

    public Optional<Location> findById(String id) {
        return findAll().stream().filter(l -> l.getLocationId().equals(id)).findFirst();
    }

    private String get(List<Object> row, int idx) {
        if (row.size() > idx && row.get(idx) != null) {
            return row.get(idx).toString();
        }
        return "";
    }
}
