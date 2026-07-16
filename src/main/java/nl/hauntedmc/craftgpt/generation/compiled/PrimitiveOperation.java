package nl.hauntedmc.craftgpt.generation.compiled;

import nl.hauntedmc.craftgpt.generation.IntVec3;

public sealed interface PrimitiveOperation permits BoxOperation, CylinderOperation, EllipsoidOperation, LineOperation, PointOperation, PointSetOperation, ProfileOperation, RawCuboidOperation {
    PrimitiveKind kind();

    OperationMode mode();

    String paletteId();

    PrimitiveOperation transform(java.util.function.UnaryOperator<IntVec3> pointTransform);
}
