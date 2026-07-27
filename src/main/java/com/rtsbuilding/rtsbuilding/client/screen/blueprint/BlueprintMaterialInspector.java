package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.FluidEntry;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintMaterialUiState;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

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
            if (!ForgeRegistries.ITEMS.containsKey(material.getKey())) {
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(material.getKey());
            long available = availableItemCount(controller, itemId, item);
            ItemStack stack = new ItemStack(item);
            out.add(new MaterialLine(stack, stack.getDisplayName(), displayAvailable(available, required), required));
        }
        addFluidLines(out, entry, controller);
        return out;
    }

    static List<UnsupportedLine> unsupportedBlockLines(BlueprintEntry entry) {
        if (entry == null || entry.unsupportedBlocks().isEmpty()) {
            return Collections.emptyList();
        }
        List<UnsupportedLine> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entryLine : entry.unsupportedBlocks().entrySet()) {
            out.add(new UnsupportedLine(entryLine.getKey(), entryLine.getValue()));
        }
        return out;
    }

    static List<MissingBlueprintBlockLine> missingBlueprintBlockLines(BlueprintEntry entry) {
        if (entry == null || entry.missingBlueprintBlocks().isEmpty()) {
            return Collections.emptyList();
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
                    BlueprintMaterialUiState.Tone.MISSING));
        }
        if (!isCreativePlayer()) {
            for (UnsupportedLine line : unsupportedBlockLines(entry)) {
                out.add(new DetailLine(
                        ItemStack.EMPTY,
                        line.label(),
                        text("screen.rtsbuilding.blueprints.details_unsupported_count", line.count()),
                        BlueprintMaterialUiState.Tone.MISSING));
            }
        }
        for (MaterialLine line : materialLines(entry, controller)) {
            boolean enough = line.available() >= line.required();
            out.add(new DetailLine(
                    line.preview(),
                    line.label(),
                    text("screen.rtsbuilding.blueprints.details_count", line.available(), line.required()),
                    enough ? BlueprintMaterialUiState.Tone.READY
                            : BlueprintMaterialUiState.Tone.WARNING));
        }
        return out;
    }

    static BuildStats buildStats(BlueprintEntry entry, ClientRtsController controller) {
        if (entry == null || !entry.error().trim().isEmpty()) {
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
            int percent = clampPercent(buildable * 100L / total);
            return new BuildStats(percent, buildable, total, 0, 0, missingBlockTypes);
        }
        long buildable = buildableBlockCount(entry, controller);
        int missingTypes = 0;
        for (Map.Entry<ResourceLocation, Integer> material : entry.requiredItems().entrySet()) {
            int required = Math.max(0, material.getValue());
            long available = availableItemCount(controller, material.getKey().toString(), ForgeRegistries.ITEMS.getValue(material.getKey()));
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
            long availableLava = availableFluidBuckets(controller, FluidRegistry.LAVA);
            if (availableLava < fluids.lavaBlocks()) {
                missingTypes++;
            }
        }
        int unsupportedTypes = unsupportedBlockLines(entry).size();
        int percent = clampPercent(buildable * 100L / total);
        return new BuildStats(percent, (int) Math.min(buildable, total), total, missingTypes, unsupportedTypes, missingBlockTypes);
    }

    static boolean hasEnoughMaterials(BlueprintEntry entry, ClientRtsController controller) {
        if (entry == null || !entry.error().trim().isEmpty() || controller == null) {
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
            if (availableItemCount(controller, material.getKey().toString(), ForgeRegistries.ITEMS.getValue(material.getKey()))
                    < material.getValue()) {
                return false;
            }
        }
        FluidRequirement fluids = fluidRequirement(entry);
        if (fluids.waterBlocks() > 0 && availableWaterBuckets(controller) < WATER_BUCKET_THRESHOLD) {
            return false;
        }
        if (fluids.lavaBlocks() > 0 && availableFluidBuckets(controller, FluidRegistry.LAVA) < fluids.lavaBlocks()) {
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
            if (id != null && ForgeRegistries.ITEMS.containsKey(id)) {
                remainingItems.put(id, availableItemCount(controller, id.toString(), ForgeRegistries.ITEMS.getValue(id)));
            }
        }
        boolean waterReady = availableWaterBuckets(controller) >= WATER_BUCKET_THRESHOLD;
        long remainingLava = availableFluidBuckets(controller, FluidRegistry.LAVA);
        long buildable = 0L;
        for (RtsBlueprintBlock block : entry.blueprint().blocks()) {
            if (block == null || block.isMissingBlock() || block.state() == null) {
                continue;
            }
            if (block.state().getMaterial() == Material.WATER) {
                if (waterReady) {
                    buildable++;
                }
                continue;
            }
            if (block.state().getMaterial() == Material.LAVA) {
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
        return Minecraft.getMinecraft().player != null && Minecraft.getMinecraft().player.capabilities.isCreativeMode;
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
            return Collections.emptyMap();
        }
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Integer> missing : entry.missingBlueprintBlocks().entrySet()) {
            String namespace = namespaceOf(missing.getKey());
            if (namespace.trim().isEmpty() || "minecraft".equals(namespace)) {
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
                    new ItemStack(Items.WATER_BUCKET).getDisplayName(),
                    available,
                    WATER_BUCKET_THRESHOLD));
        }
        if (fluids.lavaBlocks() > 0) {
            long available = availableFluidBuckets(controller, FluidRegistry.LAVA);
            out.add(new MaterialLine(
                    new ItemStack(Items.LAVA_BUCKET),
                    new ItemStack(Items.LAVA_BUCKET).getDisplayName(),
                    displayAvailable(available, fluids.lavaBlocks()),
                    fluids.lavaBlocks()));
        }
    }

    private static long availableItemCount(ClientRtsController controller, String itemId, Item item) {
        if (isCreativePlayer()) {
            return Long.MAX_VALUE;
        }
        long total = controller == null ? 0L : controller.getStorageTotalCount(itemId);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player != null && item != null && item != Items.AIR) {
            for (ItemStack stack : minecraft.player.inventory.mainInventory) {
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
        ResourceLocation bucketId = Item.REGISTRY.getNameForObject(Items.WATER_BUCKET);
        long bucketItems = availableItemCount(controller, bucketId == null ? "minecraft:water_bucket" : bucketId.toString(), Items.WATER_BUCKET);
        long storedFluidBuckets = availableFluidBuckets(controller, FluidRegistry.WATER);
        return saturatedAdd(bucketItems, storedFluidBuckets);
    }

    private static long availableFluidBuckets(ClientRtsController controller, Fluid fluid) {
        if (isCreativePlayer()) {
            return Long.MAX_VALUE;
        }
        if (controller == null || fluid == null) {
            return 0L;
        }
        String id = FluidRegistry.getDefaultFluidName(fluid);
        if (id == null || id.trim().isEmpty()) {
            return 0L;
        }
        long amount = 0L;
        for (FluidEntry entry : controller.getFluidEntries()) {
            if (id.equals(entry.fluidId())) {
                amount = saturatedAdd(amount, entry.amount());
            }
        }
        return amount / Fluid.BUCKET_VOLUME;
    }

    private static FluidRequirement fluidRequirement(BlueprintEntry entry) {
        if (entry == null || entry.blueprint() == null) {
            return FluidRequirement.EMPTY;
        }
        int water = 0;
        int lava = 0;
        for (RtsBlueprintBlock block : entry.blueprint().blocks()) {
            if (block == null || block.isMissingBlock() || block.state() == null) {
                continue;
            }
            if (block.state().getMaterial() == Material.WATER) {
                water++;
            } else if (block.state().getMaterial() == Material.LAVA) {
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

    private static int clampPercent(long value) {
        return (int) Math.max(0L, Math.min(100L, value));
    }
}

final class FluidRequirement {
    private final int waterBlocks;
    private final int lavaBlocks;
    static final FluidRequirement EMPTY = new FluidRequirement(0, 0);
    FluidRequirement(int waterBlocks, int lavaBlocks) { this.waterBlocks = waterBlocks; this.lavaBlocks = lavaBlocks; }
    int waterBlocks() { return waterBlocks; }
    int lavaBlocks() { return lavaBlocks; }
}

final class MaterialLine {
    private final ItemStack preview; private final String label; private final long available; private final int required;
    MaterialLine(ItemStack preview, String label, long available, int required) {
        this.preview = preview; this.label = label; this.available = available; this.required = required;
    }
    ItemStack preview() { return preview; } String label() { return label; }
    long available() { return available; } int required() { return required; }
}

final class UnsupportedLine {
    private final String label; private final int count;
    UnsupportedLine(String label, int count) { this.label = label; this.count = count; }
    String label() { return label; } int count() { return count; }
}

final class MissingBlueprintBlockLine {
    private final String blockId; private final int count; private final String namespace;
    MissingBlueprintBlockLine(String blockId, int count, String namespace) {
        this.blockId = blockId; this.count = count; this.namespace = namespace;
    }
    String blockId() { return blockId; } int count() { return count; } String namespace() { return namespace; }
}

final class DetailLine {
    private final ItemStack preview; private final String label; private final String detail;
    private final BlueprintMaterialUiState.Tone tone;
    DetailLine(ItemStack preview, String label, String detail, BlueprintMaterialUiState.Tone tone) {
        this.preview = preview; this.label = label; this.detail = detail; this.tone = tone;
    }
    ItemStack preview() { return preview; } String label() { return label; }
    String detail() { return detail; } BlueprintMaterialUiState.Tone tone() { return tone; }
}

final class BuildStats {
    private final int percent; private final int buildable; private final int total;
    private final int missingTypes; private final int unsupportedTypes; private final int missingBlockTypes;
    BuildStats(int percent, int buildable, int total, int missingTypes, int unsupportedTypes, int missingBlockTypes) {
        this.percent = percent; this.buildable = buildable; this.total = total; this.missingTypes = missingTypes;
        this.unsupportedTypes = unsupportedTypes; this.missingBlockTypes = missingBlockTypes;
    }
    int percent() { return percent; } int buildable() { return buildable; } int total() { return total; }
    int missingTypes() { return missingTypes; } int unsupportedTypes() { return unsupportedTypes; }
    int missingBlockTypes() { return missingBlockTypes; }
}
