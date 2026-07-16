package nl.hauntedmc.craftgpt.generation;

import nl.hauntedmc.craftgpt.ai.ModelPreset;

import java.util.Map;

public record PipelineRequest(
        ModelPreset modelPreset,
        GenerationContext context,
        WorkflowSettings workflowSettings,
        String userPrompt,
        String systemPrompt,
        int timeoutSeconds,
        int maxGenerationAttempts,
        int maxRepairAttempts,
        boolean applyBestEffortResult,
        boolean repairWarningsEnabled,
        boolean visualReviewRepairEnabled,
        int logExcerptMaxLength,
        Map<String, String> promptVariables
) {
    public PipelineRequest {
        workflowSettings = java.util.Objects.requireNonNull(workflowSettings, "workflowSettings");
        promptVariables = Map.copyOf(promptVariables);
    }
}
