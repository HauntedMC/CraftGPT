package nl.hauntedmc.craftgpt.generation.compiled;

import nl.hauntedmc.craftgpt.generation.IntVec3;

import java.util.function.UnaryOperator;

public record LineOperation(
        OperationMode mode,
        String paletteId,
        IntVec3 from,
        IntVec3 to,
        int width
) implements PrimitiveOperation {
    @Override
    public PrimitiveKind kind() {
        return PrimitiveKind.LINE;
    }

    @Override
    public PrimitiveOperation transform(UnaryOperator<IntVec3> pointTransform) {
        return new LineOperation(
                mode,
                paletteId,
                pointTransform.apply(from),
                pointTransform.apply(to),
                width
        );
    }
}
