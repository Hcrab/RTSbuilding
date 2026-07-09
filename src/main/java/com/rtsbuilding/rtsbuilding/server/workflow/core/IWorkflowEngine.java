package com.rtsbuilding.rtsbuilding.server.workflow.core;

import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventListener;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 工作流引擎公共入口。服务层通过这里创建任务、更新进度和查询状态。
 */
public interface IWorkflowEngine {
    Optional<RtsWorkflowToken> start(ServerPlayer player, RtsWorkflowType type, RtsWorkflowPriority priority, int totalBlocks);

    Optional<RtsWorkflowToken> from(ServerPlayer player, int entryId);

    Optional<RtsWorkflowToken> lastActive(ServerPlayer player);

    void addListener(WorkflowEventListener listener);

    void removeListener(WorkflowEventListener listener);

    RtsWorkflowStatus getProgress(RtsWorkflowToken token);

    RtsWorkflowStatus getProgress(ServerPlayer player, int entryId);

    List<RtsWorkflowStatus> getAllProgress(ServerPlayer player);

    boolean hasActiveWorkflow(ServerPlayer player);

    int activeWorkflowCount(ServerPlayer player);

    int occupiedSlotCount(ServerPlayer player);

    boolean isFull(ServerPlayer player);

    void setWorkflowExtraData(ServerPlayer player, int entryId, @Nullable CompoundTag data);

    @Nullable CompoundTag getWorkflowExtraData(ServerPlayer player, int entryId);

    void deleteWorkflow(ServerPlayer player, int entryId);

    void setWorkflowProtected(ServerPlayer player, int entryId, boolean protectedWorkflow);

    void cancelAll(ServerPlayer player);

    void clearPlayerData(UUID playerId);

    void clearAllData();

    boolean isEntryPaused(UUID playerId, ResourceKey<Level> dimension, int entryId);

    boolean isEntrySuspended(UUID playerId, ResourceKey<Level> dimension, int entryId);
}
