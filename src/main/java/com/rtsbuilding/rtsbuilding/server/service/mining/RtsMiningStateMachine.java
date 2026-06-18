package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.WorkflowPipeline;
import com.rtsbuilding.rtsbuilding.server.pipeline.sync.HistoryRecordPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolReturnPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowCompletePipe;
import com.rtsbuilding.rtsbuilding.server.service.RtsPageService;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementSound;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * State machine for single-block remote mining progress.
 *
 * <p>This class owns the per-tick accumulation loop
 * ({@link #tickActiveMining}) and the low-level block-destruction helpers
 * ({@link #destroyMinedBlock}, {@link #computeRemoteDestroyStep}).  Every
 * method is stateless — all mutable state lives in
 * {@link RtsStorageSession}.</p>
 *
 * <p><b>Improvements over the monolithic original:</b>
 * <ul>
 *   <li>Waterlogged blocks are no longer incorrectly excluded.</li>
 *   <li>Multi-block structures (doors, beds, double-plants) that are
 *       collateral-destroyed by vanilla are now tracked for history.</li>
 *   <li>Temporary context-switching helpers are kept package-private.</li>
 * </ul>
 */
public final class RtsMiningStateMachine {

    /**
     * Per-player workflow entry ID tracking, decoupled from
     * {@link com.rtsbuilding.rtsbuilding.server.storage.RtsMiningState}.
     *
     * <p>Pipes write via {@link #setWorkflowEntryId(UUID, int)} during
     * pipeline execution; async mining completion reads via
     * {@link #getWorkflowEntryId(UUID)}.</p>
     */
    private static final Map<UUID, Integer> WORKFLOW_ENTRY_IDS = new ConcurrentHashMap<>();

    /**
     * Records a workflow entry ID for the given player, replacing any
     * previous value.
     */
    public static void setWorkflowEntryId(UUID playerUuid, int entryId) {
        if (entryId >= 0) {
            WORKFLOW_ENTRY_IDS.put(playerUuid, entryId);
        }
    }

    /**
     * Returns the workflow entry ID for the given player, or -1 if none.
     */
    public static int getWorkflowEntryId(UUID playerUuid) {
        return WORKFLOW_ENTRY_IDS.getOrDefault(playerUuid, -1);
    }

    /**
     * Removes and returns the workflow entry ID for the given player.
     */
    public static int removeWorkflowEntryId(UUID playerUuid) {
        Integer removed = WORKFLOW_ENTRY_IDS.remove(playerUuid);
        return removed != null ? removed : -1;
    }

    private RtsMiningStateMachine() {
    }

    // =========================================================================
    //  Mining Job Queue
    // =========================================================================

    /**
     * A single queued mining operation (independent thread) waiting to be
     * activated when the current operation finishes.
     *
     * <p>Analogous to {@link com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch.PlaceBatchJob}
     * but for mining operations.  Each job carries its own workflow entry ID
     * and target positions so multiple range-mining tasks can coexist in a
     * FIFO queue.</p>
     *
     * @param workflowEntryId  the workflow entry tracking this job's progress
     * @param targets          the block positions to destroy
     * @param totalTargets     total number of targets before validation losses
     */
    public record MiningJob(int workflowEntryId, Deque<BlockPos> targets, int totalTargets) {
    }

    /**
     * Activates the next queued mining job by loading its data into the
     * session's active mining state fields and updating the workflow entry
     * ID tracking map.
     */
    private static void activateNextJob(ServerPlayer player, RtsStorageSession session) {
        MiningJob job = session.mining.ultimineJobQueue.removeFirst();
        RtsbuildingMod.LOGGER.info("[RtsMiningStateMachine] activateNextJob: entryId={}, {} targets for {}", job.workflowEntryId(), job.targets().size(), player.getGameProfile().getName());
        session.mining.ultimineTargets.addAll(job.targets());
        session.mining.ultimineTotalTargets = job.totalTargets();
        session.mining.ultimineProgressPos = job.targets().peekFirst();
        session.mining.ultimineProcessedTargets = 0;
        session.mining.ultimineBrokenTargets = 0;
        session.mining.ultimineProcessedPositions.clear();
        session.mining.ultimineAbsorbedDrops = false;
        // Point the active workflow tracking to this job's entry
        setWorkflowEntryId(player.getUUID(), job.workflowEntryId());
        // 队列模式激活走批处理路径 (processUltimineTargets)，
        // 不要设置 miningPos —— beginRemoteMining 会设 miningPos = target，
        // 但 processUltimineTargets 在同一 tick 立即销毁首个目标，
        // 导致下一 tick 进入单方块挖掘路径时 miningPos 指向的空气格，
        // 触发 isBreakableBlock 检查失败进而 stopActiveMining。
        session.mining.miningFace = Direction.DOWN;
        session.mining.miningProgress = 0.0F;
        session.mining.miningStage = -1;
    }

    // =========================================================================
    //  Main Tick Handler
    // =========================================================================

    /**
     * Main tick handler for remote mining progress, invoked every server tick
     * while the player is in an RTS screen or remote-mining state.
     *
     * <p><b>Single-block mode</b> ({@code session.mining.miningPos != null}):
     * accumulates progress and sends break-stage updates to the client.  On
     * completion, breaks the block, records history, absorbs drops, and either
     * proceeds to the next ultimine target or finalises.</p>
     *
     * <p><b>Ultimine mode</b> delegates to
     * {@link RtsUltimineProcessor#processUltimineTargets}.</p>
     */
    public static void tickActiveMining(ServerPlayer player, RtsStorageSession session) {
        // 工作流状态检查：已关闭则停止挖掘，已暂停则跳过此 tick
        int entryId = getWorkflowEntryId(player.getUUID());
        if (entryId >= 0) {
            var tokenOpt = RtsWorkflowEngine.getInstance().from(player, entryId);
            if (tokenOpt.isEmpty()) {
                RtsbuildingMod.LOGGER.warn("[RtsMiningStateMachine] tickActiveMining: workflow token not found for entryId={}, stopping for {}", entryId, player.getGameProfile().getName());
                // 工作流已被关闭（删除），停止挖掘操作
                stopActiveMining(player, session);
                return;
            }
            if (tokenOpt.get().isPaused()) {
                // 暂停中，跳过此 tick（保留挖掘进度动画）
                return;
            }
        }

        if (session.mining.miningPos == null) {
            if (!session.mining.ultimineTargets.isEmpty()) {
                RtsUltimineProcessor.processUltimineTargets(player, session);
            } else if (!session.mining.ultimineJobQueue.isEmpty()) {
                // 当前作业已完成，激活下一个排队作业
                activateNextJob(player, session);
                // ultimineTargets 现在非空，立即处理这个 tick 内的方块
                RtsUltimineProcessor.processUltimineTargets(player, session);
            }
            return;
        }
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, session.mining.miningPos)) {
            stopActiveMining(player, session);
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = session.mining.miningPos;
        BlockState state = level.getBlockState(pos);
        // FIXED: No longer incorrectly excludes waterlogged blocks
        if (!RtsMiningValidator.isBreakableBlock(state)
                || !RtsMiningValidator.hasValidDestroySpeed(state, level, pos)) {
            stopActiveMining(player, session);
            return;
        }
        if (RtsMiningValidator.isToolNearBreak(player, session)) {
            stopActiveMining(player, session);
            return;
        }

        float step = computeRemoteDestroyStep(player, state, pos, session.mining.miningToolSlot,
                session.mining.miningToolLease.stack(), session.mining.miningSelectedToolRequested);
        if (step <= 0.0F) {
            return;
        }

        session.mining.miningProgress += step;
        if (session.mining.miningProgress < 1.0F) {
            int stage = RtsMiningValidator.visibleMiningStage(session.mining.miningProgress);
            if (stage != session.mining.miningStage) {
                level.destroyBlockProgress(player.getId(), pos, stage);
                RtsMiningNetworkHelper.sendMineProgress(player, pos, stage);
                session.mining.miningStage = stage;
            }
            return;
        }

        // --- Progress complete: break the block ---

        // Capture before-state for history (must be done before destroy)
        HistoryBlockRecord preRecord = ServerHistoryManager.captureBlock(player.serverLevel(), pos);
        // Also capture neighbor states for multi-block tracking
        List<HistoryBlockRecord> neighborRecords = captureNeighborRecords(player.serverLevel(), pos);

        MiningBreakResult result = destroyMinedBlock(player, session, pos, session.mining.miningToolSlot);
        level.destroyBlockProgress(player.getId(), pos, -1);

        if (result.broken() && !session.mining.ultimineTargets.isEmpty()) {
            // Part of an ultimine batch — advance to next target
            removeUltimineTarget(session, pos);
            session.mining.ultimineProcessedTargets = Math.max(session.mining.ultimineProcessedTargets, 1);
            session.mining.ultimineBrokenTargets++;
            session.mining.ultimineProcessedPositions.add(preRecord);
            // Record any collateral blocks (multi-block structures)
            recordCollateralBlocks(session, neighborRecords, pos);
            if (RtsMiningValidator.canAutoStoreDrops(player, session)) {
                RtsDropAbsorber.absorbMinedDropsImmediately(player, session, pos);
            }
            // 存储页刷新等到整批挖掘收尾时统一执行，避免第一块之后阻塞后续批量破坏。
            session.mining.miningPos = null;
            session.mining.miningProgress = 0.0F;
            session.mining.miningStage = -1;
            RtsUltimineProcessor.processUltimineTargets(player, session);
            return;
        }

        // Single-block mode — finish
        RtsMiningNetworkHelper.clearMineProgress(player, pos);
        List<HistoryBlockRecord> miningRecords = new ArrayList<>();
        if (result.broken()) {
            if (preRecord != null) {
                miningRecords.add(preRecord);
            }
            // Add any collateral blocks
            for (HistoryBlockRecord nr : neighborRecords) {
                BlockState currentState = player.serverLevel().getBlockState(nr.pos());
                if (currentState.isAir() && !nr.state().isAir()) {
                    miningRecords.add(nr);
                }
            }
        }
        if (result.broken() && RtsMiningValidator.canAutoStoreDrops(player, session)) {
            RtsDropAbsorber.absorbMinedDropsImmediately(player, session, pos);
        }
        finalizeMiningOperation(player, session, miningRecords, session.mining.miningFace);
    }

    // =========================================================================
    //  Stop
    // =========================================================================

    /**
     * Stops all active mining/ultimine activity for the given session,
     * clears break-stage particles on the client, returns the borrowed tool,
     * and resets the session's mining state.
     *
     * @param preserveEntry if {@code true}, the workflow entry is <b>not</b>
     *                       cancelled — only the runtime mining state is
     *                       cleared. Use when the entry was already paused
     *                       (e.g. RTS mode disabled) and should remain
     *                       visible in the UI.
     */
    public static void stopActiveMining(ServerPlayer player, RtsStorageSession session, boolean preserveEntry) {
        int queueSize = session.mining.ultimineJobQueue.size();
        RtsbuildingMod.LOGGER.info("[RtsMiningStateMachine] stopActiveMining: player={}, miningPos={}, ultimineTargets={}, queueSize={}, preserveEntry={}",
                player.getGameProfile().getName(),
                session.mining.miningPos,
                session.mining.ultimineTargets.size(),
                queueSize,
                preserveEntry);
        boolean hadMiningState = session.mining.miningPos != null
                || session.mining.ultimineProgressPos != null
                || !session.mining.ultimineTargets.isEmpty()
                || !session.mining.miningToolLease.isEmpty();
        boolean hadUltimine = session.mining.ultimineProgressPos != null || !session.mining.ultimineTargets.isEmpty();

        // Complete workflow tracking via entry ID if single-block mining was active
        BlockPos progressPos = session.mining.miningPos != null ? session.mining.miningPos : session.mining.ultimineProgressPos;
        if (progressPos != null) {
            player.serverLevel().destroyBlockProgress(player.getId(), progressPos, -1);
            RtsMiningNetworkHelper.sendMineProgress(player, progressPos, -1);
        }
        if (hadUltimine) {
            RtsMiningNetworkHelper.sendUltimineProgress(player, -1, 0);
        }

        if (!preserveEntry) {
            // Cancel the workflow entry (if any) before returning tool
            int entryId = removeWorkflowEntryId(player.getUUID());
            if (entryId >= 0) {
                RtsWorkflowEngine.getInstance().from(player, entryId)
                        .ifPresent(token -> token.cancel());
            }

            // 取消所有排队作业的工作流 entry
            for (MiningJob queued : session.mining.ultimineJobQueue) {
                RtsWorkflowEngine.getInstance().from(player, queued.workflowEntryId())
                        .ifPresent(token -> token.cancel());
            }
        }
        session.mining.ultimineJobQueue.clear();

        RtsToolLeaseManager.returnMiningTool(player, session, session.mining.miningToolLease);
        if (hadMiningState) {
            RtsPageService.markStorageViewDirty(player, session);
        }
        resetMiningState(session);
    }

    /**
     * Stops all active mining/ultimine activity, cancelling the workflow
     * entries. Equivalent to {@code stopActiveMining(player, session, false)}.
     *
     * @see #stopActiveMining(ServerPlayer, RtsStorageSession, boolean)
     */
    public static void stopActiveMining(ServerPlayer player, RtsStorageSession session) {
        stopActiveMining(player, session, false);
    }

    /**
     * Releases mining resources (borrowed tool, break-stage particles) without
     * cancelling the workflow entry or clearing the runtime mining state.
     *
     * <p>Use this when RTS mode is disabled — the workflow entries are paused
     * separately via {@code pauseAllActive()}, and when the player re-enables
     * RTS mode and unpauses, the mining operation can continue from where it
     * left off because the mining state ({@code miningPos},
     * {@code ultimineTargets}, etc.) is preserved.</p>
     *
     * <p>This is intentionally <b>not</b> equivalent to
     * {@code stopActiveMining(player, session, true)} — that method still
     * clears the runtime mining state via {@code resetMiningState()} and
     * {@code ultimineJobQueue.clear()}, making resumption impossible.</p>
     */
    public static void releaseMiningResources(ServerPlayer player, RtsStorageSession session) {
        // Clear break-stage particles on the client
        BlockPos progressPos = session.mining.miningPos != null
                ? session.mining.miningPos
                : session.mining.ultimineProgressPos;
        if (progressPos != null) {
            player.serverLevel().destroyBlockProgress(player.getId(), progressPos, -1);
            RtsMiningNetworkHelper.sendMineProgress(player, progressPos, -1);
        }
        boolean hadUltimine = session.mining.ultimineProgressPos != null
                || !session.mining.ultimineTargets.isEmpty();
        if (hadUltimine) {
            RtsMiningNetworkHelper.sendUltimineProgress(player, -1, 0);
        }

        // Return the borrowed tool (if any) — player gets their item back
        RtsToolLeaseManager.returnMiningTool(player, session, session.mining.miningToolLease);

        // Keep the mining state intact — do NOT reset miningPos, ultimineTargets,
        // ultimineJobQueue, or workflow entry IDs. The workflow entry is paused
        // by the caller (pauseAllActive), and tickActiveMining() will skip
        // paused entries. When unpaused, mining resumes from where it stopped.
        // We only reset the tool lease since we already returned it.
        session.mining.miningToolLease = RtsToolLease.empty();
        session.mining.miningSelectedToolRequested = false;
    }

    // =========================================================================
    //  Mining Init
    // =========================================================================

    /**
     * Initialises remote mining state for the given block position, clearing
     * any previous break-stage particles from a different target.
     */
    public static void beginRemoteMining(ServerPlayer player, RtsStorageSession session, BlockPos pos, Direction face,
            int toolSlot) {
        if (session.mining.miningPos != null && !session.mining.miningPos.equals(pos)) {
            RtsMiningNetworkHelper.clearMineProgress(player, session.mining.miningPos);
        }
        session.mining.miningPos = pos.immutable();
        session.mining.miningFace = face == null ? Direction.DOWN : face;
        session.mining.miningToolSlot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        session.mining.miningProgress = 0.0F;
        session.mining.miningStage = -1;
    }

    // =========================================================================
    //  Block Destruction
    // =========================================================================

    /**
     * Result of a {@link #destroyMinedBlock} call.
     *
     * @param broken  whether the target block was successfully broken
     * @param remainder  the tool stack remainder after breaking
     */
    public record MiningBreakResult(boolean broken, ItemStack remainder) {
    }

    /**
     * Destroys the block at {@code pos}, either via a borrowed tool lease
     * (which tracks the mutated remainder) or by temporarily switching the
     * player's selected hotbar slot.
     */
    public static MiningBreakResult destroyMinedBlock(ServerPlayer player, RtsStorageSession session, BlockPos pos, int toolSlot) {
        BlockState beforeState = player.serverLevel().getBlockState(pos);
        boolean broken;
        ItemStack remainder;
        if (session != null && session.mining.miningToolLease != null && !session.mining.miningToolLease.isEmpty()) {
            RtsToolLease lease = session.mining.miningToolLease;
            MiningBreakResult outcome = destroyBlockWithTemporaryMainHand(player, pos, lease.stack());
            remainder = RtsToolLeaseManager.protectBorrowedToolRemainder(player, lease, outcome.remainder());
            session.mining.miningToolLease = lease.withStack(remainder);
            broken = outcome.broken();
        } else if (session != null && session.mining.miningSelectedToolRequested) {
            broken = false;
            remainder = ItemStack.EMPTY;
        } else {
            broken = withTemporarySelectedSlot(player, toolSlot, () -> player.gameMode.destroyBlock(pos));
            remainder = ItemStack.EMPTY;
        }
        if (broken) {
            BlockState resultState = player.serverLevel().getBlockState(pos);
            RtsMiningNetworkHelper.sendBreakAnimation(player, pos, beforeState, resultState);
            RtsPlacementSound.playRemoteBlockBreakSound(player, player.serverLevel(), pos);
        }
        return new MiningBreakResult(broken, remainder);
    }

    // =========================================================================
    //  Progress Calculation
    // =========================================================================

    /**
     * Computes the per-tick destroy progress for the given block/tool
     * combination, applying underwater penalty cancellation.
     *
     * @return a float in (0.0, 1.0] representing progress per tick, or
     *         ≤ 0.0 if the block cannot be mined
     */
    public static float computeRemoteDestroyStep(ServerPlayer player, BlockState state, BlockPos pos, int toolSlot,
            ItemStack linkedTool, boolean selectedToolRequested) {
        if (linkedTool != null && !linkedTool.isEmpty()) {
            return withTemporaryOnGround(player, true, () -> removeMiningSpeedPenalty(player,
                    computeDestroyStepForTool(player, state, pos, linkedTool)));
        }
        if (selectedToolRequested) {
            return 0.0F;
        }
        return withTemporaryOnGround(player, true, () -> withTemporarySelectedSlot(
                player,
                toolSlot,
                () -> removeMiningSpeedPenalty(player, state.getDestroyProgress(player, player.serverLevel(), pos))));
    }

    // =========================================================================
    //  MiningDestroyOutcome (temporary swapper)
    // =========================================================================

    /**
     * Swaps the player's main hand to the given tool stack, destroys the
     * block, reads back the (possibly damaged) remainder, and restores the
     * original main-hand item.
     */
    static MiningBreakResult destroyBlockWithTemporaryMainHand(ServerPlayer player, BlockPos pos, ItemStack tool) {
        ItemStack previousMainHand = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        boolean broken;
        ItemStack remainder;
        try {
            broken = player.gameMode.destroyBlock(pos);
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new MiningBreakResult(broken, remainder);
    }

    // =========================================================================
    //  Tool-Speed Calculation (avoids item-swap sync storms)
    // =========================================================================

    /**
     * Computes the per-tick destroy progress for the given block/tool
     * combination using the tool stack <b>directly</b>, without swapping
     * the player's main hand item.
     *
     * <p>This replicates the logic of
     * {@code state.getDestroyProgress(player, level, pos)} but uses the
     * provided tool stack instead of {@code player.getMainHandItem()},
     * avoiding costly {@code player.setItemInHand()} calls that trigger
     * a {@code ClientboundContainerSetSlotPacket} every tick.
     *
     * @return a float in (0.0, 1.0] representing progress per tick, or
     *         ≤ 0.0 if the block cannot be mined
     */
    private static float computeDestroyStepForTool(ServerPlayer player, BlockState state, BlockPos pos, ItemStack tool) {
        float destroySpeed = state.getDestroySpeed(player.serverLevel(), pos);
        if (destroySpeed == -1.0F) {
            return 0.0F;
        }
        float digSpeed = getToolDigSpeed(player, state, tool);
        int divisor = tool.isCorrectToolForDrops(state) ? 30 : 100;
        return digSpeed / destroySpeed / (float) divisor;
    }

    /**
     * Replicates {@code Player.getDigSpeed(BlockState, BlockPos)} using
     * the given {@code tool} stack instead of the player's main hand item.
     *
     * <p>Water penalty and on-ground checks are omitted here — they are
     * handled separately by {@link #removeMiningSpeedPenalty} and
     * {@link #withTemporaryOnGround} respectively.
     */
    private static float getToolDigSpeed(ServerPlayer player, BlockState state, ItemStack tool) {
        float f = tool.getDestroySpeed(state);
        if (f > 1.0F) {
            int efficiency = getEfficiencyLevel(tool);
            if (efficiency > 0 && !tool.isEmpty()) {
                f += (float) (efficiency * efficiency + 1);
            }
        }
        if (player.hasEffect(MobEffects.DIG_SPEED)) {
            f *= 1.0F + (float) (player.getEffect(MobEffects.DIG_SPEED).getAmplifier() + 1) * 0.2F;
        }
        if (player.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            f *= 1.0F - (float) (player.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier() + 1) * 0.2F;
        }
        return f;
    }

    /**
     * Returns the Efficiency enchantment level on the given ItemStack
     * by iterating its enchantment components directly.
     */
    private static int getEfficiencyLevel(ItemStack stack) {
        for (var entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(Enchantments.EFFICIENCY)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    // =========================================================================
    //  Underwater Speed Penalty
    // =========================================================================

    /**
     * Cancels the underwater mining speed penalty ({@code SUBMERGED_MINING_SPEED})
     * while preserving any positive modifier from enchantments or mods.
     */
    static float removeMiningSpeedPenalty(ServerPlayer player, float destroyStep) {
        if (destroyStep <= 0.0F) {
            return destroyStep;
        }
        float adjusted = destroyStep;
        if (player.isEyeInFluid(FluidTags.WATER)) {
            double submergedMiningSpeed = player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
            if (submergedMiningSpeed > 0.0D && submergedMiningSpeed < 1.0D) {
                adjusted *= (float) (1.0D / submergedMiningSpeed);
            }
        }
        return adjusted;
    }

    // =========================================================================
    //  Temporary Context Switchers
    // =========================================================================

    public static <T> T withTemporaryMainHandItem(ServerPlayer player, ItemStack stack, Supplier<T> action) {
        ItemStack previousMainHand = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            return action.get();
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
    }

    public static <T> T withTemporaryOnGround(ServerPlayer player, boolean onGround, Supplier<T> action) {
        boolean previous = player.onGround();
        player.setOnGround(onGround);
        try {
            return action.get();
        } finally {
            player.setOnGround(previous);
        }
    }

    static <T> T withTemporarySelectedSlot(ServerPlayer player, int toolSlot, Supplier<T> action) {
        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        int prevSelected = player.getInventory().selected;

        player.getInventory().selected = slot;
        try {
            return action.get();
        } finally {
            player.getInventory().selected = prevSelected;
        }
    }

    // =========================================================================
    //  Mining Completion (shared cleanup)
    // =========================================================================

    /**
     * Finalises a completed mining operation by delegating to the three
     * cleanup pipes ({@link WorkflowCompletePipe}, {@link ToolReturnPipe},
     * {@link HistoryRecordPipe}), then refreshing the storage page and
     * resetting the mining state.
     *
     * <p>This method builds a {@link PipelineContext} from the current
     * session state so that the three pipes can execute with the same
     * data contract as the sync-phase pipeline.  Cleanup is best-effort:
     * failures are logged via {@link WorkflowPipeline#runCleanupSequence}
     * but do not prevent subsequent cleanup steps.</p>
     *
     * @param player     the server-side player
     * @param session    the player's storage session
     * @param records    history records captured before block break (may be empty)
     * @param face       the mining face used (may be null)
     */
    static void finalizeMiningOperation(ServerPlayer player, RtsStorageSession session,
            List<HistoryBlockRecord> records, @Nullable Direction face) {

        // ── Build pipeline context from session state ────────────────
        PipelineContext ctx = new PipelineContext(player, Map.of());

        // Session (required by ToolReturnPipe and HistoryRecordPipe)
        ctx.setData(SessionValidatePipe.KEY_SESSION, session);

        // Workflow entry ID (from dedicated tracking map, not from session)
        int wfEntryId = removeWorkflowEntryId(player.getUUID());
        if (wfEntryId >= 0) {
            ctx.setData(PipelineContext.KEY_WORKFLOW_ENTRY_ID, wfEntryId);
        }

        boolean hasQueuedJobs = !session.mining.ultimineJobQueue.isEmpty();
        RtsbuildingMod.LOGGER.info("[RtsMiningStateMachine] finalizeMiningOperation: entryId={}, hasQueuedJobs={}, records={} for {}", wfEntryId, hasQueuedJobs, records.size(), player.getGameProfile().getName());

        // Borrowed tool lease — only return if this is the LAST job
        if (!hasQueuedJobs
                && session.mining.miningToolLease != null
                && !session.mining.miningToolLease.isEmpty()) {
            ctx.setData(ToolReturnPipe.KEY_TOOL_LEASE, session.mining.miningToolLease);
        }

        // History records (pre-captured before-state)
        if (!records.isEmpty()) {
            ctx.setData(HistoryRecordPipe.ARG_HISTORY_RECORDS, records);
            ctx.setData(HistoryRecordPipe.ARG_HISTORY_FACE, face != null ? face : Direction.DOWN);
        }

        // ── Execute cleanup sequence via WorkflowPipeline ───────────
        // 当队列中还有排队作业时，跳过 ToolReturnPipe（工具由下一个作业继续使用）
        var cleanupPipes = new ArrayList<PipelinePipe<? super PipelineContext>>();
        cleanupPipes.add(new WorkflowCompletePipe());
        if (!hasQueuedJobs) {
            cleanupPipes.add(new ToolReturnPipe());
        }
        cleanupPipes.add(new HistoryRecordPipe());
        WorkflowPipeline.runCleanupSequence(ctx, cleanupPipes);

        // 只在没有后续排队挖掘时重建存储页，避免连续作业之间被页面刷新卡住。
        if (!hasQueuedJobs) {
            RtsStorageTickService.INSTANCE.forceRefresh(player);
            session.transfer.pageDataVersion.incrementAndGet();
            RtsPageService.requestPage(player, session.browser.page, session.browser.search,
                    session.browser.category, session.browser.sort, session.browser.ascending);
        }
        resetMiningState(session, hasQueuedJobs);
    }

    // =========================================================================
    //  State Reset
    // =========================================================================

    public static void resetMiningState(RtsStorageSession session) {
        resetMiningState(session, false);
    }

    /**
     * Resets the session's active mining state fields.
     *
     * <p>When {@code preserveTool} is {@code true} (i.e., there are queued
     * mining jobs waiting to be activated), the borrowed tool lease and
     * related settings are kept alive so the next job can reuse them.</p>
     */
    public static void resetMiningState(RtsStorageSession session, boolean preserveTool) {
        session.mining.miningPos = null;
        session.mining.ultimineTargets.clear();
        session.mining.ultimineProgressPos = null;
        session.mining.ultimineTotalTargets = 0;
        session.mining.ultimineProcessedTargets = 0;
        session.mining.ultimineBrokenTargets = 0;
        session.mining.ultimineAbsorbedDrops = false;
        session.mining.miningFace = Direction.DOWN;
        session.mining.miningProgress = 0.0F;
        session.mining.miningStage = -1;
        if (!preserveTool) {
            session.mining.miningToolLease = RtsToolLease.empty();
            session.mining.miningSelectedToolRequested = false;
            session.mining.miningToolProtectionEnabled = true;
        }
        // workflowEntryId no longer lives here — see WORKFLOW_ENTRY_IDS in RtsMiningStateMachine
    }

    // =========================================================================
    //  Multi-Block Collateral Tracking
    // =========================================================================

    /**
     * Captures the before-break state of all 6 neighbors for multi-block
     * structure tracking (doors, beds, double plants, etc.).
     */
    private static List<HistoryBlockRecord> captureNeighborRecords(ServerLevel level, BlockPos pos) {
        List<HistoryBlockRecord> records = new ArrayList<>(6);
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState state = level.getBlockState(neighbor);
            if (!state.isAir()) {
                records.add(new HistoryBlockRecord(neighbor.immutable(), state));
            }
        }
        return records;
    }

    /**
     * After a block is broken, checks which neighbor positions changed to air
     * and adds them to the session's ultimine processed positions so they are
     * included in the batch history record.
     */
    private static void recordCollateralBlocks(RtsStorageSession session, List<HistoryBlockRecord> neighborRecords,
            BlockPos brokenPos) {
        for (HistoryBlockRecord nr : neighborRecords) {
            if (nr.pos().equals(brokenPos)) {
                continue;
            }
            // If the neighbor was solid before but is now air, it was collateral
            // destroyed by vanilla (e.g. the other half of a door or bed).
            // We rely on the caller to check current state since we don't have
            // a ServerLevel reference here — the caller's history recording
            // handles this.
            session.mining.ultimineProcessedPositions.add(nr);
        }
    }

    /**
     * Removes a specific position from the ultimine target queue.
     */
    private static void removeUltimineTarget(RtsStorageSession session, BlockPos pos) {
        session.mining.ultimineTargets.removeIf(target -> target.equals(pos));
    }
}
