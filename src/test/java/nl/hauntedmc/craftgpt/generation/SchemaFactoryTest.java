package nl.hauntedmc.craftgpt.generation;

import nl.hauntedmc.craftgpt.generation.schema.SchemaFactory;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaFactoryTest {
    @Test
    void compiledSchemaUsesStrictCompatibleOperationVariants() {
        Map<String, Object> schema = SchemaFactory.compiledBuildProgramSchema(TestSupport.limits());

        assertFalse(containsKeyRecursively(schema, "oneOf"));

        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertEquals(List.of(2), ((Map<?, ?>) properties.get("v")).get("enum"));
        Map<?, ?> operations = (Map<?, ?>) properties.get("a");
        Map<?, ?> items = (Map<?, ?>) operations.get("items");
        List<?> anyOf = (List<?>) items.get("anyOf");
        assertTrue(anyOf.size() >= 10);

        Map<?, ?> instances = (Map<?, ?>) properties.get("i");
        Map<?, ?> instanceItems = (Map<?, ?>) instances.get("items");
        List<?> instanceAnyOf = (List<?>) instanceItems.get("anyOf");
        assertEquals(3, instanceAnyOf.size());
    }

    @Test
    void compiledSchemaRequiresAllObjectProperties() {
        Map<String, Object> schema = SchemaFactory.compiledBuildProgramSchema(TestSupport.limits());
        assertAllObjectSchemasRequireEveryProperty(schema);
    }

    @Test
    void compiledSchemaKeepsStrictPaletteIds() {
        Map<String, Object> schema = SchemaFactory.compiledBuildProgramSchema(TestSupport.limits());
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        Map<?, ?> palette = (Map<?, ?>) properties.get("p");
        Map<?, ?> paletteItem = (Map<?, ?>) palette.get("items");
        Map<?, ?> paletteProperties = (Map<?, ?>) paletteItem.get("properties");
        Map<?, ?> idSchema = (Map<?, ?>) paletteProperties.get("i");
        assertEquals("^[1-9A-Z]$", idSchema.get("pattern"));
    }

    @Test
    void planningAndCritiqueSchemasAreStrictObjects() {
        assertAllObjectSchemasRequireEveryProperty(SchemaFactory.designPlanSchema());
        assertAllObjectSchemasRequireEveryProperty(SchemaFactory.designCritiqueSchema());
    }

    private void assertAllObjectSchemasRequireEveryProperty(Object node) {
        if (node instanceof Map<?, ?> rawMap) {
            if ("object".equals(rawMap.get("type"))) {
                assertTrue(rawMap.get("properties") instanceof Map<?, ?>);
                assertTrue(rawMap.get("required") instanceof List<?>);
                Map<?, ?> properties = (Map<?, ?>) rawMap.get("properties");
                List<?> required = (List<?>) rawMap.get("required");
                assertEquals(Set.copyOf(properties.keySet()), Set.copyOf(required));
            }
            for (Object value : rawMap.values()) {
                assertAllObjectSchemasRequireEveryProperty(value);
            }
            return;
        }
        if (node instanceof Collection<?> collection) {
            for (Object item : collection) {
                assertAllObjectSchemasRequireEveryProperty(item);
            }
        }
    }

    private boolean containsKeyRecursively(Object node, String key) {
        if (node instanceof Map<?, ?> rawMap) {
            if (rawMap.containsKey(key)) {
                return true;
            }
            for (Object value : rawMap.values()) {
                if (containsKeyRecursively(value, key)) {
                    return true;
                }
            }
            return false;
        }
        if (node instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (containsKeyRecursively(item, key)) {
                    return true;
                }
            }
        }
        return false;
    }
}
