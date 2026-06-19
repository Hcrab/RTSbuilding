package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.FluidEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanelUi.text;

/**
 * Computes the material, unsupported-block, and missing-mod summaries shown by
 * the blueprint panel.
 */
final class BlueprintMaterialInspector {
    private static final int WATER_BUCKET_THRESHOLD = 2;

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

    static List<MaterialLine> materialLines(BlueprintEntry entry, ClientRtsController controller) {
        List<MaterialLine> out = new ArrayList<>();
        if (entry == null) {
            return out;
        }
        for (Map.Entry<ResourceLocation, Integer> material : entry.requiredItems().entrySet()) {
            String itemId = material.getKey().toString();
            int required = Math.max(0, material.getValue());
            if (!BuiltInRegistries.ITEM.containsKey(material.getKey())) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(material.getKey());
            long available = availableItemCount(controller, itemId, item);
            ItemStack stack = new ItemStack(item);
            out.add(new MaterialLine(stack, stack.getHoverName().getString(), displayAvailable(available, required), required));
        }
        addFluidLines(out, entry, controller);
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
                    text("screen.rtsbuilding.blueprints.details_missing_mod_count"),
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
        }
        for (MaterialLine line : materialLines(entry, controller)) {
            boolean enough = line.available() >= line.required();
            out.add(new DetailLine(
                    line.preview(),
                    line.label(),
                    text("screen.rtsbuilding.blueprints.details_count", line.available(), line.required()),
                    enough ? 0xFF8EEA9B : 0xFFFFC06C));
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
        long buildable = buildableBlockCount(entry, controller);
        int missingTypes = 0;
        for (Map.Entry<ResourceLocation, Integer> material : entry.requiredItems().entrySet()) {
            int required = Math.max(0, material.getValue());
            long available = availableItemCount(controller, material.getKey().toString(), BuiltInRegistries.ITEM.get(material.getKey()));
            if (available < required) {
                missingTypes++;
            }
        }
        FluidRequirement fluids = fluidRequirement(entry);
        if (fluids.waterBlocks() > 0) {
            boolean ready = availableWaterBuckets(controller) >= WATER_BUCKET_THRESHOLD;
            if (!ready) {
                missingTypes++;
            }
        }
        if (fluids.lavaBlocks() > 0) {
            long availableLava = availableFluidBuckets(controller, Fluids.LAVA);
            if (availableLava < fluids.lavaBlocks()) {
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
            if (availableItemCount(controller, material.getKey().toString(), BuiltInRegistries.ITEM.get(material.getKey()))
                    < material.getValue()) {
                return false;
            }
        }
        FluidRequirement fluids = fluidRequirement(entry);
        if (fluids.waterBlocks() > 0 && availableWaterBuckets(controller) < WATER_BUCKET_THRESHOLD) {
            return false;
        }
        if (fluids.lavaBlocks() > 0 && availableFluidBuckets(controller, Fluids.LAVA) < fluids.lavaBlocks()) {
            return false;
        }
        return true;
    }

    private static long buildableBlockCount(BlueprintEntry entry, ClientRtsController controller) {
        if (entry == null || entry.blueprint() == null) {
            return 0L;
        }
        Map<ResourceLocation, Long> remainingItems = new LinkedHashMap<>();
        for (ResourceLocation id : entry.requiredItems().keySet()) {
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                remainingItems.put(id, availableItemCount(controller, id.toString(), BuiltInRegistries.ITEM.get(id)));
            }
        }
        boolean waterReady = availableWaterBuckets(controller) >= WATER_BUCKET_THRESHOLD;
        long remainingLava = availableFluidBuckets(controller, Fluids.LAVA);
        long buildable = 0L;
        for (var block : entry.blueprint().blocks()) {
            if (block == null || block.isMissingBlock() || block.state() == null) {
                continue;
            }
            if (block.state().getFluidState().is(FluidTags.WATER)) {
                if (waterReady) {
                    buildable++;
                }
                continue;
            }
            if (block.state().getFluidState().is(FluidTags.LAVA)) {
                if (remainingLava > 0L) {
                    remainingLava--;
                    buildable++;
                }
                continue;
            }
            List<ResourceLocation> ids = RtsBlueprint.materialItemIds(block);
            if (ids.isEmpty()) {
                continue;
            }
            boolean ready = true;
            for (ResourceLocation id : ids) {
                if (remainingItems.getOrDefault(id, 0L) <= 0L) {
                    ready = false;
                    break;
                }
            }
            if (!ready) {
                continue;
            }
            for (ResourceLocation id : ids) {
                remainingItems.put(id, remainingItems.getOrDefault(id, 0L) - 1L);
            }
            buildable++;
        }
        return buildable;
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

    private static void addFluidLines(List<MaterialLine> out, BlueprintEntry entry, ClientRtsController controller) {
        FluidRequirement fluids = fluidRequirement(entry);
        if (fluids.waterBlocks() > 0) {
            long available = displayAvailable(availableWaterBuckets(controller), WATER_BUCKET_THRESHOLD);
            out.add(new MaterialLine(
                    new ItemStack(Items.WATER_BUCKET),
                    new ItemStack(Items.WATER_BUCKET).getHoverName().getString(),
                    available,
                    WATER_BUCKET_THRESHOLD));
        }
        if (fluids.lavaBlocks() > 0) {
            long available = availableFluidBuckets(controller, Fluids.LAVA);
            out.add(new MaterialLine(
                    new ItemStack(Items.LAVA_BUCKET),
                    new ItemStack(Items.LAVA_BUCKET).getHoverName().getString(),
                    displayAvailable(available, fluids.lavaBlocks()),
                    fluids.lavaBlocks()));
        }
    }

    private static long availableItemCount(ClientRtsController controller, String itemId, Item item) {
        if (isCreativePlayer()) {
            return Long.MAX_VALUE;
        }
        long total = controller == null ? 0L : controller.getStorageTotalCount(itemId);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && item != null && item != Items.AIR) {
            for (ItemStack stack : minecraft.player.getInventory().items) {
                if (!stack.isEmpty() && stack.getItem() == item) {
                    total = saturatedAdd(total, stack.getCount());
                }
            }
        }
        return total;
    }

