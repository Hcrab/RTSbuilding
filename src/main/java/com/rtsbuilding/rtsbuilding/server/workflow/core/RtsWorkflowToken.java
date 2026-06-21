package com.rtsbuilding.rtsbuilding.server.workflow.core;

import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 单个工作流条目的稳定操作句柄。
 */
public record RtsWorkflowToken(
        UUID playerId,
        int entryId,
        ResourceKey<Level> dimension,
        RtsWorkflowEngine engine) {

    public RtsWorkflowToken {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(engine, "engine");
    }

    public boolean isValid() {
        return resolveEntry() != null;
    }

    public void markProgress() {
        updateProgress(1, null);
    }

    public void updateProgress(int completedDelta, @Nullable List<String> missingItems) {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry == null) {
            return;
        }
        entry.addCompletedBlocks(completedDelta);
        entry.addMissingItems(missingItems);
        this.engine.fireEvent(WorkflowEventType.PROGRESS, this.playerId, this.entryId, entry);
        this.engine.notifyPlayer(this.playerId, this.dimension);
    }

    public void setCompletedBlocks(int absoluteValue) {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.setCompletedBlocks(absoluteValue);
            this.engine.fireEvent(WorkflowEventType.PROGRESS, this.playerId, this.entryId, entry);
            this.engine.notifyPlayer(this.playerId, this.dimension);
        }
    }

    public void setTotalBlocks(int totalBlocks) {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.setTotalBlocks(totalBlocks);
            this.engine.notifyPlayer(this.playerId, this.dimension);
        }
    }

    public void recordFailure() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.addFailedBlocks(1);
            this.engine.fireEvent(WorkflowEventType.PROGRESS, this.playerId, this.entryId, entry);
            this.engine.notifyPlayer(this.playerId, this.dimension);
        }
    }

    public void setDetailMessage(String message) {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.setDetailMessage(message);
            this.engine.notifyPlayer(this.playerId, this.dimension);
        }
    }

    public void suspend() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.setSuspended(true);
            entry.setDetailMessage("等待材料...");
            this.engine.fireEvent(WorkflowEventType.SUSPENDED, this.playerId, this.entryId, entry);
            this.engine.notifyPlayer(this.playerId, this.dimension);
        }
    }

    public boolean resume() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null && entry.suspended()) {
            entry.setSuspended(false);
            entry.setDetailMessage("");
            this.engine.fireEvent(WorkflowEventType.RESUMED, this.playerId, this.entryId, entry);
            this.engine.notifyPlayer(this.playerId, this.dimension);
            return true;
        }
        return false;
    }

    public void pause() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null && !entry.paused()) {
            entry.setPaused(true);
            this.engine.fireEvent(WorkflowEventType.PAUSED, this.playerId, this.entryId, entry);
            this.engine.notifyPlayer(this.playerId, this.dimension);
        }
    }

    public boolean unpause() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null && entry.paused()) {
            entry.setPaused(false);
            this.engine.fireEvent(WorkflowEventType.UNPAUSED, this.playerId, this.entryId, entry);
            this.engine.notifyPlayer(this.playerId, this.dimension);
            return true;
        }
        return false;
    }

    public boolean isPaused() {
        RtsWorkflowEntry entry = resolveEntry();
        return entry != null && entry.paused();
    }

    public RtsWorkflowStatus getProgress() {
        RtsWorkflowEntry entry = resolveEntry();
        return entry == null ? RtsWorkflowStatus.idle() : entry.snapshot();
    }

    public void complete() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            this.engine.fireEvent(WorkflowEventType.COMPLETED, this.playerId, this.entryId, entry);
            this.engine.removeEntry(this.playerId, this.dimension, this.entryId);
        }
    }

    public void cancel() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            this.engine.fireEvent(WorkflowEventType.CANCELLED, this.playerId, this.entryId, entry);
            this.engine.removeEntry(this.playerId, this.dimension, this.entryId);
        }
    }

    private @Nullable RtsWorkflowEntry resolveEntry() {
        return this.engine.findEntry(this.playerId, this.dimension, this.entryId);
    }
}
