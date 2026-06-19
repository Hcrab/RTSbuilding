package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowToken;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * 单个活跃（正在 Tick）的管道实例，包装了一个 {@link PipelineContext}
 * 及其 {@link TickablePipe}。
 *
 * <p>由 {@link TickablePipelineRegistry} 在管道同步阶段成功完成后创建。
 * 每个服务器 Tick，注册表在所有活跃实例上调用 {@link #tick()}。
 * 当可 Tick Pipe 发出完成信号（{@link TickResult.Done} 或
 * {@link TickResult.Error}）时，此实例被标记为已完成并从注册表中移除。</p>
 *
 * <p>失败时（{@link TickResult.Error} 或异常），管道自动回滚关联的
 * 工作流条目以防止槽泄漏——镜像了 {@link WorkflowPipeline} 的
 * 快速失败回滚行为。</p>
 *
 * <p>实例<b>不</b>是线程安全的——它们是为单线程服务器 Tick 使用而设计的。</p>
 */
public final class ActivePipeline {

    private final ServerPlayer player;
    private final PipelineContext ctx;
    private final TickablePipe pipe;
    private final int workflowEntryId;
    private boolean completed;

    /**
     * @param player 服务器端玩家
     * @param ctx    管道上下文（包含带有条目 ID 的共享数据）
     * @param pipe   每个 Tick 调用的可 Tick Pipe
     */
    public ActivePipeline(ServerPlayer player, PipelineContext ctx, TickablePipe pipe) {
        this.player = player;
        this.ctx = ctx;
        this.pipe = pipe;
        // 在构造时缓存工作流条目 ID，避免后续每 tick 从 data map 中 hasData + getData 两次查找
        Integer cached = ctx.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
        this.workflowEntryId = cached != null ? cached : -1;
    }

    // ──────────────────────────────────────────────────────────────────
    //  访问器
    // ──────────────────────────────────────────────────────────────────

    /** 返回服务器端玩家。 */
    public ServerPlayer player() {
        return player;
    }

    /** 返回管道上下文。 */
    public PipelineContext context() {
        return ctx;
    }

    /** 返回此管道是否已完成 Tick。 */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * 返回缓存的工作流条目 ID，若未关联工作流则返回 -1。
     * 消除每 tick 通过 ctx.hasData + ctx.getData 两次 HashMap 查找的开销。
     */
    public int entryId() {
        return workflowEntryId;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Tick
    // ──────────────────────────────────────────────────────────────────

    /**
     * 调用可 Tick Pipe 一次。每个服务器 Tick 调用，直到此
     * 方法返回非空结果。
     *
     * <p>失败时，工作流条目（如果有）会自动取消以防止槽泄漏。</p>
     *
     * @return 如果 Pipe 仍在工作中则返回空的 {@link Optional}（下次 Tick 再次调用），
     *         如果 Pipe 已完成（成功或失败）则返回 {@link PipelineResult}
     */
    public Optional<PipelineResult> tick() {
        if (completed) {
            return Optional.empty();
        }
        try {
            TickResult result = pipe.tick(ctx);
            return switch (result) {
                case TickResult.Running r -> Optional.empty();
                case TickResult.Done d -> {
                    completed = true;
                    completeWorkflow();
                    yield Optional.of(PipelineResult.success());
                }
                case TickResult.Error e -> {
                    completed = true;
                    rollbackWorkflow();
                    RtsbuildingMod.LOGGER.warn("[ActivePipeline] Tickable pipe failed for player {}: {}",
                            player.getGameProfile().getName(), e.message());
                    yield Optional.of(PipelineResult.failure(e.message()));
                }
            };
        } catch (Exception e) {
            completed = true;
            rollbackWorkflow();
            RtsbuildingMod.LOGGER.error("[ActivePipeline] Tickable pipe threw for player {}",
                    player.getGameProfile().getName(), e);
            return Optional.of(PipelineResult.failure(
                    "Tickable pipe threw: " + e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  回滚
    // ──────────────────────────────────────────────────────────────────

    /**
     * 当可 Tick Pipe 发出正常完成信号时，完成工作流条目（如果已创建）。
     *
     * <p>这确保即使在业务逻辑未完成它的边缘情况下
     *（例如创造模式连锁挖掘，目标被立即破坏而无需经过
     * {@code finalizeMiningOperation}），工作流条目也能正确关闭。
     * 由于 {@code token.complete()} 是幂等的——
     * 如果条目已被移除则变为空操作——
     * 即使在业务逻辑已完成工作流后调用此方法也是安全的。</p>
     */
    private void completeWorkflow() {
        if (workflowEntryId < 0) {
            return;
        }
        RtsWorkflowEngine.getInstance().from(player, workflowEntryId)
                .ifPresent(RtsWorkflowToken::complete);
    }

    /**
     * 当可 Tick 阶段失败或抛出异常时，取消工作流条目（如果已创建）
     * 以防止槽泄漏。
     *
     * <p>镜像 {@link WorkflowPipeline} 中的快速失败回滚。
     * 即使没有条目 ID 也是安全的——它会变为空操作。</p>
     */
    private void rollbackWorkflow() {
        if (workflowEntryId < 0) {
            return;
        }
        RtsWorkflowEngine.getInstance().from(player, workflowEntryId)
                .ifPresent(token -> {
                    token.cancel();
                    RtsbuildingMod.LOGGER.info("[ActivePipeline] Rolled back workflow #{} for player {}",
                            workflowEntryId, player.getGameProfile().getName());
                });
    }
}
