package nl.hauntedmc.craftgpt.generation;

import nl.hauntedmc.craftgpt.generation.compiled.BuildProgram;
import nl.hauntedmc.craftgpt.generation.compiled.parser.BuildProgramParseException;
import nl.hauntedmc.craftgpt.generation.compiled.parser.BuildProgramParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildProgramParserTest {
    private final BuildProgramParser parser = new BuildProgramParser();

    @Test
    void parsesStrictBuildProgram() throws Exception {
        String json = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[{"n":"column","a":[{"k":"box","m":"add","i":"1","f":[0,0,0],"t":[0,3,0]}]}],"i":[{"c":"column","at":[2,0,2],"r":90,"mx":false,"mz":true}],"a":[{"k":"seg","m":"add","i":"1","f":[4,0,4],"t":[4,1,4]}]}
                """;

        BuildProgram program = parser.parse(json, TestSupport.limits());

        assertEquals(2, program.version());
        assertEquals(1, program.paletteEntries().size());
        assertEquals(1, program.components().size());
        assertEquals(1, program.instances().size());
        assertEquals(1, program.operations().size());
    }

    @Test
    void rejectsDuplicatePaletteIds() {
        String json = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"},{"i":"1","b":"minecraft:dirt"}],"c":[],"i":[],"a":[]}
                """;
        assertThrows(BuildProgramParseException.class, () -> parser.parse(json, TestSupport.limits()));
    }

    @Test
    void rejectsUnknownFields() {
        String json = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[],"a":[],"x":1}
                """;
        assertThrows(BuildProgramParseException.class, () -> parser.parse(json, TestSupport.limits()));
    }

    @Test
    void rejectsInvalidRotation() {
        String json = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[{"c":"a","at":[0,0,0],"r":45,"mx":false,"mz":false}],"a":[]}
                """;
        assertThrows(BuildProgramParseException.class, () -> parser.parse(json, TestSupport.limits()));
    }

    @Test
    void enforcesOperationLimit() {
        String json = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[],"a":[{"k":"seg","m":"add","i":"1","f":[0,0,0],"t":[0,0,0]},{"k":"seg","m":"add","i":"1","f":[1,0,0],"t":[1,0,0]}]}
                """;
        GenerationLimits limits = new GenerationLimits(128, 2048, 1, 512, 50000, 50000, 4000);
        assertThrows(BuildProgramParseException.class, () -> parser.parse(json, limits));
    }

    @Test
    void enforcesInstanceLimit() {
        String json = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[{"c":"a","at":[0,0,0],"r":0,"mx":false,"mz":false},{"c":"a","at":[1,0,0],"r":0,"mx":false,"mz":false}],"a":[]}
                """;
        GenerationLimits limits = new GenerationLimits(128, 1, 4000, 512, 50000, 50000, 4000);
        assertThrows(BuildProgramParseException.class, () -> parser.parse(json, limits));
    }

    @Test
    void rejectsNonPositivePrimitiveDimensions() {
        String json = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[],"a":[{"k":"cyl","m":"add","i":"1","c":[0,0,0],"r":0,"h":3,"ax":"y"}]}
                """;
        assertThrows(BuildProgramParseException.class, () -> parser.parse(json, TestSupport.limits()));
    }

    @Test
    void rejectsInvalidProfilePlane() {
        String json = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[],"a":[{"k":"prof","m":"add","i":"1","f":[0,0,0],"t":[1,1,1],"ax":"z","d":2}]}
                """;
        BuildProgramParseException exception = assertThrows(BuildProgramParseException.class,
                () -> parser.parse(json, TestSupport.limits()));
        assertTrue(exception.getMessage().contains("ax:'z' requires f.z == t.z"));
        assertTrue(exception.getMessage().contains("use box, line, cyl, ell, or seg instead of prof"));
    }

    @Test
    void parsesRepeatedInstanceGridCompactly() throws Exception {
        String json = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[{"c":"post","at":[1,0,1],"r":0,"mx":false,"mz":false,"s":[2,0,0],"n":3,"s2":[0,0,3],"n2":2}],"a":[]}
                """;

        BuildProgram program = parser.parse(json, TestSupport.limits());

        assertEquals(1, program.instances().size());
        assertEquals(6, program.expandedInstanceCount());
        assertEquals(new IntVec3(2, 0, 0), program.instances().get(0).repeatStep());
        assertEquals(3, program.instances().get(0).repeatCount());
        assertEquals(new IntVec3(0, 0, 3), program.instances().get(0).repeatStepSecondary());
        assertEquals(2, program.instances().get(0).repeatCountSecondary());
    }

    @Test
    void parsesDslV2ExactPointOperations() throws Exception {
        String json = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"},{"i":"2","b":"minecraft:oak_stairs[facing=north]"}],"c":[],"i":[],"a":[
                  {"k":"pt","m":"add","i":"1","at":[1,2,3]},
                  {"k":"pts","m":"add","i":"2","ps":[[0,0,0],[1,0,0],[2,0,0]]},
                  {"k":"pt","m":"cut","at":[1,0,0]}
                ]}
                """;

        BuildProgram program = parser.parse(json, TestSupport.limits());

        assertEquals(2, program.version());
        assertEquals(3, program.operations().size());
    }

    @Test
    void rejectsIncompleteOrDegenerateRepeatedInstanceFields() {
        String missingCount = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[{"c":"post","at":[0,0,0],"r":0,"mx":false,"mz":false,"s":[2,0,0]}],"a":[]}
                """;
        String zeroStep = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[{"c":"post","at":[0,0,0],"r":0,"mx":false,"mz":false,"s":[0,0,0],"n":2}],"a":[]}
                """;
        String orphanSecondary = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[{"c":"post","at":[0,0,0],"r":0,"mx":false,"mz":false,"s2":[0,0,2],"n2":2}],"a":[]}
                """;

        assertThrows(BuildProgramParseException.class, () -> parser.parse(missingCount, TestSupport.limits()));
        assertThrows(BuildProgramParseException.class, () -> parser.parse(zeroStep, TestSupport.limits()));
        assertThrows(BuildProgramParseException.class, () -> parser.parse(orphanSecondary, TestSupport.limits()));
    }

    @Test
    void enforcesExpandedRepeatedInstanceLimit() {
        String json = """
                {"v":2,"o":[0,0,0],"p":[{"i":"1","b":"minecraft:stone"}],"c":[],"i":[{"c":"post","at":[0,0,0],"r":0,"mx":false,"mz":false,"s":[1,0,0],"n":3,"s2":[0,0,1],"n2":2}],"a":[]}
                """;
        GenerationLimits limits = new GenerationLimits(128, 5, 4000, 512, 50000, 50000, 4000);

        assertThrows(BuildProgramParseException.class, () -> parser.parse(json, limits));
    }
}
