package com.shiftbot.state;

import java.util.HashMap;
import java.util.Map;

public class PersonalScheduleFsm {
    public static final String STATE_NAME = "personal_schedule";
    public static final String STEP_KEY = "step";

    public enum Step {
        TEXT
    }

    public ConversationState start() {
        Map<String, String> data = new HashMap<>();
        data.put(STEP_KEY, Step.TEXT.name());
        return new ConversationState(STATE_NAME, data);
    }

    public boolean supports(ConversationState state) {
        return state != null && STATE_NAME.equals(state.getName());
    }

    public Step currentStep(ConversationState state) {
        if (state == null) {
            return Step.TEXT;
        }
        return Step.valueOf(state.getData().getOrDefault(STEP_KEY, Step.TEXT.name()));
    }
}
