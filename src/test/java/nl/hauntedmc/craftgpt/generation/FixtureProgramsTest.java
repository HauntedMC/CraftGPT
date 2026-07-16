package nl.hauntedmc.craftgpt.generation;

import nl.hauntedmc.craftgpt.generation.compiled.parser.BuildProgramParser;
import nl.hauntedmc.craftgpt.generation.compiled.compiler.VoxelCompiler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FixtureProgramsTest {
    private final BuildProgramParser parser = new BuildProgramParser();
    private final VoxelCompiler compiler = new VoxelCompiler();

    @ParameterizedTest
    @ValueSource(strings = {
            "fixtures/house.json",
            "fixtures/pavilion.json",
            "fixtures/tree.json",
            "fixtures/mechanical.json",
            "fixtures/separate-components.json",
            "fixtures/compact-repetition.json"
    })
    void fixtureProgramsParseAndCompile(String path) throws Exception {
        String json = readResource(path);
        var program = parser.parse(json, TestSupport.limits());
        assertTrue(compiler.compile(program, TestSupport.context(16, 16, 16), TestSupport.paletteResolver()).isSuccess(), path);
    }

    private String readResource(String path) throws IOException {
        try (var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing fixture " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
