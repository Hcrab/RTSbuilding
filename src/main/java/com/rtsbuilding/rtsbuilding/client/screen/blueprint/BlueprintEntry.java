package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 蓝图库的一行不可变快照。这里缓存解析结果与材料摘要，但不负责文件写入或 UI 状态。
 */
final class BlueprintEntry {
    private final Path path;
    private final String fileName;
    private final String name;
    private final BlueprintFormat format;
    private final String sizeText;
    private final int blockCount;
    private final RtsBlueprint blueprint;
    private final Map<ResourceLocation, Integer> requiredItems;
    private final Map<String, Integer> unsupportedBlocks;
    private final Map<String, Integer> missingBlueprintBlocks;
    private final List<ItemStack> previewItems;
    private final String error;

    BlueprintEntry(Path path, String fileName, String name, BlueprintFormat format, String sizeText,
            int blockCount, RtsBlueprint blueprint, Map<ResourceLocation, Integer> requiredItems,
            Map<String, Integer> unsupportedBlocks, Map<String, Integer> missingBlueprintBlocks,
            List<ItemStack> previewItems, String error) {
        this.path = path;
        this.fileName = fileName;
        this.name = name;
        this.format = format;
        this.sizeText = sizeText;
        this.blockCount = blockCount;
        this.blueprint = blueprint;
        this.requiredItems = Collections.unmodifiableMap(new LinkedHashMap<ResourceLocation, Integer>(requiredItems));
        this.unsupportedBlocks = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(unsupportedBlocks));
        this.missingBlueprintBlocks = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(missingBlueprintBlocks));
        this.previewItems = Collections.unmodifiableList(new ArrayList<ItemStack>(previewItems));
        this.error = error == null ? "" : error;
    }

    Path path() { return path; }
    String fileName() { return fileName; }
    String name() { return name; }
    BlueprintFormat format() { return format; }
    String sizeText() { return sizeText; }
    int blockCount() { return blockCount; }
    RtsBlueprint blueprint() { return blueprint; }
    Map<ResourceLocation, Integer> requiredItems() { return requiredItems; }
    Map<String, Integer> unsupportedBlocks() { return unsupportedBlocks; }
    Map<String, Integer> missingBlueprintBlocks() { return missingBlueprintBlocks; }
    List<ItemStack> previewItems() { return previewItems; }
    String error() { return error; }

    static BlueprintEntry from(Path path, String fileName, RtsBlueprint blueprint, String error) {
        Vec3i size = blueprint.size();
        List<ItemStack> preview = new ArrayList<ItemStack>();
        for (ResourceLocation id : blueprint.requiredItems().keySet()) {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) continue;
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty()) preview.add(stack);
            if (preview.size() >= 18) break;
        }

        Map<String, Integer> unsupported = new LinkedHashMap<String, Integer>();
        Map<String, Integer> missing = new LinkedHashMap<String, Integer>();
        for (RtsBlueprintBlock block : blueprint.blocks()) {
            if (block.isMissingBlock()) {
                increment(missing, block.missingBlockId());
                continue;
            }
            Material material = block.state().getMaterial();
            if (material == Material.WATER || material == Material.LAVA) continue;
            Item item = Item.getItemFromBlock(block.state().getBlock());
            if (item == null || item == Items.AIR) {
                increment(unsupported, block.state().getBlock().getLocalizedName());
            }
        }

        return new BlueprintEntry(path, fileName, displayName(fileName, blueprint.name()), blueprint.format(),
                size.getX() + "x" + size.getY() + "x" + size.getZ(), blueprint.blockCount(), blueprint,
                blueprint.requiredItems(), unsupported, missing, preview, error);
    }

    static BlueprintEntry error(Path path, String fileName, String error) {
        String name = fileName;
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        BlueprintFormat format = BlueprintFormat.fromFileName(fileName);
        return new BlueprintEntry(path, fileName, name, format, "-", 0,
                RtsBlueprint.create(name, fileName, format, Vec3i.NULL_VECTOR,
                        Collections.<RtsBlueprintBlock>emptyList()),
                Collections.<ResourceLocation, Integer>emptyMap(),
                Collections.<String, Integer>emptyMap(), Collections.<String, Integer>emptyMap(),
                Collections.<ItemStack>emptyList(), error == null ? "Parse failed" : error);
    }

    private static void increment(Map<String, Integer> counts, String key) {
        Integer previous = counts.get(key);
        counts.put(key, previous == null ? 1 : previous + 1);
    }

    private static String displayName(String fileName, String fallback) {
        String name = BlueprintPanelFiles.stripBlueprintExtension(fileName);
        if (isBlank(name)) name = isBlank(fallback) ? "blueprint" : fallback;
        return name;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
