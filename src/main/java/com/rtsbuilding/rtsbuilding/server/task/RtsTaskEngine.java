package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.destruction.RtsDestructionBatch;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsDropAbsorber;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlueprintTickPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.service.RtsPlacedRecoveryService;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Command Gateway 之后的统一任务执行入口。
 *
 * <p>放置、拆除、挖掘与掉落缓存回写共享同一个全局数量/时间预算。
 * 新功能必须直接提交真正的 TaskRecord，不得再新增 Session 内的独立 Tick 驱动器。</p>
 */
public final class RtsTaskEngine {
    public static final RtsTaskEngine INSTANCE = new RtsTaskEngine();

    private final TaskScheduler scheduler = new TaskScheduler(System::nanoTime);
    private final RtsDurableTaskExecutionRuntime durableRuntime =
            new RtsDurableTaskExecutionRuntime();
    private final Map<WorkflowTaskKey, TaskRecord> blueprintRecords = new java.util.HashMap<>();
    private final Map<UUID, TaskRecord> funnelRecords = new java.util.HashMap<>();
    private final Map<UUID, TaskRecord> recoveryRecords = new java.util.HashMap<>();
    private final Map<WorkflowTaskKey, Boolean> workflowPauseOverrides = new java.util.HashMap<>();
    private final Map<UUID, TaskStatus> projectedTaskStatuses = new java.util.HashMap<>();
    private final Map<com.rtsbuilding.rtsbuilding.server.task.identity.TaskId,
            Long> projectedDurableStates =
            new java.util.HashMap<>();
    private final DurableBlueprintTaskBridge durableBlueprintBridge =
            new DurableBlueprintTaskBridge(this,
                    com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE);
    private RtsTaskEngine() {
        scheduler.registerExecutor(TaskType.BLUEPRINT, this::executeBlueprint);
        scheduler.registerExecutor(TaskType.FUNNEL, this::executeFunnel);
        scheduler.registerExecutor(TaskType.PLACED_RECOVERY, this::executePlacedRecovery);
    }

    public TaskScheduler.TickStats tick(MinecraftServer server) {
        durableBlueprintBridge.beforeTaskTick(server);
        var coordinator = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator();
        durableRuntime.beginTick(server, coordinator);
        var sessionService = ServiceRegistry.getInstance().session();
        for (var player : server.getPlayerList().getPlayers()) {
            var session = sessionService.getIfPresent(player);
            if (session == null) continue;
            drainMiningBuffer(player, session);
            resumeAvailableToolWaits(player, session, coordinator);
            syncFunnelTask(player, session);
            syncPlacedRecoveryTask(player, session);
        }
        java.util.LinkedHashMap<com.rtsbuilding.rtsbuilding.server.task.identity.TaskId,
                com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot> durableCandidates =
                new java.util.LinkedHashMap<>();
        for (var player : server.getPlayerList().getPlayers()) {
            coordinator.query().runnableFor(player.getUniqueID(), dimensionId(player.dimension))
                    .stream()
                    .filter(snapshot -> snapshot.type() == TaskType.PLACEMENT
                            || snapshot.type() == TaskType.DESTRUCTION
                            || snapshot.type() == TaskType.MINING)
                    .forEach(snapshot -> durableCandidates.put(snapshot.id(), snapshot));
        }
        DurableTaskScheduler.TickStats durableStats = durableRuntime.tick(
                coordinator, durableCandidates.values(),
                Config.taskEngineMaxNanosPerTick(), Config.taskEngineMaxUnitsPerTick(),
                Config.taskEngineMaxUnitsPerSlice());
        long remainingNanos = Math.max(0L,
                Config.taskEngineMaxNanosPerTick() - durableStats.elapsedNanos());
        int remainingUnits = Math.max(0,
                Config.taskEngineMaxUnitsPerTick() - durableStats.processedUnits());
        TaskScheduler.TickStats legacyStats = scheduler.tick(
                remainingNanos, remainingUnits, Config.taskEngineMaxUnitsPerSlice());
        TaskScheduler.TickStats stats = new TaskScheduler.TickStats(
                durableStats.slices() + legacyStats.slices(),
                durableStats.processedUnits() + legacyStats.processedUnits(),
                durableStats.elapsedNanos() + legacyStats.elapsedNanos(),
                durableStats.timeBudgetExhausted() || legacyStats.timeBudgetExhausted(),
                durableStats.unitBudgetExhausted() || legacyStats.unitBudgetExhausted());
        durableBlueprintBridge.afterTaskTick(server);
        projectWorkflowLifecycles();
        projectDurableWorkflowLifecycles(server, coordinator);
        blueprintRecords.entrySet().removeIf(entry -> entry.getValue().status().terminal());
        return stats;
    }

    /** command gateway 只负责排队；返回成功不代表执行器或 Workflow 已经可见。 */
    public DurableBlueprintTaskBridge.QueueResult queueDurableBlueprint(BlueprintContext context) {
        return durableBlueprintBridge.queue(context);
    }

    /** 旧 Workflow heavy-extraData 的确定性迁移入口。 */
    public DurableBlueprintTaskBridge.QueueResult queueLegacyDurableBlueprint(BlueprintContext context) {
        return durableBlueprintBridge.queueLegacy(context);
    }

    /** 兼容入口据此跳过旧 Workflow heavy-extraData checkpoint。 */
    public boolean isDurableBlueprintContext(BlueprintContext context) {
        return durableBlueprintBridge.isDurableContext(context);
    }

    /** 开发者采样读取的轻量任务快照；不暴露可变 TaskRecord 或领域 payload。 */
    public TaskDiagnostics diagnostics(UUID ownerId) {
        java.util.EnumMap<TaskType, Integer> active = new java.util.EnumMap<>(TaskType.class);
        java.util.EnumMap<TaskType, Integer> waiting = new java.util.EnumMap<>(TaskType.class);
        java.util.List<TaskRecord> records = new java.util.ArrayList<>();
        records.addAll(blueprintRecords.values());
        TaskRecord funnel = funnelRecords.get(ownerId);
        if (funnel != null) records.add(funnel);
        TaskRecord recovery = recoveryRecords.get(ownerId);
        if (recovery != null) records.add(recovery);
        for (TaskRecord record : records) {
            if (!record.ownerId().equals(ownerId) || record.status().terminal()) continue;
            active.merge(record.type(), 1, Integer::sum);
            if (record.status() == TaskStatus.WAITING_RESOURCE) {
                waiting.merge(record.type(), 1, Integer::sum);
            }
        }
        for (var snapshot : com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator().query().ownedBy(ownerId)) {
            if (snapshot.state().terminal()) continue;
            active.merge(snapshot.type(), 1, Integer::sum);
            if (snapshot.state().waiting()) waiting.merge(snapshot.type(), 1, Integer::sum);
        }
        return new TaskDiagnostics(active, waiting);
    }

    public void detachPlayer(UUID playerId) {
        // 必须在移除 scheduler lane 前把 durable cursor 冻结，并释放 Context→EntityPlayerMP 强引用。
        durableBlueprintBridge.detachOwner(playerId);
        long now = System.nanoTime();
        for (TaskRecord detached : scheduler.detachOwner(playerId)) {
            if (!isPhaseOneDurable(detached.type())) detached.cancel(now);
        }
        java.util.Set<UUID> removedTaskIds = new java.util.HashSet<>();
        blueprintRecords.values().stream().filter(record -> record.ownerId().equals(playerId))
                .map(TaskRecord::id).forEach(removedTaskIds::add);
        TaskRecord funnel = funnelRecords.get(playerId);
        if (funnel != null) removedTaskIds.add(funnel.id());
        TaskRecord recovery = recoveryRecords.get(playerId);
        if (recovery != null) removedTaskIds.add(recovery.id());
        blueprintRecords.keySet().removeIf(key -> key.playerId().equals(playerId));
        funnelRecords.remove(playerId);
        recoveryRecords.remove(playerId);
        workflowPauseOverrides.keySet().removeIf(key -> key.playerId().equals(playerId));
        projectedTaskStatuses.keySet().removeAll(removedTaskIds);
    }

