package nl.hauntedmc.craftgpt.generation.compiled;

import nl.hauntedmc.craftgpt.generation.Axis;
import nl.hauntedmc.craftgpt.generation.IntVec3;

import java.util.function.UnaryOperator;

public record CylinderOperation(
        OperationMode mode,
        String paletteId,
        IntVec3 center,
        int radius,
        int height,
        Axis axis,
        boolean hollow,
        int wallThickness
) implements PrimitiveOperation {
    @Override
    public PrimitiveKind kind() {
        return PrimitiveKind.CYLINDER;
    }

    @Override
    public PrimitiveOperation transform(UnaryOperator<IntVec3> pointTransform) {
        return new CylinderOperation(
                mode,
                paletteId,
                pointTransform.apply(center),
                radius,
                height,
                axis,
                hollow,
                wallThickness
        );
    }
}
