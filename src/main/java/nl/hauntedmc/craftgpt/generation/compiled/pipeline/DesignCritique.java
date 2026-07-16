package nl.hauntedmc.craftgpt.generation.compiled.pipeline;

import java.util.List;
import java.util.Locale;

public record DesignCritique(
        String verdict,
        String summary,
        List<Issue> issues,
        List<String> focus_areas
) {
    public boolean shouldRefine() {
        return verdict != null && verdict.trim().toLowerCase(Locale.ROOT).equals("refine");
    }

    public record Issue(String severity, String category, String observation, String fix) {
    }
}
