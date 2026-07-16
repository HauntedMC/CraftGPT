package nl.hauntedmc.craftgpt.generation;

import java.util.Objects;

public record GenerationContext(
        BuildVolume buildVolume,
        GenerationLimits limits,
        VisualReviewSettings visualReviewSettings,
        RequestClassification classification,
        String frontDirection,
        String minecraftVersion
) {
    public GenerationContext {
        Objects.requireNonNull(buildVolume, "buildVolume");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(visualReviewSettings, "visualReviewSettings");
        Objects.requireNonNull(classification, "classification");
        minecraftVersion = minecraftVersion == null ? "unknown" : minecraftVersion;
        frontDirection = frontDirection == null || frontDirection.isBlank() ? "positive Z" : frontDirection;
    }
}
