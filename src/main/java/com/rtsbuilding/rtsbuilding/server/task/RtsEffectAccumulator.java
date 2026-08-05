package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.task.effect.RtsEffectCommitBarrier;
import com.rtsbuilding.rtsbuilding.server.task.effect.RtsEffectKind;
import com.rtsbuilding.rtsbuilding.server.task.effect.RtsEffectLedger;
import com.rtsbuilding.rtsbuilding.server.task.effect.RtsEffectLedgerMetrics;
import com.rtsbuilding.rtsbuilding.server.task.effect.RtsPlayerEffectTarget;
import com.rtsbuilding.rtsbuilding.server.task.effect.RtsProductionEffectCommitter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import java.util.UUID;

/**
 * 业务层保留的轻量副作用入口。
 *
 * <p>旧实现用 {@code drain()+clear()} 清空队列，提交中任一保存或发包异常都会丢掉本批剩余标记。
 * 现在所有可幂等覆盖的投影先进入强类型 Ledger，再由有界 Commit Barrier 逐类型确认；失败类型
 * 留到后续 Tick。材料、工具、世界、ItemEntity 与 Capability 事务明确不经过这里。</p>
 */
public final class RtsEffectAccumulator {
    public static final RtsEffectAccumulator INSTANCE = new RtsEffectAccumulator();

    private final RtsEffectLedger<RtsPlayerEffectTarget> ledger = new RtsEffectLedger<>();
    private final RtsEffectCommitBarrier<RtsPlayerEffectTarget> barrier =
            new RtsEffectCommitBarrier<>(ledger);

    private RtsEffectAccumulator() {
    }

    public void markStorageViewDirty(UUID playerId, int dimension) {
        ledger.mark(dimensionTarget(playerId, dimension), RtsEffectKind.STORAGE_VIEW_DIRTY);
    }

    public void markWorkflow(UUID playerId, int dimension) {
        ledger.mark(dimensionTarget(playerId, dimension), RtsEffectKind.WORKFLOW_SNAPSHOT);
    }

    public void markPersistence(UUID playerId, int ignoredDimension) {
        ledger.mark(RtsPlayerEffectTarget.global(playerId), RtsEffectKind.SESSION_PERSISTENCE);
    }

    public void markHistory(UUID playerId) {
        ledger.mark(RtsPlayerEffectTarget.global(playerId), RtsEffectKind.HISTORY_SNAPSHOT);
    }

    public void markPluginState(UUID playerId) {
        ledger.mark(RtsPlayerEffectTarget.global(playerId), RtsEffectKind.PLUGIN_STATE_SNAPSHOT);
    }

    public void markProgressionState(UUID playerId) {
        ledger.mark(RtsPlayerEffectTarget.global(playerId), RtsEffectKind.PROGRESSION_STATE_SNAPSHOT);
    }

    /** 每个服务器 Tick 只允许一次提交；同 Tick 的重复调用是常数成本空操作。 */
    public RtsEffectCommitBarrier.CommitReport flush(MinecraftServer server) {
        WorldServer overworld = server == null ? null : com.rtsbuilding.rtsbuilding.platform.server.ServerCompat.getWorld(server, 0);
        if (overworld == null) {
            return new RtsEffectCommitBarrier.CommitReport(
                    Long.MIN_VALUE, true, 0, 0, 0, 0, 0, 0, ledger.pendingTargetCount());
        }
        long tick = overworld.getTotalWorldTime();
        return barrier.commit(tick, new RtsProductionEffectCommitter(server));
    }

    public RtsEffectLedgerMetrics metrics() {
        return ledger.snapshotMetrics();
    }

    public int pendingTargets() {
        return ledger.pendingTargetCount();
    }

    public void clearPlayer(UUID playerId) {
        if (playerId != null) ledger.discardMatching(target -> target.playerId().equals(playerId));
    }

    public void clearDimension(UUID playerId, int dimension) {
        if (playerId == null) return;
        String dimensionId = Integer.toString(dimension);
        ledger.discardMatching(target -> target.playerId().equals(playerId)
                && !target.isGlobal() && target.dimensionId().equals(dimensionId));
    }

    public void resetForServerStart() {
        ledger.clear();
        barrier.resetTickGate();
    }

    public void clearAll() {
        ledger.clear();
        barrier.resetTickGate();
    }

    private static RtsPlayerEffectTarget dimensionTarget(
            UUID playerId, int dimension) {
        return RtsPlayerEffectTarget.inDimension(playerId, Integer.toString(dimension));
    }
}
