package nl.hauntedmc.craftgpt.ai;

public interface AiClient {
    AiResponse execute(ModelPreset preset, AiRequest request, int timeoutSeconds);
}
