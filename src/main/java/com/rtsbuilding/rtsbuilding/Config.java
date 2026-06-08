package com.rtsbuilding.rtsbuilding;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_SURVIVAL_PROGRESSION = BUILDER
            .comment("Enable RTS Building survival progression, feature unlocks, home anchors, and progression radius limits.")
            .translation("rtsbuilding.configuration.enableSurvivalProgression")
            .define("enableSurvivalProgression", false);

    public static final ModConfigSpec.BooleanValue SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS = BUILDER
            .comment("When survival progression is enabled, share unlocked progression nodes and RTS home anchors with the player's FTB Team, or vanilla scoreboard team when FTB Teams is unavailable.")
            .translation("rtsbuilding.configuration.shareSurvivalProgressionWithTeams")
            .define("shareSurvivalProgressionWithTeams", false);

    public static final ModConfigSpec.IntValue MAX_ACTION_RADIUS_BLOCKS = BUILDER
            .comment("Maximum RTS action radius in blocks. Used directly when survival progression is disabled, and by the Radius Max skill when survival progression is enabled.")
            .translation("rtsbuilding.configuration.maxActionRadiusBlocks")
            .defineInRange("maxActionRadiusBlocks", 128, 48, 512);

    public static final ModConfigSpec.BooleanValue ENABLE_BLUEPRINTS = BUILDER
            .comment("Enable the RTS blueprint library tab, local blueprint upload, and server-side blueprint placement.")
            .translation("rtsbuilding.configuration.enableBlueprints")
            .define("enableBlueprints", true);

    public static final ModConfigSpec.IntValue MAX_BLUEPRINT_BLOCKS = BUILDER
            .comment("Maximum non-air blocks allowed in one RTS blueprint import, capture, or placement job.")
            .translation("rtsbuilding.configuration.maxBlueprintBlocks")
            .defineInRange("maxBlueprintBlocks", 20000, 1, 200000);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROGRESSION_COST_OVERRIDES = BUILDER
            .comment("Skill material overrides. Format: node_path=minecraft:item:count,minecraft:item2:count. Example: ultimine=minecraft:diamond_pickaxe:1,minecraft:redstone_block:1")
            .translation("rtsbuilding.configuration.progressionCostOverrides")
            .defineListAllowEmpty("progressionCostOverrides", List.of(), () -> "", obj -> obj instanceof String);

    // ---- Rendering options ----

    public static final ModConfigSpec.BooleanValue USE_BLOCK_GHOST_PREVIEW = BUILDER
            .comment("Render translucent block ghost models for placement previews and place/break animations.")
            .translation("rtsbuilding.configuration.useBlockGhostPreview")
            .define("useBlockGhostPreview", true);

    public static final ModConfigSpec.BooleanValue USE_WIREFRAME_PREVIEW = BUILDER
            .comment("Render wireframe outlines for placement previews and place/break animations.")
            .translation("rtsbuilding.configuration.useWireframePreview")
            .define("useWireframePreview", false);

    public static final ModConfigSpec.BooleanValue USE_RANGE_DESTROY_SKELETON = BUILDER
            .comment("Render merged skeleton borders for chain and range destroy previews.")
            .translation("rtsbuilding.configuration.useRangeDestroySkeleton")
            .define("useRangeDestroySkeleton", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static void setSurvivalProgressionEnabled(boolean enabled) {
        ENABLE_SURVIVAL_PROGRESSION.set(enabled);
        SPEC.save();
    }

    public static int maxActionRadiusBlocks() {
        return MAX_ACTION_RADIUS_BLOCKS.getAsInt();
    }

    public static void setMaxActionRadiusBlocks(int radiusBlocks) {
        MAX_ACTION_RADIUS_BLOCKS.set(Math.max(48, Math.min(512, radiusBlocks)));
        SPEC.save();
    }

    public static boolean areBlueprintsEnabled() {
        return ENABLE_BLUEPRINTS.getAsBoolean();
    }

    public static int maxBlueprintBlocks() {
        return MAX_BLUEPRINT_BLOCKS.getAsInt();
    }

    public static void saveProgressionSettings(boolean survivalEnabled, boolean shareWithTeams, int radiusBlocks,
            boolean blueprintsEnabled, int maxBlueprintBlocks, Map<String, String> costOverrides) {
        saveGeneralSettings(
                survivalEnabled,
                shareWithTeams,
                radiusBlocks,
                blueprintsEnabled,
                maxBlueprintBlocks,
                isBlockGhostPreviewEnabled(),
                isWireframePreviewEnabled(),
                isRangeDestroySkeletonEnabled(),
                costOverrides);
    }

    public static void saveGeneralSettings(boolean survivalEnabled, boolean shareWithTeams, int radiusBlocks,
            boolean blueprintsEnabled, int maxBlueprintBlocks, boolean blockGhostPreview,
            boolean wireframePreview, boolean rangeDestroySkeleton, Map<String, String> costOverrides) {
        ENABLE_SURVIVAL_PROGRESSION.set(survivalEnabled);
        SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.set(shareWithTeams);
        MAX_ACTION_RADIUS_BLOCKS.set(Math.max(48, Math.min(512, radiusBlocks)));
        ENABLE_BLUEPRINTS.set(blueprintsEnabled);
        MAX_BLUEPRINT_BLOCKS.set(Math.max(1, Math.min(200000, maxBlueprintBlocks)));
        USE_BLOCK_GHOST_PREVIEW.set(blockGhostPreview);
        USE_WIREFRAME_PREVIEW.set(wireframePreview);
        USE_RANGE_DESTROY_SKELETON.set(rangeDestroySkeleton);
        setProgressionCostOverrides(costOverrides);
        SPEC.save();
    }

    public static Map<String, String> progressionCostOverrides() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String raw : PROGRESSION_COST_OVERRIDES.get()) {
            if (raw == null) {
                continue;
            }
            int split = raw.indexOf('=');
            if (split <= 0) {
                continue;
            }
            String node = raw.substring(0, split).trim();
            String costs = raw.substring(split + 1).trim();
            if (!node.isBlank()) {
                out.put(node, costs);
            }
        }
        return out;
    }

    public static boolean isBlockGhostPreviewEnabled() {
        return USE_BLOCK_GHOST_PREVIEW.getAsBoolean();
    }

    public static void setBlockGhostPreviewEnabled(boolean enabled) {
        USE_BLOCK_GHOST_PREVIEW.set(enabled);
        SPEC.save();
    }

    public static boolean isWireframePreviewEnabled() {
        return USE_WIREFRAME_PREVIEW.getAsBoolean();
    }

    public static void setWireframePreviewEnabled(boolean enabled) {
        USE_WIREFRAME_PREVIEW.set(enabled);
        SPEC.save();
    }

    public static boolean isRangeDestroySkeletonEnabled() {
        return USE_RANGE_DESTROY_SKELETON.getAsBoolean();
    }

    public static void setRangeDestroySkeletonEnabled(boolean enabled) {
        USE_RANGE_DESTROY_SKELETON.set(enabled);
        SPEC.save();
    }

    public static void setProgressionCostOverride(String nodePath, String costsText) {
        if (nodePath == null || nodePath.isBlank()) {
            return;
        }
        Map<String, String> current = progressionCostOverrides();
        String clean = costsText == null ? "" : costsText.trim();
        if (clean.isBlank()) {
            current.remove(nodePath);
        } else {
            current.put(nodePath, clean);
        }
        setProgressionCostOverrides(current);
        SPEC.save();
    }

    private static void setProgressionCostOverrides(Map<String, String> overrides) {
        Map<String, String> current = overrides == null ? Map.of() : overrides;
        List<String> encoded = new ArrayList<>(current.size());
        for (var entry : current.entrySet()) {
            String node = entry.getKey() == null ? "" : entry.getKey().trim();
            String costs = entry.getValue() == null ? "" : entry.getValue().trim();
            if (!node.isBlank() && !costs.isBlank()) {
                encoded.add(node + "=" + costs);
            }
        }
        PROGRESSION_COST_OVERRIDES.set(encoded);
    }
}

