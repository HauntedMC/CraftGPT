package nl.hauntedmc.craftgpt.generation.compiled.parser;

public final class BuildProgramParseException extends Exception {
    private static final long serialVersionUID = 1L;

    private final boolean repairable;

    public BuildProgramParseException(String message) {
        this(message, true);
    }

    public BuildProgramParseException(String message, boolean repairable) {
        super(message);
        this.repairable = repairable;
    }

    public boolean isRepairable() {
        return repairable;
    }
}
