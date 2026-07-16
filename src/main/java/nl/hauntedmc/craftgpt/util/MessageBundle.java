package nl.hauntedmc.craftgpt.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class MessageBundle {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final Component DEFAULT_PREFIX = MINI_MESSAGE.deserialize(
            "<color:#475569>[</color><gradient:#f59e0b:#38bdf8>CraftGPT</gradient><color:#475569>]</color> "
    );

    private final FileConfiguration config;

    public MessageBundle(FileConfiguration config) {
        this.config = config;
    }

    public Component prefix() {
        return parseTemplate(config.getString("prefix"), "", DEFAULT_PREFIX);
    }

    public Component render(String key, String parameter) {
        ConfigurationSection messages = config.getConfigurationSection("messages");
        String template = messages == null ? null : messages.getString(key);
        if (template == null || template.isBlank()) {
            return MINI_MESSAGE.deserialize(
                    "<color:#ef4444><bold>Missing message:</bold> <message_key></color>",
                    Placeholder.unparsed("message_key", key)
            );
        }
        return parseTemplate(template, parameter, Component.empty());
    }

    private Component parseTemplate(String template, String parameter, Component fallback) {
        if (template == null || template.isBlank()) {
            return fallback;
        }
        String normalizedTemplate = template.replace("{param}", "<param>");
        if (looksLikeMiniMessage(template)) {
            return MINI_MESSAGE.deserialize(normalizedTemplate, Placeholder.unparsed("param", parameter == null ? "" : parameter));
        }
        String resolvedLegacy = normalizedTemplate.replace("<param>", parameter == null ? "" : parameter);
        return LEGACY_SERIALIZER.deserialize(resolvedLegacy);
    }

    private boolean looksLikeMiniMessage(String template) {
        return template.indexOf('<') >= 0 && template.indexOf('>') > template.indexOf('<');
    }
}
