package nl.hauntedmc.craftgpt.generation;

public record GenerationLimits(
        int maxDslComponents,
        int maxDslInstances,
        int maxDslOperations,
        int maxDslOperationsPerComponent,
        int maxMatrixBlocksPerGeneration,
        int maxTotalActionsPerGeneration,
        int maxSparseSegmentsPerGeneration
) {
}
