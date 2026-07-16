package nl.hauntedmc.craftgpt.generation.compiled;

import java.util.List;

public record ValidationResult(
        List<BuildDiagnostic> diagnostics,
        BuildMetrics metrics
) {
    public ValidationResult {
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean hasFatalErrors() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == BuildDiagnostic.Severity.FATAL);
    }

    public boolean hasWarnings() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == BuildDiagnostic.Severity.WARNING);
    }
}
