package nl.hauntedmc.craftgpt.ai;

import java.util.List;
import java.util.Map;

public record AiRequest(
        String schemaName,
        String instructions,
        String inputText,
        Map<String, Object> schema,
        List<AiImageAttachment> images
) {
    public AiRequest {
        schema = Map.copyOf(schema);
        images = List.copyOf(images);
    }
}
