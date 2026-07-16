package nl.hauntedmc.craftgpt.generation.compiled;

import nl.hauntedmc.craftgpt.generation.IntVec3;

import java.util.function.UnaryOperator;

public record RawCuboidOperation(
        OperationMode mode,
        String paletteId,
        IntVec3 from,
        IntVec3 to
) implements PrimitiveOperation {
    @Override
    public PrimitiveKind kind() {
        return PrimitiveKind.RAW_CUBOID;
    }

    @Override
    public PrimitiveOperation transform(UnaryOperator<IntVec3> pointTransform) {
        return new RawCuboidOperation(
                mode,
                paletteId,
                pointTransform.apply(from),
                pointTransform.apply(to)
        );
    }
}
