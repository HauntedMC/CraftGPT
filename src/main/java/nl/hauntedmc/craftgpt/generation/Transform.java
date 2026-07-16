package nl.hauntedmc.craftgpt.generation;

import java.util.Objects;

/**
 * Applies compiled DSL instance transforms in the documented order:
 * mirror local X/Z around the component origin, rotate around the local Y axis,
 * then translate into top-level local selection coordinates.
 */
public record Transform(boolean mirrorX, boolean mirrorZ, QuarterTurn rotation, IntVec3 translation) {
    public Transform {
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(translation, "translation");
    }

    public IntVec3 apply(IntVec3 point) {
        Objects.requireNonNull(point, "point");
        return rotate(mirror(point)).add(translation);
    }

    private IntVec3 mirror(IntVec3 point) {
        int mirroredX = mirrorX ? Math.negateExact(point.x()) : point.x();
        int mirroredZ = mirrorZ ? Math.negateExact(point.z()) : point.z();
        return new IntVec3(mirroredX, point.y(), mirroredZ);
    }

    private IntVec3 rotate(IntVec3 point) {
        return switch (rotation) {
            case DEG_0 -> point;
            case DEG_90 -> new IntVec3(point.z(), point.y(), Math.negateExact(point.x()));
            case DEG_180 -> new IntVec3(Math.negateExact(point.x()), point.y(), Math.negateExact(point.z()));
            case DEG_270 -> new IntVec3(Math.negateExact(point.z()), point.y(), point.x());
        };
    }
}
