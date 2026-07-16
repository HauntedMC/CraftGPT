package nl.hauntedmc.craftgpt.generation.compiled.compiler;

import nl.hauntedmc.craftgpt.generation.BuildVolume;
import nl.hauntedmc.craftgpt.generation.GenerationContext;
import nl.hauntedmc.craftgpt.generation.IntVec3;
import nl.hauntedmc.craftgpt.generation.Transform;
import nl.hauntedmc.craftgpt.generation.compiled.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VoxelCompiler {
    private final PrimitiveVoxelizer primitiveVoxelizer = new PrimitiveVoxelizer();
    private final SparseCuboidCompressor compressor = new SparseCuboidCompressor();
    private final BuildValidator validator = new BuildValidator();

    public CompileResult compile(BuildProgram program, GenerationContext context, PaletteResolver paletteResolver) {
        List<BuildFailure> failures = new ArrayList<>();
        Map<String, ResolvedPaletteEntry> paletteById = resolvePalette(program, paletteResolver, failures);
        if (!failures.isEmpty()) {
            return new CompileResult(null, failures);
        }

        Map<String, ComponentDefinition> components = new LinkedHashMap<>();
        for (ComponentDefinition component : program.components()) {
            components.put(component.name(), component);
        }
        for (ComponentInstance instance : program.instances()) {
            if (!components.containsKey(instance.componentName())) {
                failures.add(new BuildFailure("unknown_component_reference",
                        "Unknown component reference: " + instance.componentName() + ".", true));
            }
        }
        if (!failures.isEmpty()) {
            return new CompileResult(null, failures);
        }

        VoxelModel voxelModel = new VoxelModel();
        Counters counters = new Counters();
        try {
            for (ComponentInstance instance : program.instances()) {
                for (int secondary = 0; secondary < instance.repeatCountSecondary(); secondary++) {
                    IntVec3 secondaryOffset;
                    try {
                        secondaryOffset = instance.repeatStepSecondary().multiply(secondary);
                    } catch (ArithmeticException e) {
                        throw new CompileRuntimeException(new CompileException("numeric_overflow", "Numeric overflow while expanding repeated instance offsets.", true));
                    }
                    for (int primary = 0; primary < instance.repeatCount(); primary++) {
                        IntVec3 primaryOffset;
                        IntVec3 placement;
                        try {
                            primaryOffset = instance.repeatStep().multiply(primary);
                            placement = instance.translation().add(primaryOffset).add(secondaryOffset);
                        } catch (ArithmeticException e) {
                            throw new CompileRuntimeException(new CompileException("numeric_overflow", "Numeric overflow while expanding repeated instance offsets.", true));
                        }
                        Transform transform = new Transform(
                                instance.mirrorX(),
                                instance.mirrorZ(),
                                instance.rotation(),
                                placement
                        );
                        for (PrimitiveOperation operation : components.get(instance.componentName()).operations()) {
                            applyOperation(operation, transform, paletteById, voxelModel, context.buildVolume(), context, counters);
                        }
                    }
                }
            }
            for (PrimitiveOperation operation : program.operations()) {
                applyOperation(operation, null, paletteById, voxelModel, context.buildVolume(), context, counters);
            }
        } catch (CompileRuntimeException e) {
            failures.add(new BuildFailure(e.compileException.code, e.compileException.getMessage(), e.compileException.repairable));
            return new CompileResult(null, failures);
        } catch (IllegalArgumentException e) {
            failures.add(new BuildFailure("numeric_overflow", e.getMessage(), true));
            return new CompileResult(null, failures);
        }

        List<SparseCuboid> cuboids = compressor.compress(voxelModel);
        if (cuboids.size() > context.limits().maxSparseSegmentsPerGeneration()) {
            failures.add(new BuildFailure("sparse_segment_limit_exceeded",
                    "Sparse cuboid compression exceeded max_sparse_segments_per_generation.", true));
            return new CompileResult(null, failures);
        }
        if (cuboids.size() > context.limits().maxTotalActionsPerGeneration()) {
            failures.add(new BuildFailure("max_total_actions_exceeded",
                    "Final WorldEdit action count exceeded max_total_actions_per_generation.", true));
            return new CompileResult(null, failures);
        }

        ValidationResult validationResult = validator.validate(
                program,
                voxelModel,
                cuboids,
                context.buildVolume(),
                context.classification(),
                counters.materialReplacements,
                counters.cutCells
        );
        if (validationResult.hasFatalErrors()) {
            validationResult.diagnostics().stream()
                    .filter(diagnostic -> diagnostic.severity() == BuildDiagnostic.Severity.FATAL)
                    .forEach(diagnostic -> failures.add(new BuildFailure(diagnostic.code(), diagnostic.message(), true)));
            return new CompileResult(null, failures);
        }

        return new CompileResult(new CompiledBuild(program, paletteById, voxelModel, validationResult, cuboids), List.of());
    }
    private Map<String, ResolvedPaletteEntry> resolvePalette(BuildProgram program, PaletteResolver paletteResolver, List<BuildFailure> failures) {
        Map<String, ResolvedPaletteEntry> resolved = new LinkedHashMap<>();
        for (PaletteEntry paletteEntry : program.paletteEntries()) {
            try {
                ResolvedBlock resolvedBlock = paletteResolver.resolve(paletteEntry.blockState());
                resolved.put(paletteEntry.id(), new ResolvedPaletteEntry(paletteEntry.id(), paletteEntry.blockState(), resolvedBlock));
            } catch (PaletteValidationException e) {
                failures.add(new BuildFailure("invalid_palette_entry",
                        "Invalid palette entry " + paletteEntry.id() + ": " + e.getMessage(), true));
            }
        }
        return resolved;
    }

    private void applyOperation(PrimitiveOperation operation, Transform transform, Map<String, ResolvedPaletteEntry> paletteById,
                                VoxelModel voxelModel, BuildVolume volume, GenerationContext context, Counters counters) {
        ResolvedPaletteEntry paletteEntry = null;
        if (operation.mode() == OperationMode.ADD) {
            paletteEntry = paletteById.get(operation.paletteId());
            if (paletteEntry == null) {
                throw new CompileRuntimeException(new CompileException(
                        "undefined_palette_id",
                        "Undefined palette ID: " + operation.paletteId() + ".",
                        true
                ));
            }
        }

        ResolvedPaletteEntry finalPaletteEntry = paletteEntry;
        try {
            primitiveVoxelizer.voxelize(operation, localPoint -> {
                IntVec3 transformedPoint;
                try {
                    transformedPoint = transform == null ? localPoint : transform.apply(localPoint);
                } catch (ArithmeticException e) {
                    throw new CompileRuntimeException(new CompileException("numeric_overflow", "Numeric overflow while transforming component coordinates.", true));
                }
                if (!volume.contains(transformedPoint)) {
                    if (context.ignoreOutOfBoundsPlacements()) {
                        return;
                    }
                    throw new CompileRuntimeException(new CompileException("coordinate_outside_selection",
                            "Generated coordinate [%d,%d,%d] is outside local bounds X: 0..%d, Y: 0..%d, Z: 0..%d. All coordinates and inclusive endpoints must stay inside those ranges."
                                    .formatted(
                                            transformedPoint.x(),
                                            transformedPoint.y(),
                                            transformedPoint.z(),
                                            volume.maxX(),
                                            volume.maxY(),
                                            volume.maxZ()
                                    ), true));
                }

                if (operation.mode() == OperationMode.CUT) {
                    if (voxelModel.remove(transformedPoint) != null) {
                        counters.cutCells++;
                    }
                    return;
                }

                ResolvedPaletteEntry replaced = voxelModel.put(transformedPoint, finalPaletteEntry);
                if (replaced != null && !replaced.equals(finalPaletteEntry)) {
                    counters.materialReplacements++;
                }
                if (voxelModel.size() > context.limits().maxMatrixBlocksPerGeneration()) {
                    throw new CompileRuntimeException(new CompileException("occupied_cell_limit_exceeded",
                            "Occupied-cell limit exceeded during compilation.", true));
                }
            });
        } catch (IllegalArgumentException e) {
            throw new CompileRuntimeException(new CompileException(
                    "malformed_build_program",
                    e.getMessage() == null ? "The build program contained an invalid primitive definition." : e.getMessage(),
                    true
            ));
        }
    }

    private static final class Counters {
        private int materialReplacements;
        private int cutCells;
    }

    private static final class CompileException extends Exception {
        private static final long serialVersionUID = 1L;

        private final String code;
        private final boolean repairable;

        private CompileException(String code, String message, boolean repairable) {
            super(message);
            this.code = code;
            this.repairable = repairable;
        }
    }

    private static final class CompileRuntimeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final CompileException compileException;

        private CompileRuntimeException(CompileException compileException) {
            super(compileException);
            this.compileException = compileException;
        }
    }
}
