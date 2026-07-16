package nl.hauntedmc.craftgpt.generation;

import nl.hauntedmc.craftgpt.ai.AiClient;
import nl.hauntedmc.craftgpt.ai.AiRequest;
import nl.hauntedmc.craftgpt.ai.AiResponse;
import nl.hauntedmc.craftgpt.ai.ModelPreset;
import nl.hauntedmc.craftgpt.generation.compiled.PaletteResolver;
import nl.hauntedmc.craftgpt.generation.compiled.PaletteValidationException;
import nl.hauntedmc.craftgpt.generation.compiled.ResolvedBlock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TestSupport {
    private TestSupport() {
    }

    public static GenerationLimits limits() {
        return new GenerationLimits(128, 2048, 4000, 512, 50000, 50000, 4000);
    }

    public static VisualReviewSettings visualReviewDisabled() {
        return new VisualReviewSettings(false, 250, 256, false, "");
    }

    public static GenerationContext context(int width, int height, int depth) {
        return new GenerationContext(new BuildVolume(width, height, depth), limits(), visualReviewDisabled(),
                RequestClassification.VOLUMETRIC, "positive Z", "1.21.11");
    }

    public static PaletteResolver paletteResolver() {
        return blockState -> {
            if (blockState == null || !blockState.startsWith("minecraft:")) {
                throw new PaletteValidationException("Invalid namespaced block ID.");
            }
            return new ResolvedBlock(blockState, blockState, blockState);
        };
    }

    public static ModelPreset preset() {
        return new ModelPreset("test", "https://api.openai.com/v1/responses", "test-key", Map.of("model", "test-model"));
    }

    public static WorkflowSettings fastWorkflow() {
        return WorkflowSettings.defaults(WorkflowProfile.FAST);
    }

    public static WorkflowSettings balancedWorkflow() {
        return WorkflowSettings.defaults(WorkflowProfile.BALANCED);
    }

    public static WorkflowSettings maximumQualityWorkflow() {
        return WorkflowSettings.defaults(WorkflowProfile.MAXIMUM_QUALITY);
    }

    public static PipelineRequest pipelineRequest(GenerationContext context, boolean applyBestEffort,
                                                  int maxGenerationAttempts, int maxRepairAttempts, String prompt) {
        return pipelineRequest(context, applyBestEffort, maxGenerationAttempts, maxRepairAttempts,
                false, false, prompt);
    }

    public static PipelineRequest pipelineRequest(GenerationContext context, boolean applyBestEffort,
                                                  int maxGenerationAttempts, int maxRepairAttempts,
                                                  boolean repairWarningsEnabled, boolean visualReviewRepairEnabled,
                                                  String prompt) {
        return new PipelineRequest(
                preset(),
                context,
                fastWorkflow(),
                prompt,
                "system prompt",
                30,
                maxGenerationAttempts,
                maxRepairAttempts,
                applyBestEffort,
                repairWarningsEnabled,
                visualReviewRepairEnabled,
                2000,
                Map.of()
        );
    }

    public static final class SequenceAiClient implements AiClient {
        private final ArrayDeque<AiResponse> responses = new ArrayDeque<>();
        private final List<AiRequest> requests = new ArrayList<>();

        public SequenceAiClient(List<AiResponse> responses) {
            this.responses.addAll(responses);
        }

        @Override
        public AiResponse execute(ModelPreset preset, AiRequest request, int timeoutSeconds) {
            requests.add(request);
            if (responses.isEmpty()) {
                throw new AssertionError("No queued AI response for request " + request.schemaName());
            }
            return responses.removeFirst();
        }

        public List<AiRequest> requests() {
            return List.copyOf(requests);
        }
    }
}
