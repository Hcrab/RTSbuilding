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
import java.util.IdentityHashMap;
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
    private final Map<RtsPlacementBatch.PlaceBatchJob, TaskRecord> placementRecords = new IdentityHashMap<>();
    private final Map<RtsDestructionBatch.DestructionJob, TaskRecord> destructionRecords = new IdentityHashMap<>();
    private final Map<MiningTaskKey, TaskRecord> miningRecords = new java.util.HashMap<>();
    private final Map<UUID, TaskRecord> bufferRecords = new java.util.HashMap<>();
    private final Map<WorkflowTaskKey, TaskRecord> blueprintRecords = new java.util.HashMap<>();
    private final Map<UUID, TaskRecord> funnelRecords = new java.util.HashMap<>();
    private final Map<UUID, TaskRecord> recoveryRecords = new java.util.HashMap<>();
    private final Map<WorkflowTaskKey, Boolean> workflowPauseOverrides = new java.util.HashMap<>();
    private final Map<UUID, TaskStatus> projectedTaskStatuses = new java.util.HashMap<>();

    private RtsTaskEngine() {
        scheduler.registerExecutor(TaskType.PLACEMENT, this::executePlacement);
        scheduler.registerExecutor(TaskType.DESTRUCTION, this::executeDestruction);
        scheduler.registerExecutor(TaskType.MINING, this::executeMining);
        scheduler.registerExecutor(TaskType.BUFFER_DRAIN, this::executeBufferDrain);
        scheduler.registerExecutor(TaskType.BLUEPRINT, this::executeBlueprint);
        scheduler.registerExecutor(TaskType.FUNNEL, this::executeFunnel);
        scheduler.registerExecutor(TaskType.PLACED_RECOVERY, this::executePlacedRecovery);
    }

    public TaskScheduler.TickStats tick(MinecraftServer server) {
        var sessionService = ServiceRegistry.getInstance().session();
        for (var player : server.getPlayerList().getPlayers()) {
            var session = sessionService.getIfPresent(player);
            if (session == null) continue;
            syncPlacementTasks(player, session);
            syncDestructionTasks(player, session);
            syncMiningTasks(player, session);
            syncBufferTask(player, session);
            syncFunnelTask(player, session);
            syncPlacedRecoveryTask(player, session);
        }
        TaskScheduler.TickStats stats = scheduler.tick(
                Config.taskEngineMaxNanosPerTick(),
                Config.taskEngineMaxUnitsPerTick(),
                Config.taskEngineMaxUnitsPerSlice());
        projectWorkflowLifecycles();
        blueprintRecords.entrySet().removeIf(entry -> entry.getValue().status().terminal());
        return stats;
    }

    /** 开发者采样读取的轻量任务快照；不暴露可变 TaskRecord 或领域 payload。 */
    public TaskDiagnostics diagnostics(UUID ownerId) {
        java.util.EnumMap<TaskType, Integer> active = new java.util.EnumMap<>(TaskType.class);
        java.util.EnumMap<TaskType, Integer> waiting = new java.util.EnumMap<>(TaskType.class);
        java.util.List<TaskRecord> records = new java.util.ArrayList<>();
        records.addAll(placementRecords.values());
        records.addAll(destructionRecords.values());
        records.addAll(miningRecords.values());
        records.addAll(blueprintRecords.values());
        TaskRecord buffer = bufferRecords.get(ownerId);
        if (buffer != null) records.add(buffer);
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
        return new TaskDiagnostics(java.util.Map.copyOf(active), java.util.Map.copyOf(waiting));
    }

    public void onPlayerLogout(UUID playerId) {
        scheduler.cancelOwner(playerId, System.nanoTime());
        java.util.Set<UUID> removedTaskIds = new java.util.HashSet<>();
        placementRecords.values().stream().filter(record -> record.ownerId().equals(playerId))
                .map(TaskRecord::id).forEach(removedTaskIds::add);
        destructionRecords.values().stream().filter(record -> record.ownerId().equals(playerId))
                .map(TaskRecord::id).forEach(removedTaskIds::add);
        miningRecords.values().stream().filter(record -> record.ownerId().equals(playerId))
                .map(TaskRecord::id).forEach(removedTaskIds::add);
        blueprintRecords.values().stream().filter(record -> record.ownerId().equals(playerId))
                .map(TaskRecord::id).forEach(removedTaskIds::add);
        placementRecords.entrySet().removeIf(entry -> entry.getValue().ownerId().equals(playerId));
        destructionRecords.entrySet().removeIf(entry -> entry.getValue().ownerId().equals(playerId));
        miningRecords.entrySet().removeIf(entry -> entry.getKey().playerId().equals(playerId));
        blueprintRecords.keySet().removeIf(key -> key.playerId().equals(playerId));
        bufferRecords.remove(playerId);
        funnelRecords.remove(playerId);
        recoveryRecords.remove(playerId);
        workflowPauseOverrides.keySet().removeIf(key -> key.playerId().equals(playerId));
        projectedTaskStatuses.keySet().removeAll(removedTaskIds);
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
        var session = ServiceRegistry.getInstance().session().getIfPresent(player);
        boolean cleaned = false;
        if (session != null) {
            for (var job : java.util.List.copyOf(session.placement.placeBatchJobs)) {
                if (job.workflowEntryId() == workflowEntryId) {
                    RtsPlacementBatch.cancelPlaceTask(player, session, job);
                    cleaned = true;
                }
            }
            for (var job : java.util.List.copyOf(session.placement.pendingJobs)) {
                if (job.workflowEntryId() == workflowEntryId) {
                    RtsPlacementBatch.cancelPlaceTask(player, session, job);
                    cleaned = true;
                }
            }
            for (var job : java.util.List.copyOf(session.destruction.destroyJobs)) {
                if (job.workflowEntryId() == workflowEntryId) {
                    RtsDestructionBatch.cancelDestroyTask(player, session, job);
                    cleaned = true;
                }
            }
            for (var job : java.util.List.copyOf(session.destruction.pendingDestroyJobs)) {
                if (job.workflowEntryId() == workflowEntryId) {
                    RtsDestructionBatch.cancelDestroyTask(player, session, job);
                    cleaned = true;
                }
            }
            cleaned |= RtsMiningStateMachine.cancelMiningTask(player, session, workflowEntryId);
        }
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
        return record != null || cleaned;
    }

    /**
     * 蓝图同步准入完成后的唯一提交入口；恢复路径也使用同一个入口。
     */
    public void submitBlueprint(BlueprintContext context, java.util.LinkedList<Integer> restoredRemaining) {
        if (context == null || context.player() == null) return;
        submitBlueprint(context, restoredRemaining, context.player().serverLevel().dimension());
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

    private void syncPlacementTasks(net.minecraft.server.level.ServerPlayer player,
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session) {
        long now = System.nanoTime();
        placementRecords.entrySet().removeIf(entry -> {
            TaskRecord record = entry.getValue();
            if (!record.ownerId().equals(player.getUUID())) return false;
            var job = entry.getKey();
            return !session.placement.placeBatchJobs.contains(job)
                    && !session.placement.pendingJobs.contains(job)
                    && record.status().terminal();
        });

        var job = session.placement.placeBatchJobs.peekFirst();
        if (job != null) {
            TaskRecord record = placementRecords.get(job);
            if (record == null) {
                record = createPlacementRecord(player, session, job, now);
                placementRecords.put(job, record);
                scheduler.submit(record);
            } else if (record.status() == TaskStatus.WAITING_RESOURCE) {
                record.resume(now);
            }
        }
    }

    private TaskRecord createPlacementRecord(net.minecraft.server.level.ServerPlayer player,
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session,
            RtsPlacementBatch.PlaceBatchJob job, long now) {
        UUID taskId = job.workflowEntryId() < 0
                ? UUID.randomUUID()
                : UUID.nameUUIDFromBytes((player.getUUID() + ":placement:"
                        + player.serverLevel().dimension().location() + ":" + job.workflowEntryId())
                        .getBytes(StandardCharsets.UTF_8));
        TaskRecord record = new TaskRecord(
                taskId,
                player.getUUID(), TaskType.PLACEMENT,
                new PlacementTaskPayload(player, session, job), job.totalCount(), now);
        record.restoreCursor(job.getIndex(), now);
        applyInitialPause(player, job.workflowEntryId(), record, now);
        return record;
    }

    private TaskStepResult executePlacement(TaskRecord task, TaskBudget budget) {
        PlacementTaskPayload payload = (PlacementTaskPayload) task.payload();
        var player = payload.player();
        var session = payload.session();
        var job = payload.job();
        if (session.placement.pendingJobs.contains(job)) return TaskStepResult.waitForResource();
        if (!session.placement.placeBatchJobs.contains(job)) return TaskStepResult.complete(0);
        if (session.placement.placeBatchJobs.peekFirst() != job) return TaskStepResult.yield(0);

        if (job.workflowEntryId() >= 0) {
            var token = com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                    .from(player, job.workflowEntryId()).orElse(null);
            if (token == null) {
                RtsPlacementBatch.cancelPlaceTask(player, session, job);
                return TaskStepResult.fail("rtsbuilding.task.error.workflow_missing");
            }
        }

        int beforeIndex = job.getIndex();
        int beforeSucceeded = job.successfulCount();
        int beforeFailed = job.failedCount();
        int processed = RtsPlacementBatch.tickPlaceTask(player, session, job,
                budget.maxUnits(), System.nanoTime() + budget.remainingNanos());
        int cursor = Math.max(0, job.getIndex() - beforeIndex);
        int succeeded = Math.max(0, job.successfulCount() - beforeSucceeded);
        int failed = Math.max(0, job.failedCount() - beforeFailed);
        if (session.placement.pendingJobs.contains(job)) {
            return TaskStepResult.waitForResource(processed, cursor, succeeded, failed);
        }
        if (!session.placement.placeBatchJobs.contains(job)) {
            return TaskStepResult.complete(processed, cursor, succeeded, failed);
        }
        return TaskStepResult.continueWith(processed, cursor, succeeded, failed);
    }

    private void syncDestructionTasks(net.minecraft.server.level.ServerPlayer player,
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session) {
        RtsDestructionBatch.tryResumePendingDestroyJobs(player, session);
        long now = System.nanoTime();
        destructionRecords.entrySet().removeIf(entry -> {
            TaskRecord record = entry.getValue();
            if (!record.ownerId().equals(player.getUUID())) return false;
            var job = entry.getKey();
            return !session.destruction.destroyJobs.contains(job)
                    && !session.destruction.pendingDestroyJobs.contains(job)
                    && record.status().terminal();
        });

        var job = session.destruction.destroyJobs.peekFirst();
        if (job != null) {
            TaskRecord record = destructionRecords.get(job);
            if (record == null) {
                record = createDestructionRecord(player, session, job, now);
                destructionRecords.put(job, record);
                scheduler.submit(record);
            } else if (record.status() == TaskStatus.WAITING_RESOURCE) {
                record.resume(now);
            }
        }
    }

    private TaskRecord createDestructionRecord(net.minecraft.server.level.ServerPlayer player,
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session,
            RtsDestructionBatch.DestructionJob job, long now) {
        UUID taskId = UUID.nameUUIDFromBytes((player.getUUID() + ":destruction:"
                + player.serverLevel().dimension().location() + ":" + job.workflowEntryId())
                .getBytes(StandardCharsets.UTF_8));
        TaskRecord record = new TaskRecord(
                taskId,
                player.getUUID(), TaskType.DESTRUCTION,
                new DestructionTaskPayload(player, session, job), job.totalCount(), now);
        record.restoreCursor(job.getIndex(), now);
        applyInitialPause(player, job.workflowEntryId(), record, now);
        return record;
    }

    private TaskStepResult executeDestruction(TaskRecord task, TaskBudget budget) {
        DestructionTaskPayload payload = (DestructionTaskPayload) task.payload();
        var player = payload.player();
        var session = payload.session();
        var job = payload.job();
        if (session.destruction.pendingDestroyJobs.contains(job)) return TaskStepResult.waitForResource();
        if (!session.destruction.destroyJobs.contains(job)) return TaskStepResult.complete(0);
        if (session.destruction.destroyJobs.peekFirst() != job) return TaskStepResult.yield(0);

        var token = com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                .from(player, job.workflowEntryId()).orElse(null);
        if (token == null) {
            RtsDestructionBatch.cancelDestroyTask(player, session, job);
            return TaskStepResult.fail("rtsbuilding.task.error.workflow_missing");
        }
        int beforeIndex = job.getIndex();
        int beforeSucceeded = job.successfulCount();
        int beforeFailed = job.failedCount();
        int processed = RtsDestructionBatch.tickDestroyTask(player, session, job,
                budget.maxUnits(), System.nanoTime() + budget.remainingNanos());
        int cursor = Math.max(0, job.getIndex() - beforeIndex);
        int succeeded = Math.max(0, job.successfulCount() - beforeSucceeded);
        int failed = Math.max(0, job.failedCount() - beforeFailed);
        if (session.destruction.pendingDestroyJobs.contains(job)) {
            return TaskStepResult.waitForResource(processed, cursor, succeeded, failed);
        }
        if (!session.destruction.destroyJobs.contains(job)) {
            return TaskStepResult.complete(processed, cursor, succeeded, failed);
        }
        return TaskStepResult.continueWith(processed, cursor, succeeded, failed);
    }

    private void syncMiningTasks(net.minecraft.server.level.ServerPlayer player,
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session) {
        MiningTaskSource source = currentMiningSource(session);
        miningRecords.entrySet().removeIf(entry -> entry.getKey().playerId().equals(player.getUUID())
                && entry.getValue().status().terminal()
                && (source == null
                || !entry.getKey().dimension().equals(player.serverLevel().dimension())
                || entry.getKey().workflowEntryId() != source.workflowEntryId()));
        if (source == null) return;

        MiningTaskKey key = new MiningTaskKey(
                player.getUUID(), player.serverLevel().dimension(), source.workflowEntryId());
        TaskRecord record = miningRecords.get(key);
        long now = System.nanoTime();
        if (record == null) {
            record = new TaskRecord(
                    UUID.nameUUIDFromBytes((player.getUUID() + ":mining:"
                            + player.serverLevel().dimension().location() + ":" + source.workflowEntryId())
                            .getBytes(StandardCharsets.UTF_8)),
                    player.getUUID(), TaskType.MINING,
                    new MiningTaskPayload(player, session, source.workflowEntryId()), source.totalUnits(), now);
            record.restoreSnapshot(source.cursorUnits(), source.succeededUnits(), source.failedUnits(), now);
            applyInitialPause(player, source.workflowEntryId(), record, now);
            miningRecords.put(key, record);
            scheduler.submit(record);
        } else if (record.status() == TaskStatus.WAITING_RESOURCE && !session.miningDropBuffer.isFull()) {
            record.resume(now);
        }
    }

    private MiningTaskSource currentMiningSource(
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session) {
        boolean hasActiveState = session.mining.miningPos != null
                || session.mining.ultimineProgressPos != null
                || !session.mining.ultimineTargets.isEmpty();
        if (hasActiveState && session.mining.workflowEntryId >= 0) {
            int total = session.mining.ultimineTotalTargets > 0 ? session.mining.ultimineTotalTargets : 1;
            int cursor = Math.max(0, session.mining.ultimineProcessedTargets);
            int succeeded = Math.max(0, session.mining.ultimineBrokenTargets);
            return new MiningTaskSource(
                    session.mining.workflowEntryId, total, cursor, succeeded, Math.max(0, cursor - succeeded));
        }
        var queued = session.mining.ultimineJobQueue.peekFirst();
        if (queued == null) return null;
        return new MiningTaskSource(queued.workflowEntryId(), queued.totalTargets(), 0, 0, 0);
    }

    private TaskStepResult executeMining(TaskRecord task, TaskBudget budget) {
        MiningTaskPayload payload = (MiningTaskPayload) task.payload();
        var player = payload.player();
        var session = payload.session();
        MiningTaskSource source = currentMiningSource(session);
        if (source == null || source.workflowEntryId() != payload.workflowEntryId()) {
            return TaskStepResult.complete(0, 0, 0, 0);
        }
        var token = com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                .from(player, payload.workflowEntryId()).orElse(null);
        if (token == null) {
            RtsMiningStateMachine.cancelMiningTask(player, session, payload.workflowEntryId());
            return TaskStepResult.fail("rtsbuilding.task.error.workflow_missing");
        }
        if (session.miningDropBuffer.isFull()) {
            return TaskStepResult.waitForResource();
        }

        var advance = RtsMiningStateMachine.tickActiveMining(
                player, session, budget.maxUnits(), System.nanoTime() + budget.remainingNanos());
        if (advance.waitingForBuffer()) {
            return TaskStepResult.waitForResource(
                    advance.processedUnits(), advance.processedUnits(),
                    advance.succeededUnits(), advance.failedUnits());
        }
        if (advance.operationEnded()) {
            return TaskStepResult.complete(
                    advance.processedUnits(), advance.processedUnits(),
                    advance.succeededUnits(), advance.failedUnits());
        }
        return TaskStepResult.nextTick(
                advance.processedUnits(), advance.processedUnits(),
                advance.succeededUnits(), advance.failedUnits());
    }

    private void syncBufferTask(net.minecraft.server.level.ServerPlayer player,
            com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession session) {
        TaskRecord existing = bufferRecords.get(player.getUUID());
        if (session.miningDropBuffer.isEmpty()) {
            if (existing != null && existing.status().terminal()) bufferRecords.remove(player.getUUID());
            return;
        }
        if (existing != null && !existing.status().terminal()) return;
        long now = System.nanoTime();
        TaskRecord record = new TaskRecord(
                UUID.randomUUID(), player.getUUID(), TaskType.BUFFER_DRAIN,
                new BufferDrainTaskPayload(player, session), 0, now);
        bufferRecords.put(player.getUUID(), record);
        scheduler.submit(record);
    }

    private TaskStepResult executeBufferDrain(TaskRecord task, TaskBudget budget) {
        BufferDrainTaskPayload payload = (BufferDrainTaskPayload) task.payload();
        var player = payload.player();
        var session = payload.session();
        int beforeStacks = session.miningDropBuffer.stacks.size();
        int processed = RtsDropAbsorber.drainDropBuffer(
                player, session, budget.maxUnits(), System.nanoTime() + budget.remainingNanos());
        int completedStacks = Math.max(0, beforeStacks - session.miningDropBuffer.stacks.size());
        if (session.miningDropBuffer.isEmpty()) {
            return TaskStepResult.complete(processed, completedStacks, completedStacks, 0);
        }
        return TaskStepResult.nextTick(processed, completedStacks, completedStacks, 0);
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
            if (payload.ready() && payload.shouldCheckpoint(true)) {
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
        for (var entry : placementRecords.entrySet()) {
            if (entry.getValue().ownerId().equals(key.playerId())
                    && entry.getKey().workflowEntryId() == key.workflowEntryId()) return entry.getValue();
        }
        for (var entry : destructionRecords.entrySet()) {
            if (entry.getValue().ownerId().equals(key.playerId())
                    && entry.getKey().workflowEntryId() == key.workflowEntryId()) return entry.getValue();
        }
        return miningRecords.get(new MiningTaskKey(
                key.playerId(), key.dimension(), key.workflowEntryId()));
    }

    /** TaskRecord 生命周期变更后，才把展示状态单向投影到工作流。 */
    private void projectWorkflowLifecycles() {
        java.util.List<TaskRecord> records = new java.util.ArrayList<>();
        records.addAll(placementRecords.values());
        records.addAll(destructionRecords.values());
        records.addAll(miningRecords.values());
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
        for (var entry : miningRecords.entrySet()) {
            if (entry.getValue() == record) return entry.getKey().dimension();
        }
        if (record.payload() instanceof PlacementTaskPayload payload) return payload.player().serverLevel().dimension();
        if (record.payload() instanceof DestructionTaskPayload payload) return payload.player().serverLevel().dimension();
        if (record.payload() instanceof MiningTaskPayload payload) return payload.player().serverLevel().dimension();
        throw new IllegalArgumentException("Task has no workflow dimension: " + record.type());
    }

    private int workflowEntryId(TaskRecord record) {
        if (record.payload() instanceof PlacementTaskPayload payload) return payload.job().workflowEntryId();
        if (record.payload() instanceof DestructionTaskPayload payload) return payload.job().workflowEntryId();
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

    private record MiningTaskKey(
            UUID playerId, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            int workflowEntryId) {
    }

    public record TaskDiagnostics(Map<TaskType, Integer> activeByType,
            Map<TaskType, Integer> waitingByType) {
    }

    private record WorkflowTaskKey(
            UUID playerId, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            int workflowEntryId) {
    }

    private record MiningTaskSource(
            int workflowEntryId, int totalUnits, int cursorUnits, int succeededUnits, int failedUnits) {
    }
}
