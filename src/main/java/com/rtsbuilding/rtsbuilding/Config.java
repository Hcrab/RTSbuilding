package com.rtsbuilding.rtsbuilding;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLE_SURVIVAL_PROGRESSION = BUILDER
            .comment("Enable RTS Building survival progression, feature unlocks, home anchors, and progression radius limits.")
            .define("enableSurvivalProgression", false);

    public static final ForgeConfigSpec.BooleanValue SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS = BUILDER
            .comment("When survival progression is enabled, share unlocked progression nodes and RTS home anchors with the player's FTB Team, or vanilla scoreboard team when FTB Teams is unavailable.")
            .define("shareSurvivalProgressionWithTeams", false);

    public static final ForgeConfigSpec.IntValue MAX_ACTION_RADIUS_BLOCKS = BUILDER
            .comment("Maximum RTS action radius in blocks. Used directly when survival progression is disabled, and by the Radius Max skill when survival progression is enabled.")
            .defineInRange("maxActionRadiusBlocks", 128, 48, 512);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PROGRESSION_COST_OVERRIDES = BUILDER
            .comment("Skill material overrides. Format: node_path=minecraft:item:count,minecraft:item2:count. Example: ultimine=minecraft:diamond_pickaxe:1,minecraft:redstone_block:1")
            .defineListAllowEmpty("progressionCostOverrides", List.of(), obj -> obj instanceof String);

    public static final ForgeConfigSpec.BooleanValue ENABLE_BLUEPRINTS = BUILDER
            .comment("Enable the experimental RTS blueprint panel and direct blueprint placement.")
            .define("enableBlueprints", true);

    public static final ForgeConfigSpec.IntValue MAX_BLUEPRINT_BLOCKS = BUILDER
            .comment("Maximum non-air blocks allowed in one RTS blueprint import, capture, or placement job.")
            .defineInRange("maxBlueprintBlocks", 20000, 1, 200000);

    public static final ForgeConfigSpec.BooleanValue USE_BLOCK_GHOST_PREVIEW = BUILDER
            .comment("Render translucent block ghost models for placement previews before the player confirms placement.")
            .define("useBlockGhostPreview", true);

    public static final ForgeConfigSpec.BooleanValue USE_PLACE_BLOCK_GHOST_ANIMATION = BUILDER
            .comment("Render translucent grow-in block ghosts after server-confirmed block placement.")
            .define("usePlaceBlockGhostAnimation", true);

    public static final ForgeConfigSpec.BooleanValue USE_DESTROY_BLOCK_GHOST_ANIMATION = BUILDER
            .comment("Render translucent shrink-out block ghosts after server-confirmed block destruction.")
            .define("useDestroyBlockGhostAnimation", true);

    public static final ForgeConfigSpec.BooleanValue USE_WIREFRAME_PREVIEW = BUILDER
            .comment("Render wireframe outlines for placement previews before the player confirms placement.")
            .define("useWireframePreview", false);

    public static final ForgeConfigSpec.BooleanValue USE_PLACE_WIREFRAME_ANIMATION = BUILDER
            .comment("Render grow-in wireframe outlines after server-confirmed block placement.")
            .define("usePlaceWireframeAnimation", false);

    public static final ForgeConfigSpec.BooleanValue USE_DESTROY_WIREFRAME_ANIMATION = BUILDER
            .comment("Render shrink-out wireframe outlines after server-confirmed block destruction.")
            .define("useDestroyWireframeAnimation", false);

    public static final ForgeConfigSpec.BooleanValue USE_RANGE_DESTROY_SKELETON = BUILDER
            .comment("Render merged skeleton borders for non-chain range destroy previews. Chain mining always uses the skeleton style.")
            .define("useRangeDestroySkeleton", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    public static void setSurvivalProgressionEnabled(boolean enabled) {
        ENABLE_SURVIVAL_PROGRESSION.set(enabled);
        SPEC.save();
    }

    public static int maxActionRadiusBlocks() {
        return MAX_ACTION_RADIUS_BLOCKS.get();
    }

    public static void setMaxActionRadiusBlocks(int radiusBlocks) {
        MAX_ACTION_RADIUS_BLOCKS.set(Math.max(48, Math.min(512, radiusBlocks)));
        SPEC.save();
    }

    public static void saveProgressionSettings(boolean survivalEnabled, boolean shareWithTeams, int radiusBlocks,
            boolean blueprintsEnabled, int maxBlueprintBlocks, Map<String, String> costOverrides) {
        saveGeneralSettings(
                survivalEnabled,
                shareWithTeams,
                radiusBlocks,
                blueprintsEnabled,
                maxBlueprintBlocks,
                isPlacementBlockGhostPreviewEnabled(),
                isPlaceBlockGhostAnimationEnabled(),
                isDestroyBlockGhostAnimationEnabled(),
                isPlacementWireframePreviewEnabled(),
                isPlaceWireframeAnimationEnabled(),
                isDestroyWireframeAnimationEnabled(),
                isRangeDestroySkeletonEnabled(),
                costOverrides);
    }

    public static void saveGeneralSettings(boolean survivalEnabled, boolean shareWithTeams, int radiusBlocks,
            boolean blueprintsEnabled, int maxBlueprintBlocks, boolean placementBlockGhostPreview,
            boolean placeBlockGhostAnimation, boolean destroyBlockGhostAnimation, boolean placementWireframePreview,
            boolean placeWireframeAnimation, boolean destroyWireframeAnimation, boolean rangeDestroySkeleton,
            Map<String, String> costOverrides) {
        ENABLE_SURVIVAL_PROGRESSION.set(survivalEnabled);
        SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.set(shareWithTeams);
        MAX_ACTION_RADIUS_BLOCKS.set(Math.max(48, Math.min(512, radiusBlocks)));
        ENABLE_BLUEPRINTS.set(blueprintsEnabled);
        MAX_BLUEPRINT_BLOCKS.set(Math.max(1, Math.min(200000, maxBlueprintBlocks)));
        USE_BLOCK_GHOST_PREVIEW.set(placementBlockGhostPreview);
        USE_PLACE_BLOCK_GHOST_ANIMATION.set(placeBlockGhostAnimation);
        USE_DESTROY_BLOCK_GHOST_ANIMATION.set(destroyBlockGhostAnimation);
        USE_WIREFRAME_PREVIEW.set(placementWireframePreview);
        USE_PLACE_WIREFRAME_ANIMATION.set(placeWireframeAnimation);
        USE_DESTROY_WIREFRAME_ANIMATION.set(destroyWireframeAnimation);
        USE_RANGE_DESTROY_SKELETON.set(rangeDestroySkeleton);
        setProgressionCostOverrides(costOverrides);
        SPEC.save();
    }

    public static boolean areBlueprintsEnabled() {
        return ENABLE_BLUEPRINTS.get();
    }

    public static int maxBlueprintBlocks() {
        return MAX_BLUEPRINT_BLOCKS.get();
    }

    public static boolean isBlockGhostPreviewEnabled() {
        return isPlacementBlockGhostPreviewEnabled();
    }

    public static boolean isPlacementBlockGhostPreviewEnabled() {
        return USE_BLOCK_GHOST_PREVIEW.get();
    }

    public static void setBlockGhostPreviewEnabled(boolean enabled) {
        setPlacementBlockGhostPreviewEnabled(enabled);
    }

    public static void setPlacementBlockGhostPreviewEnabled(boolean enabled) {
        USE_BLOCK_GHOST_PREVIEW.set(enabled);
        SPEC.save();
    }

    public static boolean isPlaceBlockGhostAnimationEnabled() {
        return USE_PLACE_BLOCK_GHOST_ANIMATION.get();
    }

    public static void setPlaceBlockGhostAnimationEnabled(boolean enabled) {
        USE_PLACE_BLOCK_GHOST_ANIMATION.set(enabled);
        SPEC.save();
    }

    public static boolean isDestroyBlockGhostAnimationEnabled() {
        return USE_DESTROY_BLOCK_GHOST_ANIMATION.get();
    }

    public static void setDestroyBlockGhostAnimationEnabled(boolean enabled) {
        USE_DESTROY_BLOCK_GHOST_ANIMATION.set(enabled);
        SPEC.save();
    }

    public static boolean isWireframePreviewEnabled() {
        return isPlacementWireframePreviewEnabled();
    }

    public static boolean isPlacementWireframePreviewEnabled() {
        return USE_WIREFRAME_PREVIEW.get();
    }

    public static void setWireframePreviewEnabled(boolean enabled) {
        setPlacementWireframePreviewEnabled(enabled);
    }

    public static void setPlacementWireframePreviewEnabled(boolean enabled) {
        USE_WIREFRAME_PREVIEW.set(enabled);
        SPEC.save();
    }

    public static boolean isPlaceWireframeAnimationEnabled() {
        return USE_PLACE_WIREFRAME_ANIMATION.get();
    }

    public static void setPlaceWireframeAnimationEnabled(boolean enabled) {
        USE_PLACE_WIREFRAME_ANIMATION.set(enabled);
        SPEC.save();
    }

    public static boolean isDestroyWireframeAnimationEnabled() {
        return USE_DESTROY_WIREFRAME_ANIMATION.get();
    }

    public static void setDestroyWireframeAnimationEnabled(boolean enabled) {
        USE_DESTROY_WIREFRAME_ANIMATION.set(enabled);
        SPEC.save();
    }

    public static boolean isRangeDestroySkeletonEnabled() {
        return USE_RANGE_DESTROY_SKELETON.get();
    }

    public static void setRangeDestroySkeletonEnabled(boolean enabled) {
        USE_RANGE_DESTROY_SKELETON.set(enabled);
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

