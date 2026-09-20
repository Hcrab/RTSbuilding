package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.AreaOperationExecutor;
import com.rtsbuilding.rtsbuilding.common.mining.MiningSelectionBounds;
import com.rtsbuilding.rtsbuilding.server.diagnostic.RtsDiagnosticReason;
import com.rtsbuilding.rtsbuilding.server.diagnostic.RtsOperationDiagnostics;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
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
 *   创造跳过首块蓄力，生存从首块蓄力开始；均进入每 tick 任务处理</li>
 *   <li><b>区域挖掘</b>（{@link #areaMine}）— 在限定 3D 体积内按形状/填充类型过滤破坏</li>
 *   <li><b>区域破坏</b>（{@link #areaDestroy}）— 破坏给定显式位置列表的方块（来自形状预览）</li>
 * </ul>
 *
 * <p><b>队列模式</b>：{@link #queueAreaDestroy} / {@link #queueStartUltimine} / {@link #queueAreaMine}
 * 将操作交给同一持久化任务引擎，世界修改仅在服务端主线程切片内进行。
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
     * 创造模式直接进入批处理；生存模式开始对第一个目标进行远程破坏进度。
     *
     * <p><b>前置条件（由 pipeline 保证）：</b>功能门已通过、会话已解析且维度已清理、
     * 之前的挖掘已停止、工具已借用（存储在 {@code session.mining.miningToolLease} 中）、
     * 工作流已启动（{@code ctx data: workflowEntryId}）。</p>
     */
    public static boolean startUltimine(ServerPlayer player, RtsStorageSession session,
            BlockPos pos, Direction face, byte toolSlot, int requestedLimit,
            byte mode, boolean toolProtectionEnabled) {
        int queued = queueStartUltimine(player, session, pos, face, toolSlot, requestedLimit,
                mode, toolProtectionEnabled, session.mining.workflowEntryId);
        if (queued > 0) session.mining.workflowEntryId = -1;
        return queued > 0;
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
    public static boolean areaMine(ServerPlayer player, RtsStorageSession session,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            byte toolSlot, byte shapeType, byte fillType, boolean toolProtectionEnabled) {
        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        if (RtsProgressionManager.getUltimineLimit(player) <= 0) {
            return false;
        }

        // 只校验完整区域，不把已确认请求静默缩成另一块区域。
        MiningSelectionBounds limited = MiningSelectionBounds.between(minX, maxX, minY, maxY, minZ, maxZ);
        if (!RtsMiningRequestLimits.accepts(player, limited)) return false;
        int clampedMinX = limited.minX();
        int clampedMaxX = limited.maxX();
        int clampedMinY = limited.minY();
        int clampedMaxY = limited.maxY();
        int clampedMinZ = limited.minZ();
        int clampedMaxZ = limited.maxZ();

        boolean selectedToolRequested = !player.isCreative() && session.mining.miningSelectedToolRequested;
        RtsToolLease toolLease = player.isCreative()
                ? RtsToolLease.empty()
                : session.mining.miningToolLease;
        if (!player.isCreative() && toolLease == null) {
            return false;
        }

        // 使用共享形状系统
        List<BlockPos> candidatePositions = AreaOperationExecutor.scanAreaMineTargets(
                player.serverLevel(),
                clampedMinX, clampedMaxX,
                clampedMinY, clampedMaxY,
                clampedMinZ, clampedMaxZ,
                player,
                shapeType, fillType);
        ItemStack actualTool = RtsMiningValidator.resolveMiningTool(player, slot, toolLease.stack());
        int maxRequiredLevel = RtsMiningValidator.rangeMiningMaxRequiredLevel(player, player.isCreative());
        Deque<BlockPos> targets = filterRangeMiningTargets(
                player, candidatePositions, actualTool, player.isCreative(), maxRequiredLevel);

        if (targets.isEmpty()) {
            return false;
        }

        int workflowEntryId = session.mining.workflowEntryId;
        boolean submitted = com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                .submitMiningTargets(player, workflowEntryId, targets,
                        Direction.DOWN, slot, selectedToolRequested,
                        toolProtectionEnabled, !player.isCreative());
        if (submitted) session.mining.workflowEntryId = -1;
        return submitted;
    }

    // =========================================================================
    //  区域破坏
    // =========================================================================

    /**
     * 破坏给定显式位置的方块（来自快速建造形状预览）。
     * 兼容旧服务入口；创造和生存都委托现行范围破坏任务，不再保留另一套裁剪与执行规则。
     *
     * <p><b>前置条件（由 pipeline 保证）：</b>功能门已通过、会话已解析、维度已清理、
     * 之前的挖掘已停止、工具已借用（{@code session.mining.miningToolLease}）、
     * 工作流已启动（通过 pipeline 上下文追踪）。</p>
     */
    public static void areaDestroy(ServerPlayer player, RtsStorageSession session, List<BlockPos> positions,
            byte toolSlot, boolean toolProtectionEnabled) {
        int workflowEntryId = session == null ? -1 : session.mining.workflowEntryId;
        if (com.rtsbuilding.rtsbuilding.server.service.destruction.RtsDestructionBatch.enqueueDestroyBatch(
                player, session, positions, toolSlot, toolProtectionEnabled, workflowEntryId)) {
            session.mining.workflowEntryId = -1;
        }
    }

    // =========================================================================
    //  队列模式（为独立线程延迟执行）
    // =========================================================================

    /**
     * 将区域破坏操作交给同一持久化范围破坏任务，创造模式也按 tick 预算推进。
     *
     * @param workflowEntryId  WorkflowStartPipe 创建的工作流条目
     * @return 成功提交的目标数，没有有效目标或未接纳时返回 0
     */
    public static int queueAreaDestroy(ServerPlayer player, RtsStorageSession session, List<BlockPos> positions,
            byte toolSlot, boolean toolProtectionEnabled, int workflowEntryId) {
        // 兼容旧服务入口，但只维护一条范围破坏执行路径，不能再各自裁剪目标。
        if (!com.rtsbuilding.rtsbuilding.server.service.destruction.RtsDestructionBatch.enqueueDestroyBatch(
                player, session, positions, toolSlot, toolProtectionEnabled, workflowEntryId)) return 0;
        return com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                .workflowTaskTotalUnits(player, workflowEntryId);
    }

    /**
     * 把连锁操作提交到持久化任务引擎，首块阶段由玩家模式决定。
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
        int limit = Math.max(1, Math.min(Math.min(RtsMiningValidator.ultimineMaxBlocks(), progressionLimit), requestedLimit));

        // 创造与生存共用可恢复任务；创造无需首块蓄力，但也不能跳过历史预算和 tick 调度。
        boolean creative = player.isCreative();
        boolean selectedToolRequested = !creative && session.mining.miningSelectedToolRequested;
        RtsToolLease toolLease = session.mining.miningToolLease;
        if (!creative && toolLease == null) return 0;
        Deque<BlockPos> targets = RtsMiningValidator.collectUltimineTargets(player, pos, slot,
                creative ? ItemStack.EMPTY : toolLease.stack(), selectedToolRequested, limit, creative, mode);
        if (targets.isEmpty()) return 0;
        /*
         * 即使前一轮连锁挖掘已经越过首块蓄力，新的排队操作也必须从自己的首块进度 0 开始。
         * 不能走旧 MiningJob 的 BATCH 迁移入口，否则第二轮会继承“已经开挖”的阶段而秒挖。
         */
        return com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE.submitMiningTargets(
                player, workflowEntryId, targets,
                face, slot, selectedToolRequested, toolProtectionEnabled, !creative)
                ? targets.size() : 0;
    }

    /**
     * 把区域挖掘操作提交到持久化任务引擎。
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

        MiningSelectionBounds limitBox = MiningSelectionBounds.between(minX, maxX, minY, maxY, minZ, maxZ);
        if (!RtsMiningRequestLimits.accepts(player, limitBox)) return 0;
        int clampedMinX = limitBox.minX();
        int clampedMaxX = limitBox.maxX();
        int clampedMinY = limitBox.minY();
        int clampedMaxY = limitBox.maxY();
        int clampedMinZ = limitBox.minZ();
        int clampedMaxZ = limitBox.maxZ();

        // 创造模式的大选区也使用已有持久化任务，避免一次 tick 同步处理整个体积。
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
            return com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE.submitMiningTargets(
                    player, workflowEntryId, targets, Direction.DOWN, slot, false,
                    toolProtectionEnabled, false) ? targets.size() : 0;
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
        ItemStack actualTool = RtsMiningValidator.resolveMiningTool(player, slot, toolLease.stack());
        int maxRequiredLevel = RtsMiningValidator.rangeMiningMaxRequiredLevel(player, false);
        Deque<BlockPos> targets = filterRangeMiningTargets(
                player, candidatePositions, actualTool, false, maxRequiredLevel);
        if (targets.isEmpty()) {
            return 0;
        }

        return com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE.submitMiningTargets(
                player, workflowEntryId, targets,
                Direction.DOWN, slot, selectedToolRequested, toolProtectionEnabled, true)
                ? targets.size() : 0;
    }


    private static Deque<BlockPos> filterRangeMiningTargets(
            ServerPlayer player,
            List<BlockPos> candidatePositions,
            ItemStack actualTool,
            boolean creative,
            int maxRequiredLevel) {
        Deque<BlockPos> targets = new ArrayDeque<>();
        List<BlockPos> harvestTierBlockedPositions = new ArrayList<>();
        int toolBlockedTargets = 0;
        for (BlockPos pos : candidatePositions) {
            BlockState state = player.serverLevel().getBlockState(pos);
            if (RtsMiningValidator.canRangeMineWithTool(state, actualTool, creative, maxRequiredLevel)) {
                targets.addLast(pos);
                continue;
            }
            if (RtsMiningValidator.isBlockedByRangeMiningHarvestTier(
                    state, actualTool, creative, maxRequiredLevel)) {
                harvestTierBlockedPositions.add(pos.immutable());
            } else {
                toolBlockedTargets++;
            }
        }
        if (!harvestTierBlockedPositions.isEmpty()) {
            notifyRangeMiningHarvestTierLimit(player, harvestTierBlockedPositions);
        }
        logFilteredTargets(player, RtsDiagnosticReason.TOOL_CANNOT_HARVEST, toolBlockedTargets);
        return targets;
    }

    private static void notifyRangeMiningHarvestTierLimit(
            ServerPlayer player,
            List<BlockPos> skippedPositions) {
        RtsMiningNetworkHelper.notifyHarvestTierLimit(player, skippedPositions);
        logFilteredTargets(
                player, RtsDiagnosticReason.HARVEST_TIER_TOO_LOW, skippedPositions.size());
    }

    private static void logFilteredTargets(
            ServerPlayer player, RtsDiagnosticReason reason, int targetCount) {
        if (player == null || targetCount <= 0) return;
        RtsStorageSession session =
                com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry.getInstance()
                        .session().getIfPresent(player);
        int workflowId = session == null ? -1 : session.mining.workflowEntryId;
        RtsWorkflowType workflowType = workflowId < 0
                ? null
                : RtsWorkflowEngine.getInstance().from(player, workflowId)
                        .map(token -> token.getProgress().type())
                        .orElse(null);
        RtsOperationDiagnostics.filteredTargets(
                player,
                workflowId,
                session == null ? "-" : session.mode.name(),
                workflowType,
                reason,
                targetCount);
    }


    // =========================================================================
    //  连锁挖掘批次处理
    // =========================================================================

    /**
     * 处理最多 {@link RtsMiningValidator#ULTIMINE_BLOCKS_PER_TICK}
     * 个排队的连锁挖掘目标。
     */
    static void processUltimineTargets(ServerPlayer player, RtsStorageSession session) {
        processUltimineTargets(player, session, RtsMiningValidator.ultimineBlocksPerTick(), Long.MAX_VALUE);
    }

    /** 在统一任务引擎分配的数量与时间预算内推进连锁挖掘。 */
    static RtsMiningStateMachine.MiningAdvance processUltimineTargets(ServerPlayer player, RtsStorageSession session,
            int maxUnits, long deadlineNanos) {
        if (session.mining.ultimineTargets.isEmpty()) {
            RtsbuildingMod.LOGGER.debug("[RtsUltimineProcessor] processUltimineTargets: no remaining targets, finishing batch for {}",
                    player.getGameProfile().getName());
            finishUltimineBatch(player, session);
            return RtsMiningStateMachine.MiningAdvance.ended(0, 0, 0);
        }

        ServerLevel level = player.serverLevel();
        int processedThisTick = 0;
        int brokenBeforeThisTick = session.mining.ultimineBrokenTargets;
        boolean autoStoreDrops = RtsMiningValidator.canAutoStoreDrops(player, session);
        List<BlockPos> dropsToAbsorb = new ArrayList<>();
        boolean finishAfterThisTick = false;

        int unitLimit = Math.max(0, Math.min(RtsMiningValidator.ultimineBlocksPerTick(), maxUnits));
        while (processedThisTick < unitLimit
                && System.nanoTime() < deadlineNanos
                && !session.mining.ultimineTargets.isEmpty()) {
            if (RtsMiningValidator.isToolNearBreak(player, session)) {
                int brokenDelta = session.mining.ultimineBrokenTargets - brokenBeforeThisTick;
                reportWorkflowDelta(player, session, brokenDelta, processedThisTick - brokenDelta);
                finishUltimineBatch(player, session);
                return RtsMiningStateMachine.MiningAdvance.ended(
                        processedThisTick, brokenDelta, processedThisTick - brokenDelta);
            }
            BlockPos target = session.mining.ultimineTargets.removeFirst();
            processedThisTick++;
            session.mining.ultimineProcessedTargets++;

            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) {
                continue;
            }
            if (!RtsClaimProtectionService.canBreakBlock(player, target, session.mining.miningFace)) {
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
            HistoryBlockRecord preRecord = ServerHistoryManager.captureBlock(
                    player.serverLevel(), target, player.isCreative());
            List<HistoryBlockRecord> neighborRecords = MultiBlockTracker.captureNeighborRecords(
                    level, target, player.isCreative());

            RtsMiningStateMachine.MiningBreakResult result = RtsMiningStateMachine.destroyMinedBlock(
                    player, session, target, session.mining.miningToolSlot);

            if (result.broken()) {
                if (preRecord != null) {
                    session.mining.ultimineProcessedPositions.add(preRecord);
                }
                session.mining.ultimineBrokenTargets++;
                // Record any collateral multi-block destruction
                MultiBlockTracker.recordCollateralBlocks(level, session, neighborRecords, target);
            }
            if (result.broken() && autoStoreDrops) {
                dropsToAbsorb.add(target.immutable());
            }
            if (result.broken() && RtsMiningValidator.isToolNearBreak(player, session)) {
                finishAfterThisTick = true;
                break;
            }
        }

        if (!dropsToAbsorb.isEmpty()) {
            RtsDropAbsorber.absorbMinedDropsBatch(player, session, dropsToAbsorb);
        }
        if (finishAfterThisTick) {
            int brokenDelta = session.mining.ultimineBrokenTargets - brokenBeforeThisTick;
            reportWorkflowDelta(player, session, brokenDelta, processedThisTick - brokenDelta);
            finishUltimineBatch(player, session);
            return RtsMiningStateMachine.MiningAdvance.ended(
                    processedThisTick, brokenDelta, processedThisTick - brokenDelta);
        }

        int brokenDelta = session.mining.ultimineBrokenTargets - brokenBeforeThisTick;
        reportWorkflowDelta(player, session, brokenDelta, processedThisTick - brokenDelta);

        RtsMiningNetworkHelper.sendUltimineBatchProgress(player, session);
        boolean ended = session.mining.ultimineTargets.isEmpty();
        if (session.mining.ultimineTargets.isEmpty()) {
            finishUltimineBatch(player, session);
        }
        return new RtsMiningStateMachine.MiningAdvance(
                processedThisTick,
                brokenDelta,
                processedThisTick - brokenDelta,
                ended,
                false);
    }

    /** 成功与失败分别投影到工作流；网络快照由 Tick 末 EffectAccumulator 合并。 */
    private static void reportWorkflowDelta(ServerPlayer player, RtsStorageSession session,
            int succeeded, int failed) {
        int entryId = session.mining.workflowEntryId;
        if (entryId < 0 || (succeeded <= 0 && failed <= 0)) return;
        RtsWorkflowEngine.getInstance().from(player, entryId).ifPresent(token -> {
            if (succeeded > 0) token.updateProgress(succeeded, null);
            if (failed > 0) token.recordFailures(failed);
        });
        session.mining.ultimineNotifyAccumulator = 0;
    }

    /**
     * 完成连锁挖掘批次：清除进度、归还借用的工具、标记储存页面为脏并重置挖掘状态。
     */
    static void finishUltimineBatch(ServerPlayer player, RtsStorageSession session) {
        RtsbuildingMod.LOGGER.debug("[RtsUltimineProcessor] finishUltimineBatch: {} broken / {} processed / {} total for {}",
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


}
