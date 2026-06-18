package com.rtsbuilding.rtsbuilding.server.workflow.core;

import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventListener;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Central workflow engine interface — the single entry point for all workflow
 * lifecycle management.
 *
 * <p>This interface defines the contract for starting, tracking, and querying
 * workflows. Every consumer <b>must</b> go through this interface rather than
 * accessing internal state directly. The recommended usage pattern is:</p>
 *
 * <ol>
 *   <li>Obtain an {@link RtsWorkflowToken} via one of the {@code start*}
 *       methods.</li>
 *   <li>During processing, call token methods ({@link RtsWorkflowToken#markProgress()}
 *       etc.).</li>
 *   <li>When done, call {@link RtsWorkflowToken#complete()} or
 *       {@link RtsWorkflowToken#cancel()}.</li>
 *   <li>From a different code location, reconstruct the token via
 *       {@link #from(ServerPlayer, int)} using the previously saved
 *       {@code entryId}.</li>
 * </ol>
 *
 * <p>All workflow state is managed internally by the engine implementation.
 * Consumers never touch {@code RtsStorageSession.workflow} directly.</p>
 */
public interface IWorkflowEngine {

    // ======================================================================
    //  Workflow starters — 创建新工作流，返回令牌
    // ======================================================================

    /**
     * Starts a new workflow of the given type and priority.
     *
     * @param player      the server-side player
     * @param type        the workflow type
     * @param priority    the priority level
     * @param totalBlocks total blocks to process; 0 if unknown
     * @return a token representing the workflow, or empty if at capacity
     */
    Optional<RtsWorkflowToken> start(
            ServerPlayer player,
            RtsWorkflowType type, RtsWorkflowPriority priority, int totalBlocks);

    // ======================================================================
    //  Token reconstruction — 从已有 entryId 重建令牌
    // ======================================================================

    /**
     * Reconstructs a token for an existing workflow entry by its immutable entry ID.
     * Useful when an entry ID was stored in a job record and needs to be updated
     * from a different code location.
     *
     * @return the token, or empty if the entry no longer exists
     */
    Optional<RtsWorkflowToken> from(ServerPlayer player, int entryId);

    /**
     * Creates a token for the most recent active (non-suspended) workflow entry.
     * Best-effort; prefer using {@link #from(ServerPlayer, int)} with a stored entryId.
     *
     * @return the token, or empty if no active workflow exists
     */
    Optional<RtsWorkflowToken> lastActive(ServerPlayer player);

    // ======================================================================
    //  Event subscription
    // ======================================================================

    /**
     * Registers a listener for workflow lifecycle events.
     * Listeners are notified for all players' workflows.
     */
    void addListener(WorkflowEventListener listener);

    /** Removes a previously registered listener. */
    void removeListener(WorkflowEventListener listener);

    // ======================================================================
    //  Queries
    // ======================================================================

    /**
     * Returns structured progress data for the workflow identified by the token.
     *
     * @return the workflow status, or {@link RtsWorkflowStatus#idle()} if invalid
     */
    RtsWorkflowStatus getProgress(RtsWorkflowToken token);

    /**
     * Returns structured progress data for a specific entry by its ID.
     *
     * @return the workflow status, or {@link RtsWorkflowStatus#idle()} if not found
     */
    RtsWorkflowStatus getProgress(ServerPlayer player, int entryId);

    /** Returns progress data for all occupied workflow entries of a player. */
    List<RtsWorkflowStatus> getAllProgress(ServerPlayer player);

    /** Returns true if the player has any active (non-suspended) workflows. */
    boolean hasActiveWorkflow(ServerPlayer player);

    /** Returns the number of active workflow entries for the player. */
    int activeWorkflowCount(ServerPlayer player);

    /** Returns the total number of occupied slots (active + suspended). */
    int occupiedSlotCount(ServerPlayer player);

    /** Returns true if all workflow slots are occupied. */
    boolean isFull(ServerPlayer player);

    // ======================================================================
    //  Admin operations
    // ======================================================================

    /**
     * Deletes a workflow by its entry ID.  Notifies the client and fires
     * a {@link WorkflowEventType#CANCELLED} event.
     */
    void deleteWorkflow(ServerPlayer player, int entryId);

    /** Cancels all workflows for the given player in the current dimension. */
    void cancelAll(ServerPlayer player);

    // ======================================================================
    //  World-switch cleanup
    // ======================================================================

    /**
     * Removes all workflow data for a player across all dimensions.
     * Called on player logout to prevent stale workflow entries from
     * carrying over when the player joins a different world (save).
     *
     * @param playerId the player's UUID
     */
    void clearPlayerData(UUID playerId);

    /**
     * Removes all workflow data for all players.
     * Called on server stopped to fully reset the engine state.
     */
    void clearAllData();

    // ======================================================================
    //  Pause / Resume — per-entry valve
    // ======================================================================

    /**
     * Returns {@code true} if the specific workflow entry is paused.
     *
     * @param playerId  the player's UUID
     * @param dimension the dimension where the workflow was created
     * @param entryId   the immutable workflow entry ID
     */
    boolean isEntryPaused(UUID playerId, ResourceKey<Level> dimension, int entryId);
}
