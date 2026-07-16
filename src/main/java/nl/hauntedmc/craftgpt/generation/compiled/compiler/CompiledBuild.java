package nl.hauntedmc.craftgpt.generation.compiled.compiler;

import nl.hauntedmc.craftgpt.generation.compiled.*;

import java.util.List;
import java.util.Map;

public record CompiledBuild(
        BuildProgram program,
        Map<String, ResolvedPaletteEntry> paletteById,
        VoxelModel voxelModel,
        ValidationResult validationResult,
        List<SparseCuboid> sparseCuboids
) {
    public CompiledBuild {
        sparseCuboids = List.copyOf(sparseCuboids);
        paletteById = Map.copyOf(paletteById);
    }
}
