package com.rtsbuilding.rtsbuilding.client.controller;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import net.minecraft.core.BlockPos;

public final class ProgressionStateManager {
    private boolean progressionEnabled;
    private boolean progressionHomeSet;
    private BlockPos progressionHomePos = BlockPos.ZERO;
    private String progressionHomeDimension = "";
    private long progressionHomeCooldownTicks;
    private int progressionRadiusBlocks = 48;
    private int progressionFluidCapacityBuckets = 100;
    private int progressionUltimineLimit = 256;
    private boolean progressionBypassHomeRadius;

    public boolean isProgressionEnabled() {
        return this.progressionEnabled;
    }

    public boolean isProgressionHomeSet() {
        return this.progressionHomeSet;
    }

    public BlockPos getProgressionHomePos() {
        return this.progressionHomePos;
    }

    public String getProgressionHomeDimension() {
        return this.progressionHomeDimension;
    }

    public long getProgressionHomeCooldownTicks() {
        return this.progressionHomeCooldownTicks;
    }

    public int getProgressionRadiusBlocks() {
        return this.progressionRadiusBlocks;
    }

    public int getProgressionFluidCapacityBuckets() {
        return this.progressionFluidCapacityBuckets;
    }

    public int getProgressionUltimineLimit() {
        return this.progressionUltimineLimit;
    }

    public boolean isProgressionBypassHomeRadius() {
        return this.progressionBypassHomeRadius;
    }

    public void applyProgressionState(S2CRtsProgressionStatePayload payload, Runnable onLocksCleared) {
        this.progressionEnabled = payload.enabled();
        this.progressionHomeSet = payload.homeSet();
        this.progressionHomePos = payload.homePos();
        this.progressionHomeDimension = payload.homeDimension() == null ? "" : payload.homeDimension();
        this.progressionHomeCooldownTicks = payload.homeCooldownTicks();
        this.progressionRadiusBlocks = payload.radiusBlocks();
        this.progressionFluidCapacityBuckets = payload.fluidCapacityBuckets();
        this.progressionUltimineLimit = payload.ultimineLimit();
        this.progressionBypassHomeRadius = payload.bypassHomeRadius();
        if (!this.progressionEnabled && onLocksCleared != null) {
            onLocksCleared.run();
        }
    }

    public void requestProgressionState() {
        RtsClientPacketGateway.sendRequestProgressionState();
    }

    public void setSurvivalProgressionEnabled(boolean enabled, Runnable onPreDisable) {
        if (!enabled && onPreDisable != null) {
            onPreDisable.run();
        }
        RtsClientPacketGateway.sendSetSurvivalProgression(enabled);
        RtsClientPacketGateway.sendRequestProgressionState();
        RtsClientPacketGateway.sendRequestPlugins();
    }

    public void setHome(BlockPos pos) {
        RtsClientPacketGateway.sendSetHome(pos);
    }

    public void beginHomeSelection() {
        RtsClientPacketGateway.sendBeginHomeSelection();
    }
}
