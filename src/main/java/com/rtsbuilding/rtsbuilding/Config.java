package com.rtsbuilding.rtsbuilding;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    public static final ModConfigSpec.BooleanValue ENABLE_SURVIVAL_PROGRESSION = BUILDER
            .comment("Enable RTS Building survival progression, feature unlocks, home anchors, and progression radius limits.")
            .define("enableSurvivalProgression", false);

    public static final ModConfigSpec.BooleanValue SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS = BUILDER
            .comment("When survival progression is enabled, share unlocked progression nodes and RTS home anchors with the player's FTB Team, or vanilla scoreboard team when FTB Teams is unavailable.")
            .define("shareSurvivalProgressionWithTeams", false);

    public static final ModConfigSpec.IntValue MAX_ACTION_RADIUS_BLOCKS = BUILDER
            .comment("Maximum RTS action radius in blocks. Used directly when survival progression is disabled, and by the Radius Max skill when survival progression is enabled.")
            .defineInRange("maxActionRadiusBlocks", 128, 48, 512);

    public static final ModConfigSpec.BooleanValue ENABLE_BLUEPRINTS = BUILDER
            .comment("Enable the RTS blueprint library tab, local blueprint upload, and server-side blueprint placement.")
            .define("enableBlueprints", true);

    public static final ModConfigSpec.IntValue MAX_BLUEPRINT_BLOCKS = BUILDER
            .comment("Maximum non-air blocks allowed in one RTS blueprint import, capture, or placement job.")
            .defineInRange("maxBlueprintBlocks", 20000, 1, 200000);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROGRESSION_COST_OVERRIDES = BUILDER
            .comment("Skill material overrides. Format: node_path=minecraft:item:count,minecraft:item2:count. Example: ultimine=minecraft:diamond_pickaxe:1,minecraft:redstone_block:1")
            .defineListAllowEmpty("progressionCostOverrides", List.of(), () -> "", obj -> obj instanceof String);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }

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
        ENABLE_SURVIVAL_PROGRESSION.set(survivalEnabled);
        SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.set(shareWithTeams);
        MAX_ACTION_RADIUS_BLOCKS.set(Math.max(48, Math.min(512, radiusBlocks)));
        ENABLE_BLUEPRINTS.set(blueprintsEnabled);
        MAX_BLUEPRINT_BLOCKS.set(Math.max(1, Math.min(200000, maxBlueprintBlocks)));
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

