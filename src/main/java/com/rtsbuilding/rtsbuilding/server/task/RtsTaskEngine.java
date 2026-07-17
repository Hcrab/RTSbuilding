package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.Config;
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
            coordinator.query().runnableFor(player.getUUID(), player.serverLevel().dimension().location().toString())
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
        return new TaskDiagnostics(java.util.Map.copyOf(active), java.util.Map.copyOf(waiting));
    }

    public void detachPlayer(UUID playerId) {
        // 必须在移除 scheduler lane 前把 durable cursor 冻结，并释放 Context→ServerPlayer 强引用。
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
    public void preparePlayerDetach(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return;
        var session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session == null) return;
        RtsDropAbsorber.flushDropBufferToPlayer(player, session);
    }

    /** owner flush 后消费 root ACK，并把已接管的 Session shadow 转成持久 clear。 */
    public void reconcilePlayerDetach(net.minecraft.server.level.ServerPlayer player) {
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
                server.overworld().getGameTime());
        durableBlueprintBridge.checkpointAll(server.overworld().getGameTime());
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
    public boolean setWorkflowPaused(net.minecraft.server.level.ServerPlayer player, int workflowEntryId,
            boolean paused) {
        if (player == null || workflowEntryId < 0) return false;
        WorkflowTaskKey key = new WorkflowTaskKey(
                player.getUUID(), player.serverLevel().dimension(), workflowEntryId);
        var workflow = com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                .getProgress(player, workflowEntryId);
        if (!workflow.isActive() || !isTaskEngineWorkflow(workflow.type())) return false;
        workflowPauseOverrides.put(key, paused);
        var coordinator = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator();
        var durable = coordinator.query().findByWorkflow(
                player.getUUID(), player.serverLevel().dimension().location().toString(), workflowEntryId)
                .orElse(null);
        if (durable != null && (durable.type() == TaskType.PLACEMENT
                || durable.type() == TaskType.DESTRUCTION
                || durable.type() == TaskType.MINING) && !durable.state().terminal()) {
            var state = paused
                    ? com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.PAUSED
                    : com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.QUEUED;
            var next = durable.type() == TaskType.MINING
                    ? durableRuntime.transitionMiningSnapshot(
                            durable, state, player.serverLevel().getGameTime())
                    : durable.nextRevision(state, null,
                            player.serverLevel().getGameTime(), durable.cursorUnits(), durable.succeededUnits(),
                            durable.failedUnits(), durable.payload());
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
    public void pauseAllWorkflowTasks(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return;
        for (var status : com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                .getAllProgress(player)) {
            if (status.isActive() && !status.suspended()) {
                setWorkflowPaused(player, status.entryId(), true);
            }
        }
    }

    /** 删除工作流前先收拢对应领域任务，不等待下一个 tick 通过“token 缺失”兜底。 */
    public boolean cancelWorkflowTask(net.minecraft.server.level.ServerPlayer player, int workflowEntryId) {
        if (player == null || workflowEntryId < 0) return false;
        WorkflowTaskKey key = new WorkflowTaskKey(
                player.getUUID(), player.serverLevel().dimension(), workflowEntryId);
        TaskRecord record = findWorkflowTask(key);
        var coordinator = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator();
        var durable = coordinator.query().findByWorkflow(
                player.getUUID(), player.serverLevel().dimension().location().toString(), workflowEntryId)
                .orElse(null);
        boolean durableCancelled = false;
        if (durable != null && (durable.type() == TaskType.PLACEMENT
                || durable.type() == TaskType.DESTRUCTION
                || durable.type() == TaskType.MINING) && !durable.state().terminal()) {
            if (durable.type() == TaskType.PLACEMENT) {
                RtsPlacementBatch.recordDetachedHistory(player,
                        com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskCodec
                                .decode(durable.payload()).state());
            } else if (durable.type() == TaskType.DESTRUCTION) {
                RtsDestructionBatch.recordDetachedHistory(player,
                        com.rtsbuilding.rtsbuilding.server.task.destruction.DestructionTaskCodec
                                .decode(durable.payload()).state());
            } else {
                RtsMiningStateMachine.finalizeDetachedCancellation(player,
                        ServiceRegistry.getInstance().session().getIfPresent(player),
                        durableRuntime.currentMiningState(durable));
            }
            var cancelled = durable.type() == TaskType.MINING
                    ? durableRuntime.transitionMiningSnapshot(durable,
                            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.CANCELLED,
                            player.serverLevel().getGameTime())
                    : durable.nextRevision(
                            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.CANCELLED,
                            null, player.serverLevel().getGameTime(), durable.cursorUnits(), durable.succeededUnits(),
                            durable.failedUnits(), durable.payload());
            coordinator.replace(cancelled);
            durableCancelled = true;
        }
        var session = ServiceRegistry.getInstance().session().getIfPresent(player);
        boolean cleaned = session != null
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
    public void resumeWaitingPlacementItems(net.minecraft.server.level.ServerPlayer player,
            java.util.Collection<String> changedItemIds) {
        if (player == null || changedItemIds == null || changedItemIds.isEmpty()) return;
        var coordinator = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator();
        long gameTime = player.serverLevel().getGameTime();
        for (String itemId : new java.util.LinkedHashSet<>(changedItemIds)) {
            if (itemId == null || itemId.isBlank()) continue;
            var key = new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskWaitKey("item", itemId);
            for (var snapshot : coordinator.query().waitingFor(key)) {
                if (!snapshot.ownerId().equals(player.getUUID()) || snapshot.type() != TaskType.PLACEMENT
                        || !snapshot.dimensionId().equals(
                                player.serverLevel().dimension().location().toString())) continue;
                coordinator.replace(snapshot.nextRevision(
                        com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.QUEUED,
                        null, gameTime, snapshot.cursorUnits(), snapshot.succeededUnits(),
                        snapshot.failedUnits(), snapshot.payload()));
            }
        }
    }

    /** 玩家显式点击“重试”时恢复自己的全部 placement 等待任务；仍不遍历全服任务。 */
    public int resumeAllWaitingPlacements(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return 0;
        var coordinator = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator();
        int resumed = 0;
        long gameTime = player.serverLevel().getGameTime();
        String dimensionId = player.serverLevel().dimension().location().toString();
        for (var snapshot : coordinator.query().ownedBy(player.getUUID())) {
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
            net.minecraft.server.level.ServerPlayer player, int workflowEntryId) {
        if (player == null || workflowEntryId < 0) return null;
        var snapshot = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator().query().findByWorkflow(
                        player.getUUID(), player.serverLevel().dimension().location().toString(), workflowEntryId)
                .orElse(null);
        if (snapshot == null || snapshot.type() != TaskType.PLACEMENT
                || snapshot.state()
                != com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.WAITING_RESOURCE) {
            return null;
        }
        PlacementTaskPayload payload = com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskCodec
                .decode(snapshot.payload());
        if (!payload.ownerId().equals(snapshot.ownerId())
                || !payload.dimension().location().toString().equals(snapshot.dimensionId())
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
            net.minecraft.server.level.ServerPlayer player, int workflowEntryId, int strategy,
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
                || !snapshot.ownerId().equals(player.getUUID())
                || !snapshot.dimensionId().equals(player.serverLevel().dimension().location().toString())
                || snapshot.workflowEntryId() != workflowEntryId
                || snapshot.state()
                != com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.WAITING_RESOURCE) {
            return false;
        }
        PlacementTaskPayload payload = com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskCodec
                .decode(snapshot.payload());
        if (!payload.ownerId().equals(snapshot.ownerId())
                || !payload.dimension().location().toString().equals(snapshot.dimensionId())
                || payload.workflowEntryId() != workflowEntryId) return false;
        var nextState = RtsPlacementBatch.applyDetachedResumeStrategy(player, payload.state(), strategy);
        if (nextState == null) return false;
        var nextPayload = payload.withState(nextState);
        coordinator.replace(snapshot.nextRevision(
                com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.QUEUED,
                null, player.serverLevel().getGameTime(), nextState.cursorUnits(),
                nextState.succeededUnits(), nextState.failedUnits(),
                com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskCodec.encode(nextPayload)));
        return true;
    }

    public String firstPlacementItemId(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return "";
        return com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator().query().ownedBy(player.getUUID()).stream()
                .filter(snapshot -> snapshot.type() == TaskType.PLACEMENT && !snapshot.state().terminal())
                .map(snapshot -> com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskCodec
                        .decode(snapshot.payload()).state().definition().getString("itemId"))
                .filter(value -> value != null && !value.isBlank())
                .findFirst().orElse("");
    }

    /** 返回当前维度一个仍活动的 durable mining workflow；控制管道不再读取 Session 游标。 */
    public int activeMiningWorkflowEntry(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return -1;
        String dimensionId = player.serverLevel().dimension().location().toString();
        return com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator().query().ownedBy(player.getUUID()).stream()
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
    public boolean hasCommittedMiningBatch(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return false;
        String dimensionId = player.serverLevel().dimension().location().toString();
        var states = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator().query().ownedBy(player.getUUID()).stream()
                .filter(snapshot -> snapshot.type() == TaskType.MINING && !snapshot.state().terminal())
                .filter(snapshot -> snapshot.dimensionId().equals(dimensionId))
                .map(durableRuntime::currentMiningState)
                .toList();
        return !states.isEmpty() && states.stream()
                .allMatch(com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState::committedBatch);
    }

    /** 停止当前维度的全部 durable mining；Session 清理由旧迁移兼容层随后完成。 */
    public int cancelActiveMiningTasks(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return 0;
        String dimensionId = player.serverLevel().dimension().location().toString();
        var workflowIds = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator().query().ownedBy(player.getUUID()).stream()
                .filter(snapshot -> snapshot.type() == TaskType.MINING && !snapshot.state().terminal())
                .filter(snapshot -> snapshot.dimensionId().equals(dimensionId))
                .map(com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot::workflowEntryId)
                .filter(entryId -> entryId >= 0)
                .distinct().toList();
        int cancelled = 0;
        for (int workflowId : workflowIds) {
            if (cancelWorkflowTask(player, workflowId)) cancelled++;
        }
        return cancelled;
    }

    /** 领域入口提交后读取 TaskStore 的过滤后总量，避免再窥探 Session 旧 Job。 */
    public int workflowTaskTotalUnits(
            net.minecraft.server.level.ServerPlayer player, int workflowEntryId) {
        if (player == null || workflowEntryId < 0) return 0;
        return com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator().query().findByWorkflow(
                        player.getUUID(), player.serverLevel().dimension().location().toString(), workflowEntryId)
                .map(com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot::totalUnits)
                .orElse(0);
    }

    private void resumeAvailableToolWaits(
            net.minecraft.server.level.ServerPlayer player,
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session,
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceCoordinator coordinator) {
        if (player == null || session == null
                || (!player.isCreative()
                && !session.mining.miningSelectedToolRequested
                && (session.mining.miningToolLease == null
                || session.mining.miningToolLease.isEmpty()))
                || com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator
                        .isToolNearBreak(player, session)) return;
        resumeWaitKey(coordinator, player.getUUID(),
                new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskWaitKey(
                        "tool", "usable_mining_tool"), player.serverLevel().getGameTime());
        for (int slot = 0; slot < 9; slot++) {
            resumeWaitKey(coordinator, player.getUUID(),
                    new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskWaitKey(
                            "tool", "hotbar:" + slot), player.serverLevel().getGameTime());
        }
    }

    /** Chunk Load 事件只命中该 chunk 的 WaitIndex 桶。 */
    public void resumeLoadedChunk(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.level.ChunkPos chunkPos) {
        if (level == null || chunkPos == null) return;
        var runtime = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE;
        // GameTest/专用服务器会在 ServerStarting 之前提升出生区块；此时还没有恢复索引可唤醒。
        if (!runtime.isStarted()) return;
        var coordinator = runtime.coordinator();
        var key = new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskWaitKey(
                "chunk", level.dimension().location() + ":" + chunkPos.x + ":" + chunkPos.z);
        for (var snapshot : coordinator.query().waitingFor(key)) {
            coordinator.replace(snapshot.nextRevision(
                    com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.QUEUED,
                    null, level.getGameTime(), snapshot.cursorUnits(), snapshot.succeededUnits(),
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
        submitBlueprint(context, restoredRemaining, context.player().serverLevel().dimension());
    }

    /** root 已 ACK 后由 durable bridge 调用；TaskRecord 复用稳定 TaskId，不再生成 workflow 派生 UUID。 */
    TaskRecord activateDurableBlueprint(
            com.rtsbuilding.rtsbuilding.server.task.identity.TaskId taskId,
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot snapshot,
            BlueprintContext context,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> sourceDimension) {
        int entryId = context.getData(
                com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext.KEY_WORKFLOW_ENTRY_ID);
        WorkflowTaskKey key = new WorkflowTaskKey(context.player().getUUID(), sourceDimension, entryId);
        TaskRecord existing = blueprintRecords.get(key);
        if (existing != null && !existing.status().terminal()) return existing;
        long now = System.nanoTime();
        BlueprintTaskPayload payload = new BlueprintTaskPayload(context,
                context.isPreparing() ? null : context.getRemainingQueue(), sourceDimension);
        TaskRecord record = new TaskRecord(taskId.value(), context.player().getUUID(),
                TaskType.BLUEPRINT, payload, snapshot.totalUnits(), now);
        record.restoreDurableSnapshot(snapshot.cursorUnits(), snapshot.succeededUnits(),
                snapshot.failedUnits(), DurableBlueprintTaskBridge.runtimeState(snapshot.state()), now);
        blueprintRecords.put(key, record);
        scheduler.submit(record);
        return record;
    }

    public void submitBlueprint(BlueprintContext context, java.util.LinkedList<Integer> restoredRemaining,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> sourceDimension) {
        if (context == null || context.player() == null) return;
        Integer entryId = context.getData(
                com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext.KEY_WORKFLOW_ENTRY_ID);
        if (entryId == null || entryId < 0) return;
        WorkflowTaskKey key = new WorkflowTaskKey(
                context.player().getUUID(), sourceDimension, entryId);
        TaskRecord existing = blueprintRecords.get(key);
        if (existing != null && !existing.status().terminal()) return;

        long now = System.nanoTime();
        BlueprintTaskPayload payload = new BlueprintTaskPayload(context, restoredRemaining, sourceDimension);
        TaskRecord record = new TaskRecord(
                UUID.nameUUIDFromBytes((context.player().getUUID() + ":blueprint:"
                        + sourceDimension.location() + ":" + entryId)
                        .getBytes(StandardCharsets.UTF_8)),
                context.player().getUUID(), TaskType.BLUEPRINT, payload,
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
            net.minecraft.server.level.ServerPlayer player, int workflowEntryId) {
        if (player == null) return null;
        TaskRecord record = blueprintRecords.get(new WorkflowTaskKey(
                player.getUUID(), player.serverLevel().dimension(), workflowEntryId));
        if (record == null || record.status().terminal()
                || !(record.payload() instanceof BlueprintTaskPayload payload)) return null;
        return payload.context();
    }

    public boolean resumeBlueprint(
            net.minecraft.server.level.ServerPlayer player, int workflowEntryId) {
        TaskRecord record = player == null ? null
                : blueprintRecords.get(new WorkflowTaskKey(
                        player.getUUID(), player.serverLevel().dimension(), workflowEntryId));
        if (record == null || record.status().terminal()) return false;
        if (record.payload() instanceof BlueprintTaskPayload payload) {
            payload.resetPlacementCycle();
        }
        record.resume(System.nanoTime());
        return true;
    }

    public boolean submitPlacementJob(net.minecraft.server.level.ServerPlayer player,
            RtsPlacementBatch.PlaceBatchJob job) {
        if (player == null || job == null) return false;
        var coordinator = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator();
        if (job.quickBuild()) {
            long queued = coordinator.query().ownedBy(player.getUUID()).stream()
                    .filter(snapshot -> snapshot.type() == TaskType.PLACEMENT && !snapshot.state().terminal())
                    .count();
            if (queued >= Config.buildBatchMaxQueuedJobs()) return false;
        }
        PlacementTaskPayload payload = new PlacementTaskPayload(
                player.getUUID(), player.serverLevel().dimension(), job.workflowEntryId(),
                RtsPlacementBatch.snapshotDetachedState(job, player.registryAccess()));
        var submission = job.workflowEntryId() >= 0
                ? com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId.fromLegacy(
                        player.getUUID(), "placement",
                        player.serverLevel().dimension().location() + ":" + job.workflowEntryId())
                : com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId.create();
        var taskId = com.rtsbuilding.rtsbuilding.server.task.identity.TaskId
                .fromSubmission(player.getUUID(), submission);
        long gameTime = player.serverLevel().getGameTime();
        var snapshot = new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot(
                taskId, submission, player.getUUID(),
                player.serverLevel().dimension().location().toString(), TaskType.PLACEMENT,
                com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.QUEUED,
                job.workflowEntryId(), null, 1L, gameTime, gameTime,
                job.totalCount(), job.getIndex(), job.successfulCount(), job.failedCount(),
                com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskCodec.encode(payload));
        coordinator.submit(snapshot);
        return true;
    }

    public boolean submitDestructionJob(net.minecraft.server.level.ServerPlayer player,
            RtsDestructionBatch.DestructionJob job) {
        if (player == null || job == null) return false;
        var coordinator = com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator();
        long queued = coordinator.query().ownedBy(player.getUUID()).stream()
                .filter(snapshot -> snapshot.type() == TaskType.DESTRUCTION && !snapshot.state().terminal())
                .count();
        if (queued >= RtsDestructionBatch.DESTROY_MAX_QUEUED_JOBS) return false;
        DestructionTaskPayload payload = new DestructionTaskPayload(
                player.getUUID(), player.serverLevel().dimension(), job.workflowEntryId(),
                RtsDestructionBatch.snapshotDetachedState(job));
        var submission = com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId.fromLegacy(
                player.getUUID(), "destruction",
                player.serverLevel().dimension().location() + ":" + job.workflowEntryId());
        var taskId = com.rtsbuilding.rtsbuilding.server.task.identity.TaskId
                .fromSubmission(player.getUUID(), submission);
        long gameTime = player.serverLevel().getGameTime();
        var snapshot = new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot(
                taskId, submission, player.getUUID(),
                player.serverLevel().dimension().location().toString(), TaskType.DESTRUCTION,
                com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.QUEUED,
                job.workflowEntryId(), null, 1L, gameTime, gameTime,
                job.totalCount(), job.getIndex(), job.successfulCount(), job.failedCount(),
                com.rtsbuilding.rtsbuilding.server.task.destruction.DestructionTaskCodec.encode(payload));
        coordinator.submit(snapshot);
        return true;
    }

    public boolean submitMiningTargets(
            net.minecraft.server.level.ServerPlayer player,
            int workflowEntryId,
            java.util.Collection<net.minecraft.core.BlockPos> targets,
            net.minecraft.core.Direction face,
            int toolSlot,
            boolean selectedToolRequested,
            boolean toolProtectionEnabled,
            boolean progressiveSingle) {
        if (player == null || targets == null || targets.isEmpty() || workflowEntryId < 0) return false;
        java.util.List<net.minecraft.core.BlockPos> immutableTargets = targets.stream()
                .filter(java.util.Objects::nonNull)
                .map(net.minecraft.core.BlockPos::immutable)
                .toList();
        if (immutableTargets.isEmpty()) return false;
        var state = new com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState(
                progressiveSingle
                        ? com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState.Mode.PROGRESSIVE_SINGLE
                        : com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState.Mode.BATCH,
                workflowEntryId, immutableTargets, immutableTargets.size(), 0, 0, 0,
                face, toolSlot, selectedToolRequested, toolProtectionEnabled,
                0.0F, -1, java.util.List.of());
        return submitMiningState(player, state);
    }

    /** 旧 MiningJob 的唯一迁移入口。 */
    public boolean submitMiningJob(net.minecraft.server.level.ServerPlayer player,
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session,
            RtsMiningStateMachine.MiningJob job) {
        if (player == null || session == null || job == null) return false;
        return submitMiningState(player, RtsMiningStateMachine.snapshotDetachedQueued(session, job));
    }

    private boolean submitMiningState(net.minecraft.server.level.ServerPlayer player,
            com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState state) {
        MiningTaskPayload payload = new MiningTaskPayload(
                player.getUUID(), player.serverLevel().dimension(), state.workflowEntryId(), state);
        var submission = com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId.fromLegacy(
                player.getUUID(), "mining",
                player.serverLevel().dimension().location() + ":" + state.workflowEntryId());
        var taskId = com.rtsbuilding.rtsbuilding.server.task.identity.TaskId
                .fromSubmission(player.getUUID(), submission);
        long gameTime = player.serverLevel().getGameTime();
        var snapshot = new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot(
                taskId, submission, player.getUUID(),
                player.serverLevel().dimension().location().toString(), TaskType.MINING,
                com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.QUEUED,
                state.workflowEntryId(), null, 1L, gameTime, gameTime,
                state.totalUnits(), state.cursorUnits(), state.succeededUnits(), state.failedUnits(),
                com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskCodec.encode(payload));
        com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime.INSTANCE
                .coordinator().submit(snapshot);
        return true;
    }

    private void drainMiningBuffer(
            net.minecraft.server.level.ServerPlayer player,
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session) {
        if (session.miningDropBuffer.isEmpty()) return;
        long now = System.nanoTime();
        long deadline = now > Long.MAX_VALUE - 250_000L ? Long.MAX_VALUE : now + 250_000L;
        RtsDropAbsorber.drainDropBuffer(player, session, 16, deadline);
    }

    private TaskStepResult executeBlueprint(TaskRecord task, TaskBudget budget) {
        BlueprintTaskPayload payload = (BlueprintTaskPayload) task.payload();
        if (!payload.player().serverLevel().dimension().equals(payload.dimension())) {
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

    private void syncFunnelTask(net.minecraft.server.level.ServerPlayer player,
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session) {
        TaskRecord existing = funnelRecords.get(player.getUUID());
        boolean active = session.funnel.funnelEnabled
                && session.mode == com.rtsbuilding.rtsbuilding.common.build.BuilderMode.FUNNEL;
        if (!active) {
            if (existing != null && existing.status().terminal()) funnelRecords.remove(player.getUUID());
            return;
        }
        if (existing != null && !existing.status().terminal()) return;
        long now = System.nanoTime();
        TaskRecord record = new TaskRecord(UUID.randomUUID(), player.getUUID(), TaskType.FUNNEL,
                new FunnelTaskPayload(player, session), 0, now);
        funnelRecords.put(player.getUUID(), record);
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

    private void syncPlacedRecoveryTask(net.minecraft.server.level.ServerPlayer player,
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session) {
        TaskRecord existing = recoveryRecords.get(player.getUUID());
        if (session.placement.recoveryJobs.isEmpty()) {
            if (existing != null && existing.status().terminal()) recoveryRecords.remove(player.getUUID());
            return;
        }
        if (existing != null && !existing.status().terminal()) return;
        long now = System.nanoTime();
        TaskRecord record = new TaskRecord(
                UUID.randomUUID(), player.getUUID(), TaskType.PLACED_RECOVERY,
                new PlacedRecoveryTaskPayload(player, session), 0, now);
        recoveryRecords.put(player.getUUID(), record);
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

    private void applyInitialPause(net.minecraft.server.level.ServerPlayer player, int workflowEntryId,
            TaskRecord record, long now) {
        applyInitialPause(player, workflowEntryId, record, now, player.serverLevel().dimension());
    }

    private void applyInitialPause(net.minecraft.server.level.ServerPlayer player, int workflowEntryId,
            TaskRecord record, long now,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        if (workflowEntryId < 0) return;
        WorkflowTaskKey key = new WorkflowTaskKey(
                player.getUUID(), dimension, workflowEntryId);
        Boolean override = workflowPauseOverrides.get(key);
        var entry = com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                .findEntryByPlayer(player.getUUID(), dimension, workflowEntryId);
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
            if (token == null) continue;
            var status = token.getProgress();
            switch (record.status()) {
                case PAUSED -> {
                    if (!status.paused()) token.pause();
                }
                case WAITING_RESOURCE -> {
                    if (!status.suspended()) token.suspend();
                }
                case QUEUED, RUNNING -> {
                    if (status.paused()) token.unpause();
                    if (status.suspended()) token.resume();
                }
                default -> {
                    // 终态已在 switch 前统一释放；这里仅保留枚举穷尽保护。
                }
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
            for (var snapshot : coordinator.query().ownedBy(player.getUUID())) {
                if (snapshot.type() != TaskType.PLACEMENT && snapshot.type() != TaskType.DESTRUCTION
                        && snapshot.type() != TaskType.MINING) continue;
                activeIds.add(snapshot.id());
                if (snapshot.workflowEntryId() < 0
                        || java.util.Objects.equals(projectedDurableStates.get(snapshot.id()), snapshot.revision())) {
                    continue;
                }
                var token = com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                        .from(player, snapshot.workflowEntryId()).orElse(null);
                if (token == null) continue;
                var progress = token.getProgress();
                int completedDelta = Math.max(0, snapshot.succeededUnits() - progress.completedBlocks());
                int failedDelta = Math.max(0, snapshot.failedUnits() - progress.failedBlocks());
                if (completedDelta > 0) token.updateProgress(completedDelta, null);
                if (failedDelta > 0) token.recordFailures(failedDelta);
                progress = token.getProgress();
                switch (snapshot.state()) {
                    case PAUSED -> {
                        if (!progress.paused()) token.pause();
                    }
                    case WAITING_RESOURCE, WAITING_CHUNK, WAITING_PERSISTENCE -> {
                        if (!progress.suspended()) token.suspend();
                    }
                    case QUEUED, RUNNING -> {
                        if (progress.paused()) token.unpause();
                        if (progress.suspended()) token.resume();
                    }
                    case COMPLETED -> token.complete();
                    case FAILED, CANCELLED -> token.cancel();
                }
                projectedDurableStates.put(snapshot.id(), snapshot.revision());
            }
        }
        projectedDurableStates.keySet().removeIf(id -> !activeIds.contains(id));
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

    private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> taskDimension(TaskRecord record) {
        if (record.payload() instanceof BlueprintTaskPayload payload) return payload.dimension();
        if (record.payload() instanceof PlacementTaskPayload payload) return payload.dimension();
        if (record.payload() instanceof DestructionTaskPayload payload) return payload.dimension();
        if (record.payload() instanceof MiningTaskPayload payload) return payload.dimension();
        throw new IllegalArgumentException("Task has no workflow dimension: " + record.type());
    }

    private int workflowEntryId(TaskRecord record) {
        if (record.payload() instanceof PlacementTaskPayload payload) return payload.workflowEntryId();
        if (record.payload() instanceof DestructionTaskPayload payload) return payload.workflowEntryId();
        if (record.payload() instanceof MiningTaskPayload payload) return payload.workflowEntryId();
        if (record.payload() instanceof BlueprintTaskPayload payload) return payload.workflowEntryId();
        return -1;
    }

    private boolean isTaskEngineWorkflow(
            com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType type) {
        return switch (type) {
            case MINE_SINGLE, ULTIMINE, AREA_MINE, AREA_DESTROY,
                    PLACE_SINGLE, PLACE_BATCH, QUICK_BUILD, BLUEPRINT_BUILD -> true;
            case STOP_MINING -> false;
        };
    }

    public record TaskDiagnostics(Map<TaskType, Integer> activeByType,
            Map<TaskType, Integer> waitingByType) {
    }

    /** UI 可读取的等待放置票据；TaskId + revision 用于拒绝过期扫描结果。 */
    public record PendingPlacementTaskView(
            com.rtsbuilding.rtsbuilding.server.task.identity.TaskId taskId,
            long revision,
            com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskState state) {
        public PendingPlacementTaskView {
            java.util.Objects.requireNonNull(taskId, "taskId");
            java.util.Objects.requireNonNull(state, "state");
            if (revision < 1L) throw new IllegalArgumentException("revision 必须为正数");
        }
    }

    private record WorkflowTaskKey(
            UUID playerId, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            int workflowEntryId) {
    }

}
