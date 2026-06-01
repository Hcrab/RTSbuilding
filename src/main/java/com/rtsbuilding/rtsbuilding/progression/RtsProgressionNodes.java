package com.rtsbuilding.rtsbuilding.progression;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public final class RtsProgressionNodes {
    public static final ResourceLocation CAMERA_CORE = id("camera_core");
    public static final ResourceLocation RADIUS_1 = id("radius_1");
    public static final ResourceLocation RADIUS_2 = id("radius_2");
    public static final ResourceLocation RADIUS_3 = id("radius_3");
    public static final ResourceLocation RADIUS_MAX = id("radius_max");
    public static final ResourceLocation STORAGE_LINK = id("storage_link");
    public static final ResourceLocation REMOTE_PLACE = id("remote_place");
    public static final ResourceLocation REMOTE_BREAK = id("remote_break");
    public static final ResourceLocation ROTATE_BLOCK = id("rotate_block");
    public static final ResourceLocation AUTO_STORE_MINED = id("auto_store_mined");
    public static final ResourceLocation FUNNEL = id("funnel");
    public static final ResourceLocation FLUID_BUFFER = id("fluid_buffer");
    public static final ResourceLocation REMOTE_GUI = id("remote_gui");
    public static final ResourceLocation CRAFT_TERMINAL = id("craft_terminal");
    public static final ResourceLocation JEI_TRANSFER = id("jei_transfer");
    public static final ResourceLocation ULTIMINE = id("ultimine");
    public static final ResourceLocation FIELD_DEPLOYMENT = id("field_deployment");

    private static final Map<ResourceLocation, RtsProgressionNode> NODES = buildNodes();
    private static volatile Map<String, String> syncedCostOverrides = Map.of();
    private static volatile boolean hasSyncedCostOverrides;

    private RtsProgressionNodes() {
    }

    public static RtsProgressionNode get(ResourceLocation id) {
        return NODES.get(id);
    }

    public static Collection<RtsProgressionNode> all() {
        return NODES.values();
    }

    public static List<RtsIngredientCost> costsFor(RtsProgressionNode node) {
        if (node == null) {
            return List.of();
        }
        String override = syncedCostOverrides.get(node.id().getPath());
        if (override == null) {
            override = Config.progressionCostOverrides().get(node.id().getPath());
        }
        if (override == null) {
            return node.costs();
        }
        return parseCostText(override, node.costs());
    }

    public static List<RtsIngredientCost> syncedCostsFor(RtsProgressionNode node) {
        if (node == null) {
            return List.of();
        }
        if (!hasSyncedCostOverrides) {
            return costsFor(node);
        }
        String override = syncedCostOverrides.get(node.id().getPath());
        return override == null ? node.costs() : parseCostText(override, node.costs());
    }

    public static void applySyncedCostOverrides(List<String> overrides) {
        hasSyncedCostOverrides = true;
        if (overrides == null || overrides.isEmpty()) {
            syncedCostOverrides = Map.of();
            return;
        }
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (String raw : overrides) {
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
        syncedCostOverrides = Map.copyOf(out);
    }

    public static String costTextFor(RtsProgressionNode node) {
        return formatCostText(costsFor(node));
    }

    public static boolean contains(ResourceLocation id) {
        return NODES.containsKey(id);
    }

    private static Map<ResourceLocation, RtsProgressionNode> buildNodes() {
        LinkedHashMap<ResourceLocation, RtsProgressionNode> nodes = new LinkedHashMap<>();

        add(nodes, CAMERA_CORE, List.of(), List.of(),
                List.of(
                        RtsUnlockEffect.unlock(RtsFeature.CAMERA),
                        RtsUnlockEffect.unlock(RtsFeature.INTERACT),
                        RtsUnlockEffect.radius(16)),
                0, 0);

        add(nodes, RADIUS_1, List.of(CAMERA_CORE), cost("minecraft:glass", 8),
                List.of(RtsUnlockEffect.radius(16)), 1, 0);
        add(nodes, RADIUS_2, List.of(RADIUS_1), cost("minecraft:redstone", 12),
                List.of(RtsUnlockEffect.radius(32)), 2, 0);
        add(nodes, RADIUS_3, List.of(RADIUS_2), cost("minecraft:ender_pearl", 2),
                List.of(RtsUnlockEffect.radius(48)), 3, 0);
        add(nodes, RADIUS_MAX, List.of(RADIUS_3), cost("minecraft:netherite_ingot", 1),
                List.of(RtsUnlockEffect.radius(Config.maxActionRadiusBlocks())), 4, 0);

        add(nodes, STORAGE_LINK, List.of(CAMERA_CORE), cost("minecraft:chest", 2, "minecraft:redstone", 8),
                List.of(RtsUnlockEffect.unlock(RtsFeature.LINK_STORAGE), RtsUnlockEffect.unlock(RtsFeature.STORAGE_BROWSER)), 1, 1);

        add(nodes, REMOTE_PLACE, List.of(STORAGE_LINK), cost("minecraft:copper_ingot", 16),
                List.of(RtsUnlockEffect.unlock(RtsFeature.REMOTE_PLACE)), 2, 1);
        add(nodes, REMOTE_BREAK, List.of(REMOTE_PLACE), cost("minecraft:iron_pickaxe", 1, "minecraft:redstone", 8),
                List.of(RtsUnlockEffect.unlock(RtsFeature.REMOTE_BREAK)), 3, 1);
        add(nodes, ROTATE_BLOCK, List.of(CAMERA_CORE), cost("minecraft:stick", 4, "minecraft:copper_ingot", 8),
                List.of(RtsUnlockEffect.unlock(RtsFeature.ROTATE_BLOCK)), 1, -1);

        add(nodes, AUTO_STORE_MINED, List.of(STORAGE_LINK), cost("minecraft:hopper", 1),
                List.of(RtsUnlockEffect.unlock(RtsFeature.AUTO_STORE_MINED_DROPS)), 2, 2);
        add(nodes, FUNNEL, List.of(STORAGE_LINK), cost("minecraft:hopper", 4, "minecraft:redstone", 8),
                List.of(RtsUnlockEffect.unlock(RtsFeature.FUNNEL)), 3, 2);

        add(nodes, FLUID_BUFFER, List.of(STORAGE_LINK), cost("minecraft:bucket", 4, "minecraft:iron_ingot", 16),
                List.of(RtsUnlockEffect.unlock(RtsFeature.FLUID_HANDLING), RtsUnlockEffect.fluidCapacityBuckets(100)), 2, 3);

        add(nodes, REMOTE_GUI, List.of(STORAGE_LINK), cost("minecraft:comparator", 1, "minecraft:redstone", 16),
                List.of(RtsUnlockEffect.unlock(RtsFeature.REMOTE_GUI_BINDING)), 2, -1);
        add(nodes, CRAFT_TERMINAL, List.of(STORAGE_LINK), cost("minecraft:crafting_table", 1, "minecraft:iron_ingot", 12),
                List.of(RtsUnlockEffect.unlock(RtsFeature.CRAFT_TERMINAL)), 3, -1);
        add(nodes, JEI_TRANSFER, List.of(CRAFT_TERMINAL), cost("minecraft:book", 1, "minecraft:lapis_lazuli", 8),
                List.of(RtsUnlockEffect.unlock(RtsFeature.JEI_TRANSFER)), 4, -1);

        add(nodes, ULTIMINE, List.of(AUTO_STORE_MINED), cost("minecraft:diamond_pickaxe", 1, "minecraft:redstone_block", 1),
                List.of(RtsUnlockEffect.unlock(RtsFeature.ULTIMINE), RtsUnlockEffect.ultimineLimit(64)), 3, 3);
        add(nodes, FIELD_DEPLOYMENT, List.of(RADIUS_MAX), cost("minecraft:dragon_head", 1),
                List.of(RtsUnlockEffect.bypassHomeRadius()), 5, 0);

        return Collections.unmodifiableMap(nodes);
    }

    private static void add(Map<ResourceLocation, RtsProgressionNode> nodes, ResourceLocation id,
            List<ResourceLocation> dependencies, List<RtsIngredientCost> costs, List<RtsUnlockEffect> effects,
            int x, int y) {
        nodes.put(id, new RtsProgressionNode(
                id,
                "rtsbuilding.progression." + id.getPath(),
                "rtsbuilding.progression." + id.getPath() + ".desc",
                dependencies,
                costs,
                effects,
                x,
                y));
    }

    private static List<RtsIngredientCost> cost(Object... parts) {
        if (parts.length % 2 != 0) {
            throw new IllegalArgumentException("Cost arguments must be item/count pairs");
        }
        java.util.ArrayList<RtsIngredientCost> out = new java.util.ArrayList<>(parts.length / 2);
        for (int i = 0; i < parts.length; i += 2) {
            out.add(new RtsIngredientCost(ResourceLocation.parse((String) parts[i]), (Integer) parts[i + 1]));
        }
        return List.copyOf(out);
    }

    private static List<RtsIngredientCost> parseCostText(String text, List<RtsIngredientCost> fallback) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        java.util.ArrayList<RtsIngredientCost> out = new java.util.ArrayList<>();
        String[] parts = text.split(",");
        for (String rawPart : parts) {
            String part = rawPart.trim();
            if (part.isBlank()) {
                continue;
            }
            int split = part.lastIndexOf(':');
            if (split <= 0 || split >= part.length() - 1) {
                return fallback;
            }
            try {
                ResourceLocation itemId = ResourceLocation.parse(part.substring(0, split));
                int count = Math.max(1, Integer.parseInt(part.substring(split + 1)));
                out.add(new RtsIngredientCost(itemId, count));
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
        return List.copyOf(out);
    }

    public static String formatCostText(List<RtsIngredientCost> costs) {
        if (costs == null || costs.isEmpty()) {
            return "";
        }
        java.util.ArrayList<String> parts = new java.util.ArrayList<>(costs.size());
        for (RtsIngredientCost cost : costs) {
            parts.add(cost.itemId() + ":" + cost.count());
        }
        return String.join(",", parts);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, path);
    }
}
