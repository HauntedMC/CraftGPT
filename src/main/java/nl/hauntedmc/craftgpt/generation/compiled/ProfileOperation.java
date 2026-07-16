package nl.hauntedmc.craftgpt.generation.compiled;

import nl.hauntedmc.craftgpt.generation.Axis;
import nl.hauntedmc.craftgpt.generation.IntVec3;

import java.util.function.UnaryOperator;

public record ProfileOperation(
        OperationMode mode,
        String paletteId,
        IntVec3 from,
        IntVec3 to,
        Axis axis,
        int depth,
        int wallThickness
) implements PrimitiveOperation {
    @Override
    public PrimitiveKind kind() {
        return PrimitiveKind.PROFILE;
    }

    @Override
    public PrimitiveOperation transform(UnaryOperator<IntVec3> pointTransform) {
        return new ProfileOperation(
                mode,
                paletteId,
                pointTransform.apply(from),
                pointTransform.apply(to),
                axis,
                depth,
                wallThickness
        );
    }
}
