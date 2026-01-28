package com.shiftbot.state;

import java.util.HashMap;
import java.util.Map;

public class OnboardingFsm {
    public static final String STATE_NAME = "onboarding";
    public static final String STEP_KEY = "step";
    public static final String FULL_NAME_KEY = "fullName";
    public static final String LOCATION_KEY = "locationId";

    public enum Step {
        NAME,
        LOCATION
    }

    public ConversationState start() {
        Map<String, String> data = new HashMap<>();
        data.put(STEP_KEY, Step.NAME.name());
        return new ConversationState(STATE_NAME, data);
    }

    public boolean supports(ConversationState state) {
        return state != null && STATE_NAME.equals(state.getName());
    }

    public Step currentStep(ConversationState state) {
        if (state == null) {
            return Step.NAME;
        }
        String value = state.getData().getOrDefault(STEP_KEY, Step.NAME.name());
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
