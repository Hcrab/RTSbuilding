package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 挂起放置作业的 UI/恢复服务。
 *
 * <p>TaskStore 是挂起状态、游标和恢复策略的唯一权威；Session 不再保存放置任务副本。
 *
 * <p><b>核心职责：</b>范围放置作业的挂起/恢复（蓝图扫描已移至
 * {@link RtsBlueprintJobService}，进度刷新已移至 {@link RtsProgressRefresher}）。
 *
 * <p><b>设计特点：</b>
 * <ul>
 *   <li>扫描结果缓存于 {@link #SCAN_CACHE}（ConcurrentHashMap），由客户端消费后清除</li>
 *   <li>创造模式下可用物品数视为 {@code Integer.MAX_VALUE}，永不挂起</li>
 *   <li>支持跳过（skip）和覆盖（overwrite）两种重启策略</li>
 *   <li>工作流仅由 Task Engine 在 tick 末投影，不在恢复入口直接改写</li>
 * </ul>
 */
public final class RtsPendingPlacementService {

    /** Per-player cached scan results, cleared after resume/cancel. */
    private static final Map<UUID, PendingPlacementScanTicket> SCAN_CACHE = new ConcurrentHashMap<>();

    /** 扫描缓存 TTL：30 秒后自动过期，防止玩家扫描后从未消费导致内存泄漏。 */
    private static final long SCAN_CACHE_TTL_MS = 30_000L;

    /** 每玩家的扫描时间戳（与 SCAN_CACHE 同步更新），用于 TTL 检测。 */
    private static final Map<UUID, Long> SCAN_TIMESTAMPS = new ConcurrentHashMap<>();

    private RtsPendingPlacementService() {
    }

    /**
     * 清除指定玩家的扫描缓存条目，防止玩家断线后内存泄漏。
     * 在玩家登出事件中由 {@code RtsbuildingMod} 调用。
     * 蓝图节流缓存由 {@link RtsProgressRefresher#clearPlayerCache} 清理。
     */
    public static void clearPlayerScanCache(UUID playerUuid) {
        if (playerUuid != null) {
            SCAN_CACHE.remove(playerUuid);
            SCAN_TIMESTAMPS.remove(playerUuid);
        }
    }

    /**
     * 获取并清除缓存中指定玩家的搁置扫描结果。
     */
    public static RtsResumeScanResult consumeScanResult(ServerPlayer player) {
        if (player == null) return null;
        UUID uuid = player.getUUID();
        SCAN_TIMESTAMPS.remove(uuid);
        PendingPlacementScanTicket ticket = SCAN_CACHE.remove(uuid);
        return ticket == null ? null : ticket.result();
    }

    /**
     * 扫描指定玩家的挂起作业的剩余位置，返回扫描结果。
     * 根据 workflowEntryId 找到对应的作业。
     * 结果会被缓存到 SCAN_CACHE 中。
     *
     * @param workflowEntryId 目标工作流条目 ID
     * @return 扫描结果，如果没有匹配的挂起作业则返回 null
     */
    public static RtsResumeScanResult scanPendingJob(ServerPlayer player, RtsStorageSession session, int workflowEntryId) {
        if (player == null || session == null) {
            return null;
        }
        var engine = com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE;
        var view = engine.findWaitingPlacement(player, workflowEntryId);
        if (view == null) {
            return null;
        }
        var state = view.state();
        RtsPlacementBatch.PlaceBatchJob job = RtsPlacementBatch.restoreDetachedJob(
                state, player.registryAccess());

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        String itemId = job.itemId();
        if (itemId == null || itemId.isBlank()) {
            return null;
        }

        // 获取物品的显示名称
        Identifier id = Identifier.tryParse(itemId);
        String itemLabel = itemId;
        Block expectedBlock = null;
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            itemLabel = stack.getHoverName().getString();
            if (BuiltInRegistries.ITEM.getValue(id) instanceof net.minecraft.world.item.BlockItem blockItem) {
                expectedBlock = blockItem.getBlock();
            }
        }

        List<BlockPos> remaining = job.remainingPositions();
        int totalRemaining = remaining.size();
        int alreadyPlacedCount = 0;
        int conflictCount = 0;

        if (expectedBlock != null && expectedBlock != Blocks.AIR) {
            for (BlockPos pos : remaining) {
                BlockPos targetPos = job.quickBuild()
                        ? pos
                        : com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementExecutor
                                .placementTargetPos(player.level(), pos, job.face());
                if (!player.level().hasChunkAt(targetPos)) {
                    continue;
                }
                BlockState currentState = player.level().getBlockState(targetPos);
                Block currentBlock = currentState.getBlock();

                if (currentBlock == expectedBlock) {
                    alreadyPlacedCount++;
                } else if (!currentState.isAir() && !currentState.canBeReplaced()) {
                    conflictCount++;
                }
            }
        }

        ItemStack template = resolveTemplate(job.itemPrototype(), itemId);
        final ItemStack finalTemplate = template;
        long availableItems = 0;
        if (!finalTemplate.isEmpty()) {
            availableItems = ServiceRegistry.getInstance().transfer().countLinkedItemsMatching(player,
                    stack -> ItemStack.isSameItemSameComponents(stack, finalTemplate));
            availableItems = RtsCountUtil.saturatedAdd(availableItems,
                    RtsProgressRefresher.countItemsInPlayerInventory(player, finalTemplate));
        }

        if (player.isCreative()) {
            availableItems = Integer.MAX_VALUE;
        }

        int neededItems = totalRemaining - alreadyPlacedCount;
        long missingItems = Math.max(0, neededItems - availableItems);

        RtsResumeScanResult result = new RtsResumeScanResult(
                itemId, itemLabel,
                totalRemaining, alreadyPlacedCount, conflictCount,
                availableItems, neededItems, missingItems, workflowEntryId);

        UUID uuid = player.getUUID();
        SCAN_CACHE.put(uuid, new PendingPlacementScanTicket(
                view.taskId(), view.revision(), workflowEntryId, result));
        SCAN_TIMESTAMPS.put(uuid, System.currentTimeMillis());

        // 每次写入后触发一次过期清理，防止缓存无限膨胀
        evictStaleScanCacheEntries();

        return result;
    }

    /**
     * 尝试恢复指定玩家的所有挂起放置作业。
     * 把 TaskStore 中当前维度的等待任务切换为 QUEUED。
     *
     * @return 恢复的作业数量
     */
    public static int resumeAllPendingJobs(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) {
            return 0;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        var engine = com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE;
        int count = engine.resumeAllWaitingPlacements(player);
        if (count > 0) {
            ServiceRegistry.getInstance().page().markStorageViewDirty(player, session);
        }
        return count;
    }

    /** 只检查与本次变化物品相关的挂起任务，避免任意储存变化触发全队列库存查询。 */
    public static void tryResumeAfterStorageChange(ServerPlayer player, java.util.Collection<String> changedItemIds) {
        if (player == null || changedItemIds == null || changedItemIds.isEmpty()) return;
        com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                .resumeWaitingPlacementItems(player, changedItemIds);
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session != null) ServiceRegistry.getInstance().page().markStorageViewDirty(player, session);
    }

    /**
     * 使用指定的策略重启指定搁置作业。
     *
     * @param strategy 重启策略：0=正常重启（失败项跳过），1=覆盖放置
     * @param workflowEntryId 目标工作流条目 ID
     */
    public static boolean resumeWithStrategy(ServerPlayer player, RtsStorageSession session, int strategy, int workflowEntryId) {
        if (player == null || session == null) {
            return false;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        var engine = com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE;
        Long scannedAt = SCAN_TIMESTAMPS.get(player.getUUID());
        PendingPlacementScanTicket ticket = SCAN_CACHE.get(player.getUUID());
        if (ticket == null || scannedAt == null
                || System.currentTimeMillis() - scannedAt > SCAN_CACHE_TTL_MS
                || ticket.workflowEntryId() != workflowEntryId) {
            clearPlayerScanCache(player.getUUID());
            return false;
        }
        boolean resumed = engine.resumeWaitingPlacementWithStrategy(
                player, workflowEntryId, strategy, ticket.taskId(), ticket.revision());
        clearPlayerScanCache(player.getUUID());
        if (resumed) {
            ServiceRegistry.getInstance().page().markStorageViewDirty(player, session);
        }
        return resumed;
    }

    /**
     * 移除超过 TTL 的过期扫描缓存条目。
     * 在每次新缓存写入时触发，无需额外调度线程。
     */
    private static void evictStaleScanCacheEntries() {
        long now = System.currentTimeMillis();
        SCAN_TIMESTAMPS.entrySet().removeIf(e -> (now - e.getValue() > SCAN_CACHE_TTL_MS));
        SCAN_CACHE.keySet().removeIf(k -> !SCAN_TIMESTAMPS.containsKey(k));
    }

    private record PendingPlacementScanTicket(
            com.rtsbuilding.rtsbuilding.server.task.identity.TaskId taskId,
            long revision,
            int workflowEntryId,
            RtsResumeScanResult result) {
    }

    // ======================================================================
    //  辅助方法
    // ======================================================================

    @Nullable
    private static ItemStack resolveTemplate(ItemStack template, String itemId) {
        if (!template.isEmpty() || itemId == null || itemId.isBlank()) {
            return template;
        }
        Identifier fallbackId = Identifier.tryParse(itemId);
        if (fallbackId != null && BuiltInRegistries.ITEM.containsKey(fallbackId)) {
            return new ItemStack(BuiltInRegistries.ITEM.getValue(fallbackId));
        }
        return template;
    }
}
