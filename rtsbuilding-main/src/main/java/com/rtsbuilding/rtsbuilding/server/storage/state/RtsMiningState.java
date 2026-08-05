package com.rtsbuilding.rtsbuilding.server.storage.state;

import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsToolLease;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Mutable state container for remote mining and chain-mining (Ultimine).
 *
 * <p>Extracted from RtsStorageSession, aggregated by the responsibility of "how the player performs remote mining operations".
 * Contains runtime state for single-block mining, chain-mining, tool borrowing/returning, etc.
 *
 * <h3>Design constraints</h3>
 * <ul>
 *   <li><b>Pure data container</b> — contains no business logic, only holds public mutable fields</li>
 *   <li><b>Independently instantiable</b> — allows testing mining state transitions without a full session</li>
 * </ul>
 */
public class RtsMiningState {

    // ======================================================================
    // Single-block remote mining
    // ======================================================================

    /** Current mining target position, null = not mining */
    public BlockPos miningPos;
    /** Mining direction (defaults to down) */
    public Direction miningFace = Direction.DOWN;
    /** Currently used tool bar slot index */
    public int miningToolSlot;
    /** Currently borrowed remote mining tool lease */
    public RtsToolLease miningToolLease = RtsToolLease.empty();
    /** True when an RTS-selected non-block item should be used instead of silently falling back to the hotbar. */
    public boolean miningSelectedToolRequested;
    /** True when active batch mining should stop before the consumable tool reaches its last 5% durability. */
    public boolean miningToolProtectionEnabled = true;
    /** Current mining progress [0.0, 1.0], incremented by the server each tick */
    public float miningProgress;
    /** Current destroy stage index; -1 = not yet started */
    public int miningStage = -1;

    // ======================================================================
    // Chain-mining (Ultimine)
    // ======================================================================

    /** Pending target queue for chain-mining (FIFO) */
    public final Deque<BlockPos> ultimineTargets = new ArrayDeque<>();
    /** Position currently being chain-mined */
    public BlockPos ultimineProgressPos;
    /** Total number of targets for the current chain-mining task */
    public int ultimineTotalTargets;
    /** Number of targets already processed in chain-mining */
    public int ultimineProcessedTargets;
    /** Records of successfully destroyed positions in chain-mining (pre-captured HistoryBlockRecords for batch history recording) */
    public final List<HistoryBlockRecord> ultimineProcessedPositions = new ArrayList<>();
    /** Number of target blocks successfully destroyed by chain-mining (excluding collateral), used for workflow progress tracking */
    public int ultimineBrokenTargets;
    /** Accumulated unsynchronized destruction count (for throttling to prevent flickering; notifyPlayer is only triggered when accumulation >= 5 or mining ends) */
    public int ultimineNotifyAccumulator;
    /** Whether chain-mining has already absorbed drops (prevents duplicate collection, controlled by the manager) */
    public boolean ultimineAbsorbedDrops;

    /** 上次发送连锁挖掘裂纹进度（0-9）的位置缓存（null = 无缓存，用于发包变化检测） */
    public BlockPos ultimineLastProgressPos;
    /** 上次发送的连锁挖掘裂纹阶段（-1 = 无缓存，用于发包变化检测） */
    public int ultimineLastStage = -1;

    /**
     * Queue of chain-mining jobs waiting to be executed.
     * The currently active job's state is held directly by fields like ultimineTargets / ultimineTotalTargets in this class;
     * jobs in this queue will be activated sequentially after the current job completes.
     */
    public final Deque<RtsMiningStateMachine.MiningJob> ultimineJobQueue = new ArrayDeque<>();

    // ======================================================================
    // Workflow entry ID (replaces the old RtsMiningStateMachine.WORKFLOW_ENTRY_IDS static map)
    // ======================================================================

    /**
     * Workflow entry ID for the currently active mining operation.
     * -1 = not associated with a workflow.
     * Written by MiningExecutePipe / UltimineExecutePipe during pipeline execution,
     * read by RtsMiningStateMachine.tickActiveMining / stopActiveMining,
     * read and reset in finalizeMiningOperation.
     */
    public int workflowEntryId = -1;
}
