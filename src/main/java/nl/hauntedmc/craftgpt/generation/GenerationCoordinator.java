package nl.hauntedmc.craftgpt.generation;

import nl.hauntedmc.craftgpt.CraftGPT;
import nl.hauntedmc.craftgpt.Feedback;
import nl.hauntedmc.craftgpt.selection.FancySelectionBox;
import nl.hauntedmc.craftgpt.ai.AiClient;
import nl.hauntedmc.craftgpt.generation.compiled.parser.BuildProgramParser;
import nl.hauntedmc.craftgpt.generation.compiled.pipeline.CompiledBuildGenerationPipeline;
import nl.hauntedmc.craftgpt.generation.compiled.compiler.VoxelCompiler;
import nl.hauntedmc.craftgpt.generation.preview.PreviewRenderer;
import nl.hauntedmc.craftgpt.generation.worldedit.WorldEditBuildApplicationService;
import nl.hauntedmc.craftgpt.generation.worldedit.WorldEditPaletteResolver;
import nl.hauntedmc.craftgpt.util.ModelPresetLoader;
import nl.hauntedmc.craftgpt.util.PluginConfig;
import nl.hauntedmc.craftgpt.util.PromptTemplateResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GenerationCoordinator implements Listener {
    private static final String FRONT_DIRECTION = "positive Z";

    private final CraftGPT plugin;
    private final WorldEditBuildApplicationService applicationService;
    private final BuildGenerationPipeline compiledPipeline;
    private final Map<UUID, ActiveGeneration> activeGenerations = new ConcurrentHashMap<>();

    public GenerationCoordinator(CraftGPT plugin) {
        this.plugin = plugin;
        AiClient aiClient = new nl.hauntedmc.craftgpt.ai.ResponsesAiClient();
        WorldEditPaletteResolver paletteResolver = new WorldEditPaletteResolver();
        this.applicationService = new WorldEditBuildApplicationService();
        this.compiledPipeline = new CompiledBuildGenerationPipeline(
                aiClient,
                new BuildProgramParser(),
                new VoxelCompiler(),
                paletteResolver,
                new PreviewRenderer()
        );
    }

    public boolean startGeneration(Player player, IntVec3 selectionMinWorld, IntVec3 selectionMaxWorld, String input,
                                   String modelPresetName, Map<String, String> promptOverwrites, String promptPreview) {
        PluginConfig config = plugin.getPluginConfig();
        if (activeGenerations.containsKey(player.getUniqueId())) {
            Feedback.sendFeedback(player, "error.execution.busy");
            return false;
        }

        ModelPresetLoader.LoadResult presetLoadResult = ModelPresetLoader.load(modelPresetName);
        if (!presetLoadResult.isSuccess()) {
            Feedback.sendFeedback(player, presetLoadResult.errorKey(), presetLoadResult.errorParam());
            return false;
        }
        nl.hauntedmc.craftgpt.ai.ModelPreset preset = presetLoadResult.preset();

        BuildVolume buildVolume = new BuildVolume(
                selectionMaxWorld.x() - selectionMinWorld.x() + 1,
                selectionMaxWorld.y() - selectionMinWorld.y() + 1,
                selectionMaxWorld.z() - selectionMinWorld.z() + 1
        );
        GenerationContext context = new GenerationContext(
                buildVolume,
                config.generationLimits(),
                config.visualReviewSettings(),
                RequestClassifier.classify(input),
                FRONT_DIRECTION,
                plugin.getServer().getMinecraftVersion()
        );
        Map<String, String> promptVariables = PromptTemplateResolver.variables(
                buildVolume,
                selectionMinWorld,
                selectionMaxWorld,
                plugin.getServer().getMinecraftVersion(),
                FRONT_DIRECTION,
                config.generationLimits().maxMatrixBlocksPerGeneration(),
                config.generationLimits().maxDslComponents(),
                config.generationLimits().maxDslInstances(),
                config.generationLimits().maxDslOperations(),
                config.generationLimits().maxDslOperationsPerComponent(),
                promptOverwrites
        );
        String promptTemplate = config.getCompiledPrompt();
        String systemPrompt = PromptTemplateResolver.resolve(promptTemplate, promptVariables);
        if (config.isSendPayload()) {
            String debugPayload = "mode=compiled, model=" + modelPresetName
                    + ", system_prompt='" + abbreviate(systemPrompt, config.getLogExcerptMaxLength() / 2) + "'"
                    + ", input='" + abbreviate(input, config.getLogExcerptMaxLength() / 2) + "'";
            Feedback.sendFeedback(player, "info.payload", debugPayload);
        }

        FancySelectionBox selectionBox = new FancySelectionBox(
                new Location(player.getWorld(), selectionMinWorld.x(), selectionMinWorld.y(), selectionMinWorld.z()),
                new Location(player.getWorld(), selectionMaxWorld.x(), selectionMaxWorld.y(), selectionMaxWorld.z())
        );
        selectionBox.switchToGenerating();
        Feedback.sendFeedback(player, "info.structure", promptPreview);
        BukkitTask waitingTask = runActionBarTask(player, "wait.generating.actionbar");

        ActiveGeneration activeGeneration = new ActiveGeneration(player.getWorld(), selectionMinWorld, selectionMaxWorld, selectionBox, waitingTask);
        activeGenerations.put(player.getUniqueId(), activeGeneration);

        PipelineRequest request = new PipelineRequest(
                preset,
                context,
                config.workflowSettings(),
                input,
                systemPrompt,
                config.getRequestTimeoutSeconds(),
                config.getMaxGenerationAttempts(),
                config.getMaxRepairAttempts(),
                config.isApplyBestEffortResult(),
                config.isRepairWarningsEnabled(),
                config.isVisualReviewRepairEnabled(),
                config.getLogExcerptMaxLength(),
                promptVariables
        );

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PipelineResult result = compiledPipeline.generate(request);
            Bukkit.getScheduler().runTask(plugin, () -> handlePipelineResult(player, activeGeneration, result));
        });
        return true;
    }

    public void shutdown() {
        activeGenerations.values().forEach(active -> active.cancelled.set(true));
        activeGenerations.keySet().forEach(this::cancelGeneration);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelGeneration(event.getPlayer().getUniqueId());
    }

    private void handlePipelineResult(Player player, ActiveGeneration activeGeneration, PipelineResult result) {
        ActiveGeneration current = activeGenerations.get(player.getUniqueId());
        if (current != activeGeneration || activeGeneration.cancelled.get() || !player.isOnline()) {
            cleanup(player.getUniqueId(), false);
            return;
        }

        activeGeneration.waitingTask.cancel();
        if (!result.success()) {
            sendPipelineFailure(player, result);
            cleanup(player.getUniqueId(), false);
            return;
        }
        if (result.cuboids().isEmpty()) {
            Feedback.sendFeedback(player, "error.response.no_commands");
            cleanup(player.getUniqueId(), false);
            return;
        }
        if (plugin.getPluginConfig().isSendResponse()) {
            Feedback.sendFeedback(player, "info.response.header");
            Feedback.sendFeedback(player, "info.response.content.correct",
                    abbreviate(result.finalModelOutput(), plugin.getPluginConfig().getLogExcerptMaxLength()));
        }

        Feedback.sendFeedback(player, "wait.execution");
        activeGeneration.actionBarTask = runActionBarTask(player, "wait.placing.actionbar");
        activeGeneration.buildTask = applicationService.apply(
                player,
                activeGeneration.buildWorld,
                activeGeneration.selectionMinWorld,
                result.cuboids(),
                plugin.getPluginConfig(),
                () -> {
                    Feedback.sendFeedback(player, "done.done");
                    cleanup(player.getUniqueId(), true);
                },
                error -> {
                    Feedback.sendFeedback(player, "error.execution.failed", abbreviate(error, plugin.getPluginConfig().getLogExcerptMaxLength()));
                    cleanup(player.getUniqueId(), false);
                }
        );
    }

    private void cancelGeneration(UUID playerId) {
        ActiveGeneration activeGeneration = activeGenerations.remove(playerId);
        if (activeGeneration == null) {
            return;
        }
        activeGeneration.cancelled.set(true);
        if (activeGeneration.waitingTask != null) {
            activeGeneration.waitingTask.cancel();
        }
        if (activeGeneration.actionBarTask != null) {
            activeGeneration.actionBarTask.cancel();
        }
        if (activeGeneration.buildTask != null) {
            activeGeneration.buildTask.cancel();
        }
        activeGeneration.selectionBox.stop();
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            Feedback.clearActionBar(player);
        }
    }

    private void cleanup(UUID playerId, boolean success) {
        ActiveGeneration activeGeneration = activeGenerations.remove(playerId);
        if (activeGeneration == null) {
            return;
        }
        if (activeGeneration.waitingTask != null) {
            activeGeneration.waitingTask.cancel();
        }
        if (activeGeneration.actionBarTask != null) {
            activeGeneration.actionBarTask.cancel();
        }
        activeGeneration.selectionBox.stop();
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            Feedback.clearActionBar(player);
        }
    }

    private BukkitTask runActionBarTask(Player player, String feedbackKey) {
        return new BukkitRunnable() {
            private int dots;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                dots = (dots + 1) % 4;
                Feedback.sendActionBarFeedback(player, feedbackKey, ".".repeat(dots));
            }
        }.runTaskTimer(plugin, 0L, plugin.getPluginConfig().getActionBarUpdateTicks());
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private void sendPipelineFailure(Player player, PipelineResult result) {
        String failureCode = result.failureCode();
        String message = abbreviate(result.failureMessage(), plugin.getPluginConfig().getLogExcerptMaxLength());
        if ("request_failed".equals(failureCode) || "repair_request_failed".equals(failureCode)) {
            Feedback.sendFeedback(player, "error.request.http", message);
            return;
        }
        if ("preview_render_failed".equals(failureCode)) {
            Feedback.sendFeedback(player, "error.preview", message);
            return;
        }
        Feedback.sendFeedback(player, "error.invalid_plan", message);
    }

    private static final class ActiveGeneration {
        private final IntVec3 selectionMinWorld;
        private final IntVec3 selectionMaxWorld;
        private final World buildWorld;
        private final FancySelectionBox selectionBox;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private BukkitTask waitingTask;
        private BukkitTask actionBarTask;
        private BukkitTask buildTask;

        private ActiveGeneration(World buildWorld, IntVec3 selectionMinWorld, IntVec3 selectionMaxWorld, FancySelectionBox selectionBox, BukkitTask waitingTask) {
            this.buildWorld = buildWorld;
            this.selectionMinWorld = selectionMinWorld;
            this.selectionMaxWorld = selectionMaxWorld;
            this.selectionBox = selectionBox;
            this.waitingTask = waitingTask;
        }
    }
}
