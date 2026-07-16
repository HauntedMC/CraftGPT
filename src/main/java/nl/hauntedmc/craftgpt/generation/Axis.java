package nl.hauntedmc.craftgpt.generation;

public enum Axis {
    X,
    Y,
    Z;

    public static Axis fromDslValue(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "x" -> X;
            case "y" -> Y;
            case "z" -> Z;
            default -> null;
        };
    }
}
