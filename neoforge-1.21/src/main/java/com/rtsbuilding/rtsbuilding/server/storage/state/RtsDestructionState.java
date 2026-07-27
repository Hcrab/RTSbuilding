package com.rtsbuilding.rtsbuilding.server.storage.state;

import com.rtsbuilding.rtsbuilding.server.service.destruction.RtsDestructionBatch;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Mutable state container for remote area destruction (AREA_DESTROY).
 *
 * <p>Extracted from RtsStorageSession, aggregated by the responsibility of "how the player performs batch area destruction".
 * Contains the destruction batch job queue and a queue of jobs suspended due to insufficient tool durability.
 *
 * <h3>Design constraints</h3>
 * <ul>
 *   <li><b>Pure data container</b> — contains no business logic, only holds public mutable fields</li>
 *   <li><b>Independently instantiable</b> — allows testing destruction state transitions without a full session</li>
 * </ul>
 *
 * <p>This state is decoupled from {@link RtsMiningState} and only manages the asynchronous queue for AREA_DESTROY.
 * ULTIMINE and AREA_MINE still use the original {@link RtsMiningState} state machine.</p>
 */
public class RtsDestructionState {

    // ======================================================================
    // Destruction queue
    //      Area destruction batches that have not yet been executed.
    //      DestructionJob type is defined in RtsDestructionBatch.
    // ======================================================================

    /** Queue of pending destruction batch jobs */
    public final Deque<RtsDestructionBatch.DestructionJob> destroyJobs = new ArrayDeque<>();

    /**
     * Queue of destruction jobs suspended due to insufficient tool durability.
     * After the player repairs or replaces the tool, these can be moved back to destroyJobs via a resume operation.
     */
    public final Deque<RtsDestructionBatch.DestructionJob> pendingDestroyJobs = new ArrayDeque<>();
}
