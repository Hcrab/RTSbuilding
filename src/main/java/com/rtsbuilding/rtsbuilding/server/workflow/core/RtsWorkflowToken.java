package com.rtsbuilding.rtsbuilding.server.workflow.core;

import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 不可变令牌，代表单个活动工作流。
 *
 * <p>这是与工作流系统交互的<b>主要面向消费者的 API</b>。
 * 调用者无需手动管理会话、索引和条目 ID，而是通过引擎的工厂方法获取令牌，
 * 然后直接在令牌上调用生命周期方法即可。</p>
 *
 * <p>令牌内部持有玩家的 UUID 和不可变的条目 ID，
 * 因此即使早期条目被删除导致索引偏移，令牌仍然有效。
 * 所有方法委托给创建此令牌的引擎。</p>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * // 启动工作流并获取令牌
 * var token = engine.startMining(player, 100)
 *         .orElse(null);
 * if (token == null) {
 *     // 工作流队列已满
 *     return;
 * }
 *
 * // 处理过程中
 * for (BlockPos pos : targets) {
 *     if (processBlock(pos)) {
 *         token.markProgress();
 *     } else {
 *         token.recordFailure();
 *     }
 * }
 *
 * // 完成后
 * token.complete();
 *
 * // 从其他代码位置重建：
 * var token2 = engine.from(player, savedEntryId)
 *         .orElse(null);
 * if (token2 != null) {
 *     token2.markProgress();
 * }
 * }</pre>
 *
 * <p>令牌<b>不是</b>线程安全的——它们设计用于单线程的服务端 tick 处理。
 * 为每个不同的工作流创建新的令牌。</p>
 */
