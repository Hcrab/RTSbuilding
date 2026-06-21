package com.rtsbuilding.rtsbuilding.blueprint;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public record RtsBlueprint(
        String name,
        String sourceName,
        BlueprintFormat format,
        Vec3i size,
        List<RtsBlueprintBlock> blocks,
        Map<ResourceLocation, Integer> requiredItems) {
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

    public int blockCount() {
        return this.blocks.size();
    }

    public static List<ResourceLocation> materialItemIds(RtsBlueprintBlock block) {
        if (block == null || block.isMissingBlock()) {
            return List.of();
        }
        LinkedHashSet<ResourceLocation> ids = new LinkedHashSet<>();
        addMaterialItemIds(ids, block.materialItemId());
        if (shouldScanBlockEntityMaterialIds(block)) {
            collectMaterialItemIds(block.blockEntityTag(), ids);
        }

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
