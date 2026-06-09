package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 蓝图虚影边界过滤器。
 * <p>
 * 用于将蓝图虚影方块裁剪到 RTS 边界范围内，
 * 只渲染玩家基地范围内的预览方块。
 */
public final class BlueprintGhostBoundsFilter {

    private BlueprintGhostBoundsFilter() {
    }

    /**
     * 过滤蓝图方块列表，仅保留在 RTS 边界范围内的方块。
     *
     * @param blocks 待过滤的蓝图方块列表
     * @return 仅包含边界内方块的新列表；如果控制器没有边界约束，返回原列表
     */
    public static List<BlueprintPanel.BlueprintGhostBlock> filter(
            List<BlueprintPanel.BlueprintGhostBlock> blocks) {
        ClientRtsController controller = ClientRtsController.get();
        if (!controller.hasBounds()) {
            return blocks;
        }
        double ax = controller.getAnchorX();
        double az = controller.getAnchorZ();
        double r = controller.getMaxRadius();
        int minBlockX = Mth.floor(ax - r);
        int maxBlockX = Mth.ceil(ax + r) - 1;
        int minBlockZ = Mth.floor(az - r);
        int maxBlockZ = Mth.ceil(az + r) - 1;
        List<BlueprintPanel.BlueprintGhostBlock> result = new ArrayList<>(blocks.size());
        for (BlueprintPanel.BlueprintGhostBlock block : blocks) {
            if (block == null) continue;
            BlockPos pos = block.pos();
            if (pos.getX() >= minBlockX && pos.getX() <= maxBlockX
                    && pos.getZ() >= minBlockZ && pos.getZ() <= maxBlockZ) {
                result.add(block);
            }
        }
        return result.isEmpty() ? List.of() : result;
    }
}
