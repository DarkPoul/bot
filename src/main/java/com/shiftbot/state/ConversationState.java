package com.shiftbot.state;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ConversationState {
    private final String name;
    private final Map<String, String> data = new HashMap<>();
    private final Instant updatedAt;

    public ConversationState(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public String getName() {
        return name;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Map<String, String> getData() {
        return data;
    }
}
