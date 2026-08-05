package com.rtsbuilding.rtsbuilding.client.infrastructure.module.mining;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class MiningState {

    
    public BlockPos activePos;
    public int activeFace = -1;
    public int activeToolSlot;

    
    public BlockPos renderPos;
    public int renderStage = -1;

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
    }
}
