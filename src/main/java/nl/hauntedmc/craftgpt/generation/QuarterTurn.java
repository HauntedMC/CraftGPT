package nl.hauntedmc.craftgpt.generation;

public enum QuarterTurn {
    DEG_0(0),
    DEG_90(90),
    DEG_180(180),
    DEG_270(270);

    private final int degrees;

    QuarterTurn(int degrees) {
        this.degrees = degrees;
    }

    public int degrees() {
        return degrees;
    }

    public static QuarterTurn fromDegrees(int degrees) {
        return switch (degrees) {
            case 0 -> DEG_0;
            case 90 -> DEG_90;
            case 180 -> DEG_180;
            case 270 -> DEG_270;
            default -> null;
        };
    }
}
