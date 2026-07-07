package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 挂起放置作业管理服务——管理因物品不足而被暂挂的放置作业。
 *
 * <p>当远程范围放置或快速建造因存储系统中缺少目标物品而中断时，
 * 剩余的批处理作业会被挂起到 {@code RtsPlacementState.pendingJobs}
 * 队列中，而非丢弃或持续空轮询。玩家可通过显式提交触发扫描和恢复，
 * 或在下一次涉及物品流入的操作（挖掘吸物、合成、物品传输）时自动检测。
 *
 * <p><b>核心职责：</b>范围放置作业的挂起/恢复（蓝图扫描已移至
 * {@link RtsBlueprintJobService}，进度刷新已移至 {@link RtsProgressRefresher}）。
 *
 * <p><b>设计特点：</b>
 * <ul>
 *   <li>扫描结果缓存于 {@link #SCAN_CACHE}（ConcurrentHashMap），由客户端消费后清除</li>
 *   <li>创造模式下可用物品数视为 {@code Integer.MAX_VALUE}，永不挂起</li>
 *   <li>支持跳过（skip）和覆盖（overwrite）两种重启策略</li>
 *   <li>使用 {@link RtsWorkflowEngine} 管理每个作业的独立工作流生命周期</li>
 * </ul>
 */
public final class RtsPendingPlacementService {

    /** Per-player cached scan results, cleared after resume/cancel. */
    private static final Map<UUID, RtsResumeScanResult> SCAN_CACHE = new ConcurrentHashMap<>();

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
        return SCAN_CACHE.remove(uuid);
    }

    /**
     * 根据工作流条目 ID 在挂起队列中找到对应的作业。
     */
    private static RtsPlacementBatch.PlaceBatchJob findPendingJobByEntryId(RtsStorageSession session, int workflowEntryId) {
        if (session == null || session.placement.pendingJobs.isEmpty()) {
            return null;
        }
        for (RtsPlacementBatch.PlaceBatchJob job : session.placement.pendingJobs) {
            if (job.workflowEntryId() == workflowEntryId) {
                return job;
            }
        }
        return null;
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
        RtsPlacementBatch.PlaceBatchJob job = findPendingJobByEntryId(session, workflowEntryId);
        if (job == null) {
            return null;
        }

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        String itemId = job.itemId();
        if (itemId == null || itemId.isBlank()) {
            return null;
        }

        // 获取物品的显示名称
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        String itemLabel = itemId;
        Block expectedBlock = null;
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            itemLabel = stack.getHoverName().getString();
            if (BuiltInRegistries.ITEM.get(id) instanceof net.minecraft.world.item.BlockItem blockItem) {
                expectedBlock = blockItem.getBlock();
            }
        }

        List<BlockPos> remaining = job.remainingPositions();
        int totalRemaining = remaining.size();
        int alreadyPlacedCount = 0;
        int conflictCount = 0;

        if (expectedBlock != null && expectedBlock != Blocks.AIR) {
            for (BlockPos pos : remaining) {
                if (!player.serverLevel().hasChunkAt(pos)) {
                    continue;
                }
                BlockState currentState = player.serverLevel().getBlockState(pos);
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
        SCAN_CACHE.put(uuid, result);
        SCAN_TIMESTAMPS.put(uuid, System.currentTimeMillis());

        // 每次写入后触发一次过期清理，防止缓存无限膨胀
        evictStaleScanCacheEntries();

        return result;
    }

    /**
     * 尝试恢复指定玩家的所有挂起放置作业。
     * 遍历 {@code pendingJobs}，若对应物品在当前库存中足够，
     * 则将作业移回 {@code placeBatchJobs} 继续执行。
     *
     * @return 恢复的作业数量
     */
    public static int resumeAllPendingJobs(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) {
            return 0;
        }
        if (session.placement.pendingJobs.isEmpty()) {
            return 0;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            return 0;
        }

        List<RtsPlacementBatch.PlaceBatchJob> resumed = new ArrayList<>();
        int count = 0;
        while (!session.placement.pendingJobs.isEmpty()) {
            RtsPlacementBatch.PlaceBatchJob job = session.placement.pendingJobs.peekFirst();
            if (!canResumeJob(player, session, job)) {
                break;
            }
            session.placement.pendingJobs.removeFirst();
            session.placement.placeBatchJobs.addLast(job);
            resumed.add(job);
            count++;
        }

        if (count > 0) {
            RtsbuildingMod.LOGGER.info("[PendingPlacement] {} 恢复了 {} 个挂起放置作业",
                    player.getName().getString(), count);
            for (RtsPlacementBatch.PlaceBatchJob rj : resumed) {
                RtsWorkflowEngine.getInstance().from(player, rj.workflowEntryId()).ifPresent(token -> token.resume());
            }
            ServiceRegistry.getInstance().page().markStorageViewDirty(player, session);
        }
        return count;
    }

    /**
     * 检查一个挂起作业当前是否有足够的物品继续执行。
     */
    private static boolean canResumeJob(ServerPlayer player, RtsStorageSession session,
                                         RtsPlacementBatch.PlaceBatchJob job) {
        String itemId = job.itemId();
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        ItemStack template = resolveTemplate(job.itemPrototype(), itemId);
        final ItemStack finalTemplate = template;
        if (finalTemplate.isEmpty()) {
            return false;
        }
        long available = ServiceRegistry.getInstance().transfer().countLinkedItemsMatching(player,
                stack -> ItemStack.isSameItemSameComponents(stack, finalTemplate));
        available = RtsCountUtil.saturatedAdd(available,
                RtsProgressRefresher.countItemsInPlayerInventory(player, finalTemplate));
        return available >= 1;
    }

    /**
     * 检查是否有挂起作业并尝试恢复。
     * 适合在外部操作（挖掘吸物、合成、传输）完成后调用。
     */
    public static void tryResumeAfterStorageChange(ServerPlayer player) {
        if (player == null) {
            return;
        }
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session == null) {
            return;
        }
        if (!session.placement.pendingJobs.isEmpty()
                && RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            resumeAllPendingJobs(player, session);
        }
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
        RtsPlacementBatch.PlaceBatchJob job = findPendingJobByEntryId(session, workflowEntryId);
        if (job == null) {
            return false;
        }

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        if (strategy == 0) {
            skipConflictPositions(player, job);
        } else if (strategy == 1) {
            overwriteConflictPositions(player, job, session);
        }

        session.placement.pendingJobs.remove(job);
        session.placement.placeBatchJobs.addLast(job);
        RtsWorkflowEngine.getInstance().from(player, job.workflowEntryId()).ifPresent(token -> token.resume());
        if (strategy == 0) {
            ServiceRegistry.getInstance().page().markStorageViewDirty(player, session);
        }

        RtsbuildingMod.LOGGER.info("[PendingPlacement] {} 使用策略 {} 重启了搁置放置作业",
                player.getName().getString(), strategy == 0 ? "SKIP" : "OVERWRITE");
        return true;
    }

    private static void skipConflictPositions(ServerPlayer player, RtsPlacementBatch.PlaceBatchJob job) {
        String itemId = job.itemId();
        if (itemId == null || itemId.isBlank()) return;
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return;
        if (!BuiltInRegistries.ITEM.containsKey(id)) return;
        if (!(BuiltInRegistries.ITEM.get(id) instanceof net.minecraft.world.item.BlockItem blockItem)) return;
        Block expectedBlock = blockItem.getBlock();
        if (expectedBlock == Blocks.AIR) return;

        List<BlockPos> remaining = job.remainingPositions();
        for (BlockPos pos : remaining) {
            if (!player.serverLevel().hasChunkAt(pos)) continue;
            BlockState currentState = player.serverLevel().getBlockState(pos);
            Block currentBlock = currentState.getBlock();
            if (currentBlock != expectedBlock && !currentState.isAir() && !currentState.canBeReplaced()) {
                job.skipOne();
            } else if (currentBlock == expectedBlock) {
                job.skipOne();
            } else {
                break;
            }
        }
    }

    /**
     * 覆盖冲突格位：破坏冲突方块后重启线程。
     */
    private static void overwriteConflictPositions(ServerPlayer player, RtsPlacementBatch.PlaceBatchJob job,
                                                    RtsStorageSession session) {
        String itemId = job.itemId();
        if (itemId == null || itemId.isBlank()) return;
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return;
        if (!BuiltInRegistries.ITEM.containsKey(id)) return;
        if (!(BuiltInRegistries.ITEM.get(id) instanceof net.minecraft.world.item.BlockItem blockItem)) return;
        Block expectedBlock = blockItem.getBlock();
        if (expectedBlock == Blocks.AIR) return;

        var level = player.serverLevel();
        var linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        var insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(linked);

        for (BlockPos pos : job.remainingPositions()) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState currentState = level.getBlockState(pos);
            Block currentBlock = currentState.getBlock();

            if (currentBlock == expectedBlock) continue;
            if (currentState.isAir() || currentState.canBeReplaced()) continue;
            if (!RtsClaimProtectionService.canBreakBlock(player, pos, net.minecraft.core.Direction.UP)) continue;

            java.util.List<ItemStack> drops = Block.getDrops(currentState, level, pos, level.getBlockEntity(pos));
            level.destroyBlock(pos, false);
            if (!currentState.requiresCorrectToolForDrops() || player.isCreative()) {
                for (ItemStack drop : drops) {
                    if (!drop.isEmpty()) {
                        RtsTransferInserter.storeToLinkedWithFallback(insertHandlers, player, drop);
                    }
                }
            } else {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§e警告：" + currentBlock.getName() + " 需要合适的工具才能掉落！"),
                        true);
            }
        }
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

    // ======================================================================
    //  辅助方法
    // ======================================================================

    @Nullable
    private static ItemStack resolveTemplate(ItemStack template, String itemId) {
        if (!template.isEmpty() || itemId == null || itemId.isBlank()) {
            return template;
        }
        ResourceLocation fallbackId = ResourceLocation.tryParse(itemId);
        if (fallbackId != null && BuiltInRegistries.ITEM.containsKey(fallbackId)) {
            return new ItemStack(BuiltInRegistries.ITEM.get(fallbackId));
        }
        return template;
    }
}
