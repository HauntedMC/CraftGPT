package nl.hauntedmc.craftgpt.util;

import com.google.gson.Gson;
import nl.hauntedmc.craftgpt.CraftGPT;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class RequestHandler {
    private static final Gson GSON = new Gson();

    private RequestHandler() {
    }

    public static RequestResult doRequest(String urlString, Map<String, Object> payload, String apiKey, int timeoutSeconds) {
        int effectiveTimeoutSeconds = Math.max(1, timeoutSeconds);
        if (urlString == null || urlString.trim().isEmpty()) {
            return RequestResult.failure("Endpoint is not configured.");
        }

        HttpURLConnection connection = null;
        try {
            String jsonPayload = GSON.toJson(payload);
            connection = (HttpURLConnection) URI.create(urlString).toURL().openConnection();
            configureConnection(connection, effectiveTimeoutSeconds, apiKey);
            connection.setRequestMethod("POST");
            writePayload(connection, jsonPayload);

            int responseCode = connection.getResponseCode();
            InputStream responseStream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readResponse(responseStream);

            if (responseCode >= 200 && responseCode < 300) {
                return RequestResult.success(responseCode, body);
            }
            return RequestResult.httpError(responseCode, body);
        } catch (SocketTimeoutException e) {
            return RequestResult.failure("Request timed out after " + effectiveTimeoutSeconds + " seconds.");
        } catch (IllegalArgumentException | MalformedURLException e) {
            return RequestResult.failure("Invalid endpoint URL: " + e.getMessage());
        } catch (IOException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getClass().getSimpleName() + ": " + e.getMessage();
            return RequestResult.failure(message);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void configureConnection(HttpURLConnection connection, int timeoutSeconds, String apiKey) {
        int timeoutMillis = timeoutSeconds * 1000;
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", buildUserAgent());
        String authorizationHeader = buildAuthorizationHeader(apiKey);
        if (authorizationHeader != null) {
            connection.setRequestProperty("Authorization", authorizationHeader);
        }
        connection.setDoOutput(true);
    }

    private static void writePayload(HttpURLConnection connection, String jsonPayload) throws IOException {
        byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(input, 0, input.length);
        }
    }

    private static String buildUserAgent() {
        return "CraftGPT/" + CraftGPT.getInstance().getPluginMeta().getVersion();
    }

    private static String buildAuthorizationHeader(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return null;
        }
        String trimmedApiKey = apiKey.trim();
        if (trimmedApiKey.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmedApiKey;
        }
        return "Bearer " + trimmedApiKey;
    }

    private static String readResponse(InputStream responseStream) throws IOException {
        if (responseStream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (response.length() > 0) {
                    response.append('\n');
                }
                response.append(line);
            }
            return response.toString();
        }
    }

    public static final class RequestResult {
        private final boolean success;
        private final int statusCode;
        private final String body;
        private final String errorMessage;

        private RequestResult(boolean success, int statusCode, String body, String errorMessage) {
            this.success = success;
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }

        public static RequestResult success(int statusCode, String body) {
            return new RequestResult(true, statusCode, body, "");
        }

        public static RequestResult httpError(int statusCode, String body) {
            return new RequestResult(false, statusCode, body, "");
        }

        public static RequestResult failure(String errorMessage) {
            return new RequestResult(false, -1, "", errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getBody() {
            return body;
        }

        public String describeFailure() {
            if (statusCode > 0) {
                return "HTTP " + statusCode + ": " + body;
            }
            return errorMessage;
        }

        public boolean isTimeoutFailure() {
            return statusCode < 0
                    && errorMessage != null
                    && errorMessage.toLowerCase(java.util.Locale.ROOT).contains("timed out");
        }
    }
}
