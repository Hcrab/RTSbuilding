package com.rtsbuilding.rtsbuilding.server.workflow.core;

import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventListener;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 工作流引擎核心接口——所有工作流生命周期的统一入口。
 *
 * <p>本接口定义了启动、跟踪和查询工作流的契约。所有消费者<b>必须</b>通过本接口操作，
 * 而非直接访问内部状态。推荐的使用模式如下：</p>
 *
 * <ol>
 *   <li>通过某个 {@code start*} 方法获取 {@link RtsWorkflowToken}。</li>
 *   <li>在处理过程中，调用令牌的方法（{@link RtsWorkflowToken#markProgress()} 等）。</li>
 *   <li>完成后，调用 {@link RtsWorkflowToken#complete()} 或
 *       {@link RtsWorkflowToken#cancel()}。</li>
 *   <li>从其他代码位置，通过之前保存的 {@code entryId} 使用
 *       {@link #from(ServerPlayer, int)} 重建令牌。</li>
 * </ol>
 *
 * <p>所有工作流状态由引擎实现内部管理。消费者不要直接触碰
 * {@code RtsStorageSession.workflow}。</p>
 */
public interface IWorkflowEngine {

    // ======================================================================
    //  工作流启动器
    // ======================================================================

    /**
     * 启动一个指定类型和优先级的新工作流。
     *
     * @param player      服务端玩家
     * @param type        工作流类型
     * @param priority    优先级
     * @param totalBlocks 待处理的总方块数，未知则为 0
     * @return 表示该工作流的令牌，若已达上限则返回空
     */
    Optional<RtsWorkflowToken> start(
            ServerPlayer player,
            RtsWorkflowType type, RtsWorkflowPriority priority, int totalBlocks);

    // ======================================================================
    //  令牌重建
    // ======================================================================

    /**
     * 根据不可变的条目 ID 为已有的工作流条目重建令牌。
     * 当条目 ID 已存储在任务记录中，需要从其他代码位置更新时使用。
     *
     * @return 令牌，若条目已不存在则返回空
     */
    Optional<RtsWorkflowToken> from(ServerPlayer player, int entryId);

    /**
     * 为最近的活动（非挂起）工作流条目创建令牌。
     * 尽力而为；建议优先使用 {@link #from(ServerPlayer, int)} 配合已存储的 entryId。
     *
     * @return 令牌，若没有活动工作流则返回空
     */
    Optional<RtsWorkflowToken> lastActive(ServerPlayer player);

    // ======================================================================
    //  事件订阅
    // ======================================================================

    /**
     * 注册一个工作流生命周期事件监听器。
     * 监听器会收到所有玩家工作流的事件通知。
     */
    void addListener(WorkflowEventListener listener);

    /** 移除之前注册的监听器。 */
    void removeListener(WorkflowEventListener listener);

    // ======================================================================
    //  查询
    // ======================================================================

    /**
     * 返回令牌对应工作流的结构化进度数据。
     *
     * @return 工作流状态，若无效则返回 {@link RtsWorkflowStatus#idle()}
     */
    RtsWorkflowStatus getProgress(RtsWorkflowToken token);

    /**
     * 返回指定条目 ID 对应的结构化进度数据。
     *
     * @return 工作流状态，若未找到则返回 {@link RtsWorkflowStatus#idle()}
     */
    RtsWorkflowStatus getProgress(ServerPlayer player, int entryId);

    /** 返回玩家所有已占用工作流条目的进度数据。 */
    List<RtsWorkflowStatus> getAllProgress(ServerPlayer player);

    /** 返回玩家是否有任何活动（非挂起）的工作流。 */
    boolean hasActiveWorkflow(ServerPlayer player);

    /** 返回玩家的活动工作流条目数。 */
    int activeWorkflowCount(ServerPlayer player);

    /** 返回已占用的槽位总数（活动 + 挂起）。 */
    int occupiedSlotCount(ServerPlayer player);

    /** 返回是否所有工作流槽位均已占用。 */
    boolean isFull(ServerPlayer player);

    /**
     * 设置工作流条目的额外持久化数据（工作流类型特定的上下文）。
     *
     * @param player   拥有者玩家
     * @param entryId  目标条目 ID
     * @param data     额外数据（可为 null 以清除）
     */
    void setWorkflowExtraData(ServerPlayer player, int entryId, @Nullable CompoundTag data);

    /**
     * 返回工作流条目的额外持久化数据。
     *
     * @return 额外数据，不存在时返回 null
     */
    @Nullable CompoundTag getWorkflowExtraData(ServerPlayer player, int entryId);

    // ======================================================================
    //  管理操作
    // ======================================================================

    /**
     * 根据条目 ID 删除工作流。通知客户端并触发
     * {@link WorkflowEventType#CANCELLED} 事件。
     */
    void deleteWorkflow(ServerPlayer player, int entryId);

    /** 取消当前维度中指定玩家的所有工作流。 */
    void cancelAll(ServerPlayer player);

    // ======================================================================
    //  世界切换清理
    // ======================================================================

    /**
     * 移除玩家在所有维度的工作流数据。
     * 在玩家登出时调用，防止过时的工作流条目在玩家加入不同世界（存档）时残留。
     *
     * @param playerId 玩家的 UUID
     */
    void clearPlayerData(UUID playerId);

    /**
     * 移除所有玩家的工作流数据。
     * 在服务器停止时调用，完全重置引擎状态。
     */
    void clearAllData();

    // ======================================================================
    //  暂停/恢复——逐条目阀门
    // ======================================================================

    /**
     * 返回指定工作流条目是否处于暂停状态。
     *
     * @param playerId  玩家的 UUID
     * @param dimension 工作流创建时的维度
     * @param entryId   不可变的工作流条目 ID
     */
    boolean isEntryPaused(UUID playerId, ResourceKey<Level> dimension, int entryId);

    /**
     * 返回指定工作流条目是否处于挂起（等待物品）状态。
     *
     * @param playerId  玩家的 UUID
     * @param dimension 工作流创建时的维度
     * @param entryId   不可变的工作流条目 ID
     */
    boolean isEntrySuspended(UUID playerId, ResourceKey<Level> dimension, int entryId);
}