public record RtsWorkflowToken(
        UUID playerId,
        int entryId,
        ResourceKey<Level> dimension,
        RtsWorkflowEngine engine
) {

    // ──────────────────────────────────────────────────────────────────
    //  构造（紧凑构造器——由 record 自动生成，仅做验证）
    // ──────────────────────────────────────────────────────────────────

    public RtsWorkflowToken {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(engine, "engine");
    }

    // ──────────────────────────────────────────────────────────────────
    //  标识
    // ──────────────────────────────────────────────────────────────────

    // 访问器由 record 自动生成：playerId()、entryId()、dimension()

    /**
     * 返回 {@code true} 表示此令牌仍指向一个有效的工作流条目
     *（即尚未完成、取消或超时）。
     */
    public boolean isValid() {
        return resolveEntry() != null;
    }

    // ──────────────────────────────────────────────────────────────────
    //  生命周期
    // ──────────────────────────────────────────────────────────────────

    /**
     * 标记一个单位的进度。
     * 等同于 {@code updateProgress(1, null)}。
     */
    public void markProgress() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.addCompletedBlocks(1);
            engine.notifyPlayer(playerId, dimension);
        }
    }

    /**
     * 按指定增量更新进度，并可选择报告缺失物品。
     *
     * @param completedDelta 自上次更新以来完成的单位数
     * @param missingItems   （可空）缺失的物品 ID
     */
    public void updateProgress(int completedDelta, @Nullable List<String> missingItems) {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.addCompletedBlocks(completedDelta);
            entry.addMissingItems(missingItems);
            engine.notifyPlayer(playerId, dimension);
        }
    }

    /**
     * 将已完成方块数设置为绝对值（用于世界扫描刷新）。
     * <p>这是令牌上唯一的「设置」方法；所有其他修改都是基于增量的。
     * 谨慎使用——正常进度请优先使用 {@link #updateProgress(int, List)}。</p>
     */
    public void setCompletedBlocks(int absoluteValue) {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.setCompletedBlocks(absoluteValue);
            engine.notifyPlayer(playerId, dimension);
        }
    }

    /**
     * 将总方块数设置为绝对值（在目标收集完成后使用）。
     * <p>当执行阶段完成后才知道总方块数时使用此方法
     *（例如 ultimine/area-mine 目标扫描）。</p>
     */
    public void setTotalBlocks(int totalBlocks) {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.setTotalBlocks(totalBlocks);
            engine.notifyPlayer(playerId, dimension);
        }
    }

    /**
     * 记录此工作流的一次失败。
     */
    public void recordFailure() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.addFailedBlocks(1);
            engine.notifyPlayer(playerId, dimension);
        }
    }

    /**
     * 为此工作流设置一条人类可读的详情消息。
     */
    public void setDetailMessage(String message) {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.setDetailMessage(message);
            engine.notifyPlayer(playerId, dimension);
        }
    }

    /**
     * 挂起此工作流（标记为等待物品）。
     */
    public void suspend() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.setSuspended(true);
            // 详情跨网络传输翻译键，避免服务端语言固化客户端显示。
            entry.setDetailMessage("screen.rtsbuilding.workflow.waiting_items");
            engine.fireEvent(WorkflowEventType.SUSPENDED, playerId, entryId, entry);
            engine.notifyPlayer(playerId, dimension);
        }
    }

    /**
     * 暂停此工作流（仅停止此条目的 tick 处理）。
     */
    public void pause() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            entry.setPaused(true);
            engine.fireEvent(WorkflowEventType.PAUSED, playerId, entryId, entry);
            engine.notifyPlayer(playerId, dimension);
        }
    }

    /**
     * 取消暂停此工作流（恢复此条目的 tick 处理）。
     *
     * @return {@code true} 表示工作流成功取消暂停
     */
    public boolean unpause() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null && entry.paused()) {
            entry.setPaused(false);
            engine.fireEvent(WorkflowEventType.UNPAUSED, playerId, entryId, entry);
            engine.notifyPlayer(playerId, dimension);
            return true;
        }
        return false;
    }

    /**
     * 返回 {@code true} 表示此工作流条目已暂停。
     */
    public boolean isPaused() {
        RtsWorkflowEntry entry = resolveEntry();
        return entry != null && entry.paused();
    }

    /**
     * 如果工作流已被挂起则恢复它。
     *
     * @return {@code true} 表示工作流成功恢复
     */
    public boolean resume() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null && entry.suspended()) {
            entry.setSuspended(false);
            entry.setDetailMessage("");
            engine.fireEvent(WorkflowEventType.RESUMED, playerId, entryId, entry);
            engine.notifyPlayer(playerId, dimension);
            return true;
        }
        return false;
    }

    /**
     * 完成此工作流——移除条目并通知客户端。
     *
     * <p>网络通知由 {@link RtsWorkflowEngine#removeEntry(UUID, ResourceKey, int)}
     * 内部处理，因此此处无需调用 {@code engine.notifyPlayer()}。</p>
     */
    public void complete() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            engine.fireEvent(WorkflowEventType.COMPLETED, playerId, entryId, entry);
            engine.removeEntry(playerId, dimension, entryId);
        }
    }

    /**
     * 取消此工作流——移除条目且不标记为已完成。
     *
     * <p>网络通知由 {@link RtsWorkflowEngine#removeEntry(UUID, ResourceKey, int)}
     * 内部处理，因此此处无需调用 {@code engine.notifyPlayer()}。</p>
     */
    public void cancel() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry != null) {
            engine.fireEvent(WorkflowEventType.CANCELLED, playerId, entryId, entry);
            engine.removeEntry(playerId, dimension, entryId);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  查询
    // ──────────────────────────────────────────────────────────────────

    /**
     * 返回此工作流当前进度的不可变快照。
     *
     * @return 工作流状态，若条目已不存在则返回 {@link RtsWorkflowStatus#idle()}
     */
    public RtsWorkflowStatus getProgress() {
        RtsWorkflowEntry entry = resolveEntry();
        if (entry == null) {
            return RtsWorkflowStatus.idle();
        }
        return entry.snapshot();
    }

    // ──────────────────────────────────────────────────────────────────
    //  内部方法
    // ──────────────────────────────────────────────────────────────────

    private RtsWorkflowEntry resolveEntry() {
        return engine.findEntry(playerId, dimension, entryId);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof RtsWorkflowToken other)) return false;
        return this.playerId.equals(other.playerId) && this.entryId == other.entryId;
    }

    @Override
    public int hashCode() {
        return 31 * playerId.hashCode() + entryId;
    }

    @Override
    public String toString() {
        return "RtsWorkflowToken{player=" + playerId + ", entry=" + entryId + "}";
    }
}
