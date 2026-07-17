package com.rtsbuilding.rtsbuilding.common.blueprint.model;

import com.rtsbuilding.rtsbuilding.common.blueprint.material.BlueprintMaterialResolver;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 蓝图记录，表示一个完整的建筑结构蓝图。
 *
 * @param name          蓝图名称
 * @param sourceName    来源文件名
 * @param format        蓝图格式
 * @param size          蓝图尺寸
 * @param blocks        蓝图中的方块
 * @param requiredItems 由可信方块状态推导出的材料清单
 */
public record RtsBlueprint(
        String name,
        String sourceName,
        BlueprintFormat format,
        Vec3i size,
        List<RtsBlueprintBlock> blocks,
        Map<Identifier, Integer> requiredItems) {

    /**
     * 创建蓝图实例，并自动计算材料清单。
     */
    public static RtsBlueprint create(
            String name,
            String sourceName,
            BlueprintFormat format,
            Vec3i size,
            List<RtsBlueprintBlock> blocks) {
        Map<Identifier, Integer> requirements = new LinkedHashMap<>();
        for (RtsBlueprintBlock block : blocks) {
            if (block.isMissingBlock()) {
                continue;
            }
            for (Identifier id : materialItemIds(block)) {
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
     * 获取蓝图中的方块总数。
     */
    public int blockCount() {
        return this.blocks.size();
    }

    /**
     * 获取指定方块所需的材料物品 ID。
     *
     * <p>导入文件里的 {@code rtsbuilding_material_item} 只作为旧格式元数据保留，
     * 不再参与生存模式扣料。真实消耗必须从方块状态推导，避免蓝图文件把钻石块伪装成泥土，
     * 或把泥土伪装成钻石来骗过材料系统。</p>
     */
    public static List<Identifier> materialItemIds(RtsBlueprintBlock block) {
        if (block == null || block.isMissingBlock()) {
            return List.of();
        }
        return BlueprintMaterialResolver.materialItemIds(block.state());
    }
}
