package nl.hauntedmc.craftgpt.generation.preview;

import java.awt.Color;
import java.util.Locale;
import java.util.Map;

public final class BlockColorResolver {
    private static final Map<String, Color> NAMED = Map.ofEntries(
            Map.entry("stone", new Color(125, 125, 125)),
            Map.entry("cobblestone", new Color(110, 110, 110)),
            Map.entry("deepslate", new Color(75, 79, 85)),
            Map.entry("oak", new Color(169, 135, 84)),
            Map.entry("spruce", new Color(113, 84, 52)),
            Map.entry("birch", new Color(213, 203, 159)),
            Map.entry("jungle", new Color(154, 111, 76)),
            Map.entry("acacia", new Color(185, 99, 55)),
            Map.entry("dark_oak", new Color(76, 54, 35)),
            Map.entry("mangrove", new Color(111, 52, 51)),
            Map.entry("crimson", new Color(126, 54, 74)),
            Map.entry("warped", new Color(54, 122, 122)),
            Map.entry("planks", new Color(162, 130, 78)),
            Map.entry("log", new Color(110, 86, 58)),
            Map.entry("leaves", new Color(75, 132, 67)),
            Map.entry("grass", new Color(103, 157, 74)),
            Map.entry("dirt", new Color(126, 84, 55)),
            Map.entry("sand", new Color(218, 210, 158)),
            Map.entry("glass", new Color(190, 225, 235)),
            Map.entry("water", new Color(60, 98, 184)),
            Map.entry("lava", new Color(220, 110, 24)),
            Map.entry("brick", new Color(138, 69, 54)),
            Map.entry("terracotta", new Color(152, 94, 67)),
            Map.entry("concrete", new Color(180, 180, 180)),
            Map.entry("wool", new Color(210, 210, 210)),
            Map.entry("quartz", new Color(230, 228, 220)),
            Map.entry("snow", new Color(240, 244, 246)),
            Map.entry("ice", new Color(174, 213, 233)),
            Map.entry("copper", new Color(191, 112, 73)),
            Map.entry("iron", new Color(207, 207, 207)),
            Map.entry("gold", new Color(224, 190, 75)),
            Map.entry("diamond", new Color(95, 218, 206)),
            Map.entry("emerald", new Color(76, 201, 100)),
            Map.entry("redstone", new Color(164, 33, 33)),
            Map.entry("obsidian", new Color(49, 36, 66)),
            Map.entry("blackstone", new Color(54, 54, 58)),
            Map.entry("lantern", new Color(216, 170, 72))
    );

    public Color resolve(String blockState) {
        String normalized = normalize(blockState);
        for (Map.Entry<String, Color> entry : NAMED.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return hashedColor(normalized);
    }

    private String normalize(String blockState) {
        String normalized = blockState == null ? "" : blockState.toLowerCase(Locale.ROOT);
        int stateIndex = normalized.indexOf('[');
        return stateIndex >= 0 ? normalized.substring(0, stateIndex) : normalized;
    }

    private Color hashedColor(String key) {
        int hash = key.hashCode();
        int red = 64 + Math.floorMod(hash, 128);
        int green = 64 + Math.floorMod(hash / 127, 128);
        int blue = 64 + Math.floorMod(hash / 8191, 128);
        return new Color(red, green, blue);
    }
}
