package com.rtsbuilding.rtsbuilding.client.module.mining;

import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * 采矿模块——管理单方块挖掘、连锁挖掘（Ultimine）和区域挖掘。
 */
public final class MiningModule implements FeatureModule {

    private final MiningState state = new MiningState();

    @Override
    public String moduleId() {
        return "mining";
    }

    @Override
    public void onSessionEvent(StateEvent event) {
        if (event instanceof StateEvent.RtsToggled e) {
            if (!e.enabled()) state.clearAll();
        } else if (event instanceof StateEvent.PlayerDied) {
            state.clearAll();
        }
    }

    // ======================================================================
    //  Single-block mining
    // ======================================================================

    public void startMining(BlockPos pos, int face, int toolSlot, String selectedItemId, ItemStack selectedPreview,
                            boolean blockRecovery, boolean toolProtection) {
        state.activePos = pos.immutable();
        state.activeFace = face;
        state.activeToolSlot = toolSlot;
        state.renderPos = state.activePos;
        state.renderStage = 0;
        RtsClientPacketGateway.sendMineStart(pos, face, toolSlot, selectedItemId, selectedPreview, blockRecovery, toolProtection);
    }

    public void abortMining(int toolSlot) {
        if (state.activePos == null) return;
        RtsClientPacketGateway.sendMineAbort(state.activePos, state.activeFace, toolSlot);
        state.activePos = null;
        state.activeFace = -1;
        state.renderStage = -1;
    }

    // ======================================================================
    //  Ultimine (chain mining)
    // ======================================================================

    public void startUltimine(BlockPos pos, int face, int toolSlot, int limit, byte mode,
                              String selectedItemId, ItemStack selectedPreview, boolean toolProtection) {
        state.activePos = pos.immutable();
        state.renderPos = state.activePos;
        state.renderStage = 0;
        RtsClientPacketGateway.sendUltimineStart(pos, face, toolSlot, limit, mode, selectedItemId, selectedPreview, toolProtection);
    }

    // ======================================================================
    //  Network callbacks
    // ======================================================================

    public void applyMineProgress(BlockPos pos, int stage) {
        state.applyMineProgress(pos, stage);
    }

    public void applyUltimineProgress(int processed, int total) {
        state.ultimineProcessed = processed;
        state.ultimineTotal = total;
    }

    // ======================================================================
    //  State accessors
    // ======================================================================

    public MiningState getState() { return this.state; }
    public BlockPos getActivePos() { return state.activePos; }
    public int getRenderStage() { return state.renderStage; }
    public BlockPos getRenderPos() { return state.renderPos; }
    public int getUltimineProcessed() { return state.ultimineProcessed; }
    public int getUltimineTotal() { return state.ultimineTotal; }
}
