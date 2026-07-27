package com.rtsbuilding.rtsbuilding.server.storage.state;

import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Mutable state container for remote placement and placed block recovery.
 *
 * <p>Extracted from RtsStorageSession, aggregated by the responsibility of "how the player performs remote placement and recovery".
 * Contains the placement batch job queue and a recovery queue for drops after a placed block is broken.
 *
 * <h3>Design constraints</h3>
 * <ul>
 *   <li><b>Pure data container</b> — contains no business logic, only holds public mutable fields</li>
 *   <li><b>Independently instantiable</b> — allows testing placement state transitions without a full session</li>
 * </ul>
 */
public class RtsPlacementState {

    // ======================================================================
    // Placement queue
    //      Block placement batches that have not yet been executed.
    //      PlaceBatchJob type is defined in RtsPlacementBatch.
    // ======================================================================

    /** Queue of pending placement batch jobs */
    public final Deque<RtsPlacementBatch.PlaceBatchJob> placeBatchJobs = new ArrayDeque<>();

    /**
     * Queue of placement jobs suspended due to insufficient items.
     * When the inventory meets the conditions, these can be moved back to placeBatchJobs via {@code RtsPendingPlacementService.resumeAllPendingJobs}.
     */
    public final Deque<RtsPlacementBatch.PlaceBatchJob> pendingJobs = new ArrayDeque<>();

    /** Recovery job queue for drops after placed blocks are broken */
    public final Deque<PlacedRecoveryJob> recoveryJobs = new ArrayDeque<>();

    /**
     * Recovery job for drops after a placed block is broken.
     *
     * @param targetPos The original block position
     * @param stacks    Queue of drop stacks to recover
     */
    public record PlacedRecoveryJob(BlockPos targetPos, Deque<ItemStack> stacks) {}
}
