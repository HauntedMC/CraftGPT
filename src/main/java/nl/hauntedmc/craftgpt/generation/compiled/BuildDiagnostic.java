package nl.hauntedmc.craftgpt.generation.compiled;

public record BuildDiagnostic(Severity severity, String code, String message) {
    public enum Severity {
        FATAL,
        WARNING
    }
}
