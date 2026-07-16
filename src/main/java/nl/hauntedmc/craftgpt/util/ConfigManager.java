package nl.hauntedmc.craftgpt.util;

import nl.hauntedmc.craftgpt.CraftGPT;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigManager {
    private ConfigManager() {
    }

    public static Map<String, Object> getPayload(String modelName) {
        FileConfiguration config = CraftGPT.getInstance().getConfig();
        String payloadPath = "models." + modelName + ".payload";
        if (!config.contains(payloadPath)) {
            return null;
        }

        List<?> configPayload = config.getList(payloadPath);
        if (configPayload == null) {
            return null;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        for (Object item : configPayload) {
            if (!(item instanceof Map<?, ?>)) {
                CraftGPT.getInstance().getLogger().warning("Invalid payload entry for model '" + modelName + "'. Each payload item must be a single-entry map.");
                return null;
            }

            Map<?, ?> map = (Map<?, ?>) item;
            if (map.size() != 1) {
                CraftGPT.getInstance().getLogger().warning("Invalid payload entry for model '" + modelName + "'. Each payload item must contain exactly one key.");
                return null;
            }

            Map.Entry<?, ?> entry = map.entrySet().iterator().next();
            payload.put(String.valueOf(entry.getKey()), deepCopyObject(entry.getValue()));
        }
        return payload;
    }

    public static Map<String, Object> deepCopyMap(Map<String, Object> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (map == null) {
            return copy;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            copy.put(entry.getKey(), deepCopyObject(entry.getValue()));
        }
        return copy;
    }

    public static String getUrl(String modelName) {
        String url = CraftGPT.getInstance().getConfig().getString("models." + modelName + ".endpoint");
        return url == null ? null : url.trim();
    }

    public static String getApiKey(String modelName) {
        return PluginConfig.resolveConfiguredSecret(
                CraftGPT.getInstance().getConfig().getString("models." + modelName + ".api_key")
        );
    }

    public static List<String> getModelList() {
        FileConfiguration config = CraftGPT.getInstance().getConfig();
        ConfigurationSection modelsSection = config.getConfigurationSection("models");
        if (modelsSection == null) {
            return new ArrayList<>();
        }
        Set<String> orderedModels = new LinkedHashSet<>(modelsSection.getKeys(false));
        return new ArrayList<>(orderedModels);
    }

    public static Map<String, Object> replaceInMap(Map<String, Object> map, String target, String replacement) {
        for (Map.Entry<String, Object> entry : new ArrayList<>(map.entrySet())) {
            map.put(entry.getKey(), replaceInObject(entry.getValue(), target, replacement));
        }
        return map;
    }

    public static String replaceInString(String value, String target, String replacement) {
        if (value == null) {
            return null;
        }
        return value.replace(target, replacement);
    }

    public static List<String> getVarsInMap(Map<String, Object> map) {
        List<String> result = new ArrayList<>();
        for (Object value : map.values()) {
            addVarsFromObject(value, result);
        }
        return result;
    }

    public static boolean isUnsetVariable(String value) {
        return value != null && value.startsWith("%") && value.endsWith("%");
    }

    private static Object deepCopyObject(Object value) {
        if (value instanceof Map<?, ?> sourceMap) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                copy.put(entry.getKey(), deepCopyObject(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> sourceList) {
            List<Object> copy = new ArrayList<>();
            for (Object item : sourceList) {
                copy.add(deepCopyObject(item));
            }
            return copy;
        }
        return value;
    }

    private static Object replaceInObject(Object value, String target, String replacement) {
        if (value instanceof String stringValue) {
            return replaceInString(stringValue, target, replacement);
        }
        if (value instanceof List<?> listValue) {
            List<Object> replaced = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                replaced.add(replaceInObject(item, target, replacement));
            }
            return replaced;
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<Object, Object> replaced = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                replaced.put(entry.getKey(), replaceInObject(entry.getValue(), target, replacement));
            }
            return replaced;
        }
        return value;
    }

    private static void addVarsFromObject(Object value, List<String> result) {
        if (value instanceof String stringValue) {
            if (isUnsetVariable(stringValue)) {
                result.add(stringValue);
            }
            return;
        }
        if (value instanceof List<?> listValue) {
            for (Object item : listValue) {
                addVarsFromObject(item, result);
            }
            return;
        }
        if (value instanceof Map<?, ?> mapValue) {
            for (Object item : mapValue.values()) {
                addVarsFromObject(item, result);
            }
        }
    }
}
