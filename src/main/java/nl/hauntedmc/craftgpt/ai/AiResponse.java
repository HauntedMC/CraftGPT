package nl.hauntedmc.craftgpt.ai;

public record AiResponse(boolean success, String content, String rawResponse, String failureMessage, boolean retryable) {
    public static AiResponse success(String content, String rawResponse) {
        return new AiResponse(true, content, rawResponse, "", false);
    }

    public static AiResponse failure(String failureMessage, String rawResponse, boolean retryable) {
        return new AiResponse(false, "", rawResponse, failureMessage, retryable);
    }
}
