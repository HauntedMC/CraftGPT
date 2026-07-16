package nl.hauntedmc.craftgpt.generation;

import nl.hauntedmc.craftgpt.generation.compiled.BuildProgram;
import nl.hauntedmc.craftgpt.generation.compiled.BoxOperation;
import nl.hauntedmc.craftgpt.generation.compiled.compiler.CompileResult;
import nl.hauntedmc.craftgpt.generation.compiled.ComponentDefinition;
import nl.hauntedmc.craftgpt.generation.compiled.ComponentInstance;
import nl.hauntedmc.craftgpt.generation.compiled.CylinderOperation;
import nl.hauntedmc.craftgpt.generation.compiled.EllipsoidOperation;
import nl.hauntedmc.craftgpt.generation.compiled.LineOperation;
import nl.hauntedmc.craftgpt.generation.compiled.OperationMode;
import nl.hauntedmc.craftgpt.generation.compiled.PaletteEntry;
import nl.hauntedmc.craftgpt.generation.compiled.PointOperation;
import nl.hauntedmc.craftgpt.generation.compiled.PointSetOperation;
import nl.hauntedmc.craftgpt.generation.compiled.PrimitiveOperation;
import nl.hauntedmc.craftgpt.generation.compiled.ProfileOperation;
import nl.hauntedmc.craftgpt.generation.compiled.RawCuboidOperation;
import nl.hauntedmc.craftgpt.generation.compiled.compiler.VoxelCompiler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelCompilerTest {
    private final VoxelCompiler compiler = new VoxelCompiler();

    @Test
    void expandsInstancesWithTranslationRotationAndMirrors() {
        BuildProgram program = program(
                List.of(new ComponentDefinition("column", List.of(new RawCuboidOperation(
                        OperationMode.ADD, "1", new IntVec3(0, 0, 0), new IntVec3(0, 2, 1)
                )))),
                List.of(
                        new ComponentInstance("column", new IntVec3(2, 0, 2), nl.hauntedmc.craftgpt.generation.QuarterTurn.DEG_0, false, false,
                                IntVec3.ZERO, 1, IntVec3.ZERO, 1),
                        new ComponentInstance("column", new IntVec3(5, 0, 5), nl.hauntedmc.craftgpt.generation.QuarterTurn.DEG_90, true, false,
                                IntVec3.ZERO, 1, IntVec3.ZERO, 1)
                ),
                List.of()
        );

        CompileResult result = compiler.compile(program, TestSupport.context(12, 12, 12), TestSupport.paletteResolver());

        assertTrue(result.isSuccess());
        assertTrue(result.compiledBuild().voxelModel().contains(new IntVec3(2, 0, 2)));
        assertTrue(result.compiledBuild().voxelModel().contains(new IntVec3(2, 2, 3)));
        assertTrue(result.compiledBuild().voxelModel().contains(new IntVec3(5, 0, 5)));
    }

    @Test
    void voxelizesBoxesCylindersEllipsoidsLinesProfilesAndCuts() {
        BuildProgram program = program(
                List.of(),
                List.of(),
                List.of(
                        new BoxOperation(OperationMode.ADD, "1", new IntVec3(0, 0, 0), new IntVec3(2, 2, 2), false, 1),
                        new BoxOperation(OperationMode.CUT, null, new IntVec3(1, 1, 1), new IntVec3(1, 1, 1), false, 1),
                        new BoxOperation(OperationMode.ADD, "1", new IntVec3(4, 0, 0), new IntVec3(6, 2, 2), true, 1),
                        new CylinderOperation(OperationMode.ADD, "1", new IntVec3(8, 0, 2), 1, 3, Axis.Y, false, 1),
                        new CylinderOperation(OperationMode.ADD, "1", new IntVec3(2, 5, 2), 1, 3, Axis.X, true, 1),
                        new CylinderOperation(OperationMode.ADD, "1", new IntVec3(2, 8, 2), 1, 3, Axis.Z, false, 1),
                        new EllipsoidOperation(OperationMode.ADD, "1", new IntVec3(8, 6, 6), 1, 2, 1, false, 1),
                        new EllipsoidOperation(OperationMode.ADD, "1", new IntVec3(3, 6, 6), 2, 2, 2, true, 1),
                        new LineOperation(OperationMode.ADD, "1", new IntVec3(0, 9, 0), new IntVec3(4, 9, 4), 0),
                        new ProfileOperation(OperationMode.ADD, "1", new IntVec3(6, 9, 6), new IntVec3(8, 11, 6), Axis.Z, 2, 1)
                )
        );

        CompileResult result = compiler.compile(program, TestSupport.context(16, 16, 16), TestSupport.paletteResolver());

        assertTrue(result.isSuccess());
        assertFalse(result.compiledBuild().voxelModel().contains(new IntVec3(1, 1, 1)));
        assertTrue(result.compiledBuild().voxelModel().contains(new IntVec3(0, 0, 0)));
        assertTrue(result.compiledBuild().voxelModel().contains(new IntVec3(4, 0, 0)));
        assertTrue(result.compiledBuild().voxelModel().contains(new IntVec3(8, 1, 2)));
        assertTrue(result.compiledBuild().voxelModel().contains(new IntVec3(0, 9, 0)));
        assertTrue(result.compiledBuild().voxelModel().contains(new IntVec3(6, 9, 7)));
    }

    @Test
    void appliesLastWriteWinsForOverlappingMaterials() {
        BuildProgram program = new BuildProgram(
                2,
                IntVec3.ZERO,
                List.of(
                        new PaletteEntry("1", "minecraft:stone"),
                        new PaletteEntry("2", "minecraft:dirt")
                ),
                List.of(),
                List.of(),
                List.of(
                        new RawCuboidOperation(OperationMode.ADD, "1", new IntVec3(0, 0, 0), new IntVec3(1, 1, 1)),
                        new RawCuboidOperation(OperationMode.ADD, "2", new IntVec3(1, 1, 1), new IntVec3(1, 1, 1))
                )
        );

        CompileResult result = compiler.compile(program, TestSupport.context(4, 4, 4), TestSupport.paletteResolver());

        assertTrue(result.isSuccess());
        assertEquals("2", result.compiledBuild().voxelModel().get(new IntVec3(1, 1, 1)).id());
        assertEquals(1, result.compiledBuild().validationResult().metrics().materialReplacementCount());
    }

    @Test
    void rejectsUnknownComponentReferencesAndOutOfBounds() {
        BuildProgram missingComponent = program(
                List.of(),
                List.of(new ComponentInstance("missing", IntVec3.ZERO, nl.hauntedmc.craftgpt.generation.QuarterTurn.DEG_0, false, false,
                        IntVec3.ZERO, 1, IntVec3.ZERO, 1)),
                List.of()
        );
        assertFalse(compiler.compile(missingComponent, TestSupport.context(4, 4, 4), TestSupport.paletteResolver()).isSuccess());

        BuildProgram outOfBounds = program(
                List.of(),
                List.of(),
                List.of(new RawCuboidOperation(OperationMode.ADD, "1", new IntVec3(3, 3, 3), new IntVec3(4, 4, 4)))
        );
        CompileResult outOfBoundsResult = compiler.compile(outOfBounds, TestSupport.context(4, 4, 4), TestSupport.paletteResolver());
        assertFalse(outOfBoundsResult.isSuccess());
        assertTrue(outOfBoundsResult.failures().get(0).message().contains("outside local bounds"));
        assertTrue(outOfBoundsResult.failures().get(0).message().contains("[4,"));
        assertTrue(outOfBoundsResult.failures().get(0).message().contains("X: 0..3"));
    }

    @Test
    void enforcesOccupiedCellLimitAndInclusiveMaximumCoordinates() {
        GenerationContext tinyContext = new GenerationContext(new BuildVolume(4, 4, 4),
                new GenerationLimits(128, 2048, 4000, 512, 4, 50000, 4000),
                TestSupport.visualReviewDisabled(),
                RequestClassification.VOLUMETRIC,
                "positive Z",
                "1.21.11");

        BuildProgram tooLarge = program(List.of(), List.of(),
                List.of(new BoxOperation(OperationMode.ADD, "1", new IntVec3(0, 0, 0), new IntVec3(2, 1, 1), false, 1)));
        assertFalse(compiler.compile(tooLarge, tinyContext, TestSupport.paletteResolver()).isSuccess());

        BuildProgram maxCorner = program(List.of(), List.of(),
                List.of(new RawCuboidOperation(OperationMode.ADD, "1", new IntVec3(3, 3, 3), new IntVec3(3, 3, 3))));
        assertTrue(compiler.compile(maxCorner, TestSupport.context(4, 4, 4), TestSupport.paletteResolver()).isSuccess());
    }

    @Test
    void reportsMalformedPrimitiveDefinitionsClearly() {
        BuildProgram invalidProfile = program(List.of(), List.of(),
                List.of(new ProfileOperation(OperationMode.ADD, "1", new IntVec3(0, 0, 0), new IntVec3(1, 1, 1), Axis.Z, 2, 1)));

        CompileResult result = compiler.compile(invalidProfile, TestSupport.context(8, 8, 8), TestSupport.paletteResolver());

        assertFalse(result.isSuccess());
        assertEquals("malformed_build_program", result.failures().get(0).code());
    }

    @Test
    void expandsRepeatedInstanceRowsAndGrids() {
        BuildProgram program = program(
                List.of(new ComponentDefinition("post", List.of(new RawCuboidOperation(
                        OperationMode.ADD, "1", IntVec3.ZERO, IntVec3.ZERO
                )))),
                List.of(
                        new ComponentInstance("post", new IntVec3(1, 0, 1), nl.hauntedmc.craftgpt.generation.QuarterTurn.DEG_0, false, false,
                                new IntVec3(2, 0, 0), 3, new IntVec3(0, 0, 3), 2)
                ),
                List.of()
        );

        CompileResult result = compiler.compile(program, TestSupport.context(12, 8, 12), TestSupport.paletteResolver());

        assertTrue(result.isSuccess());
        assertEquals(6, result.compiledBuild().validationResult().metrics().instanceCount());
        assertTrue(result.compiledBuild().voxelModel().contains(new IntVec3(1, 0, 1)));
        assertTrue(result.compiledBuild().voxelModel().contains(new IntVec3(5, 0, 1)));
        assertTrue(result.compiledBuild().voxelModel().contains(new IntVec3(1, 0, 4)));
        assertTrue(result.compiledBuild().voxelModel().contains(new IntVec3(5, 0, 4)));
    }

    @Test
    void appliesExactPointPlacementsAndCuts() {
        BuildProgram program = new BuildProgram(
                2,
                IntVec3.ZERO,
                List.of(
                        new PaletteEntry("1", "minecraft:stone"),
                        new PaletteEntry("2", "minecraft:oak_stairs[facing=north]")
                ),
                List.of(),
                List.of(),
                List.of(
                        new PointSetOperation(OperationMode.ADD, "1", List.of(
                                new IntVec3(0, 0, 0),
                                new IntVec3(1, 0, 0),
                                new IntVec3(2, 0, 0)
                        )),
                        new PointOperation(OperationMode.ADD, "2", new IntVec3(1, 1, 0)),
                        new PointOperation(OperationMode.CUT, null, new IntVec3(1, 0, 0))
                )
        );

        CompileResult result = compiler.compile(program, TestSupport.context(8, 8, 8), TestSupport.paletteResolver());

        assertTrue(result.isSuccess());
        assertFalse(result.compiledBuild().voxelModel().contains(new IntVec3(1, 0, 0)));
        assertEquals("2", result.compiledBuild().voxelModel().get(new IntVec3(1, 1, 0)).id());
        assertTrue(result.compiledBuild().validationResult().metrics().exactPlacementCount() >= 4);
    }

    private BuildProgram program(List<ComponentDefinition> components, List<ComponentInstance> instances, List<PrimitiveOperation> operations) {
        return new BuildProgram(
                2,
                IntVec3.ZERO,
                List.of(new PaletteEntry("1", "minecraft:stone")),
                components,
                instances,
                operations
        );
    }
}
