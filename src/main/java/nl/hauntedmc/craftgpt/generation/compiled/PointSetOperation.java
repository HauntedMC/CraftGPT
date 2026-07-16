package nl.hauntedmc.craftgpt.generation.compiled;

import nl.hauntedmc.craftgpt.generation.IntVec3;

import java.util.List;
import java.util.function.UnaryOperator;

public record PointSetOperation(
        OperationMode mode,
        String paletteId,
        List<IntVec3> points
) implements PrimitiveOperation {
    public PointSetOperation {
        points = List.copyOf(points);
    }

    @Override
    public PrimitiveKind kind() {
        return PrimitiveKind.POINT_SET;
    }

    @Override
    public PrimitiveOperation transform(UnaryOperator<IntVec3> pointTransform) {
        return new PointSetOperation(
                mode,
                paletteId,
                points.stream().map(pointTransform).toList()
        );
    }
}
