package nl.hauntedmc.craftgpt.generation.compiled;

import nl.hauntedmc.craftgpt.generation.IntVec3;

import java.util.function.UnaryOperator;

public record BoxOperation(
        OperationMode mode,
        String paletteId,
        IntVec3 from,
        IntVec3 to,
        boolean hollow,
        int wallThickness
) implements PrimitiveOperation {
    @Override
    public PrimitiveKind kind() {
        return PrimitiveKind.BOX;
    }

    @Override
    public PrimitiveOperation transform(UnaryOperator<IntVec3> pointTransform) {
        return new BoxOperation(
                mode,
                paletteId,
                pointTransform.apply(from),
                pointTransform.apply(to),
                hollow,
                wallThickness
        );
    }
}
