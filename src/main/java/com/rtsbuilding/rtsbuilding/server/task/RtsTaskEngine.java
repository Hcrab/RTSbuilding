package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsDropAbsorber;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
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
    private final Map<MiningTaskKey, TaskRecord> miningRecords = new java.util.HashMap<>();
    private final Map<UUID, TaskRecord> bufferRecords = new java.util.HashMap<>();
    private final Map<WorkflowTaskKey, Boolean> workflowPauseOverrides = new java.util.HashMap<>();
    private final Map<UUID, TaskStatus> projectedTaskStatuses = new java.util.HashMap<>();

    private RtsTaskEngine() {
        scheduler.registerExecutor(TaskType.PLACEMENT, this::executePlacement);
        scheduler.registerExecutor(TaskType.MINING, this::executeMining);
        scheduler.registerExecutor(TaskType.BUFFER_DRAIN, this::executeBufferDrain);
    }

    public TaskScheduler.TickStats tick(MinecraftServer server) {
        for (var player : server.getPlayerList().getPlayers()) {
            var session = RtsSessionService.getIfPresent(player);
            if (session == null) continue;
            syncPlacementTasks(player, session);
            syncMiningTasks(player, session);
            syncBufferTask(player, session);
        }
        TaskScheduler.TickStats stats = scheduler.tick(
                Config.taskEngineMaxNanosPerTick(),
                Config.taskEngineMaxUnitsPerTick(),
                Config.taskEngineMaxUnitsPerSlice());
        projectWorkflowLifecycles();
        return stats;
    }

    public void onPlayerLogout(UUID playerId) {
        scheduler.cancelOwner(playerId, System.nanoTime());
        java.util.Set<UUID> removedTaskIds = new java.util.HashSet<>();
        placementRecords.values().stream().filter(record -> record.ownerId().equals(playerId))
                .map(TaskRecord::id).forEach(removedTaskIds::add);
        miningRecords.values().stream().filter(record -> record.ownerId().equals(playerId))
                .map(TaskRecord::id).forEach(removedTaskIds::add);
        placementRecords.entrySet().removeIf(entry -> entry.getValue().ownerId().equals(playerId));
        miningRecords.entrySet().removeIf(entry -> entry.getKey().playerId().equals(playerId));
        bufferRecords.remove(playerId);
        workflowPauseOverrides.keySet().removeIf(key -> key.playerId().equals(playerId));
        projectedTaskStatuses.keySet().removeAll(removedTaskIds);
    }

    /** 玩家暂停或恢复工作流时，先修改真实任务；工作流令牌只承接 UI 投影。 */
    public boolean setWorkflowPaused(net.minecraft.server.level.ServerPlayer player, int workflowEntryId,
            boolean paused) {
        if (player == null || workflowEntryId < 0) return false;
        WorkflowTaskKey key = new WorkflowTaskKey(player.getUUID(), workflowEntryId);
        var workflow = com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                .getProgress(player, workflowEntryId);
        if (!workflow.isActive() || !isTaskEngineWorkflow(workflow.type())) return false;
        workflowPauseOverrides.put(key, paused);
        TaskRecord record = findWorkflowTask(key);
        if (record != null) {
            if (paused) record.pause(System.nanoTime());
            else record.resume(System.nanoTime());
        }
        return true;
    }

    /** RTS 关闭时先暂停真实任务，随后再把状态投影到工作流面板。 */
    public void pauseAllWorkflowTasks(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return;
        for (var status : com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                .getAllProgress(player)) {
            if (status.isActive() && !status.suspended()) {
                setWorkflowPaused(player, status.entryId(), true);
            }
        }
    }

    /** 删除工作流前先取消对应领域任务，避免只删 UI 条目而让后台任务继续执行。 */
    public boolean cancelWorkflowTask(net.minecraft.server.level.ServerPlayer player, int workflowEntryId) {
        if (player == null || workflowEntryId < 0) return false;
        WorkflowTaskKey key = new WorkflowTaskKey(player.getUUID(), workflowEntryId);
        TaskRecord record = findWorkflowTask(key);
        var session = RtsSessionService.getIfPresent(player);
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
            cleaned |= RtsMiningStateMachine.cancelMiningTask(player, session, workflowEntryId);
        }
        if (record != null) {
            record.cancel(System.nanoTime());
            releaseTerminalWorkflow(record);
        }
        workflowPauseOverrides.remove(key);
        return record != null || cleaned;
    }

        // 兼容调度片每 Tick 只执行一次；真实类型任务迁移后由各 Executor 决定继续或完成。
    private void syncPlacementTasks(net.minecraft.server.level.ServerPlayer player,
            com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession session) {
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
            com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession session,
            RtsPlacementBatch.PlaceBatchJob job, long now) {
        UUID taskId = job.workflowEntryId() < 0
                ? UUID.randomUUID()
                : UUID.nameUUIDFromBytes((player.getUUID() + ":placement:" + job.workflowEntryId())
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
        if (job.workflowEntryId() >= 0) {
            int projected = Math.min(task.totalUnits(), task.completedUnits() + succeeded);
            com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                    .from(player, job.workflowEntryId())
                    .ifPresent(token -> token.setCompletedBlocks(projected));
        }
        if (session.placement.pendingJobs.contains(job)) {
            return TaskStepResult.waitForResource(processed, cursor, succeeded, failed);
        }
        if (!session.placement.placeBatchJobs.contains(job)) {
            return TaskStepResult.complete(processed, cursor, succeeded, failed);
        }
        return TaskStepResult.continueWith(processed, cursor, succeeded, failed);
    }

    private void syncMiningTasks(net.minecraft.server.level.ServerPlayer player,
            com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession session) {
        MiningTaskSource source = currentMiningSource(session);
        miningRecords.entrySet().removeIf(entry -> entry.getKey().playerId().equals(player.getUUID())
                && entry.getValue().status().terminal()
                && (source == null || entry.getKey().workflowEntryId() != source.workflowEntryId()));
        if (source == null) return;

        MiningTaskKey key = new MiningTaskKey(player.getUUID(), source.workflowEntryId());
        TaskRecord record = miningRecords.get(key);
        long now = System.nanoTime();
        if (record == null) {
            record = new TaskRecord(
                    UUID.nameUUIDFromBytes((player.getUUID() + ":mining:" + source.workflowEntryId())
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
            com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession session) {
        boolean hasActiveState = session.mining.miningPos != null
                || session.mining.ultimineProgressPos != null
                || !session.mining.ultimineTargets.isEmpty();
        if (hasActiveState && session.mining.miningWorkflowEntryId >= 0) {
            int total = session.mining.ultimineTotalTargets > 0 ? session.mining.ultimineTotalTargets : 1;
            int cursor = Math.max(0, session.mining.ultimineProcessedTargets);
            int succeeded = Math.max(0, session.mining.ultimineBrokenTargets);
            return new MiningTaskSource(
                    session.mining.miningWorkflowEntryId, total, cursor, succeeded,
                    Math.max(0, cursor - succeeded));
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
            com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession session) {
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

    private void applyInitialPause(net.minecraft.server.level.ServerPlayer player, int workflowEntryId,
            TaskRecord record, long now) {
        if (workflowEntryId < 0) return;
        WorkflowTaskKey key = new WorkflowTaskKey(player.getUUID(), workflowEntryId);
        Boolean override = workflowPauseOverrides.get(key);
        boolean paused = override != null ? override
                : com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                        .from(player, workflowEntryId).map(token -> token.isPaused()).orElse(false);
        if (paused) record.pause(now);
    }

    private TaskRecord findWorkflowTask(WorkflowTaskKey key) {
        for (var entry : placementRecords.entrySet()) {
            if (entry.getValue().ownerId().equals(key.playerId())
                    && entry.getKey().workflowEntryId() == key.workflowEntryId()) return entry.getValue();
        }
        return miningRecords.get(new MiningTaskKey(key.playerId(), key.workflowEntryId()));
    }

    /** TaskRecord 生命周期变化后，才把展示状态单向投影到工作流。 */
    private void projectWorkflowLifecycles() {
        java.util.List<TaskRecord> records = new java.util.ArrayList<>();
        records.addAll(placementRecords.values());
        records.addAll(miningRecords.values());
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
                    // 终态已在上方统一释放。
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
        workflowPauseOverrides.remove(new WorkflowTaskKey(record.ownerId(), entryId));
        projectedTaskStatuses.put(record.id(), record.status());
    }

    private com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowToken workflowToken(
            TaskRecord record, int entryId) {
        if (record.payload() instanceof PlacementTaskPayload payload) {
            return com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                    .from(payload.player(), entryId).orElse(null);
        }
        if (record.payload() instanceof MiningTaskPayload payload) {
            return com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                    .from(payload.player(), entryId).orElse(null);
        }
        return null;
    }

    private int workflowEntryId(TaskRecord record) {
        if (record.payload() instanceof PlacementTaskPayload payload) return payload.job().workflowEntryId();
        if (record.payload() instanceof MiningTaskPayload payload) return payload.workflowEntryId();
        return -1;
    }

    private boolean isTaskEngineWorkflow(
            com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType type) {
        return switch (type) {
            case MINE_SINGLE, ULTIMINE, AREA_MINE, AREA_DESTROY,
                    PLACE_SINGLE, PLACE_BATCH, QUICK_BUILD -> true;
            case STOP_MINING -> false;
        };
    }

    private record MiningTaskKey(UUID playerId, int workflowEntryId) {
    }

    private record WorkflowTaskKey(UUID playerId, int workflowEntryId) {
    }

    private record MiningTaskSource(
            int workflowEntryId, int totalUnits, int cursorUnits, int succeededUnits, int failedUnits) {
    }
}
