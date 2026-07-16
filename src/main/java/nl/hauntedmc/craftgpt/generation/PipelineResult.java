package nl.hauntedmc.craftgpt.generation;

import nl.hauntedmc.craftgpt.generation.compiled.SparseCuboid;
import nl.hauntedmc.craftgpt.generation.compiled.ValidationResult;

import java.util.List;

public record PipelineResult(
        boolean success,
        String failureCode,
        String failureMessage,
        List<SparseCuboid> cuboids,
        ValidationResult validationResult,
        String initialModelOutput,
        String finalModelOutput,
        boolean repaired
) {
    public PipelineResult {
        cuboids = cuboids == null ? List.of() : List.copyOf(cuboids);
    }

    public static PipelineResult success(List<SparseCuboid> cuboids, ValidationResult validationResult,
                                         String initialModelOutput, String finalModelOutput, boolean repaired) {
        return new PipelineResult(true, "", "", cuboids, validationResult, initialModelOutput, finalModelOutput, repaired);
    }

    public static PipelineResult failure(String failureCode, String failureMessage, String initialModelOutput, String finalModelOutput) {
        return new PipelineResult(false, failureCode, failureMessage, List.of(), null, initialModelOutput, finalModelOutput, false);
    }
}
