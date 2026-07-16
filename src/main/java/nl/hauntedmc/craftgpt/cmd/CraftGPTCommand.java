package nl.hauntedmc.craftgpt.cmd;

import nl.hauntedmc.craftgpt.CraftGPT;
import nl.hauntedmc.craftgpt.Feedback;
import nl.hauntedmc.craftgpt.generation.GenerationCoordinator;
import nl.hauntedmc.craftgpt.generation.IntVec3;
import nl.hauntedmc.craftgpt.util.ConfigManager;
import nl.hauntedmc.craftgpt.util.WorldEditSelectionResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CraftGPTCommand implements CommandExecutor, TabCompleter {
    private static final int MAX_PROMPT_PREVIEW_LENGTH = 240;
    private static final String GENERATE_SUBCOMMAND = "generate";
    private static final String RELOAD_SUBCOMMAND = "reload";
    private static final PlainTextComponentSerializer PLAIN_TEXT_SERIALIZER = PlainTextComponentSerializer.plainText();

    private final GenerationCoordinator generationCoordinator;

    public CraftGPTCommand(GenerationCoordinator generationCoordinator) {
        this.generationCoordinator = generationCoordinator;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case GENERATE_SUBCOMMAND -> handleGenerate(sender, args);
            case RELOAD_SUBCOMMAND -> handleReload(sender);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filterByPrefix(List.of(GENERATE_SUBCOMMAND, RELOAD_SUBCOMMAND), args[0]);
        }
        if (args.length == 2 && GENERATE_SUBCOMMAND.equalsIgnoreCase(args[0])) {
            return filterByPrefix(ConfigManager.getModelList(), args[1]);
        }
        if (args.length >= 3 && GENERATE_SUBCOMMAND.equalsIgnoreCase(args[0])) {
            return filterByPrefix(List.of("--book"), args[args.length - 1]);
        }
        return List.of();
    }

    private boolean handleGenerate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Feedback.sendFeedback(sender, "error.not_a_player");
            return true;
        }
        if (!sender.hasPermission("craftgpt.use")) {
            Feedback.sendFeedback(sender, "error.permission.use");
            return true;
        }
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        WorldEditSelectionResolver.SelectionResult selection = WorldEditSelectionResolver.resolve(player);
        if (!selection.isSuccess()) {
            Feedback.sendFeedback(player, selection.getErrorKey());
            return true;
        }

        ParsedInput parsedInput = parseInput(player, args);
        if (parsedInput.errorKey != null) {
            Feedback.sendFeedback(player, parsedInput.errorKey);
            return true;
        }
        if (parsedInput.input.isBlank()) {
            sendUsage(sender);
            return true;
        }

        IntVec3 min = new IntVec3(
                selection.getPos1().getBlockX(),
                selection.getPos1().getBlockY(),
                selection.getPos1().getBlockZ()
        );
        IntVec3 max = new IntVec3(
                selection.getPos2().getBlockX(),
                selection.getPos2().getBlockY(),
                selection.getPos2().getBlockZ()
        );
        return generationCoordinator.startGeneration(
                player,
                min,
                max,
                parsedInput.input,
                args[1],
                parsedInput.overwrites,
                abbreviate(parsedInput.input, MAX_PROMPT_PREVIEW_LENGTH)
        );
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("craftgpt.reload")) {
            Feedback.sendFeedback(sender, "error.permission.reload");
            return true;
        }
        CraftGPT.getInstance().reloadPluginConfiguration();
        Feedback.sendFeedback(sender, "done.reload");
        return true;
    }

    private ParsedInput parseInput(Player player, String[] args) {
        Map<String, String> overwrites = new LinkedHashMap<>();
        StringBuilder input = new StringBuilder();
        boolean useHeldBook = args.length == 2;

        for (int i = 2; i < args.length; i++) {
            String token = args[i];
            if (token.equalsIgnoreCase("--book") || token.equalsIgnoreCase("@book")) {
                useHeldBook = true;
                continue;
            }
            if (token.contains("=")) {
                String[] parts = token.split("=", 2);
                overwrites.put("%" + parts[0] + "%", parts[1]);
                continue;
            }
            if (input.length() > 0) {
                input.append(' ');
            }
            input.append(token);
        }

        if (useHeldBook) {
            String bookPrompt = readPromptFromHeldBook(player);
            if (bookPrompt.isBlank()) {
                return ParsedInput.error(overwrites, "error.prompt.book");
            }
            if (input.length() > 0) {
                input.insert(0, bookPrompt + "\n\n");
            } else {
                input.append(bookPrompt);
            }
        }
        return ParsedInput.success(input.toString(), overwrites);
    }

    private void sendUsage(CommandSender sender) {
        Feedback.sendFeedback(sender, "info.usage.header");
        Feedback.sendFeedback(sender, "info.usage.generate");
        Feedback.sendFeedback(sender, "info.usage.reload");
    }

    private List<String> filterByPrefix(List<String> values, String current) {
        String normalized = current.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }

    private String readPromptFromHeldBook(Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem == null) {
            return "";
        }
        Material material = heldItem.getType();
        if (material != Material.WRITABLE_BOOK && material != Material.WRITTEN_BOOK) {
            return "";
        }
        if (!(heldItem.getItemMeta() instanceof BookMeta bookMeta)) {
            return "";
        }
        StringBuilder prompt = new StringBuilder();
        for (Component page : bookMeta.pages()) {
            String content = PLAIN_TEXT_SERIALIZER.serialize(page).trim();
            if (content.isEmpty()) {
                continue;
            }
            if (prompt.length() > 0) {
                prompt.append("\n\n");
            }
            prompt.append(content);
        }
        return prompt.toString().trim();
    }

    private String abbreviate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private record ParsedInput(String input, Map<String, String> overwrites, String errorKey) {
        private static ParsedInput success(String input, Map<String, String> overwrites) {
            return new ParsedInput(input, Map.copyOf(overwrites), null);
        }

        private static ParsedInput error(Map<String, String> overwrites, String errorKey) {
            return new ParsedInput("", Map.copyOf(overwrites), errorKey);
        }
    }
}