    /**
     * 登出/停用前把所有仍留在 Session 的旧迁移源提交给 TaskStore。
     * 调用方随后必须执行 owner 定向 flush，并再次调用 {@link #reconcilePlayerDetach}。
     */
    public void preparePlayerDetach(net.minecraft.entity.player.EntityPlayerMP player) {
        if (player == null) return;
        var session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session == null) return;
        RtsDropAbsorber.flushDropBufferToPlayer(player, session);
    }

    /** owner flush 后消费 root ACK，并把已接管的 Session shadow 转成持久 clear。 */
    public void reconcilePlayerDetach(net.minecraft.entity.player.EntityPlayerMP player) {
        if (player == null) return;
        var session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session == null) return;
        RtsDropAbsorber.flushDropBufferToPlayer(player, session);
    }

    private static boolean isPhaseOneDurable(TaskType type) {
        return type == TaskType.BLUEPRINT;
    }

    /** ServerStopping 的 persistence.stop 前置 barrier。 */
    public void checkpointAllDurableExecutions(MinecraftServer server) {
        durableRuntime.checkpointMiningExecutions(
                com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE.coordinator(),
                server.getWorld(0).getTotalWorldTime());
        durableRuntime.checkpointDestructionExecutions(
                com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE.coordinator(),
                server.getWorld(0).getTotalWorldTime());
        durableBlueprintBridge.checkpointAll(server.getWorld(0).getTotalWorldTime());
    }

    /** persistence.stop 成功后调用，清除单例中的旧世界引用。 */
    public void resetDurableRuntimeAfterServerStop() {
        durableBlueprintBridge.resetAfterServerStop();
        scheduler.clear();
        durableRuntime.resetAfterServerStop();
        blueprintRecords.clear();
        funnelRecords.clear();
        recoveryRecords.clear();
        workflowPauseOverrides.clear();
        projectedTaskStatuses.clear();
        projectedDurableStates.clear();
    }

    /** 玩家暂停/恢复命令的唯一执行入口；WorkflowToken 只负责展示投影。 */
    public boolean setWorkflowPaused(net.minecraft.entity.player.EntityPlayerMP player, int workflowEntryId,
            boolean paused) {
        if (player == null || workflowEntryId < 0) return false;
        WorkflowTaskKey key = new WorkflowTaskKey(
                player.getUniqueID(), player.dimension, workflowEntryId);
        var workflowEngine =
                com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance();
        var workflowEntry = workflowEngine.findEntryByPlayer(player, workflowEntryId);
        if (workflowEntry == null || workflowEntry.terminal()) return false;
        var workflow = workflowEngine.getProgress(player, workflowEntryId);
        if (!workflow.isActive() || !isTaskEngineWorkflow(workflow.type())) return false;
        workflowPauseOverrides.put(key, paused);
        var coordinator = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator();
        var durable = coordinator.query().findByWorkflow(
                player.getUniqueID(), dimensionId(player.dimension), workflowEntryId)
                .orElse(null);
        if (durable != null && (durable.type() == TaskType.PLACEMENT
                || durable.type() == TaskType.DESTRUCTION
                || durable.type() == TaskType.MINING) && !durable.state().terminal()) {
            var state = paused
                    ? com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.PAUSED
                    : com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.QUEUED;
            var next = durable.type() == TaskType.MINING
                    ? durableRuntime.transitionMiningSnapshot(
                            durable, state, gameTime(player))
                    : durable.type() == TaskType.DESTRUCTION
                            ? durableRuntime.transitionDestructionSnapshot(
                                    durable, state, gameTime(player))
                            : durable.nextRevision(state, null,
                                    gameTime(player), durable.cursorUnits(),
                                    durable.succeededUnits(), durable.failedUnits(), durable.payload());
            coordinator.replace(next);
        }
        TaskRecord record = findWorkflowTask(key);
        if (record != null) {
            if (paused) record.pause(System.nanoTime());
            else record.resume(System.nanoTime());
        }
        // 工作流已经存在但 TaskRecord 可能要到本 tick 的同步阶段才创建；覆盖值会在创建时应用。
        return true;
    }

    /** RTS 关闭时先暂停真实任务，再由工作流引擎刷新显示。 */
    public void pauseAllWorkflowTasks(net.minecraft.entity.player.EntityPlayerMP player) {
        if (player == null) return;
        for (var status : com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                .getAllProgress(player)) {
            if (status.isActive() && !status.suspended()) {
                setWorkflowPaused(player, status.entryId(), true);
            }
        }
    }

    /** 删除工作流前先收拢对应领域任务，不等待下一个 tick 通过“token 缺失”兜底。 */
    public boolean cancelWorkflowTask(net.minecraft.entity.player.EntityPlayerMP player, int workflowEntryId) {
        if (player == null) return false;
        return cancelWorkflowTask(player, player.dimension, workflowEntryId);
    }

    /**
     * 收口指定维度的真实任务。工作流超时可能扫描到玩家当前维度以外的旧投影，
     * 因此不能用玩家当前维度偷偷替换任务身份。
     */
    public boolean cancelWorkflowTask(
            net.minecraft.entity.player.EntityPlayerMP player,
            int dimension,
            int workflowEntryId) {
        if (player == null || workflowEntryId < 0) return false;
        boolean currentDimension = player.dimension == dimension;
        WorkflowTaskKey key = new WorkflowTaskKey(
                player.getUniqueID(), dimension, workflowEntryId);
        TaskRecord record = findWorkflowTask(key);
        var coordinator = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator();
        var durable = coordinator.query().findByWorkflow(
                player.getUniqueID(), dimensionId(dimension), workflowEntryId)
                .orElse(null);
        boolean durableCancelled = false;
        if (durable != null && (durable.type() == TaskType.PLACEMENT
                || durable.type() == TaskType.DESTRUCTION
                || durable.type() == TaskType.MINING) && !durable.state().terminal()) {
            if (currentDimension) {
                try {
                    if (durable.type() == TaskType.PLACEMENT) {
                        RtsPlacementBatch.recordDetachedHistory(player,
                                com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskCodec
                                        .decode(durable.payload()).state());
                    } else if (durable.type() == TaskType.DESTRUCTION) {
                        RtsDestructionBatch.recordDetachedHistory(player,
                                durableRuntime.currentDestructionState(durable));
                    } else {
                        RtsMiningStateMachine.finalizeDetachedCancellation(player,
                                ServiceRegistry.getInstance().session().getIfPresent(player),
                                durableRuntime.currentMiningState(durable));
                    }
                } catch (RuntimeException malformedHistory) {
                    // 损坏任务仍必须能够被收口；历史恢复失败不能让 poison task 永久占住队列。
                    RtsbuildingMod.LOGGER.warn("[TaskEngine] 取消工作流 #{} 时无法恢复历史，将直接终止任务",
                            workflowEntryId, malformedHistory);
                }
            }
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot cancelled;
            try {
                cancelled = durable.type() == TaskType.MINING
                        ? durableRuntime.transitionMiningSnapshot(durable,
                                com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.CANCELLED,
                                gameTime(player))
                        : durable.type() == TaskType.DESTRUCTION
                                ? durableRuntime.transitionDestructionSnapshot(durable,
                                        com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.CANCELLED,
                                        gameTime(player))
                                : durable.nextRevision(
                                        com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.CANCELLED,
                                        null, gameTime(player), durable.cursorUnits(),
                                        durable.succeededUnits(), durable.failedUnits(), durable.payload());
            } catch (RuntimeException malformedTransition) {
                cancelled = durable.nextRevision(
                        com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.CANCELLED,
                        null, gameTime(player), durable.cursorUnits(),
                        durable.succeededUnits(), durable.failedUnits(), durable.payload());
            }
            coordinator.replace(cancelled);
            coordinator.requestTombstone(cancelled.id(), cancelled.updatedGameTime());
            durableCancelled = true;
        }
        var session = ServiceRegistry.getInstance().session().getIfPresent(player);
        boolean cleaned = currentDimension && session != null
                && RtsMiningStateMachine.cancelMiningTask(player, session, workflowEntryId);
        if (record != null) {
            record.cancel(System.nanoTime());
            if (record.payload() instanceof BlueprintTaskPayload) {
                com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlueprintPersistence
                        .clearFromEntry(player, workflowEntryId);
            }
            releaseTerminalWorkflow(record);
            if (record.payload() instanceof BlueprintTaskPayload) {
                blueprintRecords.remove(key);
            }
        }
        workflowPauseOverrides.remove(key);
        return record != null || cleaned || durableCancelled;
    }

    /** 仅唤醒与本次真实物品变化匹配的 placement 等待任务，不扫描其它挂起任务。 */
    public void resumeWaitingPlacementItems(net.minecraft.entity.player.EntityPlayerMP player,
            java.util.Collection<String> changedItemIds) {
        if (player == null || changedItemIds == null || changedItemIds.isEmpty()) return;
        var coordinator = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator();
        long gameTime = gameTime(player);
        for (String itemId : new java.util.LinkedHashSet<>(changedItemIds)) {
            if (itemId == null || itemId.trim().isEmpty()) continue;
            var key = new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskWaitKey("item", itemId);
            for (var snapshot : coordinator.query().waitingFor(key)) {
                if (!snapshot.ownerId().equals(player.getUniqueID()) || snapshot.type() != TaskType.PLACEMENT
                        || !snapshot.dimensionId().equals(
                                dimensionId(player.dimension))) continue;
                coordinator.replace(snapshot.nextRevision(
                        com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.QUEUED,
                        null, gameTime, snapshot.cursorUnits(), snapshot.succeededUnits(),
                        snapshot.failedUnits(), snapshot.payload()));
            }
        }
    }

    /** 玩家显式点击“重试”时恢复自己的全部 placement 等待任务；仍不遍历全服任务。 */
    public int resumeAllWaitingPlacements(net.minecraft.entity.player.EntityPlayerMP player) {
        if (player == null) return 0;
        var coordinator = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator();
        int resumed = 0;
        long gameTime = gameTime(player);
        String dimensionId = dimensionId(player.dimension);
        for (var snapshot : coordinator.query().ownedBy(player.getUniqueID())) {
            if (snapshot.type() != TaskType.PLACEMENT
                    || !snapshot.dimensionId().equals(dimensionId)
                    || snapshot.state() != com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.WAITING_RESOURCE) {
                continue;
            }
            coordinator.replace(snapshot.nextRevision(
                    com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.QUEUED,
                    null, gameTime, snapshot.cursorUnits(), snapshot.succeededUnits(),
                    snapshot.failedUnits(), snapshot.payload()));
            resumed++;
        }
        return resumed;
    }

    /** UI 扫描只读取 TaskStore 中当前维度、当前 workflow 的等待快照。 */
    public PendingPlacementTaskView findWaitingPlacement(
            net.minecraft.entity.player.EntityPlayerMP player, int workflowEntryId) {
        if (player == null || workflowEntryId < 0) return null;
        var snapshot = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator().query().findByWorkflow(
                        player.getUniqueID(), dimensionId(player.dimension), workflowEntryId)
                .orElse(null);
        if (snapshot == null || snapshot.type() != TaskType.PLACEMENT
                || snapshot.state()
                != com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.WAITING_RESOURCE) {
            return null;
        }
        PlacementTaskPayload payload = com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskCodec
                .decode(snapshot.payload());
        if (!payload.ownerId().equals(snapshot.ownerId())
                || !dimensionId(payload.dimension()).equals(snapshot.dimensionId())
                || payload.workflowEntryId() != snapshot.workflowEntryId()) {
            throw new IllegalStateException("placement task snapshot/payload 身份不一致");
        }
        return new PendingPlacementTaskView(snapshot.id(), snapshot.revision(), payload.state());
    }

    /**
     * 把显式恢复策略先写入 TaskStore 新 revision，再交给 durable executor。
     * 覆盖策略包含世界破坏，因此绝不能在本方法中直接修改世界。
     */
    public boolean resumeWaitingPlacementWithStrategy(
            net.minecraft.entity.player.EntityPlayerMP player, int workflowEntryId, int strategy,
            com.rtsbuilding.rtsbuilding.server.task.identity.TaskId expectedTaskId,
            long expectedRevision) {
        if (player == null || workflowEntryId < 0 || (strategy != 0 && strategy != 1)
                || expectedTaskId == null || expectedRevision < 1L) return false;
        var coordinator = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator();
        var snapshot = coordinator.query().get(expectedTaskId)
                .orElse(null);
        if (snapshot == null || snapshot.type() != TaskType.PLACEMENT
                || snapshot.revision() != expectedRevision
                || !snapshot.ownerId().equals(player.getUniqueID())
                || !snapshot.dimensionId().equals(dimensionId(player.dimension))
                || snapshot.workflowEntryId() != workflowEntryId
                || snapshot.state()
                != com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.WAITING_RESOURCE) {
            return false;
        }
        PlacementTaskPayload payload = com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskCodec
                .decode(snapshot.payload());
        if (!payload.ownerId().equals(snapshot.ownerId())
                || !dimensionId(payload.dimension()).equals(snapshot.dimensionId())
                || payload.workflowEntryId() != workflowEntryId) return false;
        var nextState = RtsPlacementBatch.applyDetachedResumeStrategy(player, payload.state(), strategy);
        if (nextState == null) return false;
        var nextPayload = payload.withState(nextState);
        coordinator.replace(snapshot.nextRevision(
                com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.QUEUED,
                null, gameTime(player), nextState.cursorUnits(),
                nextState.succeededUnits(), nextState.failedUnits(),
                com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskCodec.encode(nextPayload)));
        return true;
    }

    public String firstPlacementItemId(net.minecraft.entity.player.EntityPlayerMP player) {
        if (player == null) return "";
        return com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator().query().ownedBy(player.getUniqueID()).stream()
                .filter(snapshot -> snapshot.type() == TaskType.PLACEMENT && !snapshot.state().terminal())
                .map(snapshot -> com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskCodec
                        .decode(snapshot.payload()).state().definition().getString("itemId"))
                .filter(value -> value != null && !value.trim().isEmpty())
                .findFirst().orElse("");
    }

    /** 返回当前维度一个仍活动的 durable mining workflow；控制管道不再读取 Session 游标。 */
    public int activeMiningWorkflowEntry(net.minecraft.entity.player.EntityPlayerMP player) {
        if (player == null) return -1;
        String dimensionId = dimensionId(player.dimension);
        return com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator().query().ownedBy(player.getUniqueID()).stream()
                .filter(snapshot -> snapshot.type() == TaskType.MINING && !snapshot.state().terminal())
                .filter(snapshot -> snapshot.dimensionId().equals(dimensionId))
                .mapToInt(com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot::workflowEntryId)
                .filter(entryId -> entryId >= 0)
                .findFirst().orElse(-1);
    }

    /**
     * 判断玩家的当前挖掘是否已经越过“必须持续按住”的首块阶段。
     *
     * <p>查询时必须读取 durable 执行镜像，而不能只看最近一次落盘快照；首块可能已经在本 tick
     * 完成并切换到批处理，但新的 checkpoint 仍在等待持久化 ACK。</p>
     */
    public boolean hasCommittedMiningBatch(net.minecraft.entity.player.EntityPlayerMP player) {
        if (player == null) return false;
        String dimensionId = dimensionId(player.dimension);
        var states = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator().query().ownedBy(player.getUniqueID()).stream()
                .filter(snapshot -> snapshot.type() == TaskType.MINING && !snapshot.state().terminal())
                .filter(snapshot -> snapshot.dimensionId().equals(dimensionId))
                .map(durableRuntime::currentMiningState)
                .collect(Collectors.toList());
        return !states.isEmpty() && states.stream()
                .allMatch(com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState::committedBatch);
    }

    /** 停止当前维度的全部 durable mining；Session 清理由旧迁移兼容层随后完成。 */
    public int cancelActiveMiningTasks(net.minecraft.entity.player.EntityPlayerMP player) {
        if (player == null) return 0;
        String dimensionId = dimensionId(player.dimension);
        var workflowIds = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator().query().ownedBy(player.getUniqueID()).stream()
                .filter(snapshot -> snapshot.type() == TaskType.MINING && !snapshot.state().terminal())
                .filter(snapshot -> snapshot.dimensionId().equals(dimensionId))
                .map(com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot::workflowEntryId)
                .filter(entryId -> entryId >= 0)
                .distinct().collect(Collectors.toList());
        int cancelled = 0;
        for (int workflowId : workflowIds) {
            if (cancelWorkflowTask(player, workflowId)) cancelled++;
        }
        return cancelled;
    }

    /** 领域入口提交后读取 TaskStore 的过滤后总量，避免再窥探 Session 旧 Job。 */
    public int workflowTaskTotalUnits(
            net.minecraft.entity.player.EntityPlayerMP player, int workflowEntryId) {
        if (player == null || workflowEntryId < 0) return 0;
        return com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator().query().findByWorkflow(
                        player.getUniqueID(), dimensionId(player.dimension), workflowEntryId)
                .map(com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot::totalUnits)
                .orElse(0);
    }

    private void resumeAvailableToolWaits(
            net.minecraft.entity.player.EntityPlayerMP player,
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session,
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceCoordinator coordinator) {
        if (player == null || session == null
                || (!player.isCreative()
                && !session.mining.miningSelectedToolRequested
                && (session.mining.miningToolLease == null
                || session.mining.miningToolLease.isEmpty()))
                || com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator
                        .isToolNearBreak(player, session)) return;
        resumeWaitKey(coordinator, player.getUniqueID(),
                new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskWaitKey(
                        "tool", "usable_mining_tool"), gameTime(player));
        for (int slot = 0; slot < 9; slot++) {
            resumeWaitKey(coordinator, player.getUniqueID(),
                    new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskWaitKey(
                            "tool", "hotbar:" + slot), gameTime(player));
        }
    }

    /** Chunk Load 事件只命中该 chunk 的 WaitIndex 桶。 */
    public void resumeLoadedChunk(net.minecraft.world.WorldServer level,
            net.minecraft.util.math.ChunkPos chunkPos) {
        if (level == null || chunkPos == null) return;
        var runtime = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE;
        // GameTest/专用服务器会在 ServerStarting 之前提升出生区块；此时还没有恢复索引可唤醒。
        if (!runtime.isStarted()) return;
        var coordinator = runtime.coordinator();
        var key = new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskWaitKey(
                "chunk", level.provider.getDimension() + ":" + chunkPos.x + ":" + chunkPos.z);
        for (var snapshot : coordinator.query().waitingFor(key)) {
            coordinator.replace(snapshot.nextRevision(
                    com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.QUEUED,
                    null, level.getTotalWorldTime(), snapshot.cursorUnits(), snapshot.succeededUnits(),
                    snapshot.failedUnits(), snapshot.payload()));
        }
    }

    private static void resumeWaitKey(
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceCoordinator coordinator,
            UUID ownerId,
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskWaitKey key,
            long gameTime) {
        for (var snapshot : coordinator.query().waitingFor(key)) {
            if (!snapshot.ownerId().equals(ownerId)) continue;
            coordinator.replace(snapshot.nextRevision(
                    com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.QUEUED,
                    null, gameTime, snapshot.cursorUnits(), snapshot.succeededUnits(),
                    snapshot.failedUnits(), snapshot.payload()));
        }
    }

    /**
     * 蓝图同步准入完成后的唯一提交入口；恢复路径也使用同一个入口。
     */
    public void submitBlueprint(BlueprintContext context, java.util.LinkedList<Integer> restoredRemaining) {
        if (context == null || context.player() == null) return;
        submitBlueprint(context, restoredRemaining, context.player().dimension);
    }

    /** root 已 ACK 后由 durable bridge 调用；TaskRecord 复用稳定 TaskId，不再生成 workflow 派生 UUID。 */
    TaskRecord activateDurableBlueprint(
            com.rtsbuilding.rtsbuilding.server.task.identity.TaskId taskId,
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot snapshot,
            BlueprintContext context,
            int sourceDimension) {
        int entryId = context.getData(
                com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext.KEY_WORKFLOW_ENTRY_ID);
        WorkflowTaskKey key = new WorkflowTaskKey(context.player().getUniqueID(), sourceDimension, entryId);
        TaskRecord existing = blueprintRecords.get(key);
        if (existing != null && !existing.status().terminal()) return existing;
        long now = System.nanoTime();
        BlueprintTaskPayload payload = new BlueprintTaskPayload(context,
                context.isPreparing() ? null : context.getRemainingQueue(), sourceDimension);
        TaskRecord record = new TaskRecord(taskId.value(), context.player().getUniqueID(),
                TaskType.BLUEPRINT, payload, snapshot.totalUnits(), now);
        record.restoreDurableSnapshot(snapshot.cursorUnits(), snapshot.succeededUnits(),
                snapshot.failedUnits(), DurableBlueprintTaskBridge.runtimeState(snapshot.state()), now);
        blueprintRecords.put(key, record);
        scheduler.submit(record);
        return record;
    }

    public void submitBlueprint(BlueprintContext context, java.util.LinkedList<Integer> restoredRemaining,
            int sourceDimension) {
        if (context == null || context.player() == null) return;
        Integer entryId = context.getData(
                com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext.KEY_WORKFLOW_ENTRY_ID);
        if (entryId == null || entryId < 0) return;
        WorkflowTaskKey key = new WorkflowTaskKey(
                context.player().getUniqueID(), sourceDimension, entryId);
        TaskRecord existing = blueprintRecords.get(key);
        if (existing != null && !existing.status().terminal()) return;

        long now = System.nanoTime();
        BlueprintTaskPayload payload = new BlueprintTaskPayload(context, restoredRemaining, sourceDimension);
        TaskRecord record = new TaskRecord(
                UUID.nameUUIDFromBytes((context.player().getUniqueID() + ":blueprint:"
                        + sourceDimension + ":" + entryId)
                        .getBytes(StandardCharsets.UTF_8)),
                context.player().getUniqueID(), TaskType.BLUEPRINT, payload,
                context.getBlueprint().blockCount(), now);
        if (restoredRemaining != null) {
            int cursor = Math.max(0, context.getBlueprint().blockCount() - restoredRemaining.size());
            int succeeded = Math.min(cursor, Math.max(0, context.getPlacedCount()));
            record.restoreSnapshot(cursor, succeeded, Math.max(0, cursor - succeeded), now);
        }
        applyInitialPause(context.player(), entryId, record, now, sourceDimension);
        blueprintRecords.put(key, record);
        scheduler.submit(record);
    }

    public BlueprintContext findBlueprintContext(
            net.minecraft.entity.player.EntityPlayerMP player, int workflowEntryId) {
        if (player == null) return null;
        TaskRecord record = blueprintRecords.get(new WorkflowTaskKey(
                player.getUniqueID(), player.dimension, workflowEntryId));
        if (record == null || record.status().terminal()
                || !(record.payload() instanceof BlueprintTaskPayload)) return null;
        BlueprintTaskPayload payload = (BlueprintTaskPayload) record.payload();
        return payload.context();
    }

    public boolean resumeBlueprint(
            net.minecraft.entity.player.EntityPlayerMP player, int workflowEntryId) {
        TaskRecord record = player == null ? null
                : blueprintRecords.get(new WorkflowTaskKey(
                        player.getUniqueID(), player.dimension, workflowEntryId));
        if (record == null || record.status().terminal()) return false;
        if (record.payload() instanceof BlueprintTaskPayload) {
            BlueprintTaskPayload payload = (BlueprintTaskPayload) record.payload();
            payload.resetPlacementCycle();
        }
        record.resume(System.nanoTime());
        return true;
    }

    /**
     * 检查 durable task 是否仍占用某个工作流编号。
     *
     * <p>工作流面板与 durable task 分别持久化，旧世界恢复时两边的 nextId
     * 可能短暂不同步。新工作流创建前必须避开仍由任务根占用的编号，不能等到
     * 任务提交时再以异常形式拒绝玩家操作。</p>
     */
    public boolean hasDurableTaskForWorkflow(
            net.minecraft.entity.player.EntityPlayerMP player, int workflowEntryId) {
        if (player == null || workflowEntryId < 0) return false;
        return com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator()
                .query()
                .findByWorkflow(
                        player.getUniqueID(),
                        dimensionId(player.dimension),
                        workflowEntryId)
                .isPresent();
    }

    public boolean submitPlacementJob(net.minecraft.entity.player.EntityPlayerMP player,
            RtsPlacementBatch.PlaceBatchJob job) {
        if (player == null || job == null) return false;
        var coordinator = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator();
        reconcileHiddenDurableWorkflows(player, coordinator);
        if (job.quickBuild() && !makeRoomForDurableTaskFamily(
                player, coordinator, TaskType.PLACEMENT, Config.buildBatchMaxQueuedJobs(),
                RtsTaskEngine::occupiesQuickBuildSlot)) {
            player.sendStatusMessage(
                    new net.minecraft.util.text.TextComponentTranslation(
                            "message.rtsbuilding.quick_build.queue_full",
                            Config.buildBatchMaxQueuedJobs()),
                    true);
            return false;
        }
        PlacementTaskPayload payload = new PlacementTaskPayload(
                player.getUniqueID(), player.dimension, job.workflowEntryId(),
                RtsPlacementBatch.snapshotDetachedState(job, null));
        /*
         * workflowEntryId 只属于当前工作流存档代次。玩家每次新放置都必须获得新的
         * submission，否则旧世界中相同编号的终态回执会把合法操作误判为任务重放。
         */
        var submission = com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId.create();
        var taskId = com.rtsbuilding.rtsbuilding.server.task.identity.TaskId
                .fromSubmission(player.getUniqueID(), submission);
        long gameTime = gameTime(player);
        var snapshot = new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot(
                taskId, submission, player.getUniqueID(),
                dimensionId(player.dimension), TaskType.PLACEMENT,
                com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.QUEUED,
                job.workflowEntryId(), null, 1L, gameTime, gameTime,
                job.totalCount(), job.getIndex(), job.successfulCount(), job.failedCount(),
                com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskCodec.encode(payload));
        coordinator.submit(snapshot);
        return true;
    }

    /**
     * 为新的玩家工作流任务整理对应任务族容量。
     *
     * <p>工作流槽位允许新任务淘汰未保护的旧任务；各持久化任务族不能再用另一套硬上限
     * 推翻这条规则。只有当前维度、仍未终结且符合该任务族谓词的任务才占用容量。
     * 达到上限时优先终止最旧的未保护任务；缺失工作流投影的孤儿任务没有可保留的
     * “钉住”状态，因此同样必须允许新任务将其回收。</p>
     */
    private boolean makeRoomForDurableTaskFamily(
            net.minecraft.entity.player.EntityPlayerMP player,
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceCoordinator coordinator,
            TaskType taskType,
            int limit,
            java.util.function.Predicate<com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot>
                    occupiesFamilySlot) {
        String dimensionId = dimensionId(player.dimension);
        while (true) {
            var queued = coordinator.query().ownedBy(player.getUniqueID()).stream()
                    .filter(snapshot -> snapshot.type() == taskType)
                    .filter(snapshot -> !snapshot.state().terminal())
                    .filter(snapshot -> snapshot.dimensionId().equals(dimensionId))
                    .filter(occupiesFamilySlot)
                    .collect(Collectors.toList());
            if (queued.size() < limit) return true;

            var workflowEngine = com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance();
            var replaceable = queued.stream()
                    .filter(snapshot -> {
                        var entry = workflowEngine.findEntryByPlayer(player, snapshot.workflowEntryId());
                        return entry == null || !entry.protectedWorkflow();
                    })
                    .min(java.util.Comparator
                            .comparingLong(com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot::createdGameTime)
                            .thenComparing(com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot::id))
                    .orElse(null);
            if (replaceable == null) return false;

            cancelDurableSnapshot(player, coordinator, replaceable);
        }
    }

    /**
     * 新任务进入前清理当前维度里没有工作流投影的真实任务。
     *
     * <p>玩家无法在面板中钉住、暂停或删除一个不可见任务，因此这种孤儿任务不能享有保护权，
     * 更不能继续占用任何任务族容量。放置、破坏和挖掘都在同一入口执行这项对账。</p>
     */
    private void reconcileHiddenDurableWorkflows(
            net.minecraft.entity.player.EntityPlayerMP player,
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceCoordinator coordinator) {
        String dimensionId = dimensionId(player.dimension);
        var workflowEngine = com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance();
        var hiddenTasks = coordinator.query().ownedBy(player.getUniqueID()).stream()
                .filter(snapshot -> !snapshot.state().terminal())
                .filter(snapshot -> snapshot.type() == TaskType.PLACEMENT
                        || snapshot.type() == TaskType.DESTRUCTION
                        || snapshot.type() == TaskType.MINING)
                .filter(snapshot -> snapshot.dimensionId().equals(dimensionId))
                .filter(snapshot -> snapshot.workflowEntryId() < 0
                        || workflowEngine.findEntryByPlayer(player, snapshot.workflowEntryId()) == null)
                .collect(Collectors.toList());
        for (var hiddenTask : hiddenTasks) {
            cancelDurableSnapshot(player, coordinator, hiddenTask);
        }
    }

    /** 通过工作流统一入口终止任务；旧存档没有合法工作流 ID 时直接写入同一持久终态。 */
    private void cancelDurableSnapshot(
            net.minecraft.entity.player.EntityPlayerMP player,
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceCoordinator coordinator,
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot snapshot) {
        if (snapshot.workflowEntryId() >= 0
                && cancelWorkflowTask(player, player.dimension, snapshot.workflowEntryId())) {
            return;
        }
        var current = coordinator.query().get(snapshot.id()).orElse(null);
        if (current == null || current.state().terminal()) return;
        var cancelled = current.nextRevision(
                com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.CANCELLED,
                null, gameTime(player), current.cursorUnits(),
                current.succeededUnits(), current.failedUnits(), current.payload());
        coordinator.replace(cancelled);
        coordinator.requestTombstone(cancelled.id(), cancelled.updatedGameTime());
    }

    /** 无法解码的旧放置任务按占位处理，随后由统一淘汰路径回收，不能静默绕过清理。 */
    private static boolean occupiesQuickBuildSlot(
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot snapshot) {
        try {
            return com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskCodec
                    .decode(snapshot.payload()).state().definition().getBoolean("quickBuild");
        } catch (RuntimeException malformed) {
            return true;
        }
    }

    public boolean submitDestructionJob(net.minecraft.entity.player.EntityPlayerMP player,
            RtsDestructionBatch.DestructionJob job) {
        if (player == null || job == null) return false;
        var coordinator = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator();
        reconcileHiddenDurableWorkflows(player, coordinator);
        if (!makeRoomForDurableTaskFamily(
                player, coordinator, TaskType.DESTRUCTION,
                RtsDestructionBatch.DESTROY_MAX_QUEUED_JOBS, ignored -> true)) {
            player.sendStatusMessage(
                    new net.minecraft.util.text.TextComponentTranslation(
                            "message.rtsbuilding.range_destroy.queue_full",
                            RtsDestructionBatch.DESTROY_MAX_QUEUED_JOBS),
                    true);
            return false;
        }
        DestructionTaskPayload payload = new DestructionTaskPayload(
                player.getUniqueID(), player.dimension, job.workflowEntryId(),
                RtsDestructionBatch.snapshotDetachedState(job));
        // 与放置、挖掘一致：新操作不能复用历史 workflow 编号作为 durable submission。
        var submission = com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId.create();
        var taskId = com.rtsbuilding.rtsbuilding.server.task.identity.TaskId
                .fromSubmission(player.getUniqueID(), submission);
        long gameTime = gameTime(player);
        var snapshot = new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot(
                taskId, submission, player.getUniqueID(),
                dimensionId(player.dimension), TaskType.DESTRUCTION,
                com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.QUEUED,
                job.workflowEntryId(), null, 1L, gameTime, gameTime,
                job.totalCount(), job.getIndex(), job.successfulCount(), job.failedCount(),
                com.rtsbuilding.rtsbuilding.server.task.destruction.DestructionTaskCodec.encode(payload));
        coordinator.submit(snapshot);
        return true;
    }

    public boolean submitMiningTargets(
            net.minecraft.entity.player.EntityPlayerMP player,
            int workflowEntryId,
            java.util.Collection<net.minecraft.util.math.BlockPos> targets,
            net.minecraft.util.EnumFacing face,
            int toolSlot,
            boolean selectedToolRequested,
            boolean toolProtectionEnabled,
            boolean progressiveSingle) {
        if (player == null || targets == null || targets.isEmpty() || workflowEntryId < 0) return false;
        java.util.List<net.minecraft.util.math.BlockPos> immutableTargets = targets.stream()
                .filter(java.util.Objects::nonNull)
                .map(net.minecraft.util.math.BlockPos::new)
                .collect(Collectors.toList());
        if (immutableTargets.isEmpty()) return false;
        var state = new com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState(
                progressiveSingle
                        ? com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState.Mode.PROGRESSIVE_SINGLE
                        : com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState.Mode.BATCH,
                workflowEntryId, immutableTargets, immutableTargets.size(), 0, 0, 0,
                face, toolSlot, selectedToolRequested, toolProtectionEnabled,
                0.0F, -1, com.rtsbuilding.rtsbuilding.server.task.Java8Collections.listOf());
        return submitMiningState(player, state);
    }

    /** 旧 MiningJob 的唯一迁移入口。 */
    public boolean submitMiningJob(net.minecraft.entity.player.EntityPlayerMP player,
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session,
            RtsMiningStateMachine.MiningJob job) {
        if (player == null || session == null || job == null) return false;
        return submitMiningState(player, RtsMiningStateMachine.snapshotDetachedQueued(session, job));
    }

    private boolean submitMiningState(net.minecraft.entity.player.EntityPlayerMP player,
            com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState state) {
        var coordinator = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator();
        reconcileHiddenDurableWorkflows(player, coordinator);
        MiningTaskPayload payload = new MiningTaskPayload(
                player.getUniqueID(), player.dimension, state.workflowEntryId(), state);
        /*
         * workflowEntryId 只在当前工作流存档代次内单调递增，旧世界/测试存档恢复时可能再次出现相同值。
         * 这里处理的是玩家刚刚发起的新操作，不是旧 durable 记录迁移；必须生成新的 submission，
         * 否则历史终态回执会把合法的新连锁挖掘误判为重放，首块进度永远无法开始。
         */
        var submission = com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId.create();
        var taskId = com.rtsbuilding.rtsbuilding.server.task.identity.TaskId
                .fromSubmission(player.getUniqueID(), submission);
        long gameTime = gameTime(player);
        var snapshot = new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot(
                taskId, submission, player.getUniqueID(),
                dimensionId(player.dimension), TaskType.MINING,
                com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.QUEUED,
                state.workflowEntryId(), null, 1L, gameTime, gameTime,
                state.totalUnits(), state.cursorUnits(), state.succeededUnits(), state.failedUnits(),
                com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskCodec.encode(payload));
        coordinator.submit(snapshot);
        return true;
    }

    private void drainMiningBuffer(
            net.minecraft.entity.player.EntityPlayerMP player,
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session) {
        if (session.miningDropBuffer.isEmpty()) return;
        long now = System.nanoTime();
        long deadline = now > Long.MAX_VALUE - 250_000L ? Long.MAX_VALUE : now + 250_000L;
        RtsDropAbsorber.drainDropBuffer(player, session, 16, deadline);
    }

    private TaskStepResult executeBlueprint(TaskRecord task, TaskBudget budget) {
        BlueprintTaskPayload payload = (BlueprintTaskPayload) task.payload();
        if (payload.player().dimension != payload.dimension()) {
            return TaskStepResult.nextTick(0, 0, 0, 0);
        }
        var token = com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                .from(payload.player(), payload.workflowEntryId()).orElse(null);
        if (token == null) {
            com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlueprintPersistence
                    .clearFromEntry(payload.player(), payload.workflowEntryId());
            return TaskStepResult.fail("rtsbuilding.task.error.workflow_missing");
        }
        if (!payload.ready()) {
            int processed = payload.prepare(budget);
            if (payload.ready() && payload.shouldCheckpoint(true)
                    && !durableBlueprintBridge.isDurableContext(payload.context())) {
                com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlueprintPersistence.saveToEntry(
                        payload.player(), payload.workflowEntryId(), payload.context());
            }
            return TaskStepResult.nextTick(processed, 0, 0, 0);
        }
        return BlueprintTickPipe.execute(payload, budget);
    }

    private void syncFunnelTask(net.minecraft.entity.player.EntityPlayerMP player,
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session) {
        TaskRecord existing = funnelRecords.get(player.getUniqueID());
        boolean active = session.funnel.funnelEnabled
                && session.mode == com.rtsbuilding.rtsbuilding.common.build.BuilderMode.FUNNEL;
        if (!active) {
            if (existing != null && existing.status().terminal()) funnelRecords.remove(player.getUniqueID());
            return;
        }
        if (existing != null && !existing.status().terminal()) return;
        long now = System.nanoTime();
        TaskRecord record = new TaskRecord(UUID.randomUUID(), player.getUniqueID(), TaskType.FUNNEL,
                new FunnelTaskPayload(player, session), 0, now);
        funnelRecords.put(player.getUniqueID(), record);
        scheduler.submit(record);
    }

    private TaskStepResult executeFunnel(TaskRecord task, TaskBudget budget) {
        FunnelTaskPayload payload = (FunnelTaskPayload) task.payload();
        var result = ServiceRegistry.getInstance().funnel().tickBudgeted(
                payload.player(), payload.session(), budget.maxUnits(),
                System.nanoTime() + budget.remainingNanos());
        if (!result.active()) return TaskStepResult.complete(result.processedUnits(), 0, 0, 0);
        return TaskStepResult.nextTick(result.processedUnits(), 0, 0, 0);
    }

    private void syncPlacedRecoveryTask(net.minecraft.entity.player.EntityPlayerMP player,
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session) {
        TaskRecord existing = recoveryRecords.get(player.getUniqueID());
        if (session.placement.recoveryJobs.isEmpty()) {
            if (existing != null && existing.status().terminal()) recoveryRecords.remove(player.getUniqueID());
            return;
        }
        if (existing != null && !existing.status().terminal()) return;
        long now = System.nanoTime();
        TaskRecord record = new TaskRecord(
                UUID.randomUUID(), player.getUniqueID(), TaskType.PLACED_RECOVERY,
                new PlacedRecoveryTaskPayload(player, session), 0, now);
        recoveryRecords.put(player.getUniqueID(), record);
        scheduler.submit(record);
    }

    private TaskStepResult executePlacedRecovery(TaskRecord task, TaskBudget budget) {
        PlacedRecoveryTaskPayload payload = (PlacedRecoveryTaskPayload) task.payload();
        var result = RtsPlacedRecoveryService.tickBudgeted(
                payload.player(), payload.session(), budget.maxUnits(),
                System.nanoTime() + budget.remainingNanos());
        if (result.complete()) return TaskStepResult.complete(result.processedUnits(), 0, 0, 0);
        return TaskStepResult.nextTick(result.processedUnits(), 0, 0, 0);
    }

    private void applyInitialPause(net.minecraft.entity.player.EntityPlayerMP player, int workflowEntryId,
            TaskRecord record, long now) {
        applyInitialPause(player, workflowEntryId, record, now, player.dimension);
    }

    private void applyInitialPause(net.minecraft.entity.player.EntityPlayerMP player, int workflowEntryId,
            TaskRecord record, long now,
            int dimension) {
        if (workflowEntryId < 0) return;
        WorkflowTaskKey key = new WorkflowTaskKey(
                player.getUniqueID(), dimension, workflowEntryId);
        Boolean override = workflowPauseOverrides.get(key);
        var entry = com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                .findEntryByPlayer(player.getUniqueID(), dimension, workflowEntryId);
        boolean paused = override != null ? override : entry != null && entry.paused();
        if (paused) record.pause(now);
    }

    private TaskRecord findWorkflowTask(WorkflowTaskKey key) {
        TaskRecord blueprint = blueprintRecords.get(key);
        if (blueprint != null) return blueprint;
        return null;
    }

    /** TaskRecord 生命周期变更后，才把展示状态单向投影到工作流。 */
    private void projectWorkflowLifecycles() {
        java.util.List<TaskRecord> records = new java.util.ArrayList<>();
        records.addAll(blueprintRecords.values());
        for (TaskRecord record : records) {
            int entryId = workflowEntryId(record);
            if (entryId < 0) continue;
            if (projectedTaskStatuses.get(record.id()) == record.status()) continue;
            if (record.status().terminal()) {
                releaseTerminalWorkflow(record);
                continue;
            }
            var token = workflowToken(record, entryId);
            if (token == null) {
                if (record.payload() instanceof BlueprintTaskPayload) {
                    BlueprintTaskPayload payload = (BlueprintTaskPayload) record.payload();
                    cancelWorkflowTask(payload.player(), payload.dimension(), entryId);
                    RtsbuildingMod.LOGGER.warn(
                            "[TaskEngine] 已终止缺失可见工作流投影的蓝图任务 {}（工作流 #{}）",
                            record.id(), entryId);
                }
                continue;
            }
            var status = token.getProgress();
            switch (record.status()) {
                case PAUSED:
                    if (!status.paused()) token.pause();
                    break;
                case WAITING_RESOURCE:
                    if (!status.suspended()) token.suspend();
                    break;
                case QUEUED:
                case RUNNING:
                    if (status.paused()) token.unpause();
                    if (status.suspended()) token.resume();
                    break;
                default:
                    // 终态已在 switch 前统一释放；这里仅保留枚举穷尽保护。
                    break;
            }
            projectedTaskStatuses.put(record.id(), record.status());
        }
        java.util.Set<UUID> activeIds = records.stream().map(TaskRecord::id)
                .collect(java.util.stream.Collectors.toSet());
        projectedTaskStatuses.keySet().removeIf(id -> !activeIds.contains(id));
    }

    /** TaskStore 生命周期到 Workflow 的单向投影；Workflow 永远不能反向成为执行权威。 */
    private void projectDurableWorkflowLifecycles(MinecraftServer server,
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceCoordinator coordinator) {
        java.util.Set<com.rtsbuilding.rtsbuilding.server.task.identity.TaskId> activeIds =
                new java.util.HashSet<>();
        for (var player : server.getPlayerList().getPlayers()) {
            for (var snapshot : coordinator.query().ownedBy(player.getUniqueID())) {
                if (snapshot.type() != TaskType.PLACEMENT && snapshot.type() != TaskType.DESTRUCTION
                        && snapshot.type() != TaskType.MINING) continue;
                if (!snapshot.dimensionId().equals(
                        dimensionId(player.dimension))) continue;
                activeIds.add(snapshot.id());
                if (snapshot.workflowEntryId() < 0) {
                    if (snapshot.state().terminal()) {
                        coordinator.requestTombstone(snapshot.id(), snapshot.updatedGameTime());
                    } else {
                        cancelDurableSnapshot(player, coordinator, snapshot);
                        RtsbuildingMod.LOGGER.warn(
                                "[TaskEngine] 已终止没有合法工作流 ID 的隐藏任务 {}", snapshot.id());
                    }
                    continue;
                }

                var workflowEngine =
                        com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance();
                var token = workflowEngine
                        .from(player, snapshot.workflowEntryId()).orElse(null);
                if (snapshot.state().terminal()) {
                    if (java.util.Objects.equals(
                            projectedDurableStates.get(snapshot.id()), snapshot.revision())) {
                        coordinator.requestTombstone(snapshot.id(), snapshot.updatedGameTime());
                        continue;
                    }
                    // 终态旧任务只收口已有投影，绝不能复活后淘汰更新的可见工作流。
                    if (token != null) {
                        projectDurableProgress(token, snapshot);
                        if (snapshot.state()
                                == com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.COMPLETED) {
                            token.complete();
                        } else {
                            token.cancel();
                        }
                    }
                    coordinator.requestTombstone(snapshot.id(), snapshot.updatedGameTime());
                    projectedDurableStates.put(snapshot.id(), snapshot.revision());
                    continue;
                }

                boolean restored = false;
                if (token == null) {
                    DurableWorkflowProjection projection = inspectDurableProjection(player, snapshot);
                    if (projection.poisoned()) {
                        if (cancelWorkflowTask(player, snapshot.workflowEntryId())) {
                            RtsbuildingMod.LOGGER.warn(
                                    "[TaskEngine] 已清理无法恢复的持久任务 {}（工作流 #{}）",
                                    snapshot.id(), snapshot.workflowEntryId());
                        }
                        continue;
                    }
                    token = workflowEngine.restoreDurableProjection(
                            player,
                            snapshot.workflowEntryId(),
                            projection.type(),
                            snapshot.totalUnits(),
                            snapshot.succeededUnits(),
                            snapshot.failedUnits()).orElse(null);
                    restored = token != null;
                }
                if (token == null) {
                    // 面板已被全部保护条目占满时，无法显示的新旧任务本身并未被钉住。
                    // 它不能作为隐藏工作流继续运行或占住后续任务容量。
                    if (cancelWorkflowTask(player, snapshot.workflowEntryId())) {
                        RtsbuildingMod.LOGGER.warn(
                                "[TaskEngine] 已终止无法恢复可见投影的隐藏任务 {}（工作流 #{}）",
                                snapshot.id(), snapshot.workflowEntryId());
                    }
                    continue;
                }
                if (!restored && java.util.Objects.equals(
                        projectedDurableStates.get(snapshot.id()), snapshot.revision())) {
                    continue;
                }
                projectDurableProgress(token, snapshot);
                token.keepAlive();
                var progress = token.getProgress();
                switch (snapshot.state()) {
                    case PAUSED:
                        if (!progress.paused()) token.pause();
                        break;
                    case WAITING_RESOURCE:
                    case WAITING_CHUNK:
                    case WAITING_PERSISTENCE:
                        if (!progress.suspended()) token.suspend();
                        break;
                    case QUEUED:
                    case RUNNING:
                        if (progress.paused()) token.unpause();
                        if (progress.suspended()) token.resume();
                        break;
                    case COMPLETED:
                        token.complete();
                        break;
                    case FAILED:
                    case CANCELLED:
                        token.cancel();
                        break;
                    default:
                        throw new IllegalStateException("未知 durable task 状态: " + snapshot.state());
                }
                projectedDurableStates.put(snapshot.id(), snapshot.revision());
            }
        }
        projectedDurableStates.keySet().removeIf(id -> !activeIds.contains(id));
    }

    /** 把持久任务的最终计数投影到 UI；终态进入时也不能漏掉最后一个执行单位。 */
    private void projectDurableProgress(
            com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowToken token,
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot snapshot) {
        var progress = token.getProgress();
        int completedDelta = Math.max(0, snapshot.succeededUnits() - progress.completedBlocks());
        int failedDelta = Math.max(0, snapshot.failedUnits() - progress.failedBlocks());
        if (completedDelta > 0) token.updateProgress(completedDelta, null);
        if (failedDelta > 0) token.recordFailures(failedDelta);
    }

    /**
     * 判断一个缺失 UI 投影的持久任务应如何重新显示。
     *
     * <p>旧版可能写入了没有建材 ID 的快速建造任务。这类任务既无法执行，也无法被
     * 任意具体物品唤醒，必须标记为 poison 并经统一取消入口回收。</p>
     */
    private DurableWorkflowProjection inspectDurableProjection(
            net.minecraft.entity.player.EntityPlayerMP player,
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot snapshot) {
        try {
            if (snapshot.type() == TaskType.PLACEMENT) {
                com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskState state =
                        com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskCodec
                                .decode(snapshot.payload()).state();
                RtsPlacementBatch.PlaceBatchJob job = RtsPlacementBatch.restoreDetachedJob(state, null);
                if (job.quickBuild() && job.itemId().trim().isEmpty()) {
                    return DurableWorkflowProjection.poison();
                }
                com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType type = job.quickBuild()
                        ? com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType.QUICK_BUILD
                        : snapshot.totalUnits() <= 1
                                ? com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType.PLACE_SINGLE
                                : com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType.PLACE_BATCH;
                return DurableWorkflowProjection.restore(type);
            }
            if (snapshot.type() == TaskType.DESTRUCTION) {
                return DurableWorkflowProjection.restore(
                        com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType.AREA_DESTROY);
            }
            if (snapshot.type() == TaskType.MINING) {
                com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState state =
                        com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskCodec
                                .decode(snapshot.payload()).state();
                return DurableWorkflowProjection.restore(
                        state.mode() == com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState.Mode.PROGRESSIVE_SINGLE
                                ? com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType.MINE_SINGLE
                                : com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType.ULTIMINE);
            }
            return DurableWorkflowProjection.poison();
        } catch (RuntimeException malformed) {
            RtsbuildingMod.LOGGER.warn("[TaskEngine] 持久任务 {} 无法重建工作流投影",
                    snapshot.id(), malformed);
            return DurableWorkflowProjection.poison();
        }
    }

    private static final class DurableWorkflowProjection {
        private final com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType type;
        private final boolean poisoned;

        private DurableWorkflowProjection(
                com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType type,
                boolean poisoned) {
            this.type = type;
            this.poisoned = poisoned;
        }
        com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType type() { return type; }
        boolean poisoned() { return poisoned; }
        private static DurableWorkflowProjection restore(
                com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType type) {
            return new DurableWorkflowProjection(type, false);
        }

        private static DurableWorkflowProjection poison() {
            return new DurableWorkflowProjection(null, true);
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof DurableWorkflowProjection)) return false;
            DurableWorkflowProjection that = (DurableWorkflowProjection) other;
            return poisoned == that.poisoned && type == that.type;
        }
        @Override public int hashCode() { return java.util.Objects.hash(type, poisoned); }
        @Override public String toString() {
            return "DurableWorkflowProjection[type=" + type + ", poisoned=" + poisoned + "]";
        }
    }

    private void releaseTerminalWorkflow(TaskRecord record) {
        int entryId = workflowEntryId(record);
        if (entryId < 0) return;
        var token = workflowToken(record, entryId);
        if (token != null) {
            if (record.status() == TaskStatus.COMPLETED) token.complete();
            else token.cancel();
        }
        workflowPauseOverrides.remove(new WorkflowTaskKey(
                record.ownerId(), taskDimension(record), entryId));
        projectedTaskStatuses.put(record.id(), record.status());
    }

    private com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowToken workflowToken(
            TaskRecord record, int entryId) {
        var engine = com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance();
        var dimension = taskDimension(record);
        if (engine.findEntryByPlayer(record.ownerId(), dimension, entryId) == null) return null;
        return new com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowToken(
                record.ownerId(), entryId, dimension, engine);
    }

    private int taskDimension(TaskRecord record) {
        if (record.payload() instanceof BlueprintTaskPayload) return ((BlueprintTaskPayload) record.payload()).dimension();
        if (record.payload() instanceof PlacementTaskPayload) return ((PlacementTaskPayload) record.payload()).dimension();
        if (record.payload() instanceof DestructionTaskPayload) return ((DestructionTaskPayload) record.payload()).dimension();
        if (record.payload() instanceof MiningTaskPayload) return ((MiningTaskPayload) record.payload()).dimension();
        throw new IllegalArgumentException("Task has no workflow dimension: " + record.type());
    }

    private int workflowEntryId(TaskRecord record) {
        if (record.payload() instanceof PlacementTaskPayload) return ((PlacementTaskPayload) record.payload()).workflowEntryId();
        if (record.payload() instanceof DestructionTaskPayload) return ((DestructionTaskPayload) record.payload()).workflowEntryId();
        if (record.payload() instanceof MiningTaskPayload) return ((MiningTaskPayload) record.payload()).workflowEntryId();
        if (record.payload() instanceof BlueprintTaskPayload) return ((BlueprintTaskPayload) record.payload()).workflowEntryId();
        return -1;
    }

    /** 1.12.2 维度持久化统一使用规范十进制整数，避免模组维度被错误映射到原版名称。 */
    private static String dimensionId(int dimension) {
        return com.rtsbuilding.rtsbuilding.server.task.persistence.DimensionIdCodec
                .fromDimension(dimension);
    }

    private static long gameTime(net.minecraft.entity.player.EntityPlayerMP player) {
        return player.getServerWorld().getTotalWorldTime();
    }

    private boolean isTaskEngineWorkflow(
            com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType type) {
        switch (type) {
            case MINE_SINGLE:
            case ULTIMINE:
            case AREA_MINE:
            case AREA_DESTROY:
            case PLACE_SINGLE:
            case PLACE_BATCH:
            case QUICK_BUILD:
            case BLUEPRINT_BUILD:
                return true;
            case STOP_MINING:
                return false;
            default:
                throw new IllegalStateException("未知工作流类型: " + type);
        }
    }

    public static final class TaskDiagnostics {
        private final Map<TaskType, Integer> activeByType;
        private final Map<TaskType, Integer> waitingByType;
        public TaskDiagnostics(Map<TaskType, Integer> activeByType, Map<TaskType, Integer> waitingByType) {
            this.activeByType = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                    java.util.Objects.requireNonNull(activeByType, "activeByType")));
            this.waitingByType = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                    java.util.Objects.requireNonNull(waitingByType, "waitingByType")));
        }
        public Map<TaskType, Integer> activeByType() { return activeByType; }
        public Map<TaskType, Integer> waitingByType() { return waitingByType; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TaskDiagnostics)) return false;
            TaskDiagnostics that = (TaskDiagnostics) other;
            return java.util.Objects.equals(activeByType, that.activeByType)
                    && java.util.Objects.equals(waitingByType, that.waitingByType);
        }
        @Override public int hashCode() { return java.util.Objects.hash(activeByType, waitingByType); }
        @Override public String toString() {
            return "TaskDiagnostics[activeByType=" + activeByType + ", waitingByType=" + waitingByType + "]";
        }
    }

    /** UI 可读取的等待放置票据；TaskId + revision 用于拒绝过期扫描结果。 */
    public static final class PendingPlacementTaskView {
        private final com.rtsbuilding.rtsbuilding.server.task.identity.TaskId taskId;
        private final long revision;
        private final com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskState state;

        public PendingPlacementTaskView(
                com.rtsbuilding.rtsbuilding.server.task.identity.TaskId taskId,
                long revision,
                com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskState state) {
            this.taskId = java.util.Objects.requireNonNull(taskId, "taskId");
            this.revision = revision;
            this.state = java.util.Objects.requireNonNull(state, "state");
            if (revision < 1L) throw new IllegalArgumentException("revision 必须为正数");
        }
        public com.rtsbuilding.rtsbuilding.server.task.identity.TaskId taskId() { return taskId; }
        public long revision() { return revision; }
        public com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskState state() { return state; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PendingPlacementTaskView)) return false;
            PendingPlacementTaskView that = (PendingPlacementTaskView) other;
            return revision == that.revision && java.util.Objects.equals(taskId, that.taskId)
                    && java.util.Objects.equals(state, that.state);
        }
        @Override public int hashCode() { return java.util.Objects.hash(taskId, revision, state); }
        @Override public String toString() {
            return "PendingPlacementTaskView[taskId=" + taskId + ", revision=" + revision
                    + ", state=" + state + "]";
        }
    }

    private static final class WorkflowTaskKey {
        private final UUID playerId;
        private final int dimension;
        private final int workflowEntryId;
        private WorkflowTaskKey(UUID playerId, int dimension, int workflowEntryId) {
            this.playerId = playerId;
            this.dimension = dimension;
            this.workflowEntryId = workflowEntryId;
        }
        UUID playerId() { return playerId; }
        int dimension() { return dimension; }
        int workflowEntryId() { return workflowEntryId; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof WorkflowTaskKey)) return false;
            WorkflowTaskKey that = (WorkflowTaskKey) other;
            return dimension == that.dimension && workflowEntryId == that.workflowEntryId
                    && java.util.Objects.equals(playerId, that.playerId);
        }
        @Override public int hashCode() { return java.util.Objects.hash(playerId, dimension, workflowEntryId); }
        @Override public String toString() {
            return "WorkflowTaskKey[playerId=" + playerId + ", dimension=" + dimension
                    + ", workflowEntryId=" + workflowEntryId + "]";
        }
    }

}
