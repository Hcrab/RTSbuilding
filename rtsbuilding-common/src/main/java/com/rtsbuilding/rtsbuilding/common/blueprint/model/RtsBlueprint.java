package com.rtsbuilding.rtsbuilding.common.blueprint.model;

import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.*;

/**
 * Blueprint record — represents a complete building structure blueprint.
 * <p>
 * Contains the blueprint name, source file name, format type, dimensions, block list, and required materials.
 * The material list {@code requiredItems} is automatically computed from the block list at creation time.
 *
 * @param name          blueprint name
 * @param sourceName    source file name
 * @param format        blueprint format
 * @param size          blueprint dimensions (in blocks)
 * @param blocks        list of blocks in the blueprint
 * @param requiredItems required material mapping (item ID → count)
 */
public record RtsBlueprint(
        String name,
        String sourceName,
        BlueprintFormat format,
        Vec3i size,
        List<RtsBlueprintBlock> blocks,
        Map<ResourceLocation, Integer> requiredItems) {

    /**
     * Create a blueprint instance and automatically compute the required material list.
     * <p>
     * Iterates through all blocks, collects each block's material item IDs, and counts
     * the required quantity for each material. Missing blocks are not counted toward material requirements.
     *
     * @param name       blueprint name (uses sourceName if blank)
     * @param sourceName source file name
     * @param format     blueprint format
     * @param size       blueprint dimensions
     * @param blocks     list of blocks in the blueprint
     * @return the newly created blueprint instance
     */
    public static RtsBlueprint create(
            String name,
            String sourceName,
            BlueprintFormat format,
            Vec3i size,
            List<RtsBlueprintBlock> blocks) {
        Map<ResourceLocation, Integer> requirements = new LinkedHashMap<>();
        for (RtsBlueprintBlock block : blocks) {
            if (block.isMissingBlock()) {
                continue;
            }
            for (ResourceLocation id : materialItemIds(block)) {
                requirements.merge(id, 1, Integer::sum);
            }
        }
        return new RtsBlueprint(
                name == null || name.isBlank() ? sourceName : name,
                sourceName == null ? "" : sourceName,
                format,
                size,
                List.copyOf(blocks),
                Collections.unmodifiableMap(requirements));
    }

    /**
     * Get the total number of blocks in the blueprint.
     *
     * @return block count
     */
    public int blockCount() {
        return this.blocks.size();
    }

    /**
     * Get the material item ID list for a given block.
     * <p>
     * Prefers the block's own {@code materialItemId},
     * also scans the block entity NBT for material IDs if the block is an AE2 cable/bus,
     * and falls back to {@link Block#asItem()} as a last resort.
     *
     * @param block the block to query
     * @return list of material item IDs (may be empty)
     */
    public static List<ResourceLocation> materialItemIds(RtsBlueprintBlock block) {
        if (block == null || block.isMissingBlock()) {
            return List.of();
        }
        LinkedHashSet<ResourceLocation> ids = new LinkedHashSet<>();
        addMaterialItemIds(ids, block.materialItemId());
        if (shouldScanBlockEntityMaterialIds(block)) {
            collectMaterialItemIds(block.blockEntityTag(), ids);
        }

        // final fallback: use the block's Item form
        Item item = block.state().getBlock().asItem();
        ResourceLocation fallback = item == Items.AIR ? null : BuiltInRegistries.ITEM.getKey(item);
        if (ids.size() > 1 && fallback != null) {
            ids.remove(fallback);
        }
        if (ids.isEmpty() && fallback != null && BuiltInRegistries.ITEM.containsKey(fallback)) {
            ids.add(fallback);
        }
        return ids.isEmpty() ? List.of() : List.copyOf(ids);
    }

    /**
     * Determine whether to scan the block entity NBT for material IDs.
     * <p>
     * Currently only applies to AE2 (Applied Energistics 2) cables and buses,
     * since these blocks store material information in NBT rather than block state.
     */
    private static boolean shouldScanBlockEntityMaterialIds(RtsBlueprintBlock block) {
        if (block == null || block.state() == null || block.state().isAir()) {
            return false;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block.state().getBlock());
        if (blockId == null || !"ae2".equals(blockId.getNamespace())) {
            return false;
        }
        String path = blockId.getPath();
        return path.contains("cable") || path.contains("bus");
    }

    /** Parse and add material item IDs from a raw string */
    private static void addMaterialItemIds(LinkedHashSet<ResourceLocation> out, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split("[,;\\s]+")) {
            ResourceLocation id = ResourceLocation.tryParse(part);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                out.add(id);
            }
        }
    }

    /** Recursively traverse the NBT tag tree, collecting all material item IDs in string form */
    private static void collectMaterialItemIds(Tag tag, LinkedHashSet<ResourceLocation> out) {
        if (tag == null) {
            return;
        }
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                collectMaterialItemIds(compound.get(key), out);
            }
            return;
        }
        if (tag instanceof ListTag list) {
            for (Tag child : list) {
                collectMaterialItemIds(child, out);
            }
            return;
        }
        if (tag.getId() == Tag.TAG_STRING) {
            addMaterialItemIds(out, tag.getAsString());
        }
    }
}
