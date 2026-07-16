package nl.hauntedmc.craftgpt.ai;

import nl.hauntedmc.craftgpt.util.ConfigManager;
import nl.hauntedmc.craftgpt.util.RequestHandler;
import nl.hauntedmc.craftgpt.util.ResponseFormatter;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ResponsesAiClient implements AiClient {
    @Override
    public AiResponse execute(ModelPreset preset, AiRequest request, int timeoutSeconds) {
        Map<String, Object> payload = ConfigManager.deepCopyMap(preset.payloadTemplate());
        payload.put("store", false);
        payload.put("instructions", request.instructions());
        payload.put("input", buildInput(request));

        Map<String, Object> text = getOrCreateObject(payload, "text");
        text.put("verbosity", text.getOrDefault("verbosity", "low"));
        text.put("format", Map.of(
                "type", "json_schema",
                "strict", true,
                "name", request.schemaName(),
                "schema", request.schema()
        ));

        sanitizeKnownUnsupportedParameters(preset.name(), preset.endpoint(), payload);
        RequestHandler.RequestResult requestResult = RequestHandler.doRequest(preset.endpoint(), payload, preset.apiKey(), timeoutSeconds);
        if (!requestResult.isSuccess()) {
            boolean retryable = requestResult.isTimeoutFailure()
                    || requestResult.getStatusCode() >= 500
                    || requestResult.getStatusCode() == -1;
            return AiResponse.failure(requestResult.describeFailure(), requestResult.getBody(), retryable);
        }

        String rawResponse = requestResult.getBody();
        String content = ResponseFormatter.extractResponseField(rawResponse);
        if (content == null || content.isBlank()) {
            return AiResponse.failure("The AI response did not contain usable structured content.", rawResponse, true);
        }
        return AiResponse.success(content, rawResponse);
    }

    private Object buildInput(AiRequest request) {
        if (request.images().isEmpty()) {
            return request.inputText();
        }

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of(
                "type", "input_text",
                "text", request.inputText()
        ));
        for (AiImageAttachment image : request.images()) {
            content.add(Map.of(
                    "type", "input_image",
                    "image_url", inlineDataUrl(image)
            ));
        }
        return List.of(Map.of("role", "user", "content", content));
    }

    private String inlineDataUrl(AiImageAttachment image) {
        try {
            byte[] bytes = Files.readAllBytes(image.file());
            String base64 = Base64.getEncoder().encodeToString(bytes);
            return "data:" + image.mimeType() + ";base64," + base64;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read preview image: " + image.file(), e);
        }
    }

    private Map<String, Object> getOrCreateObject(Map<String, Object> root, String key) {
        Object value = root.get(key);
        if (value instanceof Map<?, ?> existing) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : existing.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            root.put(key, normalized);
            return normalized;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        root.put(key, created);
        return created;
    }

    private void sanitizeKnownUnsupportedParameters(String modelPreset, String endpoint, Map<String, Object> payload) {
        if (endpoint == null || payload == null) {
            return;
        }
        String normalizedEndpoint = endpoint.trim().toLowerCase(Locale.ROOT);
        String normalizedModelPreset = modelPreset == null ? "" : modelPreset.trim().toLowerCase(Locale.ROOT);
        if (normalizedEndpoint.equals("https://api.openai.com/v1/responses")
                && normalizedModelPreset.startsWith("gpt-5.6")
                && payload.containsKey("seed")) {
            payload.remove("seed");
        }
    }
}
