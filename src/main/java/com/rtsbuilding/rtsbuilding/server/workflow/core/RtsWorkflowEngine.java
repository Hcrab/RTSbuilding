package com.rtsbuilding.rtsbuilding.server.workflow.core;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.workflow.event.RtsWorkflowEventBus;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEvent;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventListener;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSlotManager;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSyncService;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowTimeoutService;
import com.rtsbuilding.rtsbuilding.server.workflow.service.WorkflowPersistenceService;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流引擎核心——{@link IWorkflowEngine} 的唯一实现。
 *
 * <p>本引擎使用每个玩家的 {@link RtsWorkflowSlotManager} 在内部管理工作流状态。
 * 所有生命周期操作都通过本引擎创建的 {@link RtsWorkflowToken} 实例进行。
 * 事件通过事件总线分发到已注册的监听器。</p>
 *
 * <p>引擎设计为顶层单例服务。通过 {@link #getInstance()} 获取实例。</p>
 *
 * <h3>关键设计决策</h3>
 * <ul>
 *   <li><b>仅通过令牌的消费者 API：</b>外部代码绝不直接触碰条目。
 *       所有交互通过 {@link RtsWorkflowToken} 进行。</li>
 *   <li><b>事件驱动：</b>子系统通过响应工作流生命周期事件来工作，
 *       而非通过显式回调串联。</li>
 *   <li><b>基于条目 ID：</b>所有内部查找使用不可变的条目 ID，
 *       而非位置索引（索引会在删除时偏移）。</li>
 *   <li><b>超时安全：</b>{@link RtsWorkflowTimeoutService} 定期清理
 *       过时条目以防止槽位耗尽。</li>
 * </ul>
 */
public final class RtsWorkflowEngine implements IWorkflowEngine {

    private static final RtsWorkflowEngine INSTANCE = new RtsWorkflowEngine();

    // ──────────────────────────────────────────────────────────────────
    //  状态
    // ──────────────────────────────────────────────────────────────────

    /** 每个玩家每个维度的槽位管理器，懒加载创建。 */
    private final Map<UUID, Map<Integer, RtsWorkflowSlotManager>> playerSlots = new ConcurrentHashMap<>();

    /**
     * 追踪每个 UUID 最近的有效 {@link ServerPlayer} 引用。
     * 每次调用 {@code start()}、{@code from()} 和 {@code lastActive()} 时更新。
     */
    private final Map<UUID, EntityPlayerMP> playerRefs = new ConcurrentHashMap<>();

    /** 生命周期事件的事件总线。 */
    private final RtsWorkflowEventBus eventBus = new RtsWorkflowEventBus();

    /** 网络同步服务。 */
    private final RtsWorkflowSyncService syncService = new RtsWorkflowSyncService();

    /** 可选的超时服务（单独启动）。 */
    private RtsWorkflowTimeoutService timeoutService;

    /**
     * 蓝图工作流重载处理器——服务端重启后自动恢复蓝图的 Tick 管道。
     * 由蓝图模块在初始化时注册，避免引擎直接依赖蓝图类型。
     */
    @Nullable
    private static BlueprintRestoreHandler blueprintRestoreHandler;

    /** 注册蓝图重载处理器。 */
    public static void setBlueprintRestoreHandler(@Nullable BlueprintRestoreHandler handler) {
        blueprintRestoreHandler = handler;
    }

    @FunctionalInterface
    public interface BlueprintRestoreHandler {
        void restore(EntityPlayerMP player, RtsWorkflowEntry entry);
    }

    // ──────────────────────────────────────────────────────────────────
    //  单例
    // ──────────────────────────────────────────────────────────────────

    private RtsWorkflowEngine() {
    }

    /** 返回单例引擎实例。 */
    public static RtsWorkflowEngine getInstance() {
        return INSTANCE;
    }

    /**
     * 启动超时服务。在模组初始化期间调用一次。
     *
     * @param checkInterval 扫描过期工作流的间隔
     * @param maxIdleTime   清理前的最大空闲时间
     */
    public void startTimeoutService(Duration checkInterval, Duration maxIdleTime) {
        if (timeoutService == null) {
            timeoutService = new RtsWorkflowTimeoutService(playerSlots, eventBus);
            timeoutService.start(checkInterval, maxIdleTime);
        }
    }

    /**
     * 由服务端全局 Tick 编排器调用。超时服务未显式启动时保持 O(1) 空操作，
     * 不会在现有世界中凭空启用新的清理行为。
     */
    public void tickTimeoutService(MinecraftServer server, long gameTime) {
        RtsWorkflowTimeoutService service = timeoutService;
        if (service != null) {
            service.tick(server, gameTime);
        }
    }

    /**
     * 停止超时服务。在模组关闭时调用。
     */
    public void stopTimeoutService() {
        if (timeoutService != null) {
            timeoutService.stop();
            timeoutService = null;
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  内部 API（包级私有，由 RtsWorkflowToken 调用）
    // ──────────────────────────────────────────────────────────────────

    /**
     * 根据玩家 UUID、维度和条目 ID 查找条目。
     * 包级私有——由 {@link RtsWorkflowToken} 调用。
     */
    @Nullable
    RtsWorkflowEntry findEntry(UUID playerId, int dimension, int entryId) {
        RtsWorkflowSlotManager slots = getSlots(playerId, dimension);
        if (slots == null) return null;
        return slots.findEntryById(entryId);
    }

    /**
     * 根据 {@link ServerPlayer} 和条目 ID 查找条目。
     * 公开方法——供统一 Task Engine
     * 等跨包组件使用，避免重复的 engine.from() → token.isPaused() 两次独立 lookup。
     */
    @Nullable
    public RtsWorkflowEntry findEntryByPlayer(EntityPlayerMP player, int entryId) {
        if (player == null) return null;
        return findEntry(player.getUniqueID(), player.dimension, entryId);
    }

    /**
     * 根据玩家 UUID、维度和条目 ID 查找条目，无需 {@link ServerPlayer} 对象。
     * <p>供调用方已有 UUID 和维度时的 hot path 使用，避免 {@code player.level().dimension()} 额外开销。
     */
    @Nullable
    public RtsWorkflowEntry findEntryByPlayer(UUID playerId, int dimension, int entryId) {
        if (playerId == null) return null;
        return findEntry(playerId, dimension, entryId);
    }

    /**
     * 根据玩家 UUID、维度和条目 ID 移除条目，然后通知客户端并触发事件。
     * 包级私有——由 {@link RtsWorkflowToken} 调用。
     *
     * <p>使用 {@link RtsWorkflowSlotManager#removeEntryById(int)} 在一次遍历中
     * 完成查找和移除，避免额外的索引查找。
     * 当没有剩余条目时，{@link RtsWorkflowSyncService#notifyPlayer} 内部会
     * 自动分发 {@code idle()}，因此调用者无需提前检查 {@code occupiedCount()}。</p>
     */
    void removeEntry(UUID playerId, int dimension, int entryId) {
        RtsWorkflowSlotManager slots = getSlots(playerId, dimension);
        if (slots == null) return;

        boolean removed = slots.removeEntryById(entryId);
        if (!removed) return;

        // 通过网络通知玩家（notifyPlayer 内部处理 idle 的情况）
        RtsEffectAccumulator.INSTANCE.markWorkflow(playerId, dimension);
    }

    /**
     * 向指定维度的玩家发送完整的工作流状态更新。
     * 包级私有——由 {@link RtsWorkflowToken} 调用。
     */
    void notifyPlayer(UUID playerId, int dimension) {
        if (getSlots(playerId, dimension) != null) {
            RtsEffectAccumulator.INSTANCE.markWorkflow(playerId, dimension);
        }
    }

    /**
     * 仅由 Tick 末副作用提交器调用。普通业务代码应调用 token 方法并留下脏标记，
     * 避免同一 Tick 重复构建和发送完整工作流快照。
     */
    public void flushPlayerNow(UUID playerId, int dimension) {
        RtsWorkflowSlotManager slots = getSlots(playerId, dimension);
        EntityPlayerMP player = findPlayerByUUID(playerId);
        if (player == null) {
            return;
        }
        if (slots != null) {
            syncService.notifyPlayer(player, slots);
        } else {
            // 最后一条记录被超时清理后，服务端容器已经不存在；仍要显式清空客户端旧投影。
            syncService.sendIdle(player);
        }
    }

    /**
     * 触发生命周期事件。
     * 包级私有——由 {@link RtsWorkflowToken} 调用。
     */
    void fireEvent(WorkflowEventType type, UUID playerId, int entryId, RtsWorkflowEntry entry) {
        eventBus.fire(new WorkflowEvent(type, playerId, entryId, entry.snapshot()));
    }

    // ──────────────────────────────────────────────────────────────────
    //  IWorkflowEngine — 启动器
    // ──────────────────────────────────────────────────────────────────

    @Override
    public Optional<RtsWorkflowToken> start(EntityPlayerMP player,
                                            RtsWorkflowType type, RtsWorkflowPriority priority, int totalBlocks) {
        if (player == null || type == null) {
            return Optional.empty();
        }
        RtsWorkflowSlotManager slots = getOrCreateSlots(player);
        int dimension = player.dimension;
        if (slots.isFull()) {
            RtsWorkflowEntry replaced = slots.removeOldestReplaceableEntry();
            if (replaced != null) {
                com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                        .cancelWorkflowTask(player, replaced.id());
                fireEvent(WorkflowEventType.CANCELLED, player.getUniqueID(), replaced.id(), replaced);
                RtsbuildingMod.LOGGER.debug("[Workflow] {} 自动替换可覆盖工作流 #{}: {}",
                        player.getGameProfile().getName(), replaced.id(), replaced.type());
            }
        }
        RtsWorkflowEntry entry;
        do {
            entry = slots.addEntry(priority);
            if (entry == null
                    || !com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                            .hasDurableTaskForWorkflow(player, entry.id())) {
                break;
            }
            // 两套存档的 nextId 可能在旧世界升级后短暂错位。这里尚未广播 STARTED，
            // 可以无副作用地跳过被 durable task 占用的编号。
            slots.removeEntryById(entry.id());
        } while (true);
        if (entry == null) {
            String name = player.getGameProfile().getName();
            RtsbuildingMod.LOGGER.debug("[Workflow] {} 工作流已满且没有可覆盖条目 ({}), 拒绝新工作流 {}",
                    name, RtsWorkflowSlotManager.MAX_SLOTS, type);
            player.sendStatusMessage(
                    new TextComponentTranslation("message.rtsbuilding.workflow.full_protected"), true);
            return Optional.empty();
        }
        entry.setType(type);
        entry.setTotalBlocks(totalBlocks);

        // 追踪玩家引用，供后续通知使用
        playerRefs.put(player.getUniqueID(), player);

        RtsWorkflowToken token = new RtsWorkflowToken(player.getUniqueID(), entry.id(), dimension, this);
        fireEvent(WorkflowEventType.STARTED, player.getUniqueID(), entry.id(), entry);
        RtsEffectAccumulator.INSTANCE.markWorkflow(player.getUniqueID(), dimension);

        RtsbuildingMod.LOGGER.debug("[Workflow] {} 开始工作流 #{}: {} (共 {} 方块)",
                player.getGameProfile().getName(), entry.id(), type, totalBlocks);
        return Optional.of(token);
    }

    /**
     * 从持久任务库重建缺失的工作流显示条目。
     *
     * <p>TaskStore 始终是真实执行权威；这里仅恢复玩家可见、可暂停、可保护和可取消的
     * UI 投影。恢复时保留原 workflowEntryId，避免把控制操作接到另一条任务上。</p>
     */
    public Optional<RtsWorkflowToken> restoreDurableProjection(
            EntityPlayerMP player,
            int entryId,
            RtsWorkflowType type,
            int totalBlocks,
            int completedBlocks,
            int failedBlocks) {
        if (player == null || entryId < 0 || type == null) return Optional.empty();
        int dimension = player.dimension;
        RtsWorkflowSlotManager slots = getOrCreateSlots(player);
        RtsWorkflowEntry existing = slots.findEntryById(entryId);
        if (existing != null) {
            return Optional.of(new RtsWorkflowToken(player.getUniqueID(), entryId, dimension, this));
        }

        // 旧持久任务的恢复不能反过来淘汰已经可见的新工作流。
        // 满槽时由 Task Engine 终止这个未显示、因而也不可能被玩家钉住的旧任务。
        if (slots.isFull()) return Optional.empty();

        RtsWorkflowEntry restored = new RtsWorkflowEntry(entryId);
        restored.setPriority(RtsWorkflowPriority.NORMAL);
        restored.setType(type);
        restored.setTotalBlocks(totalBlocks);
        restored.setCompletedBlocks(completedBlocks);
        restored.addFailedBlocks(Math.max(0, failedBlocks));
        if (!slots.addRestoredEntry(restored)) return Optional.empty();

        playerRefs.put(player.getUniqueID(), player);
        RtsEffectAccumulator.INSTANCE.markWorkflow(player.getUniqueID(), dimension);
        RtsbuildingMod.LOGGER.info("[Workflow] 为 {} 恢复持久任务投影 #{}: {}",
                player.getGameProfile().getName(), entryId, type);
        return Optional.of(new RtsWorkflowToken(player.getUniqueID(), entryId, dimension, this));
    }

    // ──────────────────────────────────────────────────────────────────
    //  IWorkflowEngine — 令牌重建
    // ──────────────────────────────────────────────────────────────────

    @Override
    public Optional<RtsWorkflowToken> from(EntityPlayerMP player, int entryId) {
        if (player == null) return Optional.empty();
        playerRefs.putIfAbsent(player.getUniqueID(), player);
        int dimension = player.dimension;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), dimension);
        if (slots == null || slots.findEntryById(entryId) == null) {
            return Optional.empty();
        }
        return Optional.of(new RtsWorkflowToken(player.getUniqueID(), entryId, dimension, this));
    }

    /**
     * 在每玩家最多八个槽位中精确查找 durable 蓝图薄投影。
     * 该查询只读 {@code durable_task_id}，不会把旧 heavy extraData 误认成新执行许可。
     */
    public Optional<RtsWorkflowToken> findDurableBlueprintProjection(EntityPlayerMP player, UUID taskId) {
        if (player == null || taskId == null) return Optional.empty();
        playerRefs.putIfAbsent(player.getUniqueID(), player);
        int dimension = player.dimension;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), dimension);
        if (slots == null) return Optional.empty();
        for (RtsWorkflowEntry entry : slots.allEntries()) {
            NBTTagCompound extra = entry.getExtraData();
            if (entry.type() == RtsWorkflowType.BLUEPRINT_BUILD && extra != null
                    && extra.hasUniqueId("durable_task_id")
                    && taskId.equals(com.rtsbuilding.rtsbuilding.server.task.persistence.NbtCompat
                            .getUuid(extra, "durable_task_id"))) {
                return Optional.of(new RtsWorkflowToken(player.getUniqueID(), entry.id(), dimension, this));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<RtsWorkflowToken> lastActive(EntityPlayerMP player) {
        if (player == null) return Optional.empty();
        playerRefs.putIfAbsent(player.getUniqueID(), player);
        int dimension = player.dimension;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), dimension);
        if (slots == null) return Optional.empty();
        RtsWorkflowEntry entry = slots.lastActive();
        if (entry == null) return Optional.empty();
        return Optional.of(new RtsWorkflowToken(player.getUniqueID(), entry.id(), dimension, this));
    }

    // ──────────────────────────────────────────────────────────────────
    //  IWorkflowEngine — 事件订阅
    // ──────────────────────────────────────────────────────────────────

    @Override
    public void addListener(WorkflowEventListener listener) {
        eventBus.addListener(listener);
    }

    @Override
    public void removeListener(WorkflowEventListener listener) {
        eventBus.removeListener(listener);
    }

    // ──────────────────────────────────────────────────────────────────
    //  IWorkflowEngine — 查询
    // ──────────────────────────────────────────────────────────────────

    @Override
    public RtsWorkflowStatus getProgress(RtsWorkflowToken token) {
        return token.getProgress();
    }

    @Override
    public RtsWorkflowStatus getProgress(EntityPlayerMP player, int entryId) {
        if (player == null) return RtsWorkflowStatus.idle();
        int dimension = player.dimension;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), dimension);
        if (slots == null) return RtsWorkflowStatus.idle();
        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        if (entry == null || !entry.isOccupied()) return RtsWorkflowStatus.idle();
        return entry.snapshot();
    }

    @Override
    public List<RtsWorkflowStatus> getAllProgress(EntityPlayerMP player) {
        if (player == null) return java.util.Collections.emptyList();
        int dimension = player.dimension;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), dimension);
        if (slots == null) return java.util.Collections.emptyList();
        List<RtsWorkflowStatus> statuses = new ArrayList<RtsWorkflowStatus>();
        for (RtsWorkflowEntry entry : slots.occupiedEntries()) statuses.add(entry.snapshot());
        return java.util.Collections.unmodifiableList(statuses);
    }

    @Override
    public boolean hasActiveWorkflow(EntityPlayerMP player) {
        if (player == null) return false;
        int dimension = player.dimension;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), dimension);
        return slots != null && slots.hasActiveWorkflow();
    }

    @Override
    public int activeWorkflowCount(EntityPlayerMP player) {
        if (player == null) return 0;
        int dimension = player.dimension;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), dimension);
        return slots != null ? slots.activeCount() : 0;
    }

    @Override
    public int occupiedSlotCount(EntityPlayerMP player) {
        if (player == null) return 0;
        int dimension = player.dimension;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), dimension);
        return slots != null ? slots.occupiedCount() : 0;
    }

    @Override
    public boolean isFull(EntityPlayerMP player) {
        if (player == null) return false;
        int dimension = player.dimension;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), dimension);
        return slots != null && slots.isFull();
    }

    // ──────────────────────────────────────────────────────────────────
    //  管道集成——触发事件但不修改条目
    // ──────────────────────────────────────────────────────────────────

    /**
     * 为已有的工作流条目触发生命周期事件，但不修改条目本身。
     *
     * <p>由管道系统使用，在管道的同步阶段完成时通知监听器
     *（成功 → {@link WorkflowEventType#SYNC_PHASE_COMPLETED}
     * 或失败 → {@link WorkflowEventType#CANCELLED}）。
     * 与调用 {@link RtsWorkflowToken#complete()} 或
     * {@link RtsWorkflowToken#cancel()} 不同，本方法<b>不会</b>
     * 移除条目，因此异步作业（挖掘批次、放置任务等）可以在管道触发
     * SYNC_PHASE_COMPLETED 后继续执行。</p>
     *
     * @param player  工作流的拥有者玩家
     * @param entryId 不可变的条目 ID
     * @param type    事件类型（通常为 {@link WorkflowEventType#SYNC_PHASE_COMPLETED}
     *                或 {@link WorkflowEventType#CANCELLED}）
     */
    public void firePipelineEvent(EntityPlayerMP player, int entryId, WorkflowEventType type) {
        if (player == null) return;
        int dimension = player.dimension;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), dimension);
        if (slots == null) return;
        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        if (entry == null) return;
        fireEvent(type, player.getUniqueID(), entryId, entry);
    }

    // ──────────────────────────────────────────────────────────────────
    //  IWorkflowEngine — 管理
    // ──────────────────────────────────────────────────────────────────

    // ──────────────────────────────────────────────────────────────────
    //  暂停/恢复——逐条目阀门
    // ──────────────────────────────────────────────────────────────────

    @Override
    public boolean isEntryPaused(UUID playerId, int dimension, int entryId) {
        if (playerId == null) return false;
        RtsWorkflowSlotManager slots = getSlots(playerId, dimension);
        if (slots == null) return false;
        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        return entry != null && entry.paused();
    }

    @Override
    public boolean isEntrySuspended(UUID playerId, int dimension, int entryId) {
        if (playerId == null) return false;
        RtsWorkflowSlotManager slots = getSlots(playerId, dimension);
        if (slots == null) return false;
        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        return entry != null && entry.suspended();
    }

    // ──────────────────────────────────────────────────────────────────
    //  工作流条目额外数据（类型特定持久化）
    // ──────────────────────────────────────────────────────────────────

    @Override
    public void setWorkflowExtraData(EntityPlayerMP player, int entryId, @Nullable NBTTagCompound data) {
        if (player == null) return;
        int dimension = player.dimension;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), dimension);
        if (slots == null) return;
        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        if (entry == null) return;
        entry.setExtraData(data);
    }

    @Override
    public @Nullable NBTTagCompound getWorkflowExtraData(EntityPlayerMP player, int entryId) {
        if (player == null) return null;
        int dimension = player.dimension;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), dimension);
        if (slots == null) return null;
        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        return entry == null ? null : entry.getExtraData();
    }

    /**
     * 暂停指定玩家在所有维度中的所有活动（非挂起、非已暂停）工作流条目。
     *
     * <p>当玩家禁用 RTS 模式或未手动暂停线程就断开连接时使用。
     * 已暂停和已挂起的条目保持不变。</p>
     *
     * @param playerId 玩家的 UUID
     * @param notify   是否向玩家发送网络同步（离线时无操作）
     */
    public void pauseAllActive(UUID playerId, boolean notify) {
        if (playerId == null) return;
        Map<Integer, RtsWorkflowSlotManager> dimMap = playerSlots.get(playerId);
        if (dimMap == null) return;

        for (Map.Entry<Integer, RtsWorkflowSlotManager> dimEntry : dimMap.entrySet()) {
            int dimension = dimEntry.getKey();
            RtsWorkflowSlotManager slots = dimEntry.getValue();
            boolean anyChanged = false;

            for (RtsWorkflowEntry entry : slots.occupiedEntries()) {
                if (!entry.terminal() && !entry.suspended() && !entry.paused()) {
                    entry.setPaused(true);
                    fireEvent(WorkflowEventType.PAUSED, playerId, entry.id(), entry);
                    anyChanged = true;
                }
            }

            if (anyChanged && notify) {
                EntityPlayerMP player = findPlayerByUUID(playerId);
                if (player != null) {
                    syncService.notifyPlayer(player, slots);
                }
            }
        }
    }

    @Override
    public void deleteWorkflow(EntityPlayerMP player, int entryId) {
        if (player == null) return;
        int dimension = player.dimension;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), dimension);
        if (slots == null) {
            // 客户端可能仍显示服务端已经清理的旧条目；叉号同时承担一次权威状态对账。
            syncService.sendIdle(player);
            return;
        }

        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        if (entry == null || !entry.isOccupied()) {
            syncService.notifyPlayer(player, slots);
            return;
        }

        RtsbuildingMod.LOGGER.info("[Workflow] {} 删除工作流 #{}: {}",
                player.getGameProfile().getName(), entry.id(), entry.type());

        com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                .cancelWorkflowTask(player, entryId);
        fireEvent(WorkflowEventType.CANCELLED, player.getUniqueID(), entryId, entry);
        slots.removeEntryById(entryId);

        if (slots.occupiedCount() > 0) {
            syncService.notifyPlayer(player, slots);
        } else {
            syncService.sendIdle(player);
        }
    }

    @Override
    public void setWorkflowProtected(EntityPlayerMP player, int entryId, boolean protectedWorkflow) {
        if (player == null) return;
        int dimension = player.dimension;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), dimension);
        if (slots == null) return;

        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        if (entry == null || !entry.isOccupied()) return;

        entry.setProtectedWorkflow(protectedWorkflow);
        syncService.notifyPlayer(player, slots);

        player.sendStatusMessage(
                new TextComponentTranslation(protectedWorkflow
                        ? "message.rtsbuilding.workflow.protected"
                        : "message.rtsbuilding.workflow.replaceable"),
                true);
    }

    @Override
    public void cancelAll(EntityPlayerMP player) {
        if (player == null) return;
        int dimension = player.dimension;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), dimension);
        if (slots == null) return;

        for (RtsWorkflowEntry entry : slots.occupiedEntries()) {
            com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                    .cancelWorkflowTask(player, entry.id());
            fireEvent(WorkflowEventType.CANCELLED, player.getUniqueID(), entry.id(), entry);
        }
        slots.clear();
        syncService.sendIdle(player);
    }

    // ──────────────────────────────────────────────────────────────────
    //  IWorkflowEngine — 世界切换清理
    // ──────────────────────────────────────────────────────────────────

    @Override
    public void clearPlayerData(UUID playerId) {
        if (playerId == null) return;
        playerSlots.remove(playerId);
        playerRefs.remove(playerId);
    }

    @Override
    public void clearAllData() {
        int totalPlayers = playerSlots.size();
        playerSlots.clear();
        playerRefs.clear();
        RtsbuildingMod.LOGGER.info("[Workflow] 已清理所有工作流数据（共 {} 名玩家）", totalPlayers);
    }

    /**
     * 将持久化委托给 {@link WorkflowPersistenceService}。
     */
    public void saveAll(MinecraftServer server) {
        WorkflowPersistenceService.getInstance().saveAll(server, playerSlots);
    }

    /**
     * 从世界存档加载玩家工作流并合并到内存。
     */
    public void loadPlayerFromStore(MinecraftServer server, EntityPlayerMP player) {
        if (server == null || player == null) return;
        UUID playerId = player.getUniqueID();

        Map<Integer, RtsWorkflowSlotManager> loaded =
                WorkflowPersistenceService.getInstance().loadPlayerFromStore(server, playerId);

        if (loaded.isEmpty()) return;

        // 将加载的槽位管理器合并到引擎的内存映射中
        Map<Integer, RtsWorkflowSlotManager> dimMap = playerSlots
                .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

        for (Map.Entry<Integer, RtsWorkflowSlotManager> entry : loaded.entrySet()) {
            int dimension = entry.getKey();
            if (!dimMap.containsKey(dimension)) {
                dimMap.put(dimension, entry.getValue());
            }
        }

        // 通知客户端，使 UI 显示恢复的条目
        int currentDim = player.dimension;
        RtsWorkflowSlotManager currentSlots = getSlots(playerId, currentDim);
        if (currentSlots != null && currentSlots.occupiedCount() > 0) {
            syncService.notifyPlayer(player, currentSlots);
        }

        // 尝试恢复蓝图工作流的 Tick 管道
        if (blueprintRestoreHandler != null) {
            int restored = 0;
            for (Map.Entry<Integer, RtsWorkflowSlotManager> entry : loaded.entrySet()) {
                for (RtsWorkflowEntry we : entry.getValue().occupiedEntries()) {
                    if (we.type() == RtsWorkflowType.BLUEPRINT_BUILD && we.getExtraData() != null) {
                        blueprintRestoreHandler.restore(player, we);
                        restored++;
                    }
                }
            }
            if (restored > 0) {
                RtsbuildingMod.LOGGER.info("[Workflow] 已恢复 {} 个蓝图工作流管道", restored);
            }
        }

        RtsbuildingMod.LOGGER.info("[Workflow] 已从存储加载玩家 {} 的 {} 个工作流条目",
                loaded.values().stream().mapToInt(RtsWorkflowSlotManager::occupiedCount).sum(),
                playerId);
    }

    /**
     * 玩家重新上线时重置普通任务的无进展计时起点。
     *
     * <p>离线时间不算作“30 秒无进展”，否则玩家隔天进入存档时会在第一轮扫描中
     * 立刻失去尚未查看的等待任务。</p>
     */
    public void refreshPlayerIdleClocks(EntityPlayerMP player) {
        if (player == null) return;
        Map<Integer, RtsWorkflowSlotManager> dimensions =
                playerSlots.get(player.getUniqueID());
        if (dimensions == null) return;
        for (RtsWorkflowSlotManager slots : dimensions.values()) {
            for (RtsWorkflowEntry entry : slots.occupiedEntries()) {
                entry.touch();
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  内部辅助方法
    // ──────────────────────────────────────────────────────────────────

    /**
     * 获取或创建指定玩家在当前维度的槽位管理器。
     */
    private RtsWorkflowSlotManager getOrCreateSlots(EntityPlayerMP player) {
        playerRefs.put(player.getUniqueID(), player);
        int dimension = player.dimension;
        return playerSlots
                .computeIfAbsent(player.getUniqueID(), k -> new ConcurrentHashMap<Integer, RtsWorkflowSlotManager>())
                .computeIfAbsent(dimension, k -> new RtsWorkflowSlotManager());
    }

    /**
     * 获取指定玩家和维度的槽位管理器，若不存在则返回 {@code null}。
     */
    @Nullable
    private RtsWorkflowSlotManager getSlots(UUID playerId, int dimension) {
        Map<Integer, RtsWorkflowSlotManager> dimMap = playerSlots.get(playerId);
        if (dimMap == null) return null;
        return dimMap.get(dimension);
    }

    /**
     * 根据 UUID 查找 ServerPlayer。
     * 先检查缓存的玩家引用，然后回退到扫描 Minecraft 服务器的玩家列表。
     * 如果玩家离线或未找到则返回 null。
     */
    @Nullable
    private EntityPlayerMP findPlayerByUUID(UUID playerId) {
        // 先检查缓存的引用
        EntityPlayerMP cached = playerRefs.get(playerId);
        if (cached != null && cached.world != null && !cached.world.isRemote) {
            return cached;
        }
        // 回退：扫描服务器的玩家列表
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(playerId);
            if (online != null) {
                playerRefs.put(playerId, online);
                return online;
            }
        }
        // 玩家已离线——移除过期引用
        playerRefs.remove(playerId);
        return null;
    }
}
