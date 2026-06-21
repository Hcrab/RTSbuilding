package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理因缺材料或冲突被挂起的放置作业。
 *
 * <p>本服务只负责“扫描、策略选择、把作业放回队列”。它不执行实际放置，
 * 也不改写快速建造的状态计划，避免恢复 UI 追平时改变玩家放置手感。</p>
 */
public final class RtsPendingPlacementService {
    private static final Map<UUID, RtsResumeScanResult> SCAN_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SCAN_TIMESTAMPS = new ConcurrentHashMap<>();
    private static final long SCAN_CACHE_TTL_MS = 30_000L;

    private RtsPendingPlacementService() {
    }

    public static void clearPlayerScanCache(UUID playerId) {
        if (playerId == null) {
            return;
        }
        SCAN_CACHE.remove(playerId);
        SCAN_TIMESTAMPS.remove(playerId);
    }

    public static RtsResumeScanResult consumeScanResult(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        UUID playerId = player.getUUID();
        SCAN_TIMESTAMPS.remove(playerId);
        return SCAN_CACHE.remove(playerId);
    }

    public static RtsResumeScanResult scanPendingJob(
            ServerPlayer player, RtsStorageSession session, int workflowEntryId) {
        if (player == null || session == null || workflowEntryId < 0) {
            return null;
        }
        RtsPlacementBatch.PlaceBatchJob job = findPendingJob(session, workflowEntryId);
        if (job == null) {
            return null;
        }

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        String itemId = job.itemId();
        ItemStack template = resolveTemplate(job.itemPrototype(), itemId);
        Block expectedBlock = expectedBlock(template, itemId);
        String itemLabel = itemLabel(template, itemId);

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

        long availableItems = player.isCreative() ? Integer.MAX_VALUE : countAvailableItems(player, template);
        int neededItems = Math.max(0, totalRemaining - alreadyPlacedCount);
        long missingItems = Math.max(0L, neededItems - availableItems);
        RtsResumeScanResult result = new RtsResumeScanResult(
                itemId == null ? "" : itemId,
                itemLabel,
                totalRemaining,
                alreadyPlacedCount,
                conflictCount,
                availableItems,
                neededItems,
                missingItems,
                workflowEntryId);

        SCAN_CACHE.put(player.getUUID(), result);
        SCAN_TIMESTAMPS.put(player.getUUID(), System.currentTimeMillis());
        evictStaleScanCacheEntries();
        return result;
    }

    public static boolean resumeWithStrategy(
            ServerPlayer player, RtsStorageSession session, int strategy, int workflowEntryId) {
        if (player == null || session == null || workflowEntryId < 0) {
            return false;
        }
        RtsPlacementBatch.PlaceBatchJob job = findPendingJob(session, workflowEntryId);
        if (job == null) {
            return false;
        }

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (strategy == 0) {
            skipAlreadyPlacedAndConflicts(player, job);
        } else if (strategy == 1) {
            overwriteConflicts(player, session, job);
        }

        session.pendingPlaceBatchJobs.remove(job);
        session.placeBatchJobs.addLast(job);
        RtsWorkflowEngine.getInstance().from(player, workflowEntryId).ifPresent(token -> token.resume());
        RtsSessionService.saveToPlayerNbt(player, session);
        RtsPageService.markStorageViewDirty(player, session);
        consumeScanResult(player);
        RtsbuildingMod.LOGGER.info("[PendingPlacement] {} resumed workflow #{} with strategy {}",
                player.getGameProfile().getName(), workflowEntryId, strategy);
        return true;
    }

