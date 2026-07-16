package nl.hauntedmc.craftgpt.generation;

import java.util.Objects;

public record IntVec3(int x, int y, int z) {
    public static final IntVec3 ZERO = new IntVec3(0, 0, 0);

    public IntVec3 add(IntVec3 other) {
        Objects.requireNonNull(other, "other");
        return new IntVec3(
                Math.addExact(x, other.x),
                Math.addExact(y, other.y),
                Math.addExact(z, other.z)
        );
    }

    public IntVec3 subtract(IntVec3 other) {
        Objects.requireNonNull(other, "other");
        return new IntVec3(
                Math.subtractExact(x, other.x),
                Math.subtractExact(y, other.y),
                Math.subtractExact(z, other.z)
        );
    }

    public IntVec3 multiply(int factor) {
        return new IntVec3(
                Math.multiplyExact(x, factor),
                Math.multiplyExact(y, factor),
                Math.multiplyExact(z, factor)
        );
    }

    public static IntVec3 min(IntVec3 left, IntVec3 right) {
        return new IntVec3(
                Math.min(left.x, right.x),
                Math.min(left.y, right.y),
                Math.min(left.z, right.z)
        );
    }

    public static IntVec3 max(IntVec3 left, IntVec3 right) {
        return new IntVec3(
                Math.max(left.x, right.x),
                Math.max(left.y, right.y),
                Math.max(left.z, right.z)
        );
    }
}
