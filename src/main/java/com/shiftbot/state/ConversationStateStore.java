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
        state.touch();
        states.put(userId, state);
    }

    public Optional<ConversationState> get(Long userId) {
        ConversationState state = states.get(userId);
        if (state == null) {
            return Optional.empty();
        }
        if (isExpired(state)) {
            states.remove(userId);
            return Optional.of(new ConversationState(ConversationState.STATE_TIMEOUT, state.getData()));
        }
        state.touch();
        return Optional.of(state);
    }

    public boolean has(Long userId) {
        return states.containsKey(userId);
    }

    public void touch(Long userId) {
        states.computeIfPresent(userId, (id, state) -> {
            state.touch();
            return state;
        });
    }

    public void clear(Long userId) {
        states.remove(userId);
    }

    private boolean isExpired(ConversationState state) {
        return state.getUpdatedAt().isBefore(Instant.now().minus(ttl));
    }
}
