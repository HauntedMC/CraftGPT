package nl.hauntedmc.craftgpt.generation;

import java.util.Objects;

public record WorkflowSettings(
        WorkflowProfile profile,
        boolean planningEnabled,
        boolean critiqueEnabled,
        int maxRefinementPasses,
        boolean extendedPreviews
) {
    public WorkflowSettings {
        Objects.requireNonNull(profile, "profile");
        maxRefinementPasses = Math.max(0, maxRefinementPasses);
    }

    public static WorkflowSettings defaults(WorkflowProfile profile) {
        return switch (profile) {
            case FAST -> new WorkflowSettings(profile, false, false, 0, false);
            case BALANCED -> new WorkflowSettings(profile, true, true, 1, false);
            case MAXIMUM_QUALITY -> new WorkflowSettings(profile, true, true, 2, true);
        };
    }
}
