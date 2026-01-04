package com.shiftbot.state;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ConversationState {
    public static final String STATE_NOOP = "noop";
    public static final String STATE_TIMEOUT = "timeout";
    public static final String STATE_CANCELLED = "cancel";

    private final String name;
    private final Map<String, String> data = new HashMap<>();
    private Instant updatedAt;

    public ConversationState(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public ConversationState(String name, Map<String, String> data) {
        this.name = name;
        this.data.putAll(data);
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

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
