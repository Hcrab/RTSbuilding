package com.rtsbuilding.rtsbuilding.client.controller;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages RTS progression (survival mode) state on the client side.
 * Extracted from {@link ClientRtsController} to reduce its size.
 *
 * <p>Holds all progression-related fields and provides methods for
 * querying and updating progression state, including home position,
 * unlocked nodes, radius limits, and fluid/ultimine capacity.
 */
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
    private final Set<String> unlockedProgressionNodes = new HashSet<>();
    private final Set<String> unlockableProgressionNodes = new HashSet<>();

    // A flag set by the controller for home selection mode (shared state)
    // Not moved here; the controller keeps it. But clearProgressionLocksForDisabled
    // modifies it, so we expose a setter.
    // Actually, homeSelectionMode is on the controller. We pass a Runnable to clear it.

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

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

    public Set<String> getUnlockedProgressionNodes() {
        return Collections.unmodifiableSet(this.unlockedProgressionNodes);
    }

    public Set<String> getUnlockableProgressionNodes() {
        return Collections.unmodifiableSet(this.unlockableProgressionNodes);
    }

    // -------------------------------------------------------------------------
    // State applying
    // -------------------------------------------------------------------------

    /**
     * Applies a progression state payload received from the server.
     *
     * @param payload           the progression state payload
     * @param onLocksCleared    called when progression locks are cleared due to
     *                          progression being disabled (may reset {@code homeSelectionMode})
     */
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
        this.unlockedProgressionNodes.clear();
        this.unlockedProgressionNodes.addAll(payload.unlockedNodes());
        this.unlockableProgressionNodes.clear();
        this.unlockableProgressionNodes.addAll(payload.unlockableNodes());
        if (!this.progressionEnabled) {
            clearProgressionLocksForDisabled(payload.radiusBlocks(), payload.fluidCapacityBuckets(), payload.ultimineLimit());
            if (onLocksCleared != null) {
                onLocksCleared.run();
            }
        }
        RtsProgressionNodes.applySyncedCostOverrides(payload.costOverrides());
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    public void requestProgressionState() {
        RtsClientPacketGateway.sendRequestProgressionState();
    }

    public void unlockProgressionNode(ResourceLocation nodeId) {
        RtsClientPacketGateway.sendUnlockProgressionNode(nodeId);
    }

    /**
     * Enables or disables survival progression.
     *
     * @param enabled        whether progression should be enabled
     * @param onPreDisable   called just before sending the disable packet
     *                       (used by controller to clear {@code homeSelectionMode})
     */
    public void setSurvivalProgressionEnabled(boolean enabled, Runnable onPreDisable) {
        if (!enabled) {
            clearProgressionLocksForDisabled();
            if (onPreDisable != null) {
                onPreDisable.run();
            }
        }
        RtsClientPacketGateway.sendSetSurvivalProgression(enabled);
        RtsClientPacketGateway.sendRequestProgressionState();
    }

    public void setProgressionCost(ResourceLocation nodeId, String costsText) {
        if (nodeId == null) {
            return;
        }
        RtsClientPacketGateway.sendSetProgressionCost(nodeId, costsText);
    }

    public void setHome(BlockPos pos) {
        RtsClientPacketGateway.sendSetHome(pos);
    }

    public void beginHomeSelection() {
        RtsClientPacketGateway.sendBeginHomeSelection();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    void clearProgressionLocksForDisabled() {
        clearProgressionLocksForDisabled(128, 100, 256);
    }

    void clearProgressionLocksForDisabled(int radiusBlocks, int fluidCapacityBuckets, int ultimineLimit) {
        this.progressionEnabled = false;
        this.progressionHomeSet = false;
        this.progressionHomePos = BlockPos.ZERO;
        this.progressionHomeDimension = "";
        this.progressionHomeCooldownTicks = 0L;
        this.progressionRadiusBlocks = Math.max(1, radiusBlocks);
        this.progressionFluidCapacityBuckets = Math.max(1, fluidCapacityBuckets);
        this.progressionUltimineLimit = Math.max(1, ultimineLimit);
        this.progressionBypassHomeRadius = true;
        this.unlockedProgressionNodes.clear();
        this.unlockableProgressionNodes.clear();
    }

    void setProgressionEnabled(boolean enabled) {
        this.progressionEnabled = enabled;
    }
}
