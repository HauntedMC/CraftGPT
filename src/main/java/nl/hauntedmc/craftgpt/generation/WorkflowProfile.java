package nl.hauntedmc.craftgpt.generation;

import java.util.Locale;

public enum WorkflowProfile {
    FAST("fast"),
    BALANCED("balanced"),
    MAXIMUM_QUALITY("maximum_quality");

    private final String configValue;

    WorkflowProfile(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static WorkflowProfile fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return BALANCED;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (WorkflowProfile profile : values()) {
            if (profile.configValue.equals(normalized)) {
                return profile;
            }
        }
        return BALANCED;
    }
}
