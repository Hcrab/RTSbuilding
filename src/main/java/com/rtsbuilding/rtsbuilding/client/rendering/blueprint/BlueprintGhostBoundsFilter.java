package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 蓝图虚影边界过滤。
 *
 * <p>蓝图预览可以很大，但 RTS 建造仍然受玩家基地范围约束。这个类只做客户端显示裁剪，
 * 不替代服务端最终权限判断。</p>
 */
public final class BlueprintGhostBoundsFilter {
    private BlueprintGhostBoundsFilter() {
    }

    public static List<BlueprintPanel.BlueprintGhostBlock> filter(List<BlueprintPanel.BlueprintGhostBlock> blocks) {
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
            if (block == null) {
                continue;
            }
            BlockPos pos = block.pos();
            if (pos.getX() >= minBlockX && pos.getX() <= maxBlockX
                    && pos.getZ() >= minBlockZ && pos.getZ() <= maxBlockZ) {
                result.add(block);
            }
        }
        return result.isEmpty() ? List.of() : result;
    }
}
