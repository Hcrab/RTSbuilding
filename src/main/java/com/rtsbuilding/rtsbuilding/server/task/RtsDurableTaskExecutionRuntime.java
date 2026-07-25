package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.destruction.RtsDestructionBatch;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import net.minecraft.server.MinecraftServer;

import java.util.Map;

/**
 * 三类 durable 世界任务的有界执行运行时。
 *
 * <p>本类拥有 scheduler、在线玩家解析和挖掘热路径执行镜像；它不拥有命令校验、
 * Workflow UI、蓝图/漏斗/回收任务，也不负责 Session 生命周期。把这些主线程事务从
 * {@link RtsTaskEngine} 拆出后，中央引擎只保留编排职责，后续可以按领域继续拆分而不改玩家行为。</p>
 */
final class RtsDurableTaskExecutionRuntime {
    private final DurableTaskScheduler scheduler = new DurableTaskScheduler(System::nanoTime);
    /** 单方块 0-9 破坏动画是瞬时表现，不值得每阶段强制落盘。 */
    private final Map<com.rtsbuilding.rtsbuilding.server.task.identity.TaskId, MiningProgressOverlay>
            miningProgressOverlays = new java.util.HashMap<>();
    /**
     * 范围破坏的轻量执行镜像。
     *
     * <p>破坏任务会不断累积目标游标和撤销历史；若每个 slice 都生成 durable revision，
     * 后台 writer 就必须反复重写一份越来越大的 NBT。这里与连锁挖掘采用相同策略：
     * 首次接触世界前等待 root ACK，之后只在有界检查点或生命周期边界写盘。</p>
     */
    private final Map<com.rtsbuilding.rtsbuilding.server.task.identity.TaskId, DestructionProgressOverlay>
            destructionProgressOverlays = new java.util.HashMap<>();
    /** 只用于单个主线程 slice 解析在线玩家，不持有任务状态。 */
    private MinecraftServer activeServer;

    RtsDurableTaskExecutionRuntime() {
        scheduler.register(TaskType.PLACEMENT, this::executeDurablePlacement);
        scheduler.register(TaskType.DESTRUCTION, this::executeDurableDestruction);
        scheduler.register(TaskType.MINING, this::executeDurableMining);
    }

    void beginTick(
            MinecraftServer server,
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceCoordinator coordinator) {
        activeServer = server;
    }

    DurableTaskScheduler.TickStats tick(
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceCoordinator coordinator,
            java.util.Collection<com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot> candidates,
            long maxNanos,
            int maxUnits,
            int maxUnitsPerSlice) {
        return scheduler.tick(coordinator, candidates, maxNanos, maxUnits, maxUnitsPerSlice);
    }

    void resetAfterServerStop() {
        activeServer = null;
        miningProgressOverlays.clear();
        destructionProgressOverlays.clear();
    }

    /** 暂停、取消和停服时把热路径镜像合并成一个 durable revision。 */
    com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot transitionMiningSnapshot(
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot snapshot,
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState lifecycle,
            long gameTime) {
        MiningProgressOverlay overlay = miningProgressOverlays.remove(snapshot.id());
        MiningTaskPayload payload = overlay != null && overlay.baseRevision() == snapshot.revision()
                ? overlay.payload()
                : com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskCodec.decode(snapshot.payload());
        var state = payload.state();
        return snapshot.nextRevision(lifecycle, null, gameTime,
                state.cursorUnits(), state.succeededUnits(), state.failedUnits(),
                com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskCodec.encode(payload));
    }

    com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState currentMiningState(
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot snapshot) {
        MiningProgressOverlay overlay = miningProgressOverlays.get(snapshot.id());
        if (overlay != null && overlay.baseRevision() == snapshot.revision()) return overlay.payload().state();
        return com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskCodec.decode(snapshot.payload()).state();
    }

    /** 暂停、取消和停服时把范围破坏镜像合并为一个 durable revision。 */
    com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot transitionDestructionSnapshot(
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot snapshot,
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState lifecycle,
            long gameTime) {
        DestructionProgressOverlay overlay = destructionProgressOverlays.remove(snapshot.id());
        DestructionTaskPayload payload = overlay != null && overlay.baseRevision() == snapshot.revision()
                ? overlay.payload()
                : com.rtsbuilding.rtsbuilding.server.task.destruction.DestructionTaskCodec.decode(snapshot.payload());
        var state = payload.state();
        var nextPayload = new DestructionTaskPayload(
                payload.ownerId(), payload.dimension(), payload.workflowEntryId(), state);
        return snapshot.nextRevision(lifecycle, null, gameTime,
                state.cursorUnits(), state.succeededUnits(), state.failedUnits(),
                com.rtsbuilding.rtsbuilding.server.task.destruction.DestructionTaskCodec.encode(nextPayload));
    }

