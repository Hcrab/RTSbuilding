package com.rtsbuilding.rtsbuilding.blueprint.client;


import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.rtsbuilding.rtsbuilding.blueprint.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprintBlock;

import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Loaded blueprint row model used by the Blueprint Space list and details panel.
 *
 * <p>It keeps the parsed blueprint together with the file identity, compact size
 * text, preview items, unsupported block counts, and missing block-id counts.
 * The UI can therefore render a row without reparsing the source file.</p>
 */
public record BlueprintEntry(
        Path path,
        String fileName,
        String name,
        BlueprintFormat format,
        String sizeText,
        int blockCount,
        RtsBlueprint blueprint,
        Map<ResourceLocation, Integer> requiredItems,
        Map<String, Integer> unsupportedBlocks,
        Map<String, Integer> missingBlueprintBlocks,
        List<ItemStack> previewItems,
        String error) {
    /**
     * Builds a normal row from a successfully parsed blueprint.
     */
    static BlueprintEntry from(Path path, String fileName, RtsBlueprint blueprint, String error) {
        Vec3i size = blueprint.size();
        List<ItemStack> preview = new ArrayList<>();
        for (ResourceLocation id : blueprint.requiredItems().keySet()) {
            if (!BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(id);
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty()) {
                preview.add(stack);
            }
            if (preview.size() >= 18) {
                break;
            }
        }

        Map<String, Integer> unsupported = new java.util.LinkedHashMap<>();
        Map<String, Integer> missingBlueprintBlocks = new java.util.LinkedHashMap<>();
        for (RtsBlueprintBlock block : blueprint.blocks()) {
            if (block.isMissingBlock()) {
                missingBlueprintBlocks.merge(block.missingBlockId(), 1, Integer::sum);
                continue;
            }
            if (block.state().getFluidState().is(FluidTags.WATER)
                    || block.state().getFluidState().is(FluidTags.LAVA)) {
                continue;
            }
            if (block.state().getBlock().asItem() == Items.AIR) {
                unsupported.merge(block.state().getBlock().getName().getString(), 1, Integer::sum);
            }
        }

        String sizeText = size.getX() + "x" + size.getY() + "x" + size.getZ();
        return new BlueprintEntry(
                path,
                fileName,
                displayName(fileName, blueprint.name()),
                blueprint.format(),
                sizeText,
                blueprint.blockCount(),
                blueprint,
                blueprint.requiredItems(),
                Map.copyOf(unsupported),
                Map.copyOf(missingBlueprintBlocks),
                List.copyOf(preview),
                error == null ? "" : error);
    }

    /**
     * Builds a row for a file that exists but could not be parsed.
     */
    static BlueprintEntry error(Path path, String fileName, String error) {
        String name = fileName;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return new BlueprintEntry(
                path,
                fileName,
                name,
                BlueprintFormat.fromFileName(fileName),
                "-",
                0,
                RtsBlueprint.create(name, fileName, BlueprintFormat.fromFileName(fileName), Vec3i.ZERO, List.of()),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                error == null ? "Parse failed" : error);
    }

    private static String displayName(String fileName, String fallback) {
        String name = BlueprintPanelFiles.stripBlueprintExtension(fileName);
        if (name == null || name.isBlank()) {
            name = fallback == null || fallback.isBlank() ? "blueprint" : fallback;
        }
        return name;
    }
}
