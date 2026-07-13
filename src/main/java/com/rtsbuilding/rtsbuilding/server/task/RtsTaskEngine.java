package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import net.minecraft.server.MinecraftServer;

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

    private RtsTaskEngine() {
        scheduler.registerExecutor(TaskType.LEGACY_ADAPTER, this::executeLegacyPlayerSlice);
    }

    public TaskScheduler.TickStats tick(MinecraftServer server) {
        for (var player : server.getPlayerList().getPlayers()) {
            var session = RtsSessionService.getIfPresent(player);
            if (session == null) continue;
            if (scheduler.hasTasks(player.getUUID())) continue;
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
    }

    private TaskStepResult executeLegacyPlayerSlice(TaskRecord task, TaskBudget budget) {
        LegacyPlayerSlicePayload payload = (LegacyPlayerSlicePayload) task.payload();
        var player = payload.player();
        var session = payload.session();
        int processed = 0;

        if (budget.hasTime() && processed < budget.maxUnits()) {
            processed += RtsPlacementBatch.tickPlaceBatchJobs(player, session,
                    budget.maxUnits() - processed, System.nanoTime() + budget.remainingNanos());
        }
        if (budget.hasTime() && processed < budget.maxUnits()) {
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
}
