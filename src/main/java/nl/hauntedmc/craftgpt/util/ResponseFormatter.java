package nl.hauntedmc.craftgpt.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public final class ResponseFormatter {
    private ResponseFormatter() {
    }

    public static String extractResponseField(String jsonResponse) {
        try {
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();

            String outputText = getFieldAsTextOrJson(root, "output_text");
            if (outputText != null) {
                return outputText;
            }

            String outputParsed = getFieldAsTextOrJson(root, "output_parsed");
            if (outputParsed != null) {
                return outputParsed;
            }

            String responsesText = extractResponsesOutput(root);
            if (responsesText != null) {
                return responsesText;
            }
            return null;
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }

    private static String extractResponsesOutput(JsonObject root) {
        JsonArray output = root.getAsJsonArray("output");
        if (output == null || output.size() == 0) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        for (JsonElement item : output) {
            if (item == null || !item.isJsonObject()) {
                continue;
            }

            JsonObject outputItem = item.getAsJsonObject();
            JsonArray content = outputItem.getAsJsonArray("content");
            if (content == null) {
                continue;
            }

            for (JsonElement part : content) {
                if (part == null || !part.isJsonObject()) {
                    continue;
                }

                JsonObject contentObject = part.getAsJsonObject();
                String structured = getFieldAsTextOrJson(contentObject, "parsed");
                if (structured == null) {
                    structured = getFieldAsTextOrJson(contentObject, "json");
                }
                if (structured != null) {
                    builder.append(structured);
                    continue;
                }

                String type = getString(contentObject, "type");
                if ("output_text".equals(type)) {
                    String text = getFieldAsTextOrJson(contentObject, "text");
                    if (text != null) {
                        builder.append(text);
                    }
                }
            }
        }

        String combined = builder.toString().trim();
        return combined.isEmpty() ? null : combined;
    }

    private static String getContent(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            return value == null || value.isEmpty() ? null : value;
        }
        if (element.isJsonArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonElement part : element.getAsJsonArray()) {
                if (part == null || part.isJsonNull()) {
                    continue;
                }
                if (part.isJsonPrimitive()) {
                    builder.append(part.getAsString());
                    continue;
                }
                if (!part.isJsonObject()) {
                    continue;
                }
                JsonObject object = part.getAsJsonObject();
                String text = getFieldAsTextOrJson(object, "text");
                if (text != null) {
                    builder.append(text);
                    continue;
                }
                text = getFieldAsTextOrJson(object, "parsed");
                if (text != null) {
                    builder.append(text);
                    continue;
                }
                text = getFieldAsTextOrJson(object, "json");
                if (text != null) {
                    builder.append(text);
                    continue;
                }
                JsonObject inputText = object.getAsJsonObject("input_text");
                if (inputText != null) {
                    text = getFieldAsTextOrJson(inputText, "text");
                    if (text != null) {
                        builder.append(text);
                    }
                }
            }
            String value = builder.toString().trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    private static String getString(JsonObject object, String fieldName) {
        if (object == null || !object.has(fieldName)) {
            return null;
        }
        return getElementAsTextOrJson(object.get(fieldName), false);
    }

    private static String getFieldAsTextOrJson(JsonObject object, String fieldName) {
        if (object == null || !object.has(fieldName)) {
            return null;
        }
        return getElementAsTextOrJson(object.get(fieldName), true);
    }

    private static String getElementAsTextOrJson(JsonElement element, boolean allowStructuredJson) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            return value == null || value.isEmpty() ? null : value;
        }
        if (allowStructuredJson && (element.isJsonObject() || element.isJsonArray())) {
            String value = element.toString();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    public static ArrayList<String> extractResponseLines(String responseString) {
        String[] lines = responseString.split("[/\\n\\r]+");
        ArrayList<String> linesList = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();

            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("/")) {
                line = line.substring(1).trim();
            }

            while (!line.isEmpty() && (line.endsWith("\\n") || !endsWithAny(line, TERMINAL_COMMAND_CHARACTERS))) {
                line = line.endsWith("\\n")
                        ? line.substring(0, line.length() - 2).trim()
                        : line.substring(0, line.length() - 1).trim();
            }

            if (!line.isEmpty()) {
                linesList.add(line);
            }
        }

        return linesList;
    }

    private static final List<String> TERMINAL_COMMAND_CHARACTERS = buildTerminalCharacters();

    private static List<String> buildTerminalCharacters() {
        List<String> characters = new ArrayList<>();
        for (char c = 'a'; c <= 'z'; c++) {
            characters.add(String.valueOf(c));
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            characters.add(String.valueOf(c));
        }
        for (char c = '0'; c <= '9'; c++) {
            characters.add(String.valueOf(c));
        }
        characters.add("]");
        characters.add("}");
        characters.add("\"");
        return List.copyOf(characters);
    }

    private static boolean endsWithAny(String line, List<String> characters) {
        for (String character : characters) {
            if (line.endsWith(character)) {
                return true;
            }
        }
        return false;
    }
}
