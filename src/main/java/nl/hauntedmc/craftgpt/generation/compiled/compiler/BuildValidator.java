package nl.hauntedmc.craftgpt.generation.compiled.compiler;

import nl.hauntedmc.craftgpt.generation.BuildVolume;
import nl.hauntedmc.craftgpt.generation.RequestClassification;
import nl.hauntedmc.craftgpt.generation.IntVec3;
import nl.hauntedmc.craftgpt.generation.compiled.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class BuildValidator {
    public ValidationResult validate(BuildProgram program, VoxelModel voxelModel, List<SparseCuboid> cuboids, BuildVolume volume,
                                     RequestClassification classification, int materialReplacementCount, int cutCellCount) {
        List<BuildDiagnostic> diagnostics = new ArrayList<>();
        if (voxelModel.size() == 0) {
            diagnostics.add(new BuildDiagnostic(BuildDiagnostic.Severity.FATAL, "empty_final_build", "The final build is empty."));
            return new ValidationResult(diagnostics, emptyMetrics(program, cuboids, materialReplacementCount, cutCellCount));
        }

        IntVec3 min = voxelModel.min();
        IntVec3 max = voxelModel.max();
        if (min == null || max == null) {
            diagnostics.add(new BuildDiagnostic(BuildDiagnostic.Severity.FATAL, "empty_final_build", "The final build is empty."));
            return new ValidationResult(diagnostics, emptyMetrics(program, cuboids, materialReplacementCount, cutCellCount));
        }

        for (long key : voxelModel.asMap().keySet()) {
            IntVec3 point = VoxelModel.unpack(key);
            if (!volume.contains(point)) {
                diagnostics.add(new BuildDiagnostic(BuildDiagnostic.Severity.FATAL, "final_coordinates_outside_selection",
                        "Final coordinates exceeded the captured selection."));
                break;
            }
        }

        int width = max.x() - min.x() + 1;
        int height = max.y() - min.y() + 1;
        int depth = max.z() - min.z() + 1;
        double utilization = voxelModel.size() / (double) (width * height * depth);

        Connectivity connectivity = computeConnectivity(voxelModel);
        int groundContactCount = 0;
        int unsupported = 0;
        Map<Integer, Integer> layerCounts = new HashMap<>();
        Set<Integer> frontXs = new HashSet<>();
        Set<Integer> frontYs = new HashSet<>();
        Set<Integer> sideZs = new HashSet<>();
        Set<Integer> sideYs = new HashSet<>();
        Set<Integer> topXs = new HashSet<>();
        Set<Integer> topZs = new HashSet<>();
        Set<String> materials = new HashSet<>();
        Map<String, Set<Integer>> frontDepthsByColumn = new HashMap<>();
        Map<String, Set<Integer>> sideDepthsByColumn = new HashMap<>();
        int exposedFaces = 0;
        int exteriorBlocks = 0;
        int detailBlocks = 0;

        for (Map.Entry<Long, ResolvedPaletteEntry> entry : voxelModel.entries()) {
            IntVec3 point = VoxelModel.unpack(entry.getKey());
            materials.add(entry.getValue().id());
            if (isDetailBlock(entry.getValue().block().canonicalBlockState())) {
                detailBlocks++;
            }
            if (point.y() == 0) {
                groundContactCount++;
            } else if (!containsVoxel(voxelModel, new IntVec3(point.x(), point.y() - 1, point.z()))) {
                unsupported++;
            }
            layerCounts.merge(point.y(), 1, Integer::sum);
            frontXs.add(point.x());
            frontYs.add(point.y());
            sideZs.add(point.z());
            sideYs.add(point.y());
            topXs.add(point.x());
            topZs.add(point.z());
            frontDepthsByColumn.computeIfAbsent(point.x() + ":" + point.y(), ignored -> new TreeSet<>()).add(point.z());
            sideDepthsByColumn.computeIfAbsent(point.z() + ":" + point.y(), ignored -> new TreeSet<>()).add(point.x());

            int blockExposedFaces = 0;
            for (IntVec3 neighbor : neighbors(point)) {
                if (!containsVoxel(voxelModel, neighbor)) {
                    blockExposedFaces++;
                }
            }
            exposedFaces += blockExposedFaces;
            if (blockExposedFaces > 0) {
                exteriorBlocks++;
            }
        }

        double groundRatio = groundContactCount / (double) voxelModel.size();
        int frontLayeredColumns = (int) frontDepthsByColumn.values().stream().filter(depths -> depths.size() >= 2).count();
        int sideLayeredColumns = (int) sideDepthsByColumn.values().stream().filter(depths -> depths.size() >= 2).count();
        OperationProfile operationProfile = profileOperations(program);
        double detailBlockRatio = detailBlocks / (double) voxelModel.size();
        if (connectivity.componentCount > 4 && connectivity.largestComponentSize < Math.max(8, voxelModel.size() * 0.6d)) {
            diagnostics.add(new BuildDiagnostic(BuildDiagnostic.Severity.WARNING, "fragmented_components",
                    "The build is fragmented into many small disconnected components."));
        }
        if (classification != RequestClassification.FLAT_ART && depth <= 1) {
            diagnostics.add(new BuildDiagnostic(BuildDiagnostic.Severity.WARNING, "flat_side_projection",
                    "The build is only one block thick on the Z axis."));
        }
        if (classification != RequestClassification.FLAT_ART && width <= 1) {
            diagnostics.add(new BuildDiagnostic(BuildDiagnostic.Severity.WARNING, "flat_front_projection",
                    "The build is only one block thick on the X axis."));
        }
        int dominantLayerCount = layerCounts.values().stream().max(Comparator.naturalOrder()).orElse(0);
        if (classification != RequestClassification.FLAT_ART && height > 1 && dominantLayerCount >= voxelModel.size() * 0.85d) {
            diagnostics.add(new BuildDiagnostic(BuildDiagnostic.Severity.WARNING, "dominant_single_layer",
                    "The build is dominated by one horizontal layer."));
        }
        if (classification == RequestClassification.VOLUMETRIC
                && voxelModel.size() >= 350
                && materials.size() <= 3
                && operationProfile.boxLikeRatio() >= 0.8d
                && operationProfile.expressivePrimitiveCount() == 0
                && operationProfile.exactPlacementCount() <= 8) {
            diagnostics.add(new BuildDiagnostic(BuildDiagnostic.Severity.WARNING, "coarse_box_dominated_geometry",
                    "The build relies too heavily on large box-like masses with too little silhouette refinement."));
        }
        if (classification == RequestClassification.VOLUMETRIC
                && voxelModel.size() >= 500
                && width >= 6
                && height >= 6
                && depth >= 6
                && utilization >= 0.68d
                && materials.size() <= 4) {
            diagnostics.add(new BuildDiagnostic(BuildDiagnostic.Severity.WARNING, "monolithic_mass",
                    "The build occupies a large share of its bounding box and reads as a monolithic mass."));
        }
        if (classification == RequestClassification.VOLUMETRIC
                && voxelModel.size() >= 250
                && detailBlockRatio < 0.035d
                && operationProfile.exactPlacementCount() < 12) {
            diagnostics.add(new BuildDiagnostic(BuildDiagnostic.Severity.WARNING, "low_fine_detail_density",
                    "The build has too little fine block-level detailing relative to its size."));
        }
        if (classification == RequestClassification.VOLUMETRIC
                && voxelModel.size() >= 250
                && frontLayeredColumns < Math.max(4, (frontXs.size() * frontYs.size()) / 14)
                && sideLayeredColumns < Math.max(4, (sideZs.size() * sideYs.size()) / 14)) {
            diagnostics.add(new BuildDiagnostic(BuildDiagnostic.Severity.WARNING, "shallow_elevations",
                    "Front, side, or rear elevations do not show enough depth variation or recess layering."));
        }

        Map<String, Object> machineReadable = new HashMap<>();
        machineReadable.put("occupiedCellCount", voxelModel.size());
        machineReadable.put("paletteSize", materials.size());
        machineReadable.put("dslOperationCount", program.operations().size() + program.components().stream().mapToInt(component -> component.operations().size()).sum());
        machineReadable.put("instanceCount", program.expandedInstanceCount());
        machineReadable.put("boundingBox", Map.of("min", List.of(min.x(), min.y(), min.z()), "max", List.of(max.x(), max.y(), max.z())));
        machineReadable.put("boundingBoxUtilization", utilization);
        machineReadable.put("connectedComponents", connectivity.componentCount);
        machineReadable.put("largestConnectedComponent", connectivity.largestComponentSize);
        machineReadable.put("groundContactCount", groundContactCount);
        machineReadable.put("groundContactRatio", groundRatio);
        machineReadable.put("unsupportedOccupiedCells", unsupported);
        machineReadable.put("frontProjection", Map.of("width", frontXs.size(), "height", frontYs.size()));
        machineReadable.put("sideProjection", Map.of("depth", sideZs.size(), "height", sideYs.size()));
        machineReadable.put("topProjection", Map.of("width", topXs.size(), "depth", topZs.size()));
        machineReadable.put("sparseSegmentCount", cuboids.size());
        machineReadable.put("compressionRatio", cuboids.isEmpty() ? 0.0d : voxelModel.size() / (double) cuboids.size());
        machineReadable.put("materialReplacementCount", materialReplacementCount);
        machineReadable.put("cutCellCount", cutCellCount);
        machineReadable.put("boxLikePrimitiveRatio", operationProfile.boxLikeRatio());
        machineReadable.put("expressivePrimitiveCount", operationProfile.expressivePrimitiveCount());
        machineReadable.put("exactPlacementCount", operationProfile.exactPlacementCount());
        machineReadable.put("exposedFaceCount", exposedFaces);
        machineReadable.put("exteriorBlockCount", exteriorBlocks);
        machineReadable.put("detailBlockCount", detailBlocks);
        machineReadable.put("detailBlockRatio", detailBlockRatio);
        machineReadable.put("frontLayeredColumns", frontLayeredColumns);
        machineReadable.put("sideLayeredColumns", sideLayeredColumns);
        machineReadable.put("requestClassification", classification.name().toLowerCase(Locale.ROOT));

        BuildMetrics metrics = new BuildMetrics(
                voxelModel.size(),
                materials.size(),
                (Integer) machineReadable.get("dslOperationCount"),
                program.expandedInstanceCount(),
                min,
                max,
                utilization,
                connectivity.componentCount,
                connectivity.largestComponentSize,
                groundContactCount,
                groundRatio,
                unsupported,
                frontXs.size(),
                frontYs.size(),
                sideZs.size(),
                sideYs.size(),
                topXs.size(),
                topZs.size(),
                cuboids.size(),
                cuboids.isEmpty() ? 0.0d : voxelModel.size() / (double) cuboids.size(),
                materialReplacementCount,
                cutCellCount,
                exposedFaces,
                exteriorBlocks,
                detailBlocks,
                detailBlockRatio,
                operationProfile.exactPlacementCount(),
                frontLayeredColumns,
                sideLayeredColumns,
                Map.copyOf(machineReadable)
        );
        return new ValidationResult(diagnostics, metrics);
    }

    private Connectivity computeConnectivity(VoxelModel voxelModel) {
        Set<Long> unvisited = new HashSet<>(voxelModel.asMap().keySet());
        int components = 0;
        int largest = 0;
        while (!unvisited.isEmpty()) {
            components++;
            long start = unvisited.iterator().next();
            int componentSize = 0;
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(start);
            unvisited.remove(start);
            while (!queue.isEmpty()) {
                long current = queue.removeFirst();
                componentSize++;
                IntVec3 point = VoxelModel.unpack(current);
                for (IntVec3 neighbor : neighbors(point)) {
                    long packed;
                    try {
                        packed = VoxelModel.pack(neighbor);
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    if (unvisited.remove(packed)) {
                        queue.add(packed);
                    }
                }
            }
            largest = Math.max(largest, componentSize);
        }
        return new Connectivity(components, largest);
    }

    private OperationProfile profileOperations(BuildProgram program) {
        int total = 0;
        int boxLike = 0;
        int expressive = 0;
        int exactPlacements = 0;
        for (PrimitiveOperation operation : allOperations(program)) {
            total++;
            if (operation.kind() == PrimitiveKind.BOX || operation.kind() == PrimitiveKind.RAW_CUBOID) {
                boxLike++;
            }
            if (operation.kind() == PrimitiveKind.POINT) {
                exactPlacements++;
            }
            if (operation.kind() == PrimitiveKind.POINT_SET) {
                exactPlacements += ((PointSetOperation) operation).points().size();
            }
            if (operation.kind() == PrimitiveKind.LINE
                    || operation.kind() == PrimitiveKind.PROFILE
                    || operation.kind() == PrimitiveKind.CYLINDER
                    || operation.kind() == PrimitiveKind.ELLIPSOID) {
                expressive++;
            }
        }
        return new OperationProfile(total, boxLike, expressive, exactPlacements);
    }

    private List<PrimitiveOperation> allOperations(BuildProgram program) {
        List<PrimitiveOperation> operations = new ArrayList<>(program.operations());
        for (ComponentDefinition component : program.components()) {
            operations.addAll(component.operations());
        }
        return operations;
    }

    private List<IntVec3> neighbors(IntVec3 point) {
        return List.of(
                new IntVec3(point.x() + 1, point.y(), point.z()),
                new IntVec3(point.x() - 1, point.y(), point.z()),
                new IntVec3(point.x(), point.y() + 1, point.z()),
                new IntVec3(point.x(), point.y() - 1, point.z()),
                new IntVec3(point.x(), point.y(), point.z() + 1),
                new IntVec3(point.x(), point.y(), point.z() - 1)
        );
    }

    private BuildMetrics emptyMetrics(BuildProgram program, List<SparseCuboid> cuboids, int replacements, int cuts) {
        return new BuildMetrics(
                0,
                0,
                program.operations().size() + program.components().stream().mapToInt(component -> component.operations().size()).sum(),
                program.expandedInstanceCount(),
                IntVec3.ZERO,
                IntVec3.ZERO,
                0.0d,
                0,
                0,
                0,
                0.0d,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                cuboids.size(),
                0.0d,
                replacements,
                cuts,
                0,
                0,
                0,
                0.0d,
                0,
                0,
                0,
                Map.<String, Object>of()
        );
    }

    private boolean isDetailBlock(String blockState) {
        String normalized = blockState == null ? "" : blockState.toLowerCase(Locale.ROOT);
        return normalized.contains("_stairs")
                || normalized.contains("_slab")
                || normalized.contains("_wall")
                || normalized.contains("_fence")
                || normalized.contains("_pane")
                || normalized.contains("trapdoor")
                || normalized.contains("lantern")
                || normalized.contains("chain")
                || normalized.contains("bars")
                || normalized.contains("button")
                || normalized.contains("door");
    }

    private boolean containsVoxel(VoxelModel voxelModel, IntVec3 point) {
        try {
            return voxelModel.contains(point);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private record Connectivity(int componentCount, int largestComponentSize) {
    }

    private record OperationProfile(int totalPrimitiveCount, int boxLikePrimitiveCount, int expressivePrimitiveCount,
                                    int exactPlacementCount) {
        private double boxLikeRatio() {
            return totalPrimitiveCount <= 0 ? 0.0d : boxLikePrimitiveCount / (double) totalPrimitiveCount;
        }
    }
}
