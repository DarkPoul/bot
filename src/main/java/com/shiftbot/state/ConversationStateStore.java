package com.shiftbot.state;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ConversationStateStore {
    private final Map<Long, ConversationState> states = new ConcurrentHashMap<>();
    private final Duration ttl;

    public ConversationStateStore(Duration ttl) {
        this.ttl = ttl;
    }

    public void put(Long userId, ConversationState state) {
        states.put(userId, state);
    }

    public Optional<ConversationState> get(Long userId) {
        ConversationState state = states.get(userId);
        if (state == null) {
            return Optional.empty();
        }
        if (state.getUpdatedAt().isBefore(Instant.now().minus(ttl))) {
            states.remove(userId);
            return Optional.empty();
        }
        return Optional.of(state);
    }

    public void clear(Long userId) {
        states.remove(userId);
    }
}
