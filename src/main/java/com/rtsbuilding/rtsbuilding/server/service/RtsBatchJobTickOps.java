package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.service.destruction.RtsDestructionBatch;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.server.level.ServerPlayer;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

/**
 * 批处理作业 tick 处理的共享工具方法。
 *
 * <p>提取 {@link RtsPlacementBatch#tickPlaceBatchJobs} 和
 * {@link RtsDestructionBatch#tickDestroyJobs} 中的通用模式，
 * 消除两个 300+ 行方法之间的代码重复。</p>
 */
public final class RtsBatchJobTickOps {

    private RtsBatchJobTickOps() {
    }

    /**
     * 模式 1：暂停/取消检查 + 队列头部旋转。
     *
     * <p>返回包含工作流 token 的 {@link Optional}，若 job 被跳过旋转则返回
     * {@code null} 表示内层应跳过处理直接进入下一个 while 迭代。</p>
     *
     * @return 非空 Optional(token) = 可正常处理；empty = workflow 已取消（已从队列移除）
     */
    public static <J> Optional<WorkflowTokenHolder> checkPausedOrCancelled(
            Deque<J> jobs, J job, ServerPlayer player,
            ToIntFunction<J> entryIdFn, MutableInt pausedSkipped) {
        int eid = entryIdFn.applyAsInt(job);
        if (eid < 0) {
            return Optional.of(new WorkflowTokenHolder(null));
        }
        var tokenOpt = RtsWorkflowEngine.getInstance().from(player, eid);
        if (tokenOpt.isEmpty()) {
            // 工作流已被关闭 → 移除 job
            jobs.removeFirst();
            pausedSkipped.value = 0;
            return Optional.empty();
        }
        if (tokenOpt.get().isPaused()) {
            // 暂停 → 移到队尾
            jobs.removeFirst();
            jobs.addLast(job);
            pausedSkipped.value++;
            if (pausedSkipped.value >= jobs.size()) {
                // 全部暂停了，没必要继续
                return null; // sentinel: break outer loop
            }
            return Optional.empty(); // skip this iteration
        }
        pausedSkipped.value = 0;
        return Optional.of(new WorkflowTokenHolder(tokenOpt.get()));
    }

    /**
     * 持有可选 workflow token 的简单包装，避免 Optional 嵌套。
     */
    public record WorkflowTokenHolder(
            com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowToken token
    ) {}

    /**
     * 模式 2：处理此 tick 内完成的 job——历史记录 + 进度更新 + 工作流完成 + 刷新页面。
     *
     * @param <J>             job 类型
     * @param completedJobs  此 tick 内完成的 job 列表
     * @param beforeTick      tick 前每个 job 的进度快照
     * @param entryIdFn       从 job 获取 workflowEntryId
     * @param countFn         从 job 获取已处理数（placedPositions.size / destroyedPositions.size）
     * @param failedFn        从 job 获取跳过/失败数
     * @param historyRecorder 记录历史的方法（recordPlacement / recordBreak）
     */
    public static <J> void processCompletedJobs(
            ServerPlayer player, RtsStorageSession session,
            List<J> completedJobs, Map<Integer, Integer> beforeTick,
            ToIntFunction<J> entryIdFn, ToIntFunction<J> countFn,
            ToIntFunction<J> failedFn,
            BiConsumer<ServerPlayer, J> historyRecorder,
            @javax.annotation.Nullable BiConsumer<ServerPlayer, J> onCompleted,
            boolean releaseWorkflow) {
        if (completedJobs.isEmpty()) return;

        var engine = RtsWorkflowEngine.getInstance();
        for (J job : completedJobs) {
            int eid = entryIdFn.applyAsInt(job);
            int before = beforeTick.getOrDefault(eid, 0);
            int delta = countFn.applyAsInt(job) - before;

            // 记录历史
            historyRecorder.accept(player, job);

            // 合并三次 engine.from() 为一次，避免重复的 playerRefs + slots + entryId 查找
            engine.from(player, eid).ifPresent(token -> {
                // 更新工作流进度
                if (delta > 0) {
                    token.updateProgress(delta, null);
                }
                // 报告失败
                int failed = failedFn.applyAsInt(job);
                for (int i = 0; i < failed; i++) {
                    token.recordFailure();
                }
                // 旧入口仍自行释放；Task Engine 路径必须等 TaskRecord 进入终态后再释放。
                if (releaseWorkflow) token.complete();
            });

            if (onCompleted != null) {
                onCompleted.accept(player, job);
            }
        }

        // 统一刷新储存页面
        // 批处理收尾只唤醒下一次储存 tick；页面、工作流与持久化在 tick 末合并提交。
        ServiceRegistry.getInstance().serviceOp().markDirtyDeferred(player, session);
        RtsEffectAccumulator.INSTANCE.markStorageViewDirty(player.getUUID(), player.level().dimension());
        RtsEffectAccumulator.INSTANCE.markWorkflow(player.getUUID(), player.level().dimension());
        RtsEffectAccumulator.INSTANCE.markPersistence(player.getUUID(), player.level().dimension());
    }

    /**
     * 模式 3：更新活跃队列中 job 的中途进度。
     */
    public static <J> void updateMidProgress(
            ServerPlayer player, RtsStorageSession session,
            Iterable<J> activeJobs, Map<Integer, Integer> beforeTick,
            ToIntFunction<J> entryIdFn, ToIntFunction<J> countFn) {
        var engine = RtsWorkflowEngine.getInstance();
        boolean changed = false;
        for (J j : activeJobs) {
            int eid = entryIdFn.applyAsInt(j);
            int before = beforeTick.getOrDefault(eid, 0);
            int delta = countFn.applyAsInt(j) - before;
            if (delta > 0) {
                engine.from(player, eid).ifPresent(token -> token.updateProgress(delta, null));
                changed = true;
            }
        }
        if (changed) {
            ServiceRegistry.getInstance().serviceOp().markDirtyDeferred(player, session);
            RtsEffectAccumulator.INSTANCE.markWorkflow(player.getUUID(), player.level().dimension());
        }
    }

    /**
     * 用于在 lambda 中持有可变 int 的包装类。
     */
    public static final class MutableInt {
        public int value;
        public MutableInt(int value) { this.value = value; }
    }
}
