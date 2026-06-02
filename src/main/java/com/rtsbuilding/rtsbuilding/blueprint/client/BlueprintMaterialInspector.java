package com.rtsbuilding.rtsbuilding.blueprint.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.rtsbuilding.rtsbuilding.client.ClientRtsController;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelUi.text;

/**
 * Computes the material, unsupported-block, and missing-mod summaries shown by
 * the blueprint panel.
 */
final class BlueprintMaterialInspector {
    private BlueprintMaterialInspector() {
    }

    static String materialSummary(BlueprintEntry entry, ClientRtsController controller, BuildStats stats) {
        if (isCreativePlayer()) {
            if (stats.missingBlockTypes() > 0) {
                return text("screen.rtsbuilding.blueprints.missing_blocks_progress", stats.percent(), stats.buildable(), stats.total());
            }
            return text("screen.rtsbuilding.blueprints.materials_creative");
        }
        if (stats.percent() >= 100) {
            return text("screen.rtsbuilding.blueprints.materials_ready");
        }
        return text("screen.rtsbuilding.blueprints.materials_progress", stats.percent(), stats.buildable(), stats.total());
    }

    static List<MaterialLine> missingMaterialLines(BlueprintEntry entry, ClientRtsController controller) {
        List<MaterialLine> out = new ArrayList<>();
        if (entry == null || controller == null) {
            return out;
        }
        for (Map.Entry<ResourceLocation, Integer> material : entry.requiredItems().entrySet()) {
            String itemId = material.getKey().toString();
            long available = controller.getStorageTotalCount(itemId);
            int required = Math.max(0, material.getValue());
            if (available >= required) {
                continue;
            }
            if (!BuiltInRegistries.ITEM.containsKey(material.getKey())) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(material.getKey());
            ItemStack stack = new ItemStack(item);
            out.add(new MaterialLine(stack, stack.getHoverName().getString(), available, required));
        }
        return out;
    }

