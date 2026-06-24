package com.rtsbuilding.rtsbuilding.client.module.mining;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * 采矿状态——纯数据容器。
 */
public final class MiningState {

    // Active mining block
    BlockPos activePos;
    int activeFace = -1;
    int activeToolSlot;

    // Render progress
    BlockPos renderPos;
    int renderStage = -1;

    // Ultimine progress
    int ultimineProcessed = -1;
    int ultimineTotal;

    // Area mine state
    int areaMinePhase;
    BlockPos areaMinePointA, areaMinePointB;
    int areaMineHeightOffset;
    int areaMineShape;

    void applyMineProgress(BlockPos pos, int stage) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (stage < 0) {
            if (renderPos != null) {
                mc.level.destroyBlockProgress(0x525453, renderPos, -1);
                renderPos = null;
            }
            renderStage = -1;
            return;
        }
        if (renderPos != null && !renderPos.equals(pos)) {
            mc.level.destroyBlockProgress(0x525453, renderPos, -1);
        }
        mc.level.destroyBlockProgress(0x525453, pos, Math.min(9, stage));
        renderPos = pos.immutable();
        renderStage = Math.min(9, stage);
    }

    void clearAll() {
        activePos = null;
        activeFace = -1;
        renderPos = null;
        renderStage = -1;
        ultimineProcessed = -1;
        ultimineTotal = 0;
        areaMinePhase = 0;
        areaMinePointA = null;
        areaMinePointB = null;
    }
}
