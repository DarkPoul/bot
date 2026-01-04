package com.shiftbot.state;

import java.util.HashMap;
import java.util.Map;

public class CoverRequestFsm {
    public static final String STATE_NAME = "cover";
    public static final String STEP_KEY = "step";
    public static final String DATE_KEY = "date";
    public static final String START_KEY = "start";
    public static final String END_KEY = "end";
    public static final String LOCATION_KEY = "location";
    public static final String COMMENT_KEY = "comment";

    public enum Step {
        DATE,
        TIME,
        LOCATION,
        COMMENT
    }

    public ConversationState start() {
        Map<String, String> data = new HashMap<>();
        data.put(STEP_KEY, Step.DATE.name());
        return new ConversationState(STATE_NAME, data);
    }

    public boolean supports(ConversationState state) {
        return state != null && STATE_NAME.equals(state.getName());
    }

    public Step currentStep(ConversationState state) {
        if (state == null) {
            return Step.DATE;
        }
        String value = state.getData().getOrDefault(STEP_KEY, Step.DATE.name());
        return Step.valueOf(value);
    }

    public ConversationState advance(ConversationState state, Step nextStep, Map<String, String> extraData) {
        Map<String, String> data = new HashMap<>();
        if (state != null) {
            data.putAll(state.getData());
        }
        if (extraData != null) {
            data.putAll(extraData);
        }
        data.put(STEP_KEY, nextStep.name());
        return new ConversationState(STATE_NAME, data);
    }
}