    static List<UnsupportedLine> unsupportedBlockLines(BlueprintEntry entry) {
        if (entry == null || entry.unsupportedBlocks().isEmpty()) {
            return List.of();
        }
        List<UnsupportedLine> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entryLine : entry.unsupportedBlocks().entrySet()) {
            out.add(new UnsupportedLine(entryLine.getKey(), entryLine.getValue()));
        }
        return out;
    }

    static List<MissingBlueprintBlockLine> missingBlueprintBlockLines(BlueprintEntry entry) {
        if (entry == null || entry.missingBlueprintBlocks().isEmpty()) {
            return List.of();
        }
        List<MissingBlueprintBlockLine> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entryLine : entry.missingBlueprintBlocks().entrySet()) {
            String blockId = entryLine.getKey();
            out.add(new MissingBlueprintBlockLine(blockId, entryLine.getValue(), namespaceOf(blockId)));
        }
        return out;
    }

    static List<DetailLine> detailLines(BlueprintEntry entry, ClientRtsController controller) {
        List<DetailLine> out = new ArrayList<>();
        Map<String, Integer> missingMods = missingModCounts(entry);
        for (Map.Entry<String, Integer> mod : missingMods.entrySet()) {
            out.add(new DetailLine(
                    ItemStack.EMPTY,
                    text("screen.rtsbuilding.blueprints.details_missing_mod", mod.getKey()),
                    text("screen.rtsbuilding.blueprints.details_missing_mod_count", mod.getValue()),
                    0xFFFF9E88));
        }
        for (MissingBlueprintBlockLine line : missingBlueprintBlockLines(entry)) {
            out.add(new DetailLine(
                    ItemStack.EMPTY,
                    line.blockId(),
                    text("screen.rtsbuilding.blueprints.details_missing_block_count", line.count()),
                    0xFFFF9E88));
        }
        if (!isCreativePlayer()) {
            for (UnsupportedLine line : unsupportedBlockLines(entry)) {
                out.add(new DetailLine(
                        ItemStack.EMPTY,
                        line.label(),
                        text("screen.rtsbuilding.blueprints.details_unsupported_count", line.count()),
                        0xFFFF9E88));
            }
            for (MaterialLine line : missingMaterialLines(entry, controller)) {
                long missingCount = Math.max(0L, line.required() - line.available());
                out.add(new DetailLine(
                        line.preview(),
                        line.label(),
                        text("screen.rtsbuilding.blueprints.details_count", missingCount, line.available(), line.required()),
                        0xFFFFC06C));
            }
        }
        return out;
    }

    static BuildStats buildStats(BlueprintEntry entry, ClientRtsController controller) {
        if (entry == null || !entry.error().isBlank()) {
            return new BuildStats(0, 0, 0, 0, 0, 0);
        }
        int total = Math.max(0, entry.blockCount());
        if (total == 0) {
            return new BuildStats(100, 0, 0, 0, 0, 0);
        }
        int missingBlockTypes = missingBlueprintBlockLines(entry).size();
        int missingBlockCount = missingBlueprintBlockCount(entry);
        if (isCreativePlayer()) {
            int buildable = Math.max(0, total - missingBlockCount);
            int percent = (int) Mth.clamp(buildable * 100L / total, 0L, 100L);
            return new BuildStats(percent, buildable, total, 0, 0, missingBlockTypes);
        }
        long buildable = 0L;
        int missingTypes = 0;
        for (Map.Entry<ResourceLocation, Integer> material : entry.requiredItems().entrySet()) {
            int required = Math.max(0, material.getValue());
            long available = controller == null ? 0L : controller.getStorageTotalCount(material.getKey().toString());
            buildable += Math.min((long) required, Math.max(0L, available));
            if (available < required) {
                missingTypes++;
            }
        }
        int unsupportedTypes = unsupportedBlockLines(entry).size();
        int percent = (int) Mth.clamp(buildable * 100L / total, 0L, 100L);
        return new BuildStats(percent, (int) Math.min(buildable, total), total, missingTypes, unsupportedTypes, missingBlockTypes);
    }

    static boolean hasEnoughMaterials(BlueprintEntry entry, ClientRtsController controller) {
        if (entry == null || !entry.error().isBlank() || controller == null) {
            return false;
        }
        if (!entry.missingBlueprintBlocks().isEmpty()) {
            return false;
        }
        if (isCreativePlayer()) {
            return true;
        }
        if (!entry.unsupportedBlocks().isEmpty()) {
            return false;
        }
        for (Map.Entry<ResourceLocation, Integer> material : entry.requiredItems().entrySet()) {
            if (controller.getStorageTotalCount(material.getKey().toString()) < material.getValue()) {
                return false;
            }
        }
        return true;
    }

    static boolean isCreativePlayer() {
        return Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
    }

    private static int missingBlueprintBlockCount(BlueprintEntry entry) {
        if (entry == null || entry.missingBlueprintBlocks().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int value : entry.missingBlueprintBlocks().values()) {
            count += Math.max(0, value);
        }
        return count;
    }

    private static Map<String, Integer> missingModCounts(BlueprintEntry entry) {
        if (entry == null || entry.missingBlueprintBlocks().isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Integer> missing : entry.missingBlueprintBlocks().entrySet()) {
            String namespace = namespaceOf(missing.getKey());
            if (namespace.isBlank() || "minecraft".equals(namespace)) {
                continue;
            }
            out.merge(namespace, Math.max(0, missing.getValue()), Integer::sum);
        }
        return out;
    }

    private static String namespaceOf(String blockId) {
        if (blockId == null) {
            return "";
        }
        int colon = blockId.indexOf(':');
        return colon > 0 ? blockId.substring(0, colon) : "";
    }
}

record MaterialLine(ItemStack preview, String label, long available, int required) {
}

record UnsupportedLine(String label, int count) {
}

record MissingBlueprintBlockLine(String blockId, int count, String namespace) {
}

record DetailLine(ItemStack preview, String label, String detail, int color) {
}

record BuildStats(
        int percent,
        int buildable,
        int total,
        int missingTypes,
        int unsupportedTypes,
        int missingBlockTypes) {
}
