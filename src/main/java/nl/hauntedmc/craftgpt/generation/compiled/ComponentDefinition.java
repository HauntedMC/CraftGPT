package nl.hauntedmc.craftgpt.generation.compiled;

import java.util.List;

public record ComponentDefinition(String name, List<PrimitiveOperation> operations) {
    public ComponentDefinition {
        operations = List.copyOf(operations);
    }
}
