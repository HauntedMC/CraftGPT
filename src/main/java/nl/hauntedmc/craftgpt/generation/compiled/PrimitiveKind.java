package nl.hauntedmc.craftgpt.generation.compiled;

public enum PrimitiveKind {
    BOX("box"),
    CYLINDER("cyl"),
    ELLIPSOID("ell"),
    LINE("line"),
    POINT("pt"),
    POINT_SET("pts"),
    PROFILE("prof"),
    RAW_CUBOID("seg");

    private final String dslValue;

    PrimitiveKind(String dslValue) {
        this.dslValue = dslValue;
    }

    public String dslValue() {
        return dslValue;
    }

    public static PrimitiveKind fromDslValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        for (PrimitiveKind kind : values()) {
            if (kind.dslValue.equals(normalized)) {
                return kind;
            }
        }
        return null;
    }
}