    private static long availableWaterBuckets(ClientRtsController controller) {
        if (isCreativePlayer()) {
            return WATER_BUCKET_THRESHOLD;
        }
        long bucketItems = availableItemCount(controller, BuiltInRegistries.ITEM.getKey(Items.WATER_BUCKET).toString(), Items.WATER_BUCKET);
        long storedFluidBuckets = availableFluidBuckets(controller, Fluids.WATER);
        return saturatedAdd(bucketItems, storedFluidBuckets);
    }

    private static long availableFluidBuckets(ClientRtsController controller, Fluid fluid) {
        if (isCreativePlayer()) {
            return Long.MAX_VALUE;
        }
        if (controller == null || fluid == null) {
            return 0L;
        }
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null) {
            return 0L;
        }
        long amount = 0L;
        for (FluidEntry entry : controller.getFluidEntries()) {
            if (id.toString().equals(entry.fluidId())) {
                amount = saturatedAdd(amount, entry.amount());
            }
        }
        return amount / FluidType.BUCKET_VOLUME;
    }

    private static FluidRequirement fluidRequirement(BlueprintEntry entry) {
        if (entry == null || entry.blueprint() == null) {
            return FluidRequirement.EMPTY;
        }
        int water = 0;
        int lava = 0;
        for (var block : entry.blueprint().blocks()) {
            if (block == null || block.isMissingBlock() || block.state() == null) {
                continue;
            }
            if (block.state().getFluidState().is(FluidTags.WATER)) {
                water++;
            } else if (block.state().getFluidState().is(FluidTags.LAVA)) {
                lava++;
            }
        }
        return new FluidRequirement(water, lava);
    }

    private static long displayAvailable(long available, long required) {
        return isCreativePlayer() ? required : Math.min(Math.max(0L, available), required);
    }

    private static long saturatedAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static String namespaceOf(String blockId) {
        if (blockId == null) {
            return "";
        }
        int colon = blockId.indexOf(':');
        return colon > 0 ? blockId.substring(0, colon) : "";
    }
}

record FluidRequirement(int waterBlocks, int lavaBlocks) {
    static final FluidRequirement EMPTY = new FluidRequirement(0, 0);
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
