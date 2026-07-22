package com.rtsbuilding.rtsbuilding.client.infrastructure.module.mining;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * 采矿状态——纯数据容器。
 */
public final class MiningState {

    // Active mining block
    public BlockPos activePos;
    public int activeFace = -1;
    public int activeToolSlot;

    // Render progress
    public BlockPos renderPos;
    public int renderStage = -1;

    // Ultimine progress
    public int ultimineProcessed = -1;
    public int ultimineTotal;

    // Area mine state
    public int areaMinePhase;
    public BlockPos areaMinePointA, areaMinePointB;
    public int areaMineHeightOffset;
    public int areaMineShape;

    public void applyMineProgress(BlockPos pos, int stage) {
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

    public void clearAll() {
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
