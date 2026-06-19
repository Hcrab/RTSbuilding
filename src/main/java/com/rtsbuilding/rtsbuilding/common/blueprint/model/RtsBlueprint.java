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
 * 蓝图记录 —— 表示一个完整的建筑结构蓝图。
 * <p>
 * 包含蓝图名称、来源文件名、格式类型、尺寸大小、方块列表以及所需的材料清单。
 * 材料清单 {@code requiredItems} 在创建时自动从方块列表中计算生成。
 *
 * @param name          蓝图名称
 * @param sourceName    来源文件名
 * @param format        蓝图格式
 * @param size          蓝图的尺寸（方块数）
 * @param blocks        蓝图包含的方块列表
 * @param requiredItems 所需材料映射（物品 ID → 数量）
 */
public record RtsBlueprint(
        String name,
        String sourceName,
        BlueprintFormat format,
        Vec3i size,
        List<RtsBlueprintBlock> blocks,
        Map<ResourceLocation, Integer> requiredItems) {

    /**
     * 创建一个蓝图实例，并自动计算所需材料清单。
     * <p>
     * 遍历所有方块，收集每个方块的材料物品 ID，统计每种材料的需求数量。
     * 缺失方块不计入材料需求。
     *
     * @param name       蓝图名称（为空则使用 sourceName）
     * @param sourceName 来源文件名
     * @param format     蓝图格式
     * @param size       蓝图的尺寸
     * @param blocks     蓝图包含的方块列表
     * @return 新创建的蓝图实例
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
     * 获取蓝图中方块的总数。
     *
     * @return 方块数量
     */
    public int blockCount() {
        return this.blocks.size();
    }

    /**
     * 获取指定方块所需的材料物品 ID 列表。
     * <p>
     * 优先使用方块自带的 {@code materialItemId}，
     * 如果方块是 AE2 的线缆/总线，还会扫描方块实体 NBT 中的材料 ID，
     * 最后以 {@link Block#asItem()} 作为兜底。
     *
     * @param block 要查询的方块
     * @return 材料物品 ID 列表（可能为空）
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

        // 最终兜底：使用方块的 Item 形式
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
     * 判断是否需要扫描方块实体的 NBT 来获取材料 ID。
     * <p>
     * 目前仅对 AE2（Applied Energistics 2）的线缆和总线生效，
     * 因为这些方块的材料信息存储在 NBT 中而非方块状态中。
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

    /** 从原始字符串解析并添加材料物品 ID */
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

    /** 递归遍历 NBT 标签树，收集所有字符串形式的材料物品 ID */
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
