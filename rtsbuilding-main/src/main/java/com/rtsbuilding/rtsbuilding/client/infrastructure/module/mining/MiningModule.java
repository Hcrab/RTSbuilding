package com.rtsbuilding.rtsbuilding.client.infrastructure.module.mining;

import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

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

    
    
    

    public void startMining(BlockPos pos, int face, int toolSlot, String selectedItemId,
                            boolean blockRecovery, boolean toolProtection) {
        state.activePos = pos.immutable();
        state.activeFace = face;
        state.activeToolSlot = toolSlot;
        state.renderPos = state.activePos;
        state.renderStage = 0;
        RtsClientPacketGateway.sendMineStart(pos, face, toolSlot, selectedItemId, blockRecovery, toolProtection);
    }

    public void abortMining(int toolSlot) {
        if (state.activePos == null) return;
        RtsClientPacketGateway.sendMineAbort(state.activePos, state.activeFace, toolSlot);
        state.activePos = null;
        state.activeFace = -1;
        state.renderStage = -1;
    }

    
    
    
    
    public void startUltimine(BlockPos pos, int face, int toolSlot, int limit, byte mode,
                              String selectedItemId, boolean toolProtection) {
        state.activePos = pos.immutable();
        state.activeFace = face;
        RtsClientPacketGateway.sendUltimineStart(pos, face, toolSlot, limit, mode, selectedItemId, toolProtection);
    }

    
    
    

    public void applyMineProgress(BlockPos pos, int stage) {
        state.applyMineProgress(pos, stage);
    }

    
    public void applyUltimineProgress(int processed, int total) {
        // processed < 0 表示服务端批次已结束：清除活跃状态，恢复“再次点击=新批次/取消”交互
        if (processed < 0) {
            state.activePos = null;
            state.activeFace = -1;
        }
    }

    
    
    
    
    public MiningState getState() { return this.state; }
    public BlockPos getActivePos() { return state.activePos; }
}
