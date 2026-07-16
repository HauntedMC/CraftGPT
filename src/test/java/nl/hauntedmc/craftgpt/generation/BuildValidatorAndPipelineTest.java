package nl.hauntedmc.craftgpt.generation;

import nl.hauntedmc.craftgpt.ai.AiResponse;
import nl.hauntedmc.craftgpt.generation.compiled.parser.BuildProgramParser;
import nl.hauntedmc.craftgpt.generation.compiled.pipeline.CompiledBuildGenerationPipeline;
import nl.hauntedmc.craftgpt.generation.compiled.compiler.VoxelCompiler;
import nl.hauntedmc.craftgpt.generation.preview.PreviewRenderer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildValidatorAndPipelineTest {
    @Test
    void warnsOnCoarseBoxDominatedLargeBuilds() {
        String json = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[],"a":[
                  {"k":"box","m":"add","i":"1","f":[0,0,0],"t":[7,7,7]}
                ]}
                """;
        CompiledBuildGenerationPipeline pipeline = new CompiledBuildGenerationPipeline(
                new TestSupport.SequenceAiClient(List.of(AiResponse.success(json, json))),
                new BuildProgramParser(),
                new VoxelCompiler(),
                TestSupport.paletteResolver(),
                new PreviewRenderer()
        );

        PipelineResult result = pipeline.generate(TestSupport.pipelineRequest(TestSupport.context(8, 8, 8), false, 1, 0, "medieval tower"));

        assertTrue(result.success());
        assertTrue(result.validationResult().diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("coarse_box_dominated_geometry")
                        || diagnostic.code().equals("monolithic_mass")));
    }

    @Test
    void repairsWarningDrivenBuildsWithoutPreviewImages() {
        String coarse = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[],"a":[
                  {"k":"box","m":"add","i":"1","f":[0,0,0],"t":[7,7,7],"ho":false,"wt":1}
                ]}
                """;
        String repaired = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone_bricks"},{"i":"2","b":"minecraft:stone_brick_stairs[facing=north]"}],"c":[],"i":[],"a":[
                  {"k":"box","m":"add","i":"1","f":[1,0,1],"t":[6,5,6],"ho":true,"wt":1},
                  {"k":"box","m":"add","i":"2","f":[0,6,0],"t":[7,6,7],"ho":false,"wt":1}
                ]}
                """;
        TestSupport.SequenceAiClient aiClient = new TestSupport.SequenceAiClient(
                List.of(AiResponse.success(coarse, coarse), AiResponse.success(repaired, repaired))
        );
        CompiledBuildGenerationPipeline pipeline = new CompiledBuildGenerationPipeline(
                aiClient,
                new BuildProgramParser(),
                new VoxelCompiler(),
                TestSupport.paletteResolver(),
                new PreviewRenderer()
        );

        PipelineResult result = pipeline.generate(TestSupport.pipelineRequest(
                TestSupport.context(8, 8, 8),
                false,
                1,
                1,
                true,
                false,
                "highly detailed observatory"
        ));

        assertTrue(result.success());
        assertTrue(result.repaired());
        assertEquals(2, aiClient.requests().size());
        assertTrue(aiClient.requests().get(1).images().isEmpty());
    }

    @Test
    void computesConnectivityProjectionAndGroundMetrics() {
        String json = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[],"a":[
                  {"k":"seg","m":"add","i":"1","f":[0,0,0],"t":[0,0,0]},
                  {"k":"seg","m":"add","i":"1","f":[3,3,0],"t":[3,3,0]}
                ]}
                """;
        CompiledBuildGenerationPipeline pipeline = new CompiledBuildGenerationPipeline(
                new TestSupport.SequenceAiClient(List.of(AiResponse.success(json, json))),
                new BuildProgramParser(),
                new VoxelCompiler(),
                TestSupport.paletteResolver(),
                new PreviewRenderer()
        );

        PipelineResult result = pipeline.generate(TestSupport.pipelineRequest(TestSupport.context(6, 6, 6), false, 1, 0, "two stones"));

        assertTrue(result.success());
        assertEquals(2, result.validationResult().metrics().connectedComponents());
        assertEquals(1, result.validationResult().metrics().groundContactCount());
        assertTrue(result.validationResult().metrics().unsupportedOccupiedCells() >= 1);
        assertTrue(result.validationResult().metrics().frontProjectionWidth() >= 1);
    }

    @Test
    void runsFullCompiledPipelineWithOneSuccessfulResponse() {
        String json = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[{"n":"pillar","a":[{"k":"box","m":"add","i":"1","f":[0,0,0],"t":[0,2,0],"ho":false,"wt":1}]}],"i":[{"c":"pillar","at":[0,0,0],"r":0,"mx":false,"mz":false},{"c":"pillar","at":[2,0,0],"r":0,"mx":false,"mz":false}],"a":[{"k":"line","m":"add","i":"1","f":[0,2,0],"t":[2,2,0],"w":0}]}
                """;
        CompiledBuildGenerationPipeline pipeline = new CompiledBuildGenerationPipeline(
                new TestSupport.SequenceAiClient(List.of(AiResponse.success(json, json))),
                new BuildProgramParser(),
                new VoxelCompiler(),
                TestSupport.paletteResolver(),
                new PreviewRenderer()
        );

        PipelineResult result = pipeline.generate(TestSupport.pipelineRequest(TestSupport.context(6, 6, 6), false, 1, 0, "small gate"));

        assertTrue(result.success());
        assertFalse(result.repaired());
        assertFalse(result.cuboids().isEmpty());
    }

    @Test
    void doesNotTriggerVisualRepairForValidLargeBuildsByDefault() {
        String json = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"},{"i":"2","b":"minecraft:glass_pane"},{"i":"3","b":"minecraft:dark_oak_planks"},{"i":"4","b":"minecraft:oak_stairs"}],"c":[],"i":[],"a":[
                  {"k":"box","m":"add","i":"1","f":[0,0,0],"t":[15,0,15],"ho":false,"wt":1},
                  {"k":"box","m":"add","i":"3","f":[0,1,0],"t":[0,6,15],"ho":false,"wt":1},
                  {"k":"box","m":"add","i":"3","f":[15,1,0],"t":[15,6,15],"ho":false,"wt":1},
                  {"k":"box","m":"add","i":"3","f":[1,1,0],"t":[14,6,0],"ho":false,"wt":1},
                  {"k":"box","m":"add","i":"3","f":[1,1,15],"t":[14,6,15],"ho":false,"wt":1},
                  {"k":"box","m":"add","i":"4","f":[0,7,0],"t":[15,7,15],"ho":false,"wt":1},
                  {"k":"box","m":"add","i":"2","f":[4,2,0],"t":[5,4,0],"ho":false,"wt":1},
                  {"k":"box","m":"add","i":"2","f":[10,2,0],"t":[11,4,0],"ho":false,"wt":1},
                  {"k":"box","m":"add","i":"2","f":[4,2,15],"t":[5,4,15],"ho":false,"wt":1},
                  {"k":"box","m":"add","i":"2","f":[10,2,15],"t":[11,4,15],"ho":false,"wt":1}
                ]}
                """;
        TestSupport.SequenceAiClient aiClient = new TestSupport.SequenceAiClient(List.of(AiResponse.success(json, json)));
        CompiledBuildGenerationPipeline pipeline = new CompiledBuildGenerationPipeline(
                aiClient,
                new BuildProgramParser(),
                new VoxelCompiler(),
                TestSupport.paletteResolver(),
                new PreviewRenderer()
        );
        GenerationContext context = new GenerationContext(new BuildVolume(16, 8, 16), TestSupport.limits(),
                new VisualReviewSettings(true, 250, 128, false, ""),
                RequestClassification.VOLUMETRIC, "positive Z", "1.21.11");

        PipelineResult result = pipeline.generate(TestSupport.pipelineRequest(context, false, 1, 1, "compact house"));

        assertTrue(result.success());
        assertFalse(result.repaired());
        assertEquals(1, aiClient.requests().size());
    }

    @Test
    void repairsFirstInvalidCompiledResponseWithSecondValidResponse() {
        String invalid = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[],"a":[{"k":"seg","m":"add","i":"9","f":[0,0,0],"t":[0,0,0]}]}
                """;
        String repaired = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[],"a":[{"k":"seg","m":"add","i":"1","f":[0,0,0],"t":[1,0,0]}]}
                """;
        GenerationContext context = new GenerationContext(new BuildVolume(4, 4, 4), TestSupport.limits(),
                new VisualReviewSettings(true, 1, 128, false, ""),
                RequestClassification.VOLUMETRIC, "positive Z", "1.21.11");
        CompiledBuildGenerationPipeline pipeline = new CompiledBuildGenerationPipeline(
                new TestSupport.SequenceAiClient(List.of(AiResponse.success(invalid, invalid), AiResponse.success(repaired, repaired))),
                new BuildProgramParser(),
                new VoxelCompiler(),
                TestSupport.paletteResolver(),
                new PreviewRenderer()
        );

        PipelineResult result = pipeline.generate(TestSupport.pipelineRequest(context, false, 1, 1, "repair this"));

        assertTrue(result.success());
        assertTrue(result.repaired());
        assertEquals(2, result.validationResult().metrics().occupiedCellCount());
    }

    @Test
    void retriesOutOfBoundsGenerationWithCompilerFeedbackBeforeFailing() {
        String outOfBounds = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[],"a":[{"k":"seg","m":"add","i":"1","f":[4,0,0],"t":[4,0,0]}]}
                """;
        String corrected = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[],"a":[{"k":"seg","m":"add","i":"1","f":[3,0,0],"t":[3,0,0]}]}
                """;
        TestSupport.SequenceAiClient aiClient = new TestSupport.SequenceAiClient(
                List.of(AiResponse.success(outOfBounds, outOfBounds), AiResponse.success(corrected, corrected))
        );
        CompiledBuildGenerationPipeline pipeline = new CompiledBuildGenerationPipeline(
                aiClient,
                new BuildProgramParser(),
                new VoxelCompiler(),
                TestSupport.paletteResolver(),
                new PreviewRenderer()
        );

        PipelineResult result = pipeline.generate(TestSupport.pipelineRequest(TestSupport.context(4, 4, 4), false, 2, 0, "small statue"));

        assertTrue(result.success());
        assertFalse(result.repaired());
        assertEquals(outOfBounds, result.initialModelOutput());
        assertEquals(corrected, result.finalModelOutput());
        assertEquals(2, aiClient.requests().size());
        assertTrue(aiClient.requests().get(1).instructions().contains("Previous attempt was invalid."));
        assertTrue(aiClient.requests().get(1).instructions().contains("X: 0 through 3"));
        assertTrue(aiClient.requests().get(1).instructions().contains("largest valid local coordinates are exactly [3,3,3]"));
        assertTrue(aiClient.requests().get(1).instructions().contains("coordinate_outside_selection"));
        assertTrue(aiClient.requests().get(1).instructions().contains("[4,0,0]"));
    }

    @Test
    void rendersHeadlessPreviewsAndCleansUpTemporaryFiles() throws Exception {
        String json = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[],"a":[{"k":"box","m":"add","i":"1","f":[0,0,0],"t":[1,1,1],"ho":false,"wt":1}]}
                """;
        CompiledBuildGenerationPipeline pipeline = new CompiledBuildGenerationPipeline(
                new TestSupport.SequenceAiClient(List.of(AiResponse.success(json, json), AiResponse.success(json, json))),
                new BuildProgramParser(),
                new VoxelCompiler(),
                TestSupport.paletteResolver(),
                new PreviewRenderer()
        );
        GenerationContext context = new GenerationContext(new BuildVolume(4, 4, 4), TestSupport.limits(),
                new VisualReviewSettings(true, 1, 128, false, ""),
                RequestClassification.VOLUMETRIC, "positive Z", "1.21.11");

        PipelineResult result = pipeline.generate(TestSupport.pipelineRequest(context, false, 1, 1, "render"));

        assertTrue(result.success());
        assertNotNull(result.finalModelOutput());

        PreviewRenderer renderer = new PreviewRenderer();
        java.nio.file.Path frontFile;
        try (var previews = renderer.render(new VoxelCompiler()
                .compile(new BuildProgramParser().parse(json, TestSupport.limits()), context, TestSupport.paletteResolver())
                .compiledBuild()
                .voxelModel(), 128, false, "")) {
            frontFile = previews.file(nl.hauntedmc.craftgpt.generation.preview.PreviewPerspective.FRONT);
            assertTrue(Files.exists(frontFile));
        }
        assertFalse(Files.exists(frontFile));
    }

    @Test
    void runsPlanningAndExtendedPreviewCritiqueWorkflow() {
        String plan = """
                {"summary":"compact observatory with layered roofline","composition":["strong central mass with grounded base","detailed roof silhouette with exact trim"],"palette_strategy":[{"role":"main shell","block_state":"minecraft:stone_bricks","reason":"stable structural mass"},{"role":"roof trim","block_state":"minecraft:oak_stairs[facing=north]","reason":"sharper eaves and silhouette"}],"detail_agenda":["recess the walls","use exact trim for the roof edge"],"structural_rules":["keep the mass grounded","support overhangs"],"risk_checks":["avoid flat rear elevation","avoid monolithic roof slab"],"symmetry_strategy":"mostly mirrored with small roof asymmetry"}
                """;
        String coarse = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone_bricks"}],"c":[],"i":[],"a":[
                  {"k":"box","m":"add","i":"1","f":[1,0,1],"t":[8,6,8],"ho":false,"wt":1}
                ]}
                """;
        String critique = """
                {"verdict":"refine","summary":"The shell is valid but too flat and monolithic.","issues":[{"severity":"high","category":"detail","observation":"The roof and wall edges lack crafted trim.","fix":"Use exact stair placements and recess cuts to articulate the visible perimeter."}],"focus_areas":["roof edge trim","wall recess depth"]}
                """;
        String repaired = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone_bricks"},{"i":"2","b":"minecraft:oak_stairs[facing=north]"},{"i":"3","b":"minecraft:oak_stairs[facing=south]"}],"c":[],"i":[],"a":[
                  {"k":"box","m":"add","i":"1","f":[1,0,1],"t":[8,5,8],"ho":true,"wt":1},
                  {"k":"box","m":"cut","f":[2,2,1],"t":[7,3,1],"ho":false,"wt":1},
                  {"k":"pts","m":"add","i":"2","ps":[[1,6,1],[2,6,1],[3,6,1],[4,6,1],[5,6,1],[6,6,1],[7,6,1],[8,6,1]]},
                  {"k":"pts","m":"add","i":"3","ps":[[1,6,8],[2,6,8],[3,6,8],[4,6,8],[5,6,8],[6,6,8],[7,6,8],[8,6,8]]}
                ]}
                """;
        String accept = """
                {"verdict":"accept","summary":"The compiled result now has clear roof trim and better wall articulation.","issues":[],"focus_areas":[]}
                """;

        TestSupport.SequenceAiClient aiClient = new TestSupport.SequenceAiClient(List.of(
                AiResponse.success(plan, plan),
                AiResponse.success(coarse, coarse),
                AiResponse.success(critique, critique),
                AiResponse.success(repaired, repaired),
                AiResponse.success(accept, accept)
        ));
        CompiledBuildGenerationPipeline pipeline = new CompiledBuildGenerationPipeline(
                aiClient,
                new BuildProgramParser(),
                new VoxelCompiler(),
                TestSupport.paletteResolver(),
                new PreviewRenderer()
        );
        GenerationContext context = new GenerationContext(new BuildVolume(10, 10, 10), TestSupport.limits(),
                new VisualReviewSettings(true, 1, 128, false, ""),
                RequestClassification.VOLUMETRIC, "positive Z", "1.21.11");
        PipelineRequest request = new PipelineRequest(
                TestSupport.preset(),
                context,
                TestSupport.maximumQualityWorkflow(),
                "observatory",
                "system prompt",
                30,
                1,
                1,
                false,
                false,
                false,
                2000,
                java.util.Map.of()
        );

        PipelineResult result = pipeline.generate(request);

        assertTrue(result.success());
        assertTrue(result.repaired());
        assertEquals(5, aiClient.requests().size());
        assertEquals("design_plan_v1", aiClient.requests().get(0).schemaName());
        assertEquals(6, aiClient.requests().get(2).images().size());
        assertEquals(6, aiClient.requests().get(3).images().size());
    }

    @Test
    void ignoresMalformedDesignPlanJsonInsteadOfCrashingAsyncGeneration() {
        String malformedPlan = "{\"summary\":\"broken";
        String build = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:red_wool"}],"c":[],"i":[],"a":[
                  {"k":"pt","m":"add","i":"1","at":[1,1,0]},
                  {"k":"pt","m":"add","i":"1","at":[0,0,0]}
                ]}
                """;
        String accept = """
                {"verdict":"accept","summary":"The result is acceptable.","issues":[],"focus_areas":[]}
                """;

        TestSupport.SequenceAiClient aiClient = new TestSupport.SequenceAiClient(List.of(
                AiResponse.success(malformedPlan, malformedPlan),
                AiResponse.success(build, build),
                AiResponse.success(accept, accept)
        ));
        CompiledBuildGenerationPipeline pipeline = new CompiledBuildGenerationPipeline(
                aiClient,
                new BuildProgramParser(),
                new VoxelCompiler(),
                TestSupport.paletteResolver(),
                new PreviewRenderer()
        );
        GenerationContext context = new GenerationContext(new BuildVolume(4, 4, 1), TestSupport.limits(),
                TestSupport.visualReviewDisabled(),
                RequestClassification.FLAT_ART, "positive Z", "1.21.11");
        PipelineRequest request = new PipelineRequest(
                TestSupport.preset(),
                context,
                TestSupport.balancedWorkflow(),
                "a red heart",
                "system prompt",
                30,
                1,
                0,
                false,
                false,
                false,
                2000,
                java.util.Map.of()
        );

        PipelineResult result = pipeline.generate(request);

        assertTrue(result.success());
        assertEquals(3, aiClient.requests().size());
        assertEquals("design_plan_v1", aiClient.requests().get(0).schemaName());
        assertEquals("build_program_v2", aiClient.requests().get(1).schemaName());
    }
}
