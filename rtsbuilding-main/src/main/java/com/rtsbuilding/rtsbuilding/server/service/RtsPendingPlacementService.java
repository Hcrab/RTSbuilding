package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
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
 * Pending placement job management service — manages placement jobs suspended due to insufficient items.
 *
 * <p>When remote area placement or quick build is interrupted due to missing target items in the storage system,
 * the remaining batch jobs are suspended to the {@code RtsPlacementState.pendingJobs}
 * queue, rather than being discarded or continuously polling. Players can trigger scanning and resumption
 * via explicit submission, or automatically detect on the next item inflow operation (mining absorption, crafting, item transfer).
 *
 * <p><b>Core responsibilities:</b> Suspension/resumption of area placement jobs (blueprint scanning moved to
 * {@link RtsBlueprintJobService}, progress refresh moved to {@link RtsProgressRefresher}).
 *
 * <p><b>Design features:</b>
 * <ul>
 *   <li>Scan results cached in {@link #SCAN_CACHE} (ConcurrentHashMap), cleared after client consumption</li>
 *   <li>Creative mode available item count treated as {@code Integer.MAX_VALUE}, never suspended</li>
 *   <li>Supports skip and overwrite restart strategies</li>
 *   <li>Uses {@link RtsWorkflowEngine} to manage independent workflow lifecycle for each job</li>
 * </ul>
 */
public final class RtsPendingPlacementService {

    /** Per-player cached scan results, cleared after resume/cancel. */
    private static final Map<UUID, RtsResumeScanResult> SCAN_CACHE = new ConcurrentHashMap<>();

    /** Scan cache TTL: auto-expires after 30 seconds, prevents memory leak if player never consumes scan result. */
    private static final long SCAN_CACHE_TTL_MS = 30_000L;

    /** Per-player scan timestamps (updated in sync with SCAN_CACHE), used for TTL detection. */
    private static final Map<UUID, Long> SCAN_TIMESTAMPS = new ConcurrentHashMap<>();

    private RtsPendingPlacementService() {
    }

    /**
     * Clears scan cache entries for the specified player, preventing memory leak after disconnect.
     * Called by {@code RtsbuildingMod} on player logout event.
     * Blueprint throttle cache is cleared by {@link RtsProgressRefresher#clearPlayerCache}.
     */
    public static void clearPlayerScanCache(UUID playerUuid) {
        if (playerUuid != null) {
            SCAN_CACHE.remove(playerUuid);
            SCAN_TIMESTAMPS.remove(playerUuid);
        }
    }

    /**
     * Gets and clears the cached scan result for the specified player.
     */
    public static RtsResumeScanResult consumeScanResult(ServerPlayer player) {
        if (player == null) return null;
        UUID uuid = player.getUUID();
        SCAN_TIMESTAMPS.remove(uuid);
        return SCAN_CACHE.remove(uuid);
    }

    /**
     * Finds the corresponding job in the pending queue by workflow entry ID.
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
     * Scans the remaining positions of the specified player's pending job, returning scan results.
     * Finds the corresponding job by workflowEntryId.
     * The result is cached in SCAN_CACHE.
     *
     * @param workflowEntryId Target workflow entry ID
     * @return Scan result, or null if no matching pending job exists
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

        // Get the display name of the item
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
            availableItems = RtsServer.get().transfer().countLinkedItemsMatching(player,
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

        // Trigger expiration cleanup after each write, preventing infinite cache growth
        evictStaleScanCacheEntries();

        return result;
    }

    /**
     * Attempts to resume all pending placement jobs for the specified player.
     * Iterates through {@code pendingJobs}, if the corresponding item is sufficiently available in current inventory,
     * moves the job back to {@code placeBatchJobs} for continued execution.
     *
     * @return Number of resumed jobs
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
            RtsbuildingMod.LOGGER.info("[PendingPlacement] {} resumed {} pending placement jobs",
                    player.getName().getString(), count);
            for (RtsPlacementBatch.PlaceBatchJob rj : resumed) {
                RtsWorkflowEngine.getInstance().from(player, rj.workflowEntryId()).ifPresent(token -> token.resume());
            }
            RtsServer.get().page().markStorageViewDirty(player, session);
        }
        return count;
    }

    /**
     * Checks if a pending job currently has enough items to continue execution.
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
        long available = RtsServer.get().transfer().countLinkedItemsMatching(player,
                stack -> ItemStack.isSameItemSameComponents(stack, finalTemplate));
        available = RtsCountUtil.saturatedAdd(available,
                RtsProgressRefresher.countItemsInPlayerInventory(player, finalTemplate));
        return available >= 1;
    }

    /**
     * Checks for pending jobs and attempts to resume them.
     * Suitable for calling after external operations (mining absorption, crafting, transfer) complete.
     */
    public static void tryResumeAfterStorageChange(ServerPlayer player) {
        if (player == null) {
            return;
        }
        RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
        if (session == null) {
            return;
        }
        if (!session.placement.pendingJobs.isEmpty()
                && RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            resumeAllPendingJobs(player, session);
        }
    }

    /**
     * Restarts the specified suspended job using the given strategy.
     *
     * @param strategy Restart strategy: 0=normal restart (skip failures), 1=overwrite placement
     * @param workflowEntryId Target workflow entry ID
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
            RtsServer.get().page().markStorageViewDirty(player, session);
        }

        RtsbuildingMod.LOGGER.info("[PendingPlacement] {} restarted suspended placement job with strategy {}",
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
     * Overwrites conflicting positions: destroys conflicting blocks then restarts the thread.
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
                        net.minecraft.network.chat.Component.literal("§eWarning: " + currentBlock.getName() + " requires a suitable tool to drop!"),
                        true);
            }
        }
    }

    /**
     * Removes expired scan cache entries that exceed the TTL.
     * Triggered on each new cache write, no additional scheduling thread needed.
     */
    private static void evictStaleScanCacheEntries() {
        long now = System.currentTimeMillis();
        SCAN_TIMESTAMPS.entrySet().removeIf(e -> (now - e.getValue() > SCAN_CACHE_TTL_MS));
        SCAN_CACHE.keySet().removeIf(k -> !SCAN_TIMESTAMPS.containsKey(k));
    }

    // ======================================================================
    //  Helper methods
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
