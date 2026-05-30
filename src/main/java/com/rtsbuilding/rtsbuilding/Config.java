package com.rtsbuilding.rtsbuilding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    public static final ForgeConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

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

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    private static boolean validateItemName(final Object obj) {
        ResourceLocation itemId = obj instanceof String itemName ? ResourceLocation.tryParse(itemName) : null;
        return itemId != null && BuiltInRegistries.ITEM.containsKey(itemId);
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
            Map<String, String> costOverrides) {
        ENABLE_SURVIVAL_PROGRESSION.set(survivalEnabled);
        SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.set(shareWithTeams);
        MAX_ACTION_RADIUS_BLOCKS.set(Math.max(48, Math.min(512, radiusBlocks)));
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

