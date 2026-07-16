package nl.hauntedmc.craftgpt.generation.compiled.pipeline;

import java.util.List;

public record DesignPlan(
        String summary,
        List<String> composition,
        List<PaletteSuggestion> palette_strategy,
        List<String> detail_agenda,
        List<String> structural_rules,
        List<String> risk_checks,
        String symmetry_strategy
) {
    public record PaletteSuggestion(String role, String block_state, String reason) {
    }
}