    /** 返回包含尚未写盘进度的范围破坏状态，供取消和历史恢复使用。 */
    com.rtsbuilding.rtsbuilding.server.task.destruction.DestructionTaskState currentDestructionState(
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot snapshot) {
        DestructionProgressOverlay overlay = destructionProgressOverlays.get(snapshot.id());
        if (overlay != null && overlay.baseRevision() == snapshot.revision()) return overlay.payload().state();
        return com.rtsbuilding.rtsbuilding.server.task.destruction.DestructionTaskCodec
                .decode(snapshot.payload()).state();
    }

    void checkpointMiningExecutions(
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceCoordinator coordinator,
            long gameTime) {
        for (var taskId : java.util.List.copyOf(miningProgressOverlays.keySet())) {
            var snapshot = coordinator.query().get(taskId).orElse(null);
            if (snapshot == null || snapshot.state().terminal()) {
                miningProgressOverlays.remove(taskId);
                continue;
            }
            MiningProgressOverlay overlay = miningProgressOverlays.get(taskId);
            if (overlay == null || overlay.baseRevision() != snapshot.revision()) {
                miningProgressOverlays.remove(taskId);
                continue;
            }
            coordinator.replace(transitionMiningSnapshot(snapshot, snapshot.state(), gameTime));
        }
    }

    /** 停服前把仍在内存镜像中的范围破坏游标和撤销历史写回 TaskStore。 */
    void checkpointDestructionExecutions(
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceCoordinator coordinator,
            long gameTime) {
        for (var taskId : java.util.List.copyOf(destructionProgressOverlays.keySet())) {
            var snapshot = coordinator.query().get(taskId).orElse(null);
            if (snapshot == null || snapshot.state().terminal()) {
                destructionProgressOverlays.remove(taskId);
                continue;
            }
            DestructionProgressOverlay overlay = destructionProgressOverlays.get(taskId);
            if (overlay == null || overlay.baseRevision() != snapshot.revision()) {
                destructionProgressOverlays.remove(taskId);
                continue;
            }
            coordinator.replace(transitionDestructionSnapshot(snapshot, snapshot.state(), gameTime));
        }
    }

