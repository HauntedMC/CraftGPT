package nl.hauntedmc.craftgpt;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Feedback {
    private static final Component DEFAULT_PREFIX = Component.text("[CraftGPT] ");

    private Feedback() {
    }

    public static void sendFeedback(CommandSender sender, String name, String parameter) {
        sender.sendMessage(prefix().append(message(name, parameter)));
    }

    public static void sendFeedback(CommandSender sender, String name) {
        sendFeedback(sender, name, "");
    }

    public static void sendActionBarFeedback(CommandSender sender, String name, String parameter) {
        if (sender instanceof Player player) {
            player.sendActionBar(prefix().append(message(name, parameter)));
        }
    }

    public static void sendActionBarFeedback(CommandSender sender, String name) {
        sendActionBarFeedback(sender, name, "");
    }

    public static void clearActionBar(Player player) {
        player.sendActionBar(Component.empty());
    }

    private static Component prefix() {
        CraftGPT plugin = CraftGPT.getInstance();
        if (plugin == null || plugin.getMessageBundle() == null) {
            return DEFAULT_PREFIX;
        }
        return plugin.getMessageBundle().prefix();
    }

    private static Component message(String key, String parameter) {
        CraftGPT plugin = CraftGPT.getInstance();
        if (plugin == null || plugin.getMessageBundle() == null) {
            return Component.text("Missing message system: " + key);
        }
        return plugin.getMessageBundle().render(key, parameter);
    }
}
