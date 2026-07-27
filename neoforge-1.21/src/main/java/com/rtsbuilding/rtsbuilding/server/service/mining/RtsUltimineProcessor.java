package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.AreaOperationExecutor;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * 连锁挖掘/区域挖掘/区域破坏的批次处理器。
 *
 * <p>负责多方块挖掘批次的收集、每 tick 处理和结束。单方块操作委托给
 * {@link RtsMiningStateMachine}，验证委托给 {@link RtsMiningValidator}。
 *
 * <p><b>三种挖掘模式：</b>
 * <ul>
 *   <li><b>连锁挖掘</b>（{@link #startUltimine}）— 从种子位置 BFS 收集同类型连通方块，
 *   创造模式立即破坏，生存模式进入每 tick 处理</li>
 *   <li><b>区域挖掘</b>（{@link #areaMine}）— 在限定 3D 体积内按形状/填充类型过滤破坏</li>
 *   <li><b>区域破坏</b>（{@link #areaDestroy}）— 破坏给定显式位置列表的方块（来自形状预览）</li>
 * </ul>
 *
 * <p><b>队列模式</b>：{@link #queueAreaDestroy} / {@link #queueStartUltimine} / {@link #queueAreaMine}
 * 将操作排队为 {@link RtsMiningStateMachine.MiningJob}，支持独立线程或管道延迟执行。
 *
 * <p><b>改进亮点：</b>
 * <ul>
 *   <li>含水方块不再被错误排除</li>
 *   <li>多方块附属（门、床）通过邻居记录追踪</li>
 *   <li>工作流进度节流上报，避免每 tick 通信开销</li>
 * </ul>
 */
public final class RtsUltimineProcessor {

    private RtsUltimineProcessor() {
    }

    // =========================================================================
    //  连锁挖掘启动
    // =========================================================================

    /**
     * 在给定种子位置启动连锁挖掘批次（连接方块挖掘）。
     * 创造模式立即破坏；生存模式开始对第一个目标进行远程破坏进度。
     *
     * <p><b>前置条件（由 pipeline 保证）：</b>功能门已通过、会话已解析且维度已清理、
     * 之前的挖掘已停止、工具已借用（存储在 {@code session.mining.miningToolLease} 中）、
     * 工作流已启动（{@code ctx data: workflowEntryId}）。</p>
     */
    public static void startUltimine(ServerPlayer player, RtsStorageSession session,
            BlockPos pos, Direction face, byte toolSlot, int requestedLimit,
            byte mode, boolean toolProtectionEnabled) {
        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        int progressionLimit = RtsProgressionManager.getUltimineLimit(player);
        if (progressionLimit <= 0) {
            return;
        }
        int limit = Math.max(1, Math.min(Math.min(RtsMiningValidator.ULTIMINE_MAX_BLOCKS, progressionLimit), requestedLimit));

        if (player.isCreative()) {
            Deque<BlockPos> targets = RtsMiningValidator.collectUltimineTargets(player, pos, slot, ItemStack.EMPTY, false,
                    limit, true, mode);
            if (targets.isEmpty()) {
                return;
            }
            breakCreativeUltimineTargets(player, session, targets, slot);
            // UiRefresh handled by pipeline
            return;
        }

        boolean selectedToolRequested = session.mining.miningSelectedToolRequested;
        RtsToolLease toolLease = session.mining.miningToolLease;
        if (toolLease == null) {
            return;
        }
        Deque<BlockPos> targets = RtsMiningValidator.collectUltimineTargets(player, pos, slot, toolLease.stack(),
                selectedToolRequested, limit, false, mode);
        if (targets.isEmpty()) {
            return;
        }

        session.mining.miningToolProtectionEnabled = toolProtectionEnabled;
        session.mining.ultimineTargets.clear();
        session.mining.ultimineTargets.addAll(targets);
        session.mining.ultimineProgressPos = targets.peekFirst();
        session.mining.ultimineTotalTargets = targets.size();
        session.mining.ultimineProcessedTargets = 0;
        session.mining.ultimineBrokenTargets = 0;
        session.mining.ultimineNotifyAccumulator = 0;
        session.mining.ultimineProcessedPositions.clear();
        session.mining.ultimineAbsorbedDrops = false;
        session.mining.miningFace = face == null ? Direction.DOWN : face;
        session.mining.miningToolSlot = slot;
        // 工作流 token 已由上游 WorkflowStartPipe 通过 UltimineExecutePipe 设置
        RtsMiningStateMachine.beginRemoteMining(player, session, targets.peekFirst(), face, slot);
    }

    // =========================================================================
    //  区域挖掘
    // =========================================================================

    /**
     * 启动区域挖掘操作：破坏指定 3D 体积边界内所有可破坏的方块，
     * 按形状/填充类型过滤。
     *
     * <p><b>前置条件（由 pipeline 保证）：</b>功能门已通过、会话已解析、维度已清理、
     * 之前的挖掘已停止、工具已借用（{@code session.mining.miningToolLease}）、
     * 工作流已启动（通过 pipeline 上下文追踪）。</p>
     */
    public static void areaMine(ServerPlayer player, RtsStorageSession session,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            byte toolSlot, byte shapeType, byte fillType, boolean toolProtectionEnabled) {
        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        if (RtsProgressionManager.getUltimineLimit(player) <= 0) {
            return;
        }

        // 限定范围
        int clampedMinX = minX;
        int clampedMaxX = Math.min(clampedMinX + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxX);
        int clampedMinZ = minZ;
        int clampedMaxZ = Math.min(clampedMinZ + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxZ);
        int clampedMinY = minY;
        int clampedMaxY = Math.min(clampedMinY + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxY);

        boolean selectedToolRequested = !player.isCreative() && session.mining.miningSelectedToolRequested;
        RtsToolLease toolLease = player.isCreative()
                ? RtsToolLease.empty()
                : session.mining.miningToolLease;
        if (!player.isCreative() && toolLease == null) {
            return;
        }

        // 使用共享形状系统
        List<BlockPos> candidatePositions = AreaOperationExecutor.scanAreaMineTargets(
                player.serverLevel(),
                clampedMinX, clampedMaxX,
                clampedMinY, clampedMaxY,
                clampedMinZ, clampedMaxZ,
                player,
                shapeType, fillType);
        Deque<BlockPos> targets = new ArrayDeque<>(candidatePositions);

        if (targets.isEmpty()) {
            return;
        }

        if (player.isCreative()) {
            breakCreativeUltimineTargets(player, session, targets, slot);
            return;
        }

        session.mining.miningToolProtectionEnabled = toolProtectionEnabled;
        session.mining.ultimineTargets.clear();
        session.mining.ultimineTargets.addAll(targets);
        session.mining.ultimineProgressPos = targets.peekFirst();
        session.mining.ultimineTotalTargets = targets.size();
        session.mining.ultimineProcessedTargets = 0;
        session.mining.ultimineBrokenTargets = 0;
        session.mining.ultimineNotifyAccumulator = 0;
        session.mining.ultimineProcessedPositions.clear();
        session.mining.ultimineAbsorbedDrops = false;
        session.mining.miningFace = Direction.DOWN;
        session.mining.miningToolSlot = slot;
        // 工作流 token 已由上游 WorkflowStartPipe 通过 UltimineExecutePipe 设置
        RtsMiningStateMachine.beginRemoteMining(player, session, targets.peekFirst(), null, slot);
    }

    // =========================================================================
    //  区域破坏
    // =========================================================================

    /**
     * 破坏给定显式位置的方块（来自快速建造形状预览）。
     * 创造模式立即破坏；生存模式将目标送入连锁挖掘批次处理流程。
     *
     * <p><b>前置条件（由 pipeline 保证）：</b>功能门已通过、会话已解析、维度已清理、
     * 之前的挖掘已停止、工具已借用（{@code session.mining.miningToolLease}）、
     * 工作流已启动（通过 pipeline 上下文追踪）。</p>
     */
    public static void areaDestroy(ServerPlayer player, RtsStorageSession session, List<BlockPos> positions,
            byte toolSlot, boolean toolProtectionEnabled) {
        if (positions == null || positions.isEmpty()) {
            return;
        }

        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        if (player.isCreative()) {
            Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, ItemStack.EMPTY, false, true);
            if (targets.isEmpty()) {
                return;
            }
            breakCreativeUltimineTargets(player, session, targets, slot);
            return;
        }

        boolean selectedToolRequested = session.mining.miningSelectedToolRequested;
        RtsToolLease toolLease = session.mining.miningToolLease;
        if (toolLease == null) {
            return;
        }
        Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, toolLease.stack(),
                selectedToolRequested, false);
        if (targets.isEmpty()) {
            return;
        }

        session.mining.miningToolProtectionEnabled = toolProtectionEnabled;
        session.mining.ultimineTargets.clear();
        session.mining.ultimineTargets.addAll(targets);
        session.mining.ultimineProgressPos = targets.peekFirst();
        session.mining.ultimineTotalTargets = targets.size();
        session.mining.ultimineProcessedTargets = 0;
        session.mining.ultimineBrokenTargets = 0;
        session.mining.ultimineNotifyAccumulator = 0;
        session.mining.ultimineProcessedPositions.clear();
        session.mining.ultimineAbsorbedDrops = false;
        session.mining.miningFace = Direction.DOWN;
        session.mining.miningToolSlot = slot;
        RtsbuildingMod.LOGGER.info("[RtsUltimineProcessor] areaDestroy: {} valid targets out of {} positions for {}",
                targets.size(), positions.size(), player.getGameProfile().getName());
        // 工作流 token 已由上游 WorkflowStartPipe 通过 UltimineExecutePipe 设置
        RtsMiningStateMachine.beginRemoteMining(player, session, targets.peekFirst(), null, slot);
    }

    // =========================================================================
    //  队列模式（为独立线程延迟执行）
    // =========================================================================

    /**
     * 将区域破坏操作排队为待处理的 {@code MiningJob}。
     * 当队列中所有更早的作业完成时，目标将被处理。
     *
     * <p>在创造模式下，方块被立即破坏且工作流条目立即完成，
     * 因为创造模式破坏需要特殊处理，不能走常规的
     * {@link #processUltimineTargets} 路径。</p>
     *
     * @param workflowEntryId  WorkflowStartPipe 创建的工作流条目
     * @return 排队的（或创造模式立即破坏的）目标数，如果没有有效目标则返回 0
     */
    public static int queueAreaDestroy(ServerPlayer player, RtsStorageSession session, List<BlockPos> positions,
            byte toolSlot, boolean toolProtectionEnabled, int workflowEntryId) {
        if (positions == null || positions.isEmpty()) {
            return 0;
        }

        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);

        // Creative mode: break immediately to avoid slow per-tick processing
        if (player.isCreative()) {
            Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, ItemStack.EMPTY, false, true);
            if (targets.isEmpty()) {
                return 0;
            }
            // Preserve the current (active job's) tool lease by temporarily clearing it
            RtsToolLease savedLease = session.mining.miningToolLease;
            session.mining.miningToolLease = RtsToolLease.empty();
            try {
                breakCreativeUltimineTargets(player, session, targets, slot);
            } finally {
                session.mining.miningToolLease = savedLease;
            }
            // Complete the workflow entry immediately (blocks already broken)
            RtsWorkflowEngine.getInstance().from(player, workflowEntryId)
                    .ifPresent(token -> {
                        token.setTotalBlocks(targets.size());
                        token.setCompletedBlocks(targets.size());
                        token.complete();
                    });
            return targets.size();
        }

        // Survival mode: queue for deferred processing
        boolean selectedToolRequested = session.mining.miningSelectedToolRequested;
        RtsToolLease toolLease = session.mining.miningToolLease;
        if (toolLease == null) {
            return 0;
        }
        Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, toolLease.stack(),
                selectedToolRequested, false);
        if (targets.isEmpty()) {
            return 0;
        }

        session.mining.ultimineJobQueue.addLast(
                new RtsMiningStateMachine.MiningJob(workflowEntryId, targets, targets.size()));
        RtsbuildingMod.LOGGER.info("[RtsUltimineProcessor] queueAreaDestroy: queued {} targets, queue size = {} for {}",
                targets.size(), session.mining.ultimineJobQueue.size(), player.getGameProfile().getName());
        return targets.size();
    }

    /**
     * Queues an ultimine (connected-block) operation as a pending {@code MiningJob}.
     *
     * @param workflowEntryId  the workflow entry created by WorkflowStartPipe
     * @return number of targets queued, or 0 if no valid targets
     */
    public static int queueStartUltimine(ServerPlayer player, RtsStorageSession session,
            BlockPos pos, Direction face, byte toolSlot, int requestedLimit,
            byte mode, boolean toolProtectionEnabled, int workflowEntryId) {
        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        int progressionLimit = RtsProgressionManager.getUltimineLimit(player);
        if (progressionLimit <= 0) {
            return 0;
        }
        int limit = Math.max(1, Math.min(Math.min(RtsMiningValidator.ULTIMINE_MAX_BLOCKS, progressionLimit), requestedLimit));

        // Creative mode: break immediately
        if (player.isCreative()) {
            Deque<BlockPos> targets = RtsMiningValidator.collectUltimineTargets(player, pos, slot, ItemStack.EMPTY, false,
                    limit, true, mode);
            if (targets.isEmpty()) {
                return 0;
            }
            RtsToolLease savedLease = session.mining.miningToolLease;
            session.mining.miningToolLease = RtsToolLease.empty();
            try {
                breakCreativeUltimineTargets(player, session, targets, slot);
            } finally {
                session.mining.miningToolLease = savedLease;
            }
            RtsWorkflowEngine.getInstance().from(player, workflowEntryId)
                    .ifPresent(token -> {
                        token.setTotalBlocks(targets.size());
                        token.setCompletedBlocks(targets.size());
                        token.complete();
                    });
            return targets.size();
        }

        boolean selectedToolRequested = session.mining.miningSelectedToolRequested;
        RtsToolLease toolLease = session.mining.miningToolLease;
        if (toolLease == null) {
            return 0;
        }
        Deque<BlockPos> targets = RtsMiningValidator.collectUltimineTargets(player, pos, slot, toolLease.stack(),
                selectedToolRequested, limit, false, mode);
        if (targets.isEmpty()) {
            return 0;
        }

        session.mining.ultimineJobQueue.addLast(
                new RtsMiningStateMachine.MiningJob(workflowEntryId, targets, targets.size()));
        return targets.size();
    }

    /**
     * Queues an area-mine operation as a pending {@code MiningJob}.
     *
     * @param workflowEntryId  the workflow entry created by WorkflowStartPipe
     * @return number of targets queued, or 0 if no valid targets
     */
    public static int queueAreaMine(ServerPlayer player, RtsStorageSession session,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            byte toolSlot, byte shapeType, byte fillType, boolean toolProtectionEnabled, int workflowEntryId) {
        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        if (RtsProgressionManager.getUltimineLimit(player) <= 0) {
            return 0;
        }

        // 限定范围
        int clampedMinX = minX;
        int clampedMaxX = Math.min(clampedMinX + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxX);
        int clampedMinZ = minZ;
        int clampedMaxZ = Math.min(clampedMinZ + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxZ);
        int clampedMinY = minY;
        int clampedMaxY = Math.min(clampedMinY + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxY);

        // Creative mode: break immediately
        if (player.isCreative()) {
            List<BlockPos> candidatePositions = AreaOperationExecutor.scanAreaMineTargets(
                    player.serverLevel(),
                    clampedMinX, clampedMaxX,
                    clampedMinY, clampedMaxY,
                    clampedMinZ, clampedMaxZ,
                    player,
                    shapeType, fillType);
            Deque<BlockPos> targets = new ArrayDeque<>(candidatePositions);
            if (targets.isEmpty()) {
                return 0;
            }
            RtsToolLease savedLease = session.mining.miningToolLease;
            session.mining.miningToolLease = RtsToolLease.empty();
            try {
                breakCreativeUltimineTargets(player, session, targets, slot);
            } finally {
                session.mining.miningToolLease = savedLease;
            }
            RtsWorkflowEngine.getInstance().from(player, workflowEntryId)
                    .ifPresent(token -> {
                        token.setTotalBlocks(targets.size());
                        token.setCompletedBlocks(targets.size());
                        token.complete();
                    });
            return targets.size();
        }

        boolean selectedToolRequested = session.mining.miningSelectedToolRequested;
        RtsToolLease toolLease = session.mining.miningToolLease;
        if (toolLease == null) {
            return 0;
        }

        List<BlockPos> candidatePositions = AreaOperationExecutor.scanAreaMineTargets(
                player.serverLevel(),
                clampedMinX, clampedMaxX,
                clampedMinY, clampedMaxY,
                clampedMinZ, clampedMaxZ,
                player,
                shapeType, fillType);
        Deque<BlockPos> targets = new ArrayDeque<>(candidatePositions);
        if (targets.isEmpty()) {
            return 0;
        }

        session.mining.ultimineJobQueue.addLast(
                new RtsMiningStateMachine.MiningJob(workflowEntryId, targets, targets.size()));
        return targets.size();
    }

    /**
     * Filters a list of explicit positions to valid, breakable targets.
     * Unlike the original, waterlogged blocks are <b>not</b> excluded.
     */
    private static Deque<BlockPos> collectAreaDestroyTargets(ServerPlayer player, List<BlockPos> positions,
            int toolSlot, ItemStack linkedTool, boolean selectedToolRequested, boolean creative) {
        if (player == null || positions == null || positions.isEmpty()) {
            return new ArrayDeque<>();
        }
        ServerLevel level = player.serverLevel();
        // 从上往下逐层破坏：按Y降序排列
        List<BlockPos> sortedPositions = new ArrayList<>(positions);
        sortedPositions.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed());
        LinkedHashSet<BlockPos> unique = new LinkedHashSet<>();
        for (BlockPos raw : sortedPositions) {
            if (raw == null || unique.size() >= RtsMiningValidator.AREA_DESTROY_MAX_TARGETS) {
                continue;
            }
            BlockPos pos = raw.immutable();
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            // FIXED: No longer incorrectly excludes waterlogged blocks
            if (!RtsMiningValidator.isBreakableBlock(state)
                    || !RtsMiningValidator.hasValidDestroySpeed(state, level, pos)) {
                continue;
            }
            if (!creative && MiningSpeedCalculator.computeRemoteDestroyStep(player, state, pos, toolSlot, linkedTool,
                    selectedToolRequested) <= 0.0F) {
                continue;
            }
            unique.add(pos);
        }
        return new ArrayDeque<>(unique);
    }

    // =========================================================================
    //  连锁挖掘批次处理
    // =========================================================================

    /**
     * 处理最多 {@link RtsMiningValidator#ULTIMINE_BLOCKS_PER_TICK}
     * 个排队的连锁挖掘目标。
     */
    static void processUltimineTargets(ServerPlayer player, RtsStorageSession session) {
        if (session.mining.ultimineTargets.isEmpty()) {
            RtsbuildingMod.LOGGER.info("[RtsUltimineProcessor] processUltimineTargets: no remaining targets, finishing batch for {}",
                    player.getGameProfile().getName());
            finishUltimineBatch(player, session);
            return;
        }

        ServerLevel level = player.serverLevel();
        int processedThisTick = 0;
        int brokenBeforeThisTick = session.mining.ultimineBrokenTargets;

        while (processedThisTick < RtsMiningValidator.ULTIMINE_BLOCKS_PER_TICK && !session.mining.ultimineTargets.isEmpty()) {
            if (RtsMiningValidator.isToolNearBreak(player, session)) {
                finishUltimineBatch(player, session);
                return;
            }
            BlockPos target = session.mining.ultimineTargets.removeFirst();
            processedThisTick++;
            session.mining.ultimineProcessedTargets++;

            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) {
                continue;
            }
            BlockState targetState = level.getBlockState(target);
            if (!RtsMiningValidator.isBreakableBlock(targetState)
                    || !RtsMiningValidator.hasValidDestroySpeed(targetState, level, target)) {
                continue;
            }
            if (MiningSpeedCalculator.computeRemoteDestroyStep(player, targetState, target, session.mining.miningToolSlot,
                    session.mining.miningToolLease.stack(), session.mining.miningSelectedToolRequested) <= 0.0F) {
                continue;
            }

            // Capture before state for history (including neighbors for multi-block tracking)
            HistoryBlockRecord preRecord = ServerHistoryManager.captureBlock(player.serverLevel(), target);
            List<HistoryBlockRecord> neighborRecords = MultiBlockTracker.captureNeighborRecords(level, target);

            RtsMiningStateMachine.MiningBreakResult result = RtsMiningStateMachine.destroyMinedBlock(
                    player, session, target, session.mining.miningToolSlot);

            if (result.broken() && preRecord != null) {
                session.mining.ultimineProcessedPositions.add(preRecord);
                session.mining.ultimineBrokenTargets++;
                // Record any collateral multi-block destruction
                MultiBlockTracker.recordCollateralBlocks(level, session, neighborRecords, target);
            }
            if (result.broken() && RtsMiningValidator.canAutoStoreDrops(player, session)) {
                RtsDropAbsorber.absorbMinedDropsImmediately(player, session, target);
            }
            if (result.broken() && RtsMiningValidator.isToolNearBreak(player, session)) {
                finishUltimineBatch(player, session);
                return;
            }
        }

        int brokenDelta = session.mining.ultimineBrokenTargets - brokenBeforeThisTick;
        // ── Throttled workflow progress ────────────────────────────────
        // 累计破坏数达到阈值或挖掘结束时，一次性向 workflow engine 汇报
        if (brokenDelta > 0) {
            session.mining.ultimineNotifyAccumulator += brokenDelta;
            int entryId = session.mining.workflowEntryId;
            if (entryId >= 0
                    && (session.mining.ultimineTargets.isEmpty()
                        || session.mining.ultimineNotifyAccumulator >= 5)) {
                RtsWorkflowEngine.getInstance().from(player, entryId)
                        .ifPresent(token -> token.updateProgress(
                                session.mining.ultimineNotifyAccumulator, null));
                session.mining.ultimineNotifyAccumulator = 0;
            }
        }

        RtsMiningNetworkHelper.sendUltimineBatchProgress(player, session);
        if (session.mining.ultimineTargets.isEmpty()) {
            finishUltimineBatch(player, session);
        }
    }

    /**
     * 完成连锁挖掘批次：清除进度、归还借用的工具、标记储存页面为脏并重置挖掘状态。
     */
    static void finishUltimineBatch(ServerPlayer player, RtsStorageSession session) {
        RtsbuildingMod.LOGGER.info("[RtsUltimineProcessor] finishUltimineBatch: {} broken / {} processed / {} total for {}",
                session.mining.ultimineBrokenTargets, session.mining.ultimineProcessedTargets,
                session.mining.ultimineTotalTargets, player.getGameProfile().getName());
        // Copy history records before clearing the session list
        List<HistoryBlockRecord> records = new ArrayList<>(session.mining.ultimineProcessedPositions);
        session.mining.ultimineProcessedPositions.clear();

        if (session.mining.ultimineProgressPos != null) {
            RtsMiningNetworkHelper.clearMineProgress(player, session.mining.ultimineProgressPos);
        }
        RtsMiningStateMachine.finalizeMiningOperation(player, session, records, session.mining.miningFace);
    }

    /**
     * 为创造模式玩家立即破坏所有排队的连锁挖掘目标。
     */
    static void breakCreativeUltimineTargets(ServerPlayer player, RtsStorageSession session, Deque<BlockPos> targets,
            int toolSlot) {
        if (!targets.isEmpty()) {
            List<BlockPos> validTargets = new ArrayList<>();
            for (BlockPos target : targets) {
                if (RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) {
                    validTargets.add(target);
                }
            }
            if (!validTargets.isEmpty()) {
                Direction face = session != null && session.mining.miningFace != null ? session.mining.miningFace : Direction.DOWN;
                ServerHistoryManager.recordBreak(player, validTargets, face);
            }
        }
        while (!targets.isEmpty()) {
            BlockPos target = targets.removeFirst();
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) {
                continue;
            }
            RtsMiningStateMachine.destroyMinedBlock(player, session, target, toolSlot);
        }
    }

}
