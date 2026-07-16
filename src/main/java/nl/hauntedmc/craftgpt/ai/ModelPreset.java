package nl.hauntedmc.craftgpt.ai;

import java.util.Map;

public record ModelPreset(String name, String endpoint, String apiKey, Map<String, Object> payloadTemplate) {
}
