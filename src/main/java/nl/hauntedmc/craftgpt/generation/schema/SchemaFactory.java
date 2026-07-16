package nl.hauntedmc.craftgpt.generation.schema;

import nl.hauntedmc.craftgpt.generation.GenerationLimits;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SchemaFactory {
    private SchemaFactory() {
    }

    public static Map<String, Object> compiledBuildProgramSchema(GenerationLimits limits) {
        // Keep this within the Structured Outputs subset accepted by the Responses API.
        // Java-side parsing and compilation enforce the stricter cross-field DSL rules.
        Map<String, Object> operation = map(
                "anyOf", List.of(
                        boxAddOperationSchema(),
                        boxCutOperationSchema(),
                        cylinderSchema(),
                        ellipsoidSchema(),
                        lineSchema(),
                        pointAddSchema(),
                        pointCutSchema(),
                        pointSetAddSchema(),
                        pointSetCutSchema(),
                        profileSchema(),
                        rawCuboidSchema()
                )
        );

        Map<String, Object> component = objectSchema(
                map(
                        "n", stringSchema("^[a-zA-Z0-9_-]{1,32}$", 1, 32),
                        "a", arraySchema(operation, 0, limits.maxDslOperationsPerComponent())
                ),
                List.of("n", "a")
        );
        Map<String, Object> instance = map(
                "anyOf", List.of(
                        simpleInstanceSchema(),
                        repeatedInstanceSchema(),
                        repeatedGridInstanceSchema()
                )
        );
        Map<String, Object> paletteEntry = objectSchema(
                map(
                        "i", stringSchema("^[1-9A-Z]$", 1, 1),
                        "b", stringSchema("^minecraft:[a-z0-9_]+(?:\\[[a-z0-9_=,]+\\])?$", 1, 128)
                ),
                List.of("i", "b")
        );

        return objectSchema(
                map(
                        "v", map("type", "integer", "enum", List.of(2)),
                        "o", vec3Schema(),
                        "p", arraySchema(paletteEntry, 1, 35),
                        "c", arraySchema(component, 0, limits.maxDslComponents()),
                        "i", arraySchema(instance, 0, limits.maxDslInstances()),
                        "a", arraySchema(operation, 0, limits.maxDslOperations())
                ),
                List.of("v", "o", "p", "c", "i", "a")
        );
    }

    public static Map<String, Object> designPlanSchema() {
        Map<String, Object> textItem = stringSchema(".{1,240}", 1, 240);
        Map<String, Object> paletteSuggestion = objectSchema(
                map(
                        "role", stringSchema(".{1,48}", 1, 48),
                        "block_state", stringSchema("^minecraft:[a-z0-9_]+(?:\\[[a-z0-9_=,]+\\])?$", 1, 128),
                        "reason", stringSchema(".{1,160}", 1, 160)
                ),
                List.of("role", "block_state", "reason")
        );
        return objectSchema(
                map(
                        "summary", stringSchema(".{1,240}", 1, 240),
                        "composition", arraySchema(textItem, 2, 8),
                        "palette_strategy", arraySchema(paletteSuggestion, 1, 8),
                        "detail_agenda", arraySchema(textItem, 2, 10),
                        "structural_rules", arraySchema(textItem, 1, 8),
                        "risk_checks", arraySchema(textItem, 1, 8),
                        "symmetry_strategy", stringSchema(".{1,120}", 1, 120)
                ),
                List.of("summary", "composition", "palette_strategy", "detail_agenda", "structural_rules", "risk_checks", "symmetry_strategy")
        );
    }

    public static Map<String, Object> designCritiqueSchema() {
        Map<String, Object> issue = objectSchema(
                map(
                        "severity", map("type", "string", "enum", List.of("low", "medium", "high")),
                        "category", map("type", "string", "enum", List.of(
                                "proportion", "silhouette", "depth", "support", "material", "detail", "coverage", "composition"
                        )),
                        "observation", stringSchema(".{1,240}", 1, 240),
                        "fix", stringSchema(".{1,240}", 1, 240)
                ),
                List.of("severity", "category", "observation", "fix")
        );
        return objectSchema(
                map(
                        "verdict", map("type", "string", "enum", List.of("accept", "refine")),
                        "summary", stringSchema(".{1,240}", 1, 240),
                        "issues", arraySchema(issue, 0, 8),
                        "focus_areas", arraySchema(stringSchema(".{1,160}", 1, 160), 0, 8)
                ),
                List.of("verdict", "summary", "issues", "focus_areas")
        );
    }

    private static Map<String, Object> boxAddOperationSchema() {
        return objectSchema(
                map(
                        "k", map("type", "string", "enum", List.of("box")),
                        "m", map("type", "string", "enum", List.of("add")),
                        "i", stringSchema("^[1-9A-Z]$", 1, 1),
                        "f", vec3Schema(),
                        "t", vec3Schema(),
                        "ho", map("type", "boolean"),
                        "wt", positiveIntegerSchema()
                ),
                List.of("k", "m", "i", "f", "t", "ho", "wt")
        );
    }

    private static Map<String, Object> boxCutOperationSchema() {
        return objectSchema(
                map(
                        "k", map("type", "string", "enum", List.of("box")),
                        "m", map("type", "string", "enum", List.of("cut")),
                        "f", vec3Schema(),
                        "t", vec3Schema(),
                        "ho", map("type", "boolean"),
                        "wt", positiveIntegerSchema()
                ),
                List.of("k", "m", "f", "t", "ho", "wt")
        );
    }

    private static Map<String, Object> cylinderSchema() {
        return objectSchema(
                map(
                        "k", map("type", "string", "enum", List.of("cyl")),
                        "m", map("type", "string", "enum", List.of("add")),
                        "i", stringSchema("^[1-9A-Z]$", 1, 1),
                        "c", vec3Schema(),
                        "r", positiveIntegerSchema(),
                        "h", positiveIntegerSchema(),
                        "ax", map("type", "string", "enum", List.of("x", "y", "z")),
                        "ho", map("type", "boolean"),
                        "wt", positiveIntegerSchema()
                ),
                List.of("k", "m", "i", "c", "r", "h", "ax", "ho", "wt")
        );
    }

    private static Map<String, Object> ellipsoidSchema() {
        return objectSchema(
                map(
                        "k", map("type", "string", "enum", List.of("ell")),
                        "m", map("type", "string", "enum", List.of("add")),
                        "i", stringSchema("^[1-9A-Z]$", 1, 1),
                        "c", vec3Schema(),
                        "rx", positiveIntegerSchema(),
                        "ry", positiveIntegerSchema(),
                        "rz", positiveIntegerSchema(),
                        "ho", map("type", "boolean"),
                        "wt", positiveIntegerSchema()
                ),
                List.of("k", "m", "i", "c", "rx", "ry", "rz", "ho", "wt")
        );
    }

    private static Map<String, Object> lineSchema() {
        return objectSchema(
                map(
                        "k", map("type", "string", "enum", List.of("line")),
                        "m", map("type", "string", "enum", List.of("add")),
                        "i", stringSchema("^[1-9A-Z]$", 1, 1),
                        "f", vec3Schema(),
                        "t", vec3Schema(),
                        "w", nonNegativeIntegerSchema()
                ),
                List.of("k", "m", "i", "f", "t", "w")
        );
    }

    private static Map<String, Object> pointAddSchema() {
        return objectSchema(
                map(
                        "k", map("type", "string", "enum", List.of("pt")),
                        "m", map("type", "string", "enum", List.of("add")),
                        "i", stringSchema("^[1-9A-Z]$", 1, 1),
                        "at", vec3Schema()
                ),
                List.of("k", "m", "i", "at")
        );
    }

    private static Map<String, Object> pointCutSchema() {
        return objectSchema(
                map(
                        "k", map("type", "string", "enum", List.of("pt")),
                        "m", map("type", "string", "enum", List.of("cut")),
                        "at", vec3Schema()
                ),
                List.of("k", "m", "at")
        );
    }

    private static Map<String, Object> pointSetAddSchema() {
        return objectSchema(
                map(
                        "k", map("type", "string", "enum", List.of("pts")),
                        "m", map("type", "string", "enum", List.of("add")),
                        "i", stringSchema("^[1-9A-Z]$", 1, 1),
                        "ps", arraySchema(vec3Schema(), 1, 512)
                ),
                List.of("k", "m", "i", "ps")
        );
    }

    private static Map<String, Object> pointSetCutSchema() {
        return objectSchema(
                map(
                        "k", map("type", "string", "enum", List.of("pts")),
                        "m", map("type", "string", "enum", List.of("cut")),
                        "ps", arraySchema(vec3Schema(), 1, 512)
                ),
                List.of("k", "m", "ps")
        );
    }

    private static Map<String, Object> profileSchema() {
        return objectSchema(
                map(
                        "k", map("type", "string", "enum", List.of("prof")),
                        "m", map("type", "string", "enum", List.of("add")),
                        "i", stringSchema("^[1-9A-Z]$", 1, 1),
                        "f", vec3Schema(),
                        "t", vec3Schema(),
                        "ax", map("type", "string", "enum", List.of("x", "y", "z")),
                        "d", positiveIntegerSchema(),
                        "wt", positiveIntegerSchema()
                ),
                List.of("k", "m", "i", "f", "t", "ax", "d", "wt")
        );
    }

    private static Map<String, Object> rawCuboidSchema() {
        return objectSchema(
                map(
                        "k", map("type", "string", "enum", List.of("seg")),
                        "m", map("type", "string", "enum", List.of("add")),
                        "i", stringSchema("^[1-9A-Z]$", 1, 1),
                        "f", vec3Schema(),
                        "t", vec3Schema()
                ),
                List.of("k", "m", "i", "f", "t")
        );
    }

    private static Map<String, Object> simpleInstanceSchema() {
        return objectSchema(
                map(
                        "c", stringSchema("^[a-zA-Z0-9_-]{1,32}$", 1, 32),
                        "at", vec3Schema(),
                        "r", map("type", "integer", "enum", List.of(0, 90, 180, 270)),
                        "mx", map("type", "boolean"),
                        "mz", map("type", "boolean")
                ),
                List.of("c", "at", "r", "mx", "mz")
        );
    }

    private static Map<String, Object> repeatedInstanceSchema() {
        return objectSchema(
                map(
                        "c", stringSchema("^[a-zA-Z0-9_-]{1,32}$", 1, 32),
                        "at", vec3Schema(),
                        "r", map("type", "integer", "enum", List.of(0, 90, 180, 270)),
                        "mx", map("type", "boolean"),
                        "mz", map("type", "boolean"),
                        "s", vec3Schema(),
                        "n", positiveIntegerSchema()
                ),
                List.of("c", "at", "r", "mx", "mz", "s", "n")
        );
    }

    private static Map<String, Object> repeatedGridInstanceSchema() {
        return objectSchema(
                map(
                        "c", stringSchema("^[a-zA-Z0-9_-]{1,32}$", 1, 32),
                        "at", vec3Schema(),
                        "r", map("type", "integer", "enum", List.of(0, 90, 180, 270)),
                        "mx", map("type", "boolean"),
                        "mz", map("type", "boolean"),
                        "s", vec3Schema(),
                        "n", positiveIntegerSchema(),
                        "s2", vec3Schema(),
                        "n2", positiveIntegerSchema()
                ),
                List.of("c", "at", "r", "mx", "mz", "s", "n", "s2", "n2")
        );
    }

    private static Map<String, Object> vec3Schema() {
        return map(
                "type", "array",
                "minItems", 3,
                "maxItems", 3,
                "items", map("type", "integer")
        );
    }

    private static Map<String, Object> positiveIntegerSchema() {
        return map("type", "integer", "minimum", 1);
    }

    private static Map<String, Object> nonNegativeIntegerSchema() {
        return map("type", "integer", "minimum", 0);
    }

    private static Map<String, Object> stringSchema(String pattern, int minLength, int maxLength) {
        return map(
                "type", "string",
                "minLength", minLength,
                "maxLength", maxLength,
                "pattern", pattern
        );
    }

    private static Map<String, Object> arraySchema(Map<String, Object> items, int minItems, int maxItems) {
        return map(
                "type", "array",
                "minItems", minItems,
                "maxItems", maxItems,
                "items", items
        );
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return map(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", required
        );
    }

    private static Map<String, Object> map(Object... values) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}
