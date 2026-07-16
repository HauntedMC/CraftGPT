package nl.hauntedmc.craftgpt.generation.compiled;

public record BuildFailure(String code, String message, boolean repairable) {
}
