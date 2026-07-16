package nl.hauntedmc.craftgpt.generation.compiled;

public enum OperationMode {
    ADD,
    CUT;

    public static OperationMode fromDslValue(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "add" -> ADD;
            case "cut" -> CUT;
            default -> null;
        };
    }
}
