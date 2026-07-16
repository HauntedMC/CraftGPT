package nl.hauntedmc.craftgpt.generation.compiled;

import nl.hauntedmc.craftgpt.generation.IntVec3;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record BuildProgram(
        int version,
        IntVec3 origin,
        List<PaletteEntry> paletteEntries,
        List<ComponentDefinition> components,
        List<ComponentInstance> instances,
        List<PrimitiveOperation> operations
) {
    public BuildProgram {
        Objects.requireNonNull(origin, "origin");
        paletteEntries = List.copyOf(paletteEntries);
        components = List.copyOf(components);
        instances = List.copyOf(instances);
        operations = List.copyOf(operations);
    }

    public Map<String, PaletteEntry> paletteById() {
        return paletteEntries.stream().collect(java.util.stream.Collectors.toMap(
                PaletteEntry::id,
                entry -> entry,
                (left, right) -> left,
                java.util.LinkedHashMap::new
        ));
    }

    public int expandedInstanceCount() {
        int total = 0;
        for (ComponentInstance instance : instances) {
            total = Math.addExact(total, instance.expandedPlacementCount());
        }
        return total;
    }
}