    public static int resumeAllPendingJobs(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null || session.pendingPlaceBatchJobs.isEmpty()) {
            return 0;
        }
        int count = 0;
        List<RtsPlacementBatch.PlaceBatchJob> resumable = new ArrayList<>();
        for (RtsPlacementBatch.PlaceBatchJob job : session.pendingPlaceBatchJobs) {
            ItemStack template = resolveTemplate(job.itemPrototype(), job.itemId());
            if (player.isCreative() || countAvailableItems(player, template) > 0) {
                resumable.add(job);
            }
        }
        for (RtsPlacementBatch.PlaceBatchJob job : resumable) {
            session.pendingPlaceBatchJobs.remove(job);
            session.placeBatchJobs.addLast(job);
            RtsWorkflowEngine.getInstance().from(player, job.workflowEntryId()).ifPresent(token -> token.resume());
            count++;
        }
        if (count > 0) {
            RtsSessionService.saveToPlayerNbt(player, session);
            RtsPageService.markStorageViewDirty(player, session);
        }
        return count;
    }

    public static void tryResumeAfterStorageChange(ServerPlayer player) {
        if (player == null) {
            return;
        }
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        if (session == null || session.pendingPlaceBatchJobs.isEmpty()) {
            return;
        }
        resumeAllPendingJobs(player, session);
    }

    private static RtsPlacementBatch.PlaceBatchJob findPendingJob(RtsStorageSession session, int workflowEntryId) {
        if (session == null || workflowEntryId < 0) {
            return null;
        }
        for (RtsPlacementBatch.PlaceBatchJob job : session.pendingPlaceBatchJobs) {
            if (job.workflowEntryId() == workflowEntryId) {
                return job;
            }
        }
        return null;
    }

    private static void skipAlreadyPlacedAndConflicts(ServerPlayer player, RtsPlacementBatch.PlaceBatchJob job) {
        Block expectedBlock = expectedBlock(job.itemPrototype(), job.itemId());
        if (expectedBlock == null || expectedBlock == Blocks.AIR) {
            return;
        }
        for (BlockPos pos : job.remainingPositions()) {
            if (!player.serverLevel().hasChunkAt(pos)) {
                break;
            }
            BlockState currentState = player.serverLevel().getBlockState(pos);
            Block currentBlock = currentState.getBlock();
            if (currentBlock == expectedBlock || (!currentState.isAir() && !currentState.canBeReplaced())) {
                job.skipOne();
                continue;
            }
            break;
        }
    }

    private static void overwriteConflicts(
            ServerPlayer player, RtsStorageSession session, RtsPlacementBatch.PlaceBatchJob job) {
        Block expectedBlock = expectedBlock(job.itemPrototype(), job.itemId());
        if (expectedBlock == null || expectedBlock == Blocks.AIR) {
            return;
        }
        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(linked);
        for (BlockPos pos : job.remainingPositions()) {
            if (!player.serverLevel().hasChunkAt(pos)) {
                continue;
            }
            BlockState currentState = player.serverLevel().getBlockState(pos);
            Block currentBlock = currentState.getBlock();
            if (currentBlock == expectedBlock || currentState.isAir() || currentState.canBeReplaced()) {
                continue;
            }

            List<ItemStack> drops = Block.getDrops(currentState, player.serverLevel(), pos,
                    player.serverLevel().getBlockEntity(pos));
            player.serverLevel().destroyBlock(pos, false, player);
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) {
                    RtsTransferInserter.storeToLinkedWithFallback(insertHandlers, player, drop);
                }
            }
        }
    }

    private static long countAvailableItems(ServerPlayer player, ItemStack template) {
        if (player == null || template == null || template.isEmpty()) {
            return 0L;
        }
        long total = RtsTransferService.countLinkedItemsMatching(
                player, stack -> ItemStack.isSameItemSameTags(stack, template));
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        if (RtsStoragePageBuilder.shouldIncludePlayerMainInventoryInStorageView(player, session)) {
            int start = RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
            int end = RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
            for (int slot = start; slot < end; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, template)) {
                    total = RtsCountUtil.saturatedAdd(total, stack.getCount());
                }
            }
        }
        return total;
    }

    private static ItemStack resolveTemplate(ItemStack template, String itemId) {
        if (template != null && !template.isEmpty()) {
            ItemStack copy = template.copy();
            copy.setCount(1);
            return copy;
        }
        ResourceLocation id = parseItemId(itemId);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            return new ItemStack(BuiltInRegistries.ITEM.get(id));
        }
        return ItemStack.EMPTY;
    }

    private static Block expectedBlock(ItemStack template, String itemId) {
        ItemStack stack = resolveTemplate(template, itemId);
        if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock();
        }
        return null;
    }

    private static String itemLabel(ItemStack template, String itemId) {
        ItemStack stack = resolveTemplate(template, itemId);
        if (!stack.isEmpty()) {
            return stack.getHoverName().getString();
        }
        return itemId == null || itemId.isBlank() ? "unknown" : itemId;
    }

    private static ResourceLocation parseItemId(String itemId) {
        return itemId == null || itemId.isBlank() ? null : ResourceLocation.tryParse(itemId);
    }

    private static void evictStaleScanCacheEntries() {
        long now = System.currentTimeMillis();
        SCAN_TIMESTAMPS.entrySet().removeIf(entry -> now - entry.getValue() > SCAN_CACHE_TTL_MS);
        SCAN_CACHE.keySet().removeIf(playerId -> !SCAN_TIMESTAMPS.containsKey(playerId));
    }
}
