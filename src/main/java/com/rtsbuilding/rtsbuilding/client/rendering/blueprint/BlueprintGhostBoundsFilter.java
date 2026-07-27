package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 将蓝图虚影裁剪到当前 RTS 边界内。
 *
 * <p>该类只负责纯坐标筛选，不持有渲染状态。无边界时保留原列表，避免为常见路径
 * 额外复制；有边界且结果为空时返回 Java 8 可用的不可变空列表。</p>
 */
public final class BlueprintGhostBoundsFilter {
    private BlueprintGhostBoundsFilter() {
    }

    public static List<BlueprintGhostBlock> filter(List<BlueprintGhostBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return Collections.emptyList();
        }

        ClientRtsController controller = ClientRtsController.get();
        if (!controller.hasBounds()) {
            return blocks;
        }

        return filter(blocks, controller.getAnchorX(), controller.getAnchorZ(), controller.getMaxRadius());
    }

    /**
     * 可独立测试的边界筛选入口；边界采用与主线一致的半开浮点区间换算。
     */
    static List<BlueprintGhostBlock> filter(List<BlueprintGhostBlock> blocks,
            double anchorX, double anchorZ, double radius) {
        if (blocks == null || blocks.isEmpty()) {
            return Collections.emptyList();
        }

        int minBlockX = MathHelper.floor(anchorX - radius);
        int maxBlockX = MathHelper.ceil(anchorX + radius) - 1;
        int minBlockZ = MathHelper.floor(anchorZ - radius);
        int maxBlockZ = MathHelper.ceil(anchorZ + radius) - 1;
        List<BlueprintGhostBlock> result = new ArrayList<BlueprintGhostBlock>(blocks.size());
        for (BlueprintGhostBlock block : blocks) {
            if (block == null) {
                continue;
            }
            BlockPos pos = block.pos();
            if (pos != null
                    && pos.getX() >= minBlockX && pos.getX() <= maxBlockX
                    && pos.getZ() >= minBlockZ && pos.getZ() <= maxBlockZ) {
                result.add(block);
            }
        }
        return result.isEmpty() ? Collections.<BlueprintGhostBlock>emptyList() : result;
    }
}
