package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsDropAbsorber;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Command Gateway 之后的统一任务执行入口。
 *
 * <p>当前提交先把全部玩家工作放入同一个全局数量/时间预算，并通过
 * {@link LegacyPlayerSlicePayload} 兼容旧队列。新功能必须直接提交真正的 TaskRecord，
 * 不得再新增 Session 内的独立 Tick 队列。</p>
 */
public final class RtsTaskEngine {
    public static final RtsTaskEngine INSTANCE = new RtsTaskEngine();

    private final TaskScheduler scheduler = new TaskScheduler(System::nanoTime);
    private final Map<RtsPlacementBatch.PlaceBatchJob, TaskRecord> placementRecords = new IdentityHashMap<>();
    private final Map<MiningTaskKey, TaskRecord> miningRecords = new java.util.HashMap<>();
    private final Set<UUID> legacySliceQueuedOwners = new HashSet<>();

    private RtsTaskEngine() {
        scheduler.registerExecutor(TaskType.LEGACY_ADAPTER, this::executeLegacyPlayerSlice);
        scheduler.registerExecutor(TaskType.PLACEMENT, this::executePlacement);
        scheduler.registerExecutor(TaskType.MINING, this::executeMining);
    }

    public TaskScheduler.TickStats tick(MinecraftServer server) {
        for (var player : server.getPlayerList().getPlayers()) {
            var session = RtsSessionService.getIfPresent(player);
            if (session == null) continue;
            syncPlacementTasks(player, session);
            syncMiningTasks(player, session);
            if (legacySliceQueuedOwners.contains(player.getUUID())) continue;
            legacySliceQueuedOwners.add(player.getUUID());
            scheduler.submit(new TaskRecord(
                    UUID.randomUUID(), player.getUUID(), TaskType.LEGACY_ADAPTER,
                    new LegacyPlayerSlicePayload(player, session), 0, System.nanoTime()));
        }
        return scheduler.tick(
                Config.taskEngineMaxNanosPerTick(),
                Config.taskEngineMaxUnitsPerTick(),
                Config.taskEngineMaxUnitsPerSlice());
    }

    public void onPlayerLogout(UUID playerId) {
        scheduler.cancelOwner(playerId, System.nanoTime());
        legacySliceQueuedOwners.remove(playerId);
        placementRecords.entrySet().removeIf(entry -> entry.getValue().ownerId().equals(playerId));
        miningRecords.entrySet().removeIf(entry -> entry.getKey().playerId().equals(playerId));
    }

    private TaskStepResult executeLegacyPlayerSlice(TaskRecord task, TaskBudget budget) {
        LegacyPlayerSlicePayload payload = (LegacyPlayerSlicePayload) task.payload();
        var player = payload.player();
        var session = payload.session();
        int processed = 0;

        legacySliceQueuedOwners.remove(player.getUUID());

        if (budget.hasTime() && processed < budget.maxUnits()) {
            processed += RtsDropAbsorber.drainDropBuffer(player, session,
                    budget.maxUnits() - processed, System.nanoTime() + budget.remainingNanos());
        }
        if (budget.hasTime() && processed < budget.maxUnits()) {
            processed += RtsPlacementBatch.tickPlaceBatchJobs(player, session,
                    budget.maxUnits() - processed, System.nanoTime() + budget.remainingNanos());
        }
        if (currentMiningSource(session) == null && budget.hasTime() && processed < budget.maxUnits()) {
            int before = session.mining.ultimineProcessedTargets;
            boolean hadSingleTarget = session.mining.miningPos != null;
            RtsMiningStateMachine.tickActiveMining(player, session,
                    budget.maxUnits() - processed, System.nanoTime() + budget.remainingNanos());
            int miningDelta = Math.max(0, session.mining.ultimineProcessedTargets - before);
            if (hadSingleTarget && session.mining.miningPos == null) miningDelta = Math.max(1, miningDelta);
            processed += Math.min(budget.maxUnits() - processed, miningDelta);
        }

        // 兼容调度片每 Tick 只执行一次；真实类型任务迁移后由各 Executor 决定继续或完成。
        return TaskStepResult.complete(processed);
    }

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
            } else if (record.status() == TaskStatus.PAUSED) {
                var token = job.workflowEntryId() < 0 ? null
                        : com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                                .from(player, job.workflowEntryId()).orElse(null);
                if (token == null || !token.isPaused()) record.resume(now);
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
            if (token.isPaused()) {
                task.pause(System.nanoTime());
                return TaskStepResult.yield(0);
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
            miningRecords.put(key, record);
            scheduler.submit(record);
        } else if (record.status() == TaskStatus.WAITING_RESOURCE && !session.miningDropBuffer.isFull()) {
            record.resume(now);
        } else if (record.status() == TaskStatus.PAUSED) {
            var token = com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine.getInstance()
                    .from(player, source.workflowEntryId()).orElse(null);
            if (token == null || !token.isPaused()) record.resume(now);
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
        if (token.isPaused()) {
            task.pause(System.nanoTime());
            return TaskStepResult.nextTick(0, 0, 0, 0);
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

    private record MiningTaskKey(UUID playerId, int workflowEntryId) {
    }

    private record MiningTaskSource(
            int workflowEntryId, int totalUnits, int cursorUnits, int succeededUnits, int failedUnits) {
    }
}
