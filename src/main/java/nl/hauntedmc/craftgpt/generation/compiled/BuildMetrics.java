package nl.hauntedmc.craftgpt.generation.compiled;

import nl.hauntedmc.craftgpt.generation.IntVec3;

import java.util.Map;

public record BuildMetrics(
        int occupiedCellCount,
        int paletteSize,
        int dslOperationCount,
        int instanceCount,
        IntVec3 minBounds,
        IntVec3 maxBounds,
        double boundingBoxUtilization,
        int connectedComponents,
        int largestConnectedComponent,
        int groundContactCount,
        double groundContactRatio,
        int unsupportedOccupiedCells,
        int frontProjectionWidth,
        int frontProjectionHeight,
        int sideProjectionDepth,
        int sideProjectionHeight,
        int topProjectionWidth,
        int topProjectionDepth,
        int sparseSegmentCount,
        double compressionRatio,
        int materialReplacementCount,
        int cutCellCount,
        int exposedFaceCount,
        int exteriorBlockCount,
        int detailBlockCount,
        double detailBlockRatio,
        int exactPlacementCount,
        int frontLayeredColumns,
        int sideLayeredColumns,
        Map<String, Object> machineReadable
) {
}
