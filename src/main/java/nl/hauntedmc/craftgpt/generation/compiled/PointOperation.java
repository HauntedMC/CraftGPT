package nl.hauntedmc.craftgpt.generation.compiled;

import nl.hauntedmc.craftgpt.generation.IntVec3;

import java.util.function.UnaryOperator;

public record PointOperation(
        OperationMode mode,
        String paletteId,
        IntVec3 at
) implements PrimitiveOperation {
    @Override
    public PrimitiveKind kind() {
        return PrimitiveKind.POINT;
    }

    @Override
    public PrimitiveOperation transform(UnaryOperator<IntVec3> pointTransform) {
        return new PointOperation(mode, paletteId, pointTransform.apply(at));
    }
}
