package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scans the world to determine the current state of each remaining position
 * in a pending placement job.
 *
 * <p>This class owns the read-only world-scanning concerns and the scan-result
 * cache. Lifecycle coordination (resume, skip, overwrite) belongs in
 * {@link RtsPendingPlacementService}.
 *
 * <p>Extracted from {@link RtsPendingPlacementService} to separate scanning
 * from lifecycle coordination.
 */
public final class PendingJobScanner {

    /** Per-player cached scan results, cleared after resume/cancel. */
    private static final Map<UUID, RtsResumeScanResult> SCAN_CACHE = new ConcurrentHashMap<>();

    private PendingJobScanner() {
    }

    // ──────────────────────────────────────────────────────────────────
    //  Scan result cache
    // ──────────────────────────────────────────────────────────────────

    /**
     * Returns and clears the cached scan result for the given player.
     */
    public static RtsResumeScanResult consumeScanResult(ServerPlayer player) {
        if (player == null) return null;
        return SCAN_CACHE.remove(player.getUUID());
    }

    // ──────────────────────────────────────────────────────────────────
    //  Scanning
    // ──────────────────────────────────────────────────────────────────

    /**
     * Scans the remaining positions of a pending placement job to determine
     * how many blocks are already placed, how many are in conflict, and how
     * many items are available in storage. The result is cached for retrieval
     * via {@link #consumeScanResult(ServerPlayer)}.
     *
     * @param workflowEntryId the workflow entry ID identifying the job to scan
     * @return the scan result, or {@code null} if no matching job was found
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

        // Resolve item display name and expected block
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        String itemLabel = itemId;
        Block expectedBlock = null;
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            itemLabel = stack.getHoverName().getString();
            if (BuiltInRegistries.ITEM.get(id) instanceof BlockItem blockItem) {
                expectedBlock = blockItem.getBlock();
            }
        }

        // Scan each remaining position in the world
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

        // Count available items in storage + player inventory
        ItemStack template = buildTemplate(job, itemId);
        final ItemStack finalTemplate = template;
        long availableItems = 0;
        if (!finalTemplate.isEmpty()) {
            availableItems = ServiceRegistry.getInstance().transfer().countLinkedItemsMatching(player,
                    stack -> ItemStack.isSameItemSameComponents(stack, finalTemplate));
            boolean includePlayerInventory = RtsStoragePageBuilder.shouldIncludePlayerMainInventoryInStorageView(player, session);
            if (includePlayerInventory) {
                int start = RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
                int end = RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
                for (int slot = start; slot < end; slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, finalTemplate)) {
                        availableItems = RtsCountUtil.saturatedAdd(availableItems, stack.getCount());
                    }
                }
            }
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

        SCAN_CACHE.put(player.getUUID(), result);
        return result;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ──────────────────────────────────────────────────────────────────

    /**
     * Finds a pending job by workflow entry ID, or returns null.
     */
    static RtsPlacementBatch.PlaceBatchJob findPendingJobByEntryId(RtsStorageSession session, int workflowEntryId) {
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
     * Builds an ItemStack template from the job's prototype or item ID.
     */
    private static ItemStack buildTemplate(RtsPlacementBatch.PlaceBatchJob job, String itemId) {
        ItemStack template = job.itemPrototype();
        if (!template.isEmpty()) return template;

        ResourceLocation fallbackId = ResourceLocation.tryParse(itemId);
        if (fallbackId != null && BuiltInRegistries.ITEM.containsKey(fallbackId)) {
            return new ItemStack(BuiltInRegistries.ITEM.get(fallbackId));
        }
        return ItemStack.EMPTY;
    }
}
