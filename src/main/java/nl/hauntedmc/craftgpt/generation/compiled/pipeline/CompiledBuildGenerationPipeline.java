package nl.hauntedmc.craftgpt.generation.compiled.pipeline;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import nl.hauntedmc.craftgpt.ai.AiClient;
import nl.hauntedmc.craftgpt.ai.AiImageAttachment;
import nl.hauntedmc.craftgpt.ai.AiRequest;
import nl.hauntedmc.craftgpt.ai.AiResponse;
import nl.hauntedmc.craftgpt.generation.BuildGenerationPipeline;
import nl.hauntedmc.craftgpt.generation.PipelineRequest;
import nl.hauntedmc.craftgpt.generation.PipelineResult;
import nl.hauntedmc.craftgpt.generation.WorkflowSettings;
import nl.hauntedmc.craftgpt.generation.compiled.*;
import nl.hauntedmc.craftgpt.generation.compiled.compiler.CompiledBuild;
import nl.hauntedmc.craftgpt.generation.compiled.compiler.CompileResult;
import nl.hauntedmc.craftgpt.generation.compiled.compiler.VoxelCompiler;
import nl.hauntedmc.craftgpt.generation.compiled.parser.BuildProgramParseException;
import nl.hauntedmc.craftgpt.generation.compiled.parser.BuildProgramParser;
import nl.hauntedmc.craftgpt.generation.preview.PreviewArtifactSet;
import nl.hauntedmc.craftgpt.generation.preview.PreviewPerspective;
import nl.hauntedmc.craftgpt.generation.preview.PreviewRenderer;
import nl.hauntedmc.craftgpt.generation.schema.SchemaFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class CompiledBuildGenerationPipeline implements BuildGenerationPipeline {
    private static final Gson GSON = new Gson();

    private final AiClient aiClient;
    private final BuildProgramParser parser;
    private final VoxelCompiler compiler;
    private final PaletteResolver paletteResolver;
    private final PreviewRenderer previewRenderer;

    public CompiledBuildGenerationPipeline(AiClient aiClient,
                                           BuildProgramParser parser,
                                           VoxelCompiler compiler,
                                           PaletteResolver paletteResolver,
                                           PreviewRenderer previewRenderer) {
        this.aiClient = aiClient;
        this.parser = parser;
        this.compiler = compiler;
        this.paletteResolver = paletteResolver;
        this.previewRenderer = previewRenderer;
    }

    @Override
    public PipelineResult generate(PipelineRequest request) {
        String designPlanJson = createDesignPlan(request);
        String initialOutput = "";
        String successfulGenerationOutput = "";
        AiResponse generationResponse = null;
        List<BuildFailure> lastFailures = List.of();
        for (int attempt = 1; attempt <= Math.max(1, request.maxGenerationAttempts()); attempt++) {
            generationResponse = aiClient.execute(
                    request.modelPreset(),
                    new AiRequest(
                            "build_program_v2",
                            buildGenerationInstructions(request, designPlanJson, attempt, lastFailures),
                            buildGenerationInput(request, designPlanJson),
                            SchemaFactory.compiledBuildProgramSchema(request.context().limits()),
                            List.of()
                    ),
                    request.timeoutSeconds()
            );
            if (!generationResponse.success()) {
                if (!generationResponse.retryable() || attempt >= request.maxGenerationAttempts()) {
                    return PipelineResult.failure("request_failed", generationResponse.failureMessage(), "", generationResponse.rawResponse());
                }
                continue;
            }

            String currentOutput = generationResponse.content();
            if (initialOutput.isBlank()) {
                initialOutput = currentOutput;
            }

            CompilationAttempt attemptResult = parseAndCompile(currentOutput, request);
            if (!attemptResult.success()) {
                lastFailures = attemptResult.failures();
                if (canRetryGeneration(attempt, request, lastFailures)) {
                    continue;
                }
                return repairOrFail(request, designPlanJson, initialOutput, currentOutput, null, lastFailures, false, null);
            }

            successfulGenerationOutput = currentOutput;
            PipelineResult postProcessed = postProcessValidBuild(
                    request,
                    designPlanJson,
                    initialOutput,
                    new Candidate(currentOutput, attemptResult.compiledBuild())
            );
            if (postProcessed != null) {
                return postProcessed;
            }
        }

        if (generationResponse == null || !generationResponse.success()) {
            return PipelineResult.failure("request_failed", "The initial generation request failed.", "", "");
        }
        BuildFailure failure = lastFailures.isEmpty()
                ? new BuildFailure("generation_failed", "The build could not be generated.", true)
                : lastFailures.get(0);
        return PipelineResult.failure(failure.code(), failure.message(), initialOutput, successfulGenerationOutput);
    }

    private String createDesignPlan(PipelineRequest request) {
        WorkflowSettings workflow = request.workflowSettings();
        if (!workflow.planningEnabled()) {
            return "";
        }

        AiResponse response = null;
        for (int attempt = 1; attempt <= Math.min(2, Math.max(1, request.maxGenerationAttempts())); attempt++) {
            response = aiClient.execute(
                    request.modelPreset(),
                    new AiRequest(
                            "design_plan_v1",
                            buildPlanInstructions(request),
                            buildPlanInput(request),
                            SchemaFactory.designPlanSchema(),
                            List.of()
                    ),
                    request.timeoutSeconds()
            );
            if (!response.success()) {
                if (!response.retryable()) {
                    return "";
                }
                continue;
            }
            try {
                validateDesignPlan(response.content());
                return response.content();
            } catch (IllegalArgumentException ignored) {
                return "";
            }
        }
        return "";
    }

    private PipelineResult postProcessValidBuild(PipelineRequest request, String designPlanJson, String initialOutput, Candidate baseCandidate) {
        Candidate candidate = baseCandidate;
        int refinementBudget = refinementBudget(request, candidate.build());
        if (refinementBudget <= 0) {
            return toSuccess(initialOutput, candidate, false);
        }

        boolean repaired = false;
        for (int pass = 1; pass <= refinementBudget; pass++) {
            DesignCritique critique = requestCritique(request, designPlanJson, candidate);
            boolean warningDrivenRefinement = request.repairWarningsEnabled() && candidate.build().validationResult().hasWarnings();
            boolean shouldRefine = warningDrivenRefinement || (critique != null && critique.shouldRefine());
            if (!shouldRefine) {
                break;
            }

            PipelineResult refined = attemptRepair(
                    request,
                    designPlanJson,
                    initialOutput,
                    candidate.modelOutput(),
                    candidate.build(),
                    List.of(),
                    shouldUseVisualReview(request),
                    critique
            );
            if (!refined.success()) {
                return toSuccess(initialOutput, candidate, repaired);
            }

            CompilationAttempt refinedAttempt = parseAndCompile(refined.finalModelOutput(), request);
            if (!refinedAttempt.success()) {
                return toSuccess(initialOutput, candidate, repaired);
            }
            candidate = new Candidate(refined.finalModelOutput(), refinedAttempt.compiledBuild());
            repaired = true;
        }

        return toSuccess(initialOutput, candidate, repaired);
    }

    private PipelineResult repairOrFail(PipelineRequest request, String designPlanJson, String initialOutput, String currentOutput,
                                        CompiledBuild initialBuild, List<BuildFailure> failures, boolean includeVisualReview,
                                        DesignCritique critique) {
        boolean shouldRepair = request.maxRepairAttempts() > 0
                && (initialBuild != null || (failures != null && !failures.isEmpty()))
                && (failures == null || failures.isEmpty() || failures.stream().allMatch(BuildFailure::repairable));
        if (!shouldRepair) {
            BuildFailure failure = failures == null || failures.isEmpty()
                    ? new BuildFailure("generation_failed", "The build could not be generated.", true)
                    : failures.get(0);
            return PipelineResult.failure(failure.code(), failure.message(), initialOutput, currentOutput);
        }

        PipelineResult repaired = attemptRepair(
                request,
                designPlanJson,
                initialOutput,
                currentOutput,
                initialBuild,
                failures,
                includeVisualReview,
                critique
        );
        if (repaired.success()) {
            return repaired;
        }
        if (request.applyBestEffortResult() && initialBuild != null) {
            return PipelineResult.success(initialBuild.sparseCuboids(), initialBuild.validationResult(),
                    initialOutput, currentOutput, false);
        }
        return repaired;
    }

    private PipelineResult attemptRepair(PipelineRequest request, String designPlanJson, String initialOutput, String currentOutput,
                                         CompiledBuild initialBuild, List<BuildFailure> failures, boolean includeVisualReview,
                                         DesignCritique critique) {
        String diagnosticJson = initialBuild == null
                ? GSON.toJson(failures == null ? List.of() : failures)
                : GSON.toJson(initialBuild.validationResult().metrics().machineReadable());
        String critiqueJson = critique == null ? "" : GSON.toJson(critique);
        String latestOutput = currentOutput;
        String latestDiagnostics = diagnosticJson;

        for (int attempt = 1; attempt <= request.maxRepairAttempts(); attempt++) {
            PreviewArtifactSet previews = null;
            try {
                List<AiImageAttachment> images = List.of();
                if (includeVisualReview && initialBuild != null && request.context().visualReviewSettings().enabled()) {
                    previews = previewRenderer.render(
                            initialBuild.voxelModel(),
                            request.context().visualReviewSettings().previewSize(),
                            previewPerspectives(request),
                            request.context().visualReviewSettings().debugArtifactsEnabled(),
                            request.context().visualReviewSettings().debugArtifactDirectory()
                    );
                    images = previewAttachments(previews);
                }

                AiResponse repairResponse = aiClient.execute(
                        request.modelPreset(),
                        new AiRequest(
                                "build_program_v2_repair",
                                buildRepairInstructions(request, designPlanJson, critiqueJson),
                                buildRepairInput(request, designPlanJson, latestOutput, latestDiagnostics, critiqueJson),
                                SchemaFactory.compiledBuildProgramSchema(request.context().limits()),
                                images
                        ),
                        request.timeoutSeconds()
                );
                if (!repairResponse.success()) {
                    if (!repairResponse.retryable() || attempt >= request.maxRepairAttempts()) {
                        return PipelineResult.failure("repair_request_failed", repairResponse.failureMessage(), initialOutput, repairResponse.rawResponse());
                    }
                    continue;
                }

                latestOutput = repairResponse.content();
                CompilationAttempt repairedAttempt = parseAndCompile(latestOutput, request);
                if (repairedAttempt.success()) {
                    return PipelineResult.success(
                            repairedAttempt.compiledBuild().sparseCuboids(),
                            repairedAttempt.compiledBuild().validationResult(),
                            initialOutput,
                            latestOutput,
                            true
                    );
                }

                latestDiagnostics = GSON.toJson(repairedAttempt.failures());
                if (attempt >= request.maxRepairAttempts()) {
                    BuildFailure failure = repairedAttempt.failures().isEmpty()
                            ? new BuildFailure("repair_failed", "The repair attempt was still invalid.", true)
                            : repairedAttempt.failures().get(0);
                    return PipelineResult.failure(failure.code(), failure.message(), initialOutput, latestOutput);
                }
            } catch (IOException e) {
                return PipelineResult.failure("preview_render_failed", e.getMessage(), initialOutput, latestOutput);
            } finally {
                if (previews != null) {
                    previews.close();
                }
            }
        }
        return PipelineResult.failure("repair_failed", "Repair attempts were exhausted.", initialOutput, latestOutput);
    }

    private DesignCritique requestCritique(PipelineRequest request, String designPlanJson, Candidate candidate) {
        if (!request.workflowSettings().critiqueEnabled() && !request.visualReviewRepairEnabled()) {
            return null;
        }

        PreviewArtifactSet previews = null;
        try {
            List<AiImageAttachment> images = List.of();
            if (shouldUseVisualReview(request) && request.context().visualReviewSettings().enabled()) {
                previews = previewRenderer.render(
                        candidate.build().voxelModel(),
                        request.context().visualReviewSettings().previewSize(),
                        previewPerspectives(request),
                        request.context().visualReviewSettings().debugArtifactsEnabled(),
                        request.context().visualReviewSettings().debugArtifactDirectory()
                );
                images = previewAttachments(previews);
            }
            AiResponse response = aiClient.execute(
                    request.modelPreset(),
                    new AiRequest(
                            "build_critique_v1",
                            buildCritiqueInstructions(request),
                            buildCritiqueInput(request, designPlanJson, candidate.modelOutput(), candidate.build()),
                            SchemaFactory.designCritiqueSchema(),
                            images
                    ),
                    request.timeoutSeconds()
            );
            if (!response.success()) {
                return null;
            }
            return validateDesignCritique(response.content());
        } catch (IOException | IllegalArgumentException ignored) {
            return null;
        } finally {
            if (previews != null) {
                previews.close();
            }
        }
    }

    private CompilationAttempt parseAndCompile(String rawOutput, PipelineRequest request) {
        try {
            BuildProgram program = parser.parse(rawOutput, request.context().limits());
            CompileResult compileResult = compiler.compile(program, request.context(), paletteResolver);
            if (!compileResult.isSuccess()) {
                return CompilationAttempt.failure(compileResult.failures());
            }
            return CompilationAttempt.success(compileResult.compiledBuild());
        } catch (BuildProgramParseException e) {
            return CompilationAttempt.failure(List.of(new BuildFailure("malformed_build_program", e.getMessage(), e.isRepairable())));
        }
    }

    private String buildPlanInstructions(PipelineRequest request) {
        return """
                You are planning a high-quality Minecraft build before voxel authoring.
                Return one JSON object that describes the intended composition, material strategy, detail agenda, structural rules, and risk checks.
                Keep the plan generic and reusable across architecture, terrain, machinery, interiors, statues, vehicles, and organic subjects.
                Focus on proportion, silhouette hierarchy, depth layering, support logic, material transitions, and where exact block-level detail should be concentrated.
                Do not output any block coordinates or build-program operations yet.
                """;
    }

    private String buildPlanInput(PipelineRequest request) {
        return """
                User request:
                %s

                Selection dimensions:
                %dx%dx%d

                Request classification:
                %s

                Workflow profile:
                %s
                """.formatted(
                request.userPrompt(),
                request.context().buildVolume().width(),
                request.context().buildVolume().height(),
                request.context().buildVolume().depth(),
                request.context().classification().name().toLowerCase(),
                request.workflowSettings().profile().configValue()
        );
    }

    private String buildGenerationInstructions(PipelineRequest request, String designPlanJson, int attempt, List<BuildFailure> failures) {
        StringBuilder builder = new StringBuilder(request.systemPrompt());
        builder.append("""

                Target Build Program version:
                - v must be 2
                - use pt and pts for exact block-by-block detailing where primitives would look coarse
                - use exact placement for roof edges, trim, supports, recess accents, windows, machinery teeth, branch tips, terrain breakup, and silhouette cleanup when appropriate
                """);
        if (designPlanJson != null && !designPlanJson.isBlank()) {
            builder.append("\nSemantic plan JSON:\n").append(designPlanJson).append('\n');
        }
        builder.append("""

                Workflow policy:
                - author a coherent complete build, not an intermediate sketch
                - primitives may establish masses and repeats, but visible final surfaces should read as intentionally designed block by block
                - side and rear elevations must receive the same level of intent as the front unless the user asked otherwise
                """);
        if (attempt > 1 && failures != null && !failures.isEmpty()) {
            int maxX = request.context().buildVolume().width() - 1;
            int maxY = request.context().buildVolume().height() - 1;
            int maxZ = request.context().buildVolume().depth() - 1;
            boolean boundsFailure = failures.stream().anyMatch(failure -> "coordinate_outside_selection".equals(failure.code()));
            builder.append("""

                    Previous attempt was invalid. Regenerate the entire Build Program from scratch.
                    Hard local bounds remain:
                    - X: 0 through %d
                    - Y: 0 through %d
                    - Z: 0 through %d
                    Every transformed coordinate and every inclusive endpoint must stay inside those ranges.
                    Never use the selection dimensions themselves as coordinates. The largest valid local coordinates are exactly [%d,%d,%d].
                    %s
                    Do not patch the previous output. Recompute the full program.
                    Previous compiler/parser diagnostics:
                    %s
                    """.formatted(
                    maxX,
                    maxY,
                    maxZ,
                    maxX,
                    maxY,
                    maxZ,
                    boundsFailure
                            ? "Because the previous result exceeded bounds, keep a one-block interior margin around decorative edges unless full-span contact is required."
                            : "",
                    formatFailures(failures)
            ));
        }
        return builder.toString();
    }

    private String buildGenerationInput(PipelineRequest request, String designPlanJson) {
        StringBuilder builder = new StringBuilder();
        builder.append("User request:\n").append(request.userPrompt()).append("\n\n");
        builder.append("Workflow profile:\n").append(request.workflowSettings().profile().configValue()).append("\n\n");
        if (designPlanJson != null && !designPlanJson.isBlank()) {
            builder.append("Semantic plan JSON:\n").append(designPlanJson).append("\n\n");
        }
        builder.append("Return one complete Build Program DSL v2 JSON object.");
        return builder.toString();
    }

    private String buildCritiqueInstructions(PipelineRequest request) {
        return """
                You are critically reviewing a compiled Minecraft build result.
                Inspect the provided previews and machine metrics as the actual compiled build, not as a concept sketch.
                Judge whether the build already looks intentionally designed by an experienced Minecraft builder.
                Use issues only for concrete visual or structural problems that materially improve the final result.
                Prefer critique categories such as proportion, silhouette, depth, support, material transitions, detail density, complete elevations, and composition.
                If the build is already strong, return verdict accept.
                If it still needs work, return verdict refine with targeted fixes.
                """;
    }

    private String buildCritiqueInput(PipelineRequest request, String designPlanJson, String modelOutput, CompiledBuild build) {
        return """
                User request:
                %s

                Workflow profile:
                %s

                Semantic plan JSON:
                %s

                Current Build Program:
                %s

                Machine-readable compiled metrics:
                %s
                """.formatted(
                request.userPrompt(),
                request.workflowSettings().profile().configValue(),
                designPlanJson == null || designPlanJson.isBlank() ? "{}" : designPlanJson,
                modelOutput,
                GSON.toJson(build.validationResult().metrics().machineReadable())
        );
    }

    private String buildRepairInstructions(PipelineRequest request, String designPlanJson, String critiqueJson) {
        int maxX = request.context().buildVolume().width() - 1;
        int maxY = request.context().buildVolume().height() - 1;
        int maxZ = request.context().buildVolume().depth() - 1;
        return """
                You are authoring or repairing a Minecraft Build Program DSL v2 response.
                Return one complete corrected Build Program JSON object and nothing else.
                Preserve the original semantic subject, required counts, arrangement and scale intent.
                Fix every reported structural, bounds, support, projection, depth, detail and compression issue.
                Use reusable geometry for large masses and repetition, then use pt and pts exact placements to finish visible detail where needed.
                Symmetry should prefer mirroring over manual duplication, but controlled asymmetry is acceptable when it improves realism.
                Do not output sparse final compression. Return only the DSL program.
                Semantic plan JSON:
                %s

                Critique JSON:
                %s

                If the result reads as coarse, toy-like, flat, repetitive, or monolithic, increase articulation and block-level craft while preserving subject identity.
                Use stairs, slabs, walls, fences, panes, trapdoors, chains, lanterns, logs, buttons, and directional block states to sharpen silhouettes and detailing where appropriate.
                Local bounds are strict and inclusive:
                - X: 0 through %d
                - Y: 0 through %d
                - Z: 0 through %d
                Never use [%d,%d,%d] as dimensions and also as coordinates beyond those maxima.
                Priority order:
                1. valid JSON, valid bounds, supported blocks and all budgets;
                2. semantic correctness and required counts/arrangement;
                3. complete elevations, proportions, silhouette and depth;
                4. structural consistency and support logic;
                5. material coherence and fine detail density.
                """.formatted(
                designPlanJson == null || designPlanJson.isBlank() ? "{}" : designPlanJson,
                critiqueJson == null || critiqueJson.isBlank() ? "{}" : critiqueJson,
                maxX,
                maxY,
                maxZ,
                maxX,
                maxY,
                maxZ
        );
    }

    private String buildRepairInput(PipelineRequest request, String designPlanJson, String currentOutput,
                                    String diagnosticsJson, String critiqueJson) {
        int maxX = request.context().buildVolume().width() - 1;
        int maxY = request.context().buildVolume().height() - 1;
        int maxZ = request.context().buildVolume().depth() - 1;
        return """
                Original user request:
                %s

                Workflow profile:
                %s

                Selection dimensions:
                %dx%dx%d

                Local inclusive coordinate bounds:
                X: 0..%d
                Y: 0..%d
                Z: 0..%d

                Semantic plan JSON:
                %s

                Current Build Program or model output:
                %s

                Machine-readable diagnostics:
                %s

                Critique JSON:
                %s

                Return one corrected Build Program DSL v2 JSON object.
                """.formatted(
                request.userPrompt(),
                request.workflowSettings().profile().configValue(),
                request.context().buildVolume().width(),
                request.context().buildVolume().height(),
                request.context().buildVolume().depth(),
                maxX,
                maxY,
                maxZ,
                designPlanJson == null || designPlanJson.isBlank() ? "{}" : designPlanJson,
                currentOutput,
                diagnosticsJson,
                critiqueJson == null || critiqueJson.isBlank() ? "{}" : critiqueJson
        );
    }

    private PipelineResult toSuccess(String initialOutput, Candidate candidate, boolean repaired) {
        return PipelineResult.success(
                candidate.build().sparseCuboids(),
                candidate.build().validationResult(),
                initialOutput,
                candidate.modelOutput(),
                repaired
        );
    }

    private boolean canRetryGeneration(int attempt, PipelineRequest request, List<BuildFailure> failures) {
        return attempt < Math.max(1, request.maxGenerationAttempts())
                && !failures.isEmpty()
                && failures.stream().allMatch(BuildFailure::repairable);
    }

    private int refinementBudget(PipelineRequest request, CompiledBuild build) {
        int budget = request.workflowSettings().critiqueEnabled() ? request.workflowSettings().maxRefinementPasses() : 0;
        if (request.repairWarningsEnabled() && build.validationResult().hasWarnings()) {
            budget = Math.max(budget, 1);
        }
        if (request.visualReviewRepairEnabled() && shouldUseVisualReview(request)) {
            budget = Math.max(budget, 1);
        }
        return budget;
    }

    private boolean shouldUseVisualReview(PipelineRequest request) {
        return request.context().visualReviewSettings().enabled();
    }

    private Collection<PreviewPerspective> previewPerspectives(PipelineRequest request) {
        if (request.workflowSettings().extendedPreviews()) {
            return List.of(
                    PreviewPerspective.FRONT,
                    PreviewPerspective.RIGHT,
                    PreviewPerspective.BACK,
                    PreviewPerspective.LEFT,
                    PreviewPerspective.TOP,
                    PreviewPerspective.ISOMETRIC
            );
        }
        return List.of(
                PreviewPerspective.FRONT,
                PreviewPerspective.RIGHT,
                PreviewPerspective.TOP,
                PreviewPerspective.ISOMETRIC
        );
    }

    private List<AiImageAttachment> previewAttachments(PreviewArtifactSet previews) {
        return previews.files().entrySet().stream()
                .map(entry -> new AiImageAttachment(entry.getKey().name().toLowerCase(), "image/png", entry.getValue()))
                .toList();
    }

    private String formatFailures(List<BuildFailure> failures) {
        StringBuilder builder = new StringBuilder();
        for (BuildFailure failure : failures) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ").append(failure.code()).append(": ").append(failure.message());
        }
        return builder.toString();
    }

    private void validateDesignPlan(String rawJson) {
        try {
            DesignPlan plan = GSON.fromJson(rawJson, DesignPlan.class);
            if (plan == null
                    || isBlank(plan.summary())
                    || plan.composition() == null || plan.composition().isEmpty()
                    || plan.palette_strategy() == null || plan.palette_strategy().isEmpty()
                    || plan.detail_agenda() == null || plan.detail_agenda().isEmpty()
                    || plan.structural_rules() == null || plan.structural_rules().isEmpty()
                    || plan.risk_checks() == null || plan.risk_checks().isEmpty()
                    || isBlank(plan.symmetry_strategy())) {
                throw new IllegalArgumentException("Incomplete design plan.");
            }
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Malformed design plan JSON.", e);
        }
    }

    private DesignCritique validateDesignCritique(String rawJson) {
        try {
            DesignCritique critique = GSON.fromJson(rawJson, DesignCritique.class);
            if (critique == null || isBlank(critique.verdict()) || isBlank(critique.summary())
                    || critique.issues() == null || critique.focus_areas() == null) {
                throw new IllegalArgumentException("Incomplete critique.");
            }
            return critique;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Malformed critique JSON.", e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Candidate(String modelOutput, CompiledBuild build) {
        private Candidate {
            Objects.requireNonNull(modelOutput, "modelOutput");
            Objects.requireNonNull(build, "build");
        }
    }

    private record CompilationAttempt(CompiledBuild compiledBuild, List<BuildFailure> failures) {
        private static CompilationAttempt success(CompiledBuild compiledBuild) {
            return new CompilationAttempt(compiledBuild, List.of());
        }

        private static CompilationAttempt failure(List<BuildFailure> failures) {
            return new CompilationAttempt(null, List.copyOf(failures));
        }

        private boolean success() {
            return compiledBuild != null;
        }
    }
}
