package nl.hauntedmc.craftgpt.generation.compiled;

import nl.hauntedmc.craftgpt.generation.IntVec3;

import java.util.function.UnaryOperator;

public record EllipsoidOperation(
        OperationMode mode,
        String paletteId,
        IntVec3 center,
        int radiusX,
        int radiusY,
        int radiusZ,
        boolean hollow,
        int wallThickness
) implements PrimitiveOperation {
    @Override
    public PrimitiveKind kind() {
        return PrimitiveKind.ELLIPSOID;
    }

    @Override
    public PrimitiveOperation transform(UnaryOperator<IntVec3> pointTransform) {
        return new EllipsoidOperation(
                mode,
                paletteId,
                pointTransform.apply(center),
                radiusX,
                radiusY,
                radiusZ,
                hollow,
                wallThickness
        );
    }
}
