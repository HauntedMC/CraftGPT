package nl.hauntedmc.craftgpt.util;

import nl.hauntedmc.craftgpt.generation.BuildVolume;
import nl.hauntedmc.craftgpt.generation.IntVec3;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PromptTemplateResolver {
    private PromptTemplateResolver() {
    }

    public static Map<String, String> variables(BuildVolume buildVolume, IntVec3 selectionMinWorld, IntVec3 selectionMaxWorld,
                                                String minecraftVersion, String frontDirection, int maxOccupiedBlocks,
                                                int maxComponents, int maxInstances, int maxOperations, int maxOperationsPerComponent,
                                                Map<String, String> customVariables) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("%WIDTH%", String.valueOf(buildVolume.width()));
        variables.put("%HEIGHT%", String.valueOf(buildVolume.height()));
        variables.put("%DEPTH%", String.valueOf(buildVolume.depth()));
        variables.put("%WIDTH_MINUS_ONE%", String.valueOf(buildVolume.maxX()));
        variables.put("%HEIGHT_MINUS_ONE%", String.valueOf(buildVolume.maxY()));
        variables.put("%DEPTH_MINUS_ONE%", String.valueOf(buildVolume.maxZ()));
        variables.put("%FOOTPRINT%", String.valueOf(buildVolume.footprint()));
        variables.put("%VOLUME%", String.valueOf(buildVolume.volume()));
        variables.put("%MAX_OCCUPIED_BLOCKS%", String.valueOf(maxOccupiedBlocks));
        variables.put("%MAX_DSL_COMPONENTS%", String.valueOf(maxComponents));
        variables.put("%MAX_DSL_INSTANCES%", String.valueOf(maxInstances));
        variables.put("%MAX_DSL_OPERATIONS%", String.valueOf(maxOperations));
        variables.put("%MAX_OPERATIONS_PER_COMPONENT%", String.valueOf(maxOperationsPerComponent));
        variables.put("%MINECRAFT_VERSION%", minecraftVersion);
        variables.put("%FRONT_DIRECTION%", frontDirection);
        variables.put("%X1%", "0");
        variables.put("%Y1%", "0");
        variables.put("%Z1%", "0");
        variables.put("%X2%", String.valueOf(buildVolume.maxX()));
        variables.put("%Y2%", String.valueOf(buildVolume.maxY()));
        variables.put("%Z2%", String.valueOf(buildVolume.maxZ()));
        variables.putAll(customVariables);
        return variables;
    }

    public static String resolve(String template, Map<String, String> variables) {
        String result = template == null ? "" : template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
