package nl.hauntedmc.craftgpt.generation;

public record VisualReviewSettings(
        boolean enabled,
        int minOccupiedBlocks,
        int previewSize,
        boolean debugArtifactsEnabled,
        String debugArtifactDirectory
) {
}
