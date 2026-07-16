package nl.hauntedmc.craftgpt.util;

import nl.hauntedmc.craftgpt.CraftGPT;
import nl.hauntedmc.craftgpt.generation.GenerationLimits;
import nl.hauntedmc.craftgpt.generation.VisualReviewSettings;
import nl.hauntedmc.craftgpt.generation.WorkflowProfile;
import nl.hauntedmc.craftgpt.generation.WorkflowSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PluginConfig {
    private final int particlesPerBlock;
    private final long selectionBoxUpdateTicks;
    private final int buildDelayTicks;
    private final int worldEditBatchSize;
    private final long actionBarUpdateTicks;
    private final int requestTimeoutSeconds;
    private final int logExcerptMaxLength;
    private final boolean sendResponse;
    private final boolean sendPayload;
    private final GenerationLimits generationLimits;
    private final WorkflowSettings workflowSettings;
    private final int maxGenerationAttempts;
    private final int maxRepairAttempts;
    private final boolean applyBestEffortResult;
    private final boolean ignoreOutOfBoundsPlacements;
    private final boolean repairWarningsEnabled;
    private final boolean visualReviewRepairEnabled;
    private final VisualReviewSettings visualReviewSettings;
    private final String compiledPrompt;

    private PluginConfig(int particlesPerBlock,
                         long selectionBoxUpdateTicks,
                         int buildDelayTicks,
                         int worldEditBatchSize,
                         long actionBarUpdateTicks,
                         int requestTimeoutSeconds,
                         int logExcerptMaxLength,
                         boolean sendResponse,
                         boolean sendPayload,
                         GenerationLimits generationLimits,
                         WorkflowSettings workflowSettings,
                         int maxGenerationAttempts,
                         int maxRepairAttempts,
                         boolean applyBestEffortResult,
                         boolean ignoreOutOfBoundsPlacements,
                         boolean repairWarningsEnabled,
                         boolean visualReviewRepairEnabled,
                         VisualReviewSettings visualReviewSettings,
                         String compiledPrompt) {
        this.particlesPerBlock = particlesPerBlock;
        this.selectionBoxUpdateTicks = selectionBoxUpdateTicks;
        this.buildDelayTicks = buildDelayTicks;
        this.worldEditBatchSize = worldEditBatchSize;
        this.actionBarUpdateTicks = actionBarUpdateTicks;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.logExcerptMaxLength = logExcerptMaxLength;
        this.sendResponse = sendResponse;
        this.sendPayload = sendPayload;
        this.generationLimits = generationLimits;
        this.workflowSettings = workflowSettings;
        this.maxGenerationAttempts = maxGenerationAttempts;
        this.maxRepairAttempts = maxRepairAttempts;
        this.applyBestEffortResult = applyBestEffortResult;
        this.ignoreOutOfBoundsPlacements = ignoreOutOfBoundsPlacements;
        this.repairWarningsEnabled = repairWarningsEnabled;
        this.visualReviewRepairEnabled = visualReviewRepairEnabled;
        this.visualReviewSettings = visualReviewSettings;
        this.compiledPrompt = compiledPrompt;
    }

    public static PluginConfig load(FileConfiguration config) {
        CraftGPT plugin = CraftGPT.getInstance();
        int particlesPerBlock = getInt(config, "particles_per_block", 5, 0, 64);
        long selectionBoxUpdateTicks = getLong(config, "selection_box_update_ticks", 3L, 1L, 200L);
        int buildDelayTicks = getInt(config, "build_delay", 0, 0, 1200);
        int worldEditBatchSize = getInt(config, "worldedit_batch_size", 4096, 1, 100000);
        long actionBarUpdateTicks = getLong(config, "actionbar_update_ticks", 10L, 1L, 200L);
        int requestTimeoutSeconds = getInt(config, "request_timeout_seconds", 300, 1, 3600);
        int logExcerptMaxLength = getInt(config, "log_excerpt_max_length", 1800, 64, 100000);
        boolean sendResponse = config.getBoolean("sendresponse", false);
        boolean sendPayload = config.getBoolean("sendpayload", false);

        GenerationLimits limits = new GenerationLimits(
                getInt(config, "max_dsl_components", 256, 1, 10000),
                getInt(config, "max_dsl_instances", 4096, 1, 100000),
                getInt(config, "max_dsl_operations", 8000, 1, 100000),
                getInt(config, "max_dsl_operations_per_component", 1024, 1, 10000),
                getInt(config, "max_matrix_blocks_per_generation", 120000, 1, Integer.MAX_VALUE),
                getInt(config, "max_total_actions_per_generation", 120000, 1, Integer.MAX_VALUE),
                getInt(config, "max_sparse_segments_per_generation", 8000, 1, Integer.MAX_VALUE)
        );
        WorkflowSettings workflowSettings = loadWorkflowSettings(config);
        int maxGenerationAttempts = getInt(config, "max_generation_attempts", 2, 1, 10);
        int maxRepairAttempts = getInt(config, "max_repair_attempts", 1, 0, 10);
        boolean applyBestEffortResult = config.getBoolean("apply_best_effort_result", false);
        boolean ignoreOutOfBoundsPlacements = config.getBoolean("ignore_out_of_bounds_placements", false);
        boolean repairWarningsEnabled = config.getBoolean("repair_warnings_enabled", true);
        boolean visualReviewRepairEnabled = config.getBoolean("visual_review_repair_enabled", false);
        VisualReviewSettings visualReviewSettings = new VisualReviewSettings(
                config.getBoolean("visual_review_enabled", true),
                getInt(config, "visual_review_min_occupied_blocks", 250, 0, Integer.MAX_VALUE),
                getInt(config, "visual_review_preview_size", 512, 64, 4096),
                config.getBoolean("debug_artifacts_enabled", false),
                config.getString("debug_artifact_directory", "plugins/CraftGPT/debug")
        );

        String compiledPrompt = getPrompt(config, "compiled_prompt");

        validateModels(config, plugin);
        return new PluginConfig(
                particlesPerBlock,
                selectionBoxUpdateTicks,
                buildDelayTicks,
                worldEditBatchSize,
                actionBarUpdateTicks,
                requestTimeoutSeconds,
                logExcerptMaxLength,
                sendResponse,
                sendPayload,
                limits,
                workflowSettings,
                maxGenerationAttempts,
                maxRepairAttempts,
                applyBestEffortResult,
                ignoreOutOfBoundsPlacements,
                repairWarningsEnabled,
                visualReviewRepairEnabled,
                visualReviewSettings,
                compiledPrompt
        );
    }

    private static String getPrompt(FileConfiguration config, String key) {
        String prompt = config.getString(key, "");
        if (prompt == null || prompt.trim().isEmpty()) {
            CraftGPT.getInstance().getLogger().warning("The configured prompt '" + key + "' is empty.");
            return "";
        }
        return prompt;
    }

    private static WorkflowSettings loadWorkflowSettings(FileConfiguration config) {
        WorkflowProfile profile = WorkflowProfile.fromConfig(config.getString("workflow_profile", "balanced"));
        WorkflowSettings defaults = WorkflowSettings.defaults(profile);
        ConfigurationSection section = config.getConfigurationSection("workflow_profiles." + profile.configValue());
        if (section == null) {
            return defaults;
        }
        return new WorkflowSettings(
                profile,
                section.getBoolean("planning_enabled", defaults.planningEnabled()),
                section.getBoolean("critique_enabled", defaults.critiqueEnabled()),
                getInt(section, "max_refinement_passes", defaults.maxRefinementPasses(), 0, 4),
                section.getBoolean("extended_previews", defaults.extendedPreviews())
        );
    }

    private static void validateModels(FileConfiguration config, CraftGPT plugin) {
        ConfigurationSection modelsSection = config.getConfigurationSection("models");
        if (modelsSection == null || modelsSection.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("No model presets were found under 'models' in config.yml.");
            return;
        }
        for (String modelName : modelsSection.getKeys(false)) {
            String modelRoot = "models." + modelName;
            String endpoint = config.getString(modelRoot + ".endpoint");
            if (endpoint == null || endpoint.trim().isEmpty()) {
                plugin.getLogger().warning("Model preset '" + modelName + "' is missing an endpoint.");
            }
            if (!hasValidPayloadDefinition(config, modelRoot + ".payload")) {
                plugin.getLogger().warning("Model preset '" + modelName + "' is missing a valid payload definition.");
            }
            String apiKey = config.getString(modelRoot + ".api_key");
            if (isUnsetOrPlaceholderSecret(apiKey)) {
                plugin.getLogger().warning("Model preset '" + modelName + "' still has a placeholder, unresolved reference, or missing API key.");
            }
        }
    }

    private static int getInt(FileConfiguration config, String path, int defaultValue, int min, int max) {
        int value = config.getInt(path, defaultValue);
        if (value < min || value > max) {
            CraftGPT.getInstance().getLogger().warning("Invalid config value for '" + path + "': " + value + ". Using " + defaultValue + ".");
            return defaultValue;
        }
        return value;
    }

    private static int getInt(ConfigurationSection config, String path, int defaultValue, int min, int max) {
        int value = config.getInt(path, defaultValue);
        if (value < min || value > max) {
            CraftGPT.getInstance().getLogger().warning("Invalid config value for '" + path + "': " + value + ". Using " + defaultValue + ".");
            return defaultValue;
        }
        return value;
    }

    private static long getLong(FileConfiguration config, String path, long defaultValue, long min, long max) {
        long value = config.getLong(path, defaultValue);
        if (value < min || value > max) {
            CraftGPT.getInstance().getLogger().warning("Invalid config value for '" + path + "': " + value + ". Using " + defaultValue + ".");
            return defaultValue;
        }
        return value;
    }

    private static boolean hasValidPayloadDefinition(FileConfiguration config, String payloadPath) {
        List<?> payloadEntries = config.getList(payloadPath);
        if (payloadEntries == null || payloadEntries.isEmpty()) {
            return false;
        }
        for (Object entry : payloadEntries) {
            if (!(entry instanceof Map<?, ?> payloadItem) || payloadItem.size() != 1) {
                return false;
            }
        }
        return true;
    }

    public int getParticlesPerBlock() {
        return particlesPerBlock;
    }

    public long getSelectionBoxUpdateTicks() {
        return selectionBoxUpdateTicks;
    }

    public int getBuildDelayTicks() {
        return buildDelayTicks;
    }

    public int getWorldEditBatchSize() {
        return worldEditBatchSize;
    }

    public long getActionBarUpdateTicks() {
        return actionBarUpdateTicks;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public int getLogExcerptMaxLength() {
        return logExcerptMaxLength;
    }

    public boolean isSendResponse() {
        return sendResponse;
    }

    public boolean isSendPayload() {
        return sendPayload;
    }

    public GenerationLimits generationLimits() {
        return generationLimits;
    }

    public WorkflowSettings workflowSettings() {
        return workflowSettings;
    }

    public int getMaxGenerationAttempts() {
        return maxGenerationAttempts;
    }

    public int getMaxRepairAttempts() {
        return maxRepairAttempts;
    }

    public boolean isApplyBestEffortResult() {
        return applyBestEffortResult;
    }

    public boolean isIgnoreOutOfBoundsPlacements() {
        return ignoreOutOfBoundsPlacements;
    }

    public boolean isRepairWarningsEnabled() {
        return repairWarningsEnabled;
    }

    public boolean isVisualReviewRepairEnabled() {
        return visualReviewRepairEnabled;
    }

    public VisualReviewSettings visualReviewSettings() {
        return visualReviewSettings;
    }

    public String getCompiledPrompt() {
        return compiledPrompt;
    }

    public static String resolveConfiguredSecret(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }

        if (normalized.startsWith("${") && normalized.endsWith("}")) {
            String inner = normalized.substring(2, normalized.length() - 1).trim();
            String resolved = resolveExternalReference(inner);
            return resolved == null ? normalized : resolved;
        }

        if (normalized.regionMatches(true, 0, "env:", 0, 4)) {
            String resolved = resolveExternalReference(normalized);
            return resolved == null ? normalized : resolved;
        }

        return normalized;
    }

    public static boolean isUnsetOrPlaceholderSecret(String value) {
        String normalized = resolveConfiguredSecret(value);
        if (normalized == null || normalized.isEmpty()) {
            return true;
        }
        if (normalized.startsWith("%") && normalized.endsWith("%")) {
            return true;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.contains("replace-with")
                || lower.contains("your-api-key")
                || lower.contains("api-key-here")
                || lower.contains("change-me");
    }

    private static String resolveExternalReference(String reference) {
        if (reference == null) {
            return null;
        }
        String normalized = reference.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        String key = normalized.regionMatches(true, 0, "ENV:", 0, 4)
                ? normalized.substring(4).trim()
                : normalized;
        if (key.isEmpty()) {
            return null;
        }

        String envValue = System.getenv(key);
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue.trim();
        }

        String propertyValue = System.getProperty(key);
        if (propertyValue != null && !propertyValue.trim().isEmpty()) {
            return propertyValue.trim();
        }

        return null;
    }
}
