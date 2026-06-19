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
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowTimeoutService;
import com.rtsbuilding.rtsbuilding.server.workflow.service.WorkflowPersistenceService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.time.Duration;
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
    private final Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> playerSlots = new ConcurrentHashMap<>();

    /**
     * 追踪每个 UUID 最近的有效 {@link ServerPlayer} 引用。
     * 每次调用 {@code start()}、{@code from()} 和 {@code lastActive()} 时更新。
     */
    private final Map<UUID, ServerPlayer> playerRefs = new ConcurrentHashMap<>();

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
        void restore(ServerPlayer player, RtsWorkflowEntry entry);
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
            timeoutService = new RtsWorkflowTimeoutService(playerSlots, playerRefs, eventBus, syncService);
            timeoutService.start(checkInterval, maxIdleTime);
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
    RtsWorkflowEntry findEntry(UUID playerId, ResourceKey<Level> dimension, int entryId) {
        RtsWorkflowSlotManager slots = getSlots(playerId, dimension);
        if (slots == null) return null;
        return slots.findEntryById(entryId);
    }

    /**
     * 根据 {@link ServerPlayer} 和条目 ID 查找条目。
     * 公开方法——供 {@link com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipelineRegistry}
     * 等跨包组件使用，避免重复的 engine.from() → token.isPaused() 两次独立 lookup。
     */
    @Nullable
    public RtsWorkflowEntry findEntryByPlayer(ServerPlayer player, int entryId) {
        if (player == null) return null;
        return findEntry(player.getUUID(), player.level().dimension(), entryId);
    }

    /**
     * 根据玩家 UUID、维度和条目 ID 查找条目，无需 {@link ServerPlayer} 对象。
     * <p>供调用方已有 UUID 和维度时的 hot path 使用，避免 {@code player.level().dimension()} 额外开销。
     */
    @Nullable
    public RtsWorkflowEntry findEntryByPlayer(UUID playerId, ResourceKey<Level> dimension, int entryId) {
        if (playerId == null || dimension == null) return null;
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
    void removeEntry(UUID playerId, ResourceKey<Level> dimension, int entryId) {
        RtsWorkflowSlotManager slots = getSlots(playerId, dimension);
        if (slots == null) return;

        boolean removed = slots.removeEntryById(entryId);
        if (!removed) return;

        // 通过网络通知玩家（notifyPlayer 内部处理 idle 的情况）
        ServerPlayer player = findPlayerByUUID(playerId);
        if (player != null) {
            syncService.notifyPlayer(player, slots);
        }
    }

    /**
     * 向指定维度的玩家发送完整的工作流状态更新。
     * 包级私有——由 {@link RtsWorkflowToken} 调用。
     */
    void notifyPlayer(UUID playerId, ResourceKey<Level> dimension) {
        RtsWorkflowSlotManager slots = getSlots(playerId, dimension);
        if (slots == null) return;

        ServerPlayer player = findPlayerByUUID(playerId);
        if (player != null) {
            syncService.notifyPlayer(player, slots);
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
    public Optional<RtsWorkflowToken> start(ServerPlayer player,
                                            RtsWorkflowType type, RtsWorkflowPriority priority, int totalBlocks) {
        if (player == null || type == null) {
            return Optional.empty();
        }
        RtsWorkflowSlotManager slots = getOrCreateSlots(player);
        RtsWorkflowEntry entry = slots.addEntry(priority);
        if (entry == null) {
            String name = player.getGameProfile().getName();
            RtsbuildingMod.LOGGER.warn("[Workflow] {} 工作流已满 ({}), 拒绝新工作流 {}",
                    name, RtsWorkflowSlotManager.MAX_SLOTS, type);
            player.displayClientMessage(
                    Component.literal("§c工作流已满 (" + RtsWorkflowSlotManager.MAX_SLOTS
                            + "/" + RtsWorkflowSlotManager.MAX_SLOTS + "), 无法开始新的操作！"),
                    true);
            return Optional.empty();
        }
        entry.setType(type);
        entry.setTotalBlocks(totalBlocks);

        // 追踪玩家引用，供后续通知使用
        playerRefs.put(player.getUUID(), player);

        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowToken token = new RtsWorkflowToken(player.getUUID(), entry.id(), dimension, this);
        fireEvent(WorkflowEventType.STARTED, player.getUUID(), entry.id(), entry);
        syncService.notifyPlayer(player, slots);

        RtsbuildingMod.LOGGER.info("[Workflow] {} 开始工作流 #{}: {} (共 {} 方块)",
                player.getGameProfile().getName(), entry.id(), type, totalBlocks);
        return Optional.of(token);
    }

    // ──────────────────────────────────────────────────────────────────
    //  IWorkflowEngine — 令牌重建
    // ──────────────────────────────────────────────────────────────────

    @Override
    public Optional<RtsWorkflowToken> from(ServerPlayer player, int entryId) {
        if (player == null) return Optional.empty();
        playerRefs.putIfAbsent(player.getUUID(), player);
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        if (slots == null || slots.findEntryById(entryId) == null) {
            return Optional.empty();
        }
        return Optional.of(new RtsWorkflowToken(player.getUUID(), entryId, dimension, this));
    }

    @Override
    public Optional<RtsWorkflowToken> lastActive(ServerPlayer player) {
        if (player == null) return Optional.empty();
        playerRefs.putIfAbsent(player.getUUID(), player);
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        if (slots == null) return Optional.empty();
        RtsWorkflowEntry entry = slots.lastActive();
        if (entry == null) return Optional.empty();
        return Optional.of(new RtsWorkflowToken(player.getUUID(), entry.id(), dimension, this));
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
    public RtsWorkflowStatus getProgress(ServerPlayer player, int entryId) {
        if (player == null) return RtsWorkflowStatus.idle();
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        if (slots == null) return RtsWorkflowStatus.idle();
        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        if (entry == null || !entry.isOccupied()) return RtsWorkflowStatus.idle();
        return entry.snapshot();
    }

    @Override
    public List<RtsWorkflowStatus> getAllProgress(ServerPlayer player) {
        if (player == null) return List.of();
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        if (slots == null) return List.of();
        return slots.occupiedEntries().stream()
                .map(RtsWorkflowEntry::snapshot)
                .toList();
    }

    @Override
    public boolean hasActiveWorkflow(ServerPlayer player) {
        if (player == null) return false;
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        return slots != null && slots.hasActiveWorkflow();
    }

    @Override
    public int activeWorkflowCount(ServerPlayer player) {
        if (player == null) return 0;
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        return slots != null ? slots.activeCount() : 0;
    }

    @Override
    public int occupiedSlotCount(ServerPlayer player) {
        if (player == null) return 0;
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        return slots != null ? slots.occupiedCount() : 0;
    }

    @Override
    public boolean isFull(ServerPlayer player) {
        if (player == null) return false;
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
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
    public void firePipelineEvent(ServerPlayer player, int entryId, WorkflowEventType type) {
        if (player == null) return;
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        if (slots == null) return;
        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        if (entry == null) return;
        fireEvent(type, player.getUUID(), entryId, entry);
    }

    // ──────────────────────────────────────────────────────────────────
    //  IWorkflowEngine — 管理
    // ──────────────────────────────────────────────────────────────────

    // ──────────────────────────────────────────────────────────────────
    //  暂停/恢复——逐条目阀门
    // ──────────────────────────────────────────────────────────────────

    @Override
    public boolean isEntryPaused(UUID playerId, ResourceKey<Level> dimension, int entryId) {
        if (playerId == null || dimension == null) return false;
        RtsWorkflowSlotManager slots = getSlots(playerId, dimension);
        if (slots == null) return false;
        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        return entry != null && entry.paused();
    }

    @Override
    public boolean isEntrySuspended(UUID playerId, ResourceKey<Level> dimension, int entryId) {
        if (playerId == null || dimension == null) return false;
        RtsWorkflowSlotManager slots = getSlots(playerId, dimension);
        if (slots == null) return false;
        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        return entry != null && entry.suspended();
    }

    // ──────────────────────────────────────────────────────────────────
    //  工作流条目额外数据（类型特定持久化）
    // ──────────────────────────────────────────────────────────────────

    @Override
    public void setWorkflowExtraData(ServerPlayer player, int entryId, @Nullable CompoundTag data) {
        if (player == null) return;
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        if (slots == null) return;
        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        if (entry == null) return;
        entry.setExtraData(data);
    }

    @Override
    public @Nullable CompoundTag getWorkflowExtraData(ServerPlayer player, int entryId) {
        if (player == null) return null;
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
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
        Map<ResourceKey<Level>, RtsWorkflowSlotManager> dimMap = playerSlots.get(playerId);
        if (dimMap == null) return;

        for (Map.Entry<ResourceKey<Level>, RtsWorkflowSlotManager> dimEntry : dimMap.entrySet()) {
            ResourceKey<Level> dimension = dimEntry.getKey();
            RtsWorkflowSlotManager slots = dimEntry.getValue();
            boolean anyChanged = false;

            for (RtsWorkflowEntry entry : slots.occupiedEntries()) {
                if (!entry.suspended() && !entry.paused()) {
                    entry.setPaused(true);
                    fireEvent(WorkflowEventType.PAUSED, playerId, entry.id(), entry);
                    anyChanged = true;
                }
            }

            if (anyChanged && notify) {
                ServerPlayer player = findPlayerByUUID(playerId);
                if (player != null) {
                    syncService.notifyPlayer(player, slots);
                }
            }
        }
    }

    @Override
    public void deleteWorkflow(ServerPlayer player, int entryId) {
        if (player == null) return;
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        if (slots == null) return;

        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        if (entry == null || !entry.isOccupied()) return;

        RtsbuildingMod.LOGGER.info("[Workflow] {} 删除工作流 #{}: {}",
                player.getGameProfile().getName(), entry.id(), entry.type());

        fireEvent(WorkflowEventType.CANCELLED, player.getUUID(), entryId, entry);
        slots.removeEntryById(entryId);

        if (slots.occupiedCount() > 0) {
            syncService.notifyPlayer(player, slots);
        } else {
            syncService.sendIdle(player);
        }
    }

    @Override
    public void cancelAll(ServerPlayer player) {
        if (player == null) return;
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), dimension);
        if (slots == null) return;

        for (RtsWorkflowEntry entry : slots.occupiedEntries()) {
            fireEvent(WorkflowEventType.CANCELLED, player.getUUID(), entry.id(), entry);
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
    public void loadPlayerFromStore(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) return;
        UUID playerId = player.getUUID();

        Map<ResourceKey<Level>, RtsWorkflowSlotManager> loaded =
                WorkflowPersistenceService.getInstance().loadPlayerFromStore(server, playerId);

        if (loaded.isEmpty()) return;

        // 将加载的槽位管理器合并到引擎的内存映射中
        Map<ResourceKey<Level>, RtsWorkflowSlotManager> dimMap = playerSlots
                .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

        for (Map.Entry<ResourceKey<Level>, RtsWorkflowSlotManager> entry : loaded.entrySet()) {
            ResourceKey<Level> dimension = entry.getKey();
            if (!dimMap.containsKey(dimension)) {
                dimMap.put(dimension, entry.getValue());
            }
        }

        // 通知客户端，使 UI 显示恢复的条目
        ResourceKey<Level> currentDim = player.level().dimension();
        RtsWorkflowSlotManager currentSlots = getSlots(playerId, currentDim);
        if (currentSlots != null && currentSlots.occupiedCount() > 0) {
            syncService.notifyPlayer(player, currentSlots);
        }

        // 尝试恢复蓝图工作流的 Tick 管道
        if (blueprintRestoreHandler != null) {
            int restored = 0;
            for (Map.Entry<ResourceKey<Level>, RtsWorkflowSlotManager> entry : loaded.entrySet()) {
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

    // ──────────────────────────────────────────────────────────────────
    //  内部辅助方法
    // ──────────────────────────────────────────────────────────────────

    /**
     * 获取或创建指定玩家在当前维度的槽位管理器。
     */
    private RtsWorkflowSlotManager getOrCreateSlots(ServerPlayer player) {
        playerRefs.put(player.getUUID(), player);
        ResourceKey<Level> dimension = player.level().dimension();
        return playerSlots
                .computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(dimension, k -> new RtsWorkflowSlotManager());
    }

    /**
     * 获取指定玩家和维度的槽位管理器，若不存在则返回 {@code null}。
     */
    @Nullable
    private RtsWorkflowSlotManager getSlots(UUID playerId, ResourceKey<Level> dimension) {
        Map<ResourceKey<Level>, RtsWorkflowSlotManager> dimMap = playerSlots.get(playerId);
        if (dimMap == null) return null;
        return dimMap.get(dimension);
    }

    /**
     * 根据 UUID 查找 ServerPlayer。
     * 先检查缓存的玩家引用，然后回退到扫描 Minecraft 服务器的玩家列表。
     * 如果玩家离线或未找到则返回 null。
     */
    @Nullable
    private ServerPlayer findPlayerByUUID(UUID playerId) {
        // 先检查缓存的引用
        ServerPlayer cached = playerRefs.get(playerId);
        if (cached != null && cached.level() != null && !cached.level().isClientSide()) {
            return cached;
        }
        // 回退：扫描服务器的玩家列表
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
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
