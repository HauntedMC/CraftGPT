package nl.hauntedmc.craftgpt.util;

import nl.hauntedmc.craftgpt.ai.ModelPreset;

import java.util.Map;

public final class ModelPresetLoader {
    private ModelPresetLoader() {
    }

    public static LoadResult load(String modelName) {
        Map<String, Object> payload = ConfigManager.getPayload(modelName);
        if (payload == null) {
            return LoadResult.failure("error.model_preset_payload", "");
        }
        String endpoint = ConfigManager.getUrl(modelName);
        if (endpoint == null || endpoint.isBlank()) {
            return LoadResult.failure("error.model_preset_payload", "");
        }
        String apiKey = ConfigManager.getApiKey(modelName);
        if (PluginConfig.isUnsetOrPlaceholderSecret(apiKey)) {
            return LoadResult.failure("error.config.api_key", modelName);
        }
        return LoadResult.success(new ModelPreset(modelName, endpoint, apiKey, payload));
    }

    public record LoadResult(ModelPreset preset, String errorKey, String errorParam) {
        public static LoadResult success(ModelPreset preset) {
            return new LoadResult(preset, "", "");
        }

        public static LoadResult failure(String errorKey, String errorParam) {
            return new LoadResult(null, errorKey, errorParam == null ? "" : errorParam);
        }

        public boolean isSuccess() {
            return preset != null;
        }
    }
}