    private DurableTaskScheduler.SliceResult executeDurablePlacement(
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot snapshot,
            TaskBudget budget) {
        if (!durableRevisionAcknowledged(snapshot)) {
            return new DurableTaskScheduler.SliceResult(snapshot, 0);
        }
        PlacementTaskPayload payload = com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskCodec
                .decode(snapshot.payload());
        var player = activeServer == null ? null : activeServer.getPlayerList().getPlayer(payload.ownerId());
        if (player == null || !player.serverLevel().dimension().equals(payload.dimension())) {
            return durableNoProgress(snapshot, payload.state().cursorUnits(), payload.state().succeededUnits(),
                    payload.state().failedUnits(), snapshot.payload());
        }
        var session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session == null) {
            return durableNoProgress(snapshot, payload.state().cursorUnits(), payload.state().succeededUnits(),
                    payload.state().failedUnits(), snapshot.payload());
        }
        if (payload.workflowEntryId() >= 0
                && com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                        .from(player, payload.workflowEntryId()).isEmpty()) {
            var failed = snapshot.nextRevision(
                    com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.FAILED,
                    null, player.serverLevel().getGameTime(), payload.state().cursorUnits(),
                    payload.state().succeededUnits(), payload.state().failedUnits(), snapshot.payload());
            return new DurableTaskScheduler.SliceResult(failed, 0);
        }
        var result = RtsPlacementBatch.tickDetachedPlacementSlice(
                player, session, payload.state(), budget.maxUnits(),
                saturatingDeadline(System.nanoTime(), budget.remainingNanos()));
        var nextPayload = payload.withState(result.state());
        var lifecycle = switch (result.outcome()) {
            case CONTINUE -> com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.RUNNING;
            case WAITING_RESOURCE ->
                    com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.WAITING_RESOURCE;
            case COMPLETE -> com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.COMPLETED;
        };
        com.rtsbuilding.rtsbuilding.server.task.persistence.TaskWaitKey waitKey = null;
        if (result.outcome()
                == com.rtsbuilding.rtsbuilding.server.task.placement.PlacementSliceResult.Outcome.WAITING_RESOURCE) {
            String itemId = result.state().definition().getString("itemId");
            waitKey = new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskWaitKey(
                    "item", itemId.isBlank() ? "rtsbuilding:any-placement-item" : itemId);
        }
        var next = snapshot.nextRevision(lifecycle, waitKey, player.serverLevel().getGameTime(),
                result.state().cursorUnits(), result.state().succeededUnits(), result.state().failedUnits(),
                com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskCodec.encode(nextPayload));
        if (lifecycle.terminal()) {
            RtsPlacementBatch.recordDetachedHistory(player, result.state());
            projectDurableTerminal(player, next);
        }
        return new DurableTaskScheduler.SliceResult(next, result.processedUnits());
    }

    private DurableTaskScheduler.SliceResult executeDurableDestruction(
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot snapshot,
            TaskBudget budget) {
        DestructionProgressOverlay overlay = destructionProgressOverlays.get(snapshot.id());
        if (overlay != null && overlay.baseRevision() != snapshot.revision()) {
            destructionProgressOverlays.remove(snapshot.id());
            overlay = null;
        }
        // 第一次触碰世界前必须确认 root 已落盘；建立镜像后不再逐 slice 等待磁盘。
        if (overlay == null && !durableRevisionAcknowledged(snapshot)) {
            return new DurableTaskScheduler.SliceResult(snapshot, 0);
        }
        DestructionTaskPayload payload = overlay == null
                ? com.rtsbuilding.rtsbuilding.server.task.destruction.DestructionTaskCodec.decode(snapshot.payload())
                : overlay.payload();
        int uncheckpointedUnits = overlay == null ? 0 : overlay.uncheckpointedUnits();
        var player = activeServer == null ? null : activeServer.getPlayerList().getPlayer(payload.ownerId());
        if (player == null || !player.serverLevel().dimension().equals(payload.dimension())) {
            return durableNoProgress(snapshot, payload.state().cursorUnits(), payload.state().succeededUnits(),
                    payload.state().failedUnits(), snapshot.payload());
        }
        var session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session == null) {
            return durableNoProgress(snapshot, payload.state().cursorUnits(), payload.state().succeededUnits(),
                    payload.state().failedUnits(), snapshot.payload());
        }
        if (com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                .from(player, payload.workflowEntryId()).isEmpty()) {
            var failed = snapshot.nextRevision(
                    com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.FAILED,
                    null, player.serverLevel().getGameTime(), payload.state().cursorUnits(),
                    payload.state().succeededUnits(), payload.state().failedUnits(), snapshot.payload());
            return new DurableTaskScheduler.SliceResult(failed, 0);
        }
        var result = RtsDestructionBatch.tickDetachedDestructionSlice(
                player, session, payload.state(), budget.maxUnits(),
                saturatingDeadline(System.nanoTime(), budget.remainingNanos()));
        var nextPayload = new DestructionTaskPayload(
                payload.ownerId(), payload.dimension(), payload.workflowEntryId(), result.state());
        int pendingUnits = uncheckpointedUnits + result.processedUnits();

        if (result.outcome()
                == com.rtsbuilding.rtsbuilding.server.task.destruction.DestructionSliceResult.Outcome.CONTINUE) {
            // 大范围任务每 256 个目标合并一次检查点，避免目标和撤销历史每小批都整包写盘。
            boolean checkpointDue = durableRevisionAcknowledged(snapshot) && pendingUnits >= 256;
            if (!checkpointDue) {
                destructionProgressOverlays.put(snapshot.id(), new DestructionProgressOverlay(
                        snapshot.revision(), nextPayload, pendingUnits));
                return new DurableTaskScheduler.SliceResult(snapshot, result.processedUnits());
            }
            var checkpoint = snapshot.nextRevision(
                    com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.RUNNING,
                    null, player.serverLevel().getGameTime(), result.state().cursorUnits(),
                    result.state().succeededUnits(), result.state().failedUnits(),
                    com.rtsbuilding.rtsbuilding.server.task.destruction.DestructionTaskCodec.encode(nextPayload));
            destructionProgressOverlays.put(snapshot.id(), new DestructionProgressOverlay(
                    checkpoint.revision(), nextPayload, 0));
            return new DurableTaskScheduler.SliceResult(checkpoint, result.processedUnits());
        }

        // 等待资源或完成时必须先等上一检查点 ACK，再提交明确的生命周期边界。
        if (!durableRevisionAcknowledged(snapshot)) {
            destructionProgressOverlays.put(snapshot.id(), new DestructionProgressOverlay(
                    snapshot.revision(), nextPayload, pendingUnits));
            return new DurableTaskScheduler.SliceResult(snapshot, result.processedUnits());
        }
        destructionProgressOverlays.remove(snapshot.id());
        var lifecycle = switch (result.outcome()) {
            case CONTINUE -> com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.RUNNING;
            case WAITING_RESOURCE ->
                    com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.WAITING_RESOURCE;
            case COMPLETE -> com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.COMPLETED;
        };
        var waitKey = result.outcome()
                == com.rtsbuilding.rtsbuilding.server.task.destruction.DestructionSliceResult.Outcome.WAITING_RESOURCE
                ? new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskWaitKey(
                        "tool", "hotbar:" + Byte.toUnsignedInt(result.state().toolSlot()))
                : null;
        var next = snapshot.nextRevision(lifecycle, waitKey, player.serverLevel().getGameTime(),
                result.state().cursorUnits(), result.state().succeededUnits(), result.state().failedUnits(),
                com.rtsbuilding.rtsbuilding.server.task.destruction.DestructionTaskCodec.encode(nextPayload));
        if (lifecycle.terminal()) {
            RtsDestructionBatch.recordDetachedHistory(player, result.state());
            projectDurableTerminal(player, next);
            boolean another = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                    .coordinator().query().ownedBy(player.getUUID()).stream()
                    .anyMatch(other -> !other.id().equals(next.id()) && other.type() == TaskType.DESTRUCTION
                            && !other.state().terminal());
            if (!another) RtsDestructionBatch.returnDetachedDestroyTool(player, session);
        }
        return new DurableTaskScheduler.SliceResult(next, result.processedUnits());
    }

    private DurableTaskScheduler.SliceResult durableNoProgress(
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot snapshot,
            int cursor, int succeeded, int failed, net.minecraft.nbt.CompoundTag payload) {
        var next = snapshot.nextRevision(snapshot.state(), snapshot.waitKey(), snapshot.updatedGameTime(),
                cursor, succeeded, failed, payload);
        return new DurableTaskScheduler.SliceResult(next, 0);
    }

    private void projectDurableTerminal(net.minecraft.server.level.ServerPlayer player,
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot snapshot) {
        if (snapshot.workflowEntryId() < 0) return;
        var token = com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                .from(player, snapshot.workflowEntryId()).orElse(null);
        if (token == null) return;
        if (snapshot.state() == com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.COMPLETED) {
            token.complete();
        } else {
            token.cancel();
        }
    }

    private static long saturatingDeadline(long now, long remaining) {
        return remaining > 0 && now > Long.MAX_VALUE - remaining ? Long.MAX_VALUE : now + remaining;
    }



    private DurableTaskScheduler.SliceResult executeDurableMining(
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot snapshot,
            TaskBudget budget) {
        var overlay = miningProgressOverlays.get(snapshot.id());
        if (overlay != null && overlay.baseRevision() != snapshot.revision()) {
            miningProgressOverlays.remove(snapshot.id());
            overlay = null;
        }
        // 初次触碰世界前仍要求任务根已经落盘；建立镜像后，小批次推进不再等待每次 checkpoint ACK。
        if (overlay == null && !durableRevisionAcknowledged(snapshot)) {
            return new DurableTaskScheduler.SliceResult(snapshot, 0);
        }
        MiningTaskPayload payload = overlay == null
                ? com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskCodec.decode(snapshot.payload())
                : overlay.payload();
        long gameTime = activeServer == null ? snapshot.updatedGameTime()
                : activeServer.overworld().getGameTime();
        var executionState = payload.state();
        int uncheckpointedUnits = overlay == null ? 0 : overlay.uncheckpointedUnits();
        long lastCheckpointGameTime = overlay == null ? gameTime : overlay.lastCheckpointGameTime();
        var player = activeServer == null ? null : activeServer.getPlayerList().getPlayer(payload.ownerId());
        if (player == null || !player.serverLevel().dimension().equals(payload.dimension())) {
            return new DurableTaskScheduler.SliceResult(snapshot, 0);
        }
        var session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session == null) {
            return new DurableTaskScheduler.SliceResult(snapshot, 0);
        }
        var token = com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                .from(player, payload.workflowEntryId()).orElse(null);
        if (token == null) {
            miningProgressOverlays.remove(snapshot.id());
            var failed = snapshot.nextRevision(
                    com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.FAILED,
                    null, player.serverLevel().getGameTime(), payload.state().cursorUnits(),
                    payload.state().succeededUnits(), payload.state().failedUnits(), snapshot.payload());
            return new DurableTaskScheduler.SliceResult(failed, 0);
        }
        var result = RtsMiningStateMachine.tickDetachedMiningSlice(
                player, session, executionState, budget.maxUnits(),
                saturatingDeadline(System.nanoTime(), budget.remainingNanos()));
        if (result.succeededUnits() > 0) token.updateProgress(result.succeededUnits(), null);
        if (result.failedUnits() > 0) token.recordFailures(result.failedUnits());

        int pendingUnits = uncheckpointedUnits + result.processedUnits();
        if (result.outcome() == com.rtsbuilding.rtsbuilding.server.task.mining.MiningSliceResult.Outcome.CONTINUE
                || result.outcome() == com.rtsbuilding.rtsbuilding.server.task.mining.MiningSliceResult.Outcome.NEXT_TICK) {
            // 最多每 256 个目标合并写一次检查点；普通 256 方块连锁不会再经历中途磁盘停顿。
            boolean checkpointDue = durableRevisionAcknowledged(snapshot) && pendingUnits >= 256;
            if (!checkpointDue) {
                miningProgressOverlays.put(snapshot.id(), new MiningProgressOverlay(
                        snapshot.revision(), payload.withState(result.state()), pendingUnits, lastCheckpointGameTime));
                return new DurableTaskScheduler.SliceResult(snapshot, result.processedUnits());
            }
            var checkpoint = snapshot.nextRevision(
                    com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.RUNNING,
                    null, player.serverLevel().getGameTime(), result.state().cursorUnits(),
                    result.state().succeededUnits(), result.state().failedUnits(),
                    com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskCodec
                            .encode(payload.withState(result.state())));
            miningProgressOverlays.put(snapshot.id(), new MiningProgressOverlay(
                    checkpoint.revision(), payload.withState(result.state()), 0, gameTime));
            return new DurableTaskScheduler.SliceResult(checkpoint, result.processedUnits());
        }

        // 等待只保存在轻量镜像并每 Tick 重试，避免 buffer/tool/chunk 的 ACK 往返把任务挂死。
        if (result.outcome() == com.rtsbuilding.rtsbuilding.server.task.mining.MiningSliceResult.Outcome.WAITING) {
            miningProgressOverlays.put(snapshot.id(), new MiningProgressOverlay(
                    snapshot.revision(), payload.withState(result.state()), pendingUnits, lastCheckpointGameTime));
            return new DurableTaskScheduler.SliceResult(snapshot, result.processedUnits());
        }

        // 若前一检查点仍在写盘，先保留已完成镜像；ACK 到达后再一次性提交终态。
        if (!durableRevisionAcknowledged(snapshot)) {
            miningProgressOverlays.put(snapshot.id(), new MiningProgressOverlay(
                    snapshot.revision(), payload.withState(result.state()), pendingUnits, lastCheckpointGameTime));
            return new DurableTaskScheduler.SliceResult(snapshot, result.processedUnits());
        }
        miningProgressOverlays.remove(snapshot.id());
        var lifecycle = switch (result.outcome()) {
            case CONTINUE, NEXT_TICK ->
                    com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.RUNNING;
            case WAITING -> result.waitHint().kind().equals("chunk")
                    ? com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.WAITING_CHUNK
                    : com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.WAITING_RESOURCE;
            case COMPLETE -> com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.COMPLETED;
        };
        var waitKey = result.waitHint() == null ? null
                : new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskWaitKey(
                        result.waitHint().kind(), result.waitHint().value());
        var next = snapshot.nextRevision(lifecycle, waitKey, player.serverLevel().getGameTime(),
                result.state().cursorUnits(), result.state().succeededUnits(), result.state().failedUnits(),
                com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskCodec
                        .encode(payload.withState(result.state())));
        if (lifecycle.terminal()) {
            projectDurableTerminal(player, next);
        }
        return new DurableTaskScheduler.SliceResult(next, result.processedUnits());
    }

    /** 所有会触碰世界、物品或外部储存的 slice 都必须先看到当前 revision 的 root ACK。 */
    private static boolean durableRevisionAcknowledged(
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot snapshot) {
        return com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator().hasAcknowledged(snapshot.id(), snapshot.revision());
    }

    private record MiningProgressOverlay(
            long baseRevision,
            MiningTaskPayload payload,
            int uncheckpointedUnits,
            long lastCheckpointGameTime) {
    }

    private record DestructionProgressOverlay(
            long baseRevision,
            DestructionTaskPayload payload,
            int uncheckpointedUnits) {
    }


}
