package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.common.AreaOperationExecutor;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPageService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.RtsToolLease;
import com.rtsbuilding.rtsbuilding.server.storage.RtsToolLeaseManager;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowToken;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Ultimine / area-mine / area-destroy batch processing.
 *
 * <p>This class owns the collection, per-tick processing, and finalisation of
 * multi-block mining batches.  It delegates single-block operations to
 * {@link RtsMiningStateMachine} and validation to
 * {@link RtsMiningValidator}.</p>
 *
 * <p><b>Improvements over the monolithic original:</b>
 * <ul>
 *   <li>Waterlogged blocks are no longer incorrectly excluded in
 *       {@link #collectAreaDestroyTargets}.</li>
 *   <li>Multi-block collateral (doors, beds) is tracked via
 *   <li>All session state manipulation is explicit and local.</li>
 * </ul>
 */
public final class RtsUltimineProcessor {

    private RtsUltimineProcessor() {
    }

    /**
     * pipeline 启动批量挖掘后的结果。
     *
     * @param targetCount 本次真实收集到的目标数量
     * @param asyncActive 是否已经进入逐 tick 执行阶段
     */
    public record PipelineBatchStartResult(int targetCount, boolean asyncActive) {
        public static PipelineBatchStartResult completedImmediately(int targetCount) {
            return new PipelineBatchStartResult(Math.max(0, targetCount), false);
        }

        public static PipelineBatchStartResult async(int targetCount) {
            return new PipelineBatchStartResult(Math.max(0, targetCount), true);
        }
    }

    // =========================================================================
    //  Ultimine Start
    // =========================================================================

    /**
     * Starts an ultimine batch (connected-block mining) at the given seed
     * position.  Creative mode breaks instantly; survival mode borrows a tool
     * and begins remote break progress on the first target.
     */
    public static void startUltimine(ServerPlayer player, RtsStorageSession session, BlockPos pos, Direction face,
            byte toolSlot, String toolItemId, ItemStack toolPrototype, int requestedLimit, byte mode,
            boolean toolProtectionEnabled) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.ULTIMINE)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

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
                RtsMiningStateMachine.stopActiveMining(player, session);
                return;
            }
            RtsMiningStateMachine.stopActiveMining(player, session);
            breakCreativeUltimineTargets(player, session, targets, slot);
            RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
            return;
        }

        RtsMiningStateMachine.stopActiveMining(player, session);
        boolean selectedToolRequested = RtsMiningValidator.isSelectedMiningToolRequested(toolItemId, toolPrototype);
        RtsToolLease toolLease = RtsToolLeaseManager.borrowMiningTool(player, session, toolItemId, toolPrototype, slot);
        if (selectedToolRequested && toolLease.isEmpty()) {
            return;
        }
        Deque<BlockPos> targets = RtsMiningValidator.collectUltimineTargets(player, pos, slot, toolLease.stack(),
                selectedToolRequested, limit, false, mode);
        if (targets.isEmpty()) {
            RtsToolLeaseManager.returnMiningTool(player, session, toolLease);
            return;
        }
        int workflowEntryId = startMiningWorkflow(player, RtsWorkflowType.ULTIMINE, targets.size());
        if (workflowEntryId < 0) {
            RtsToolLeaseManager.returnMiningTool(player, session, toolLease);
            return;
        }

        session.mining.miningToolLease = toolLease;
        session.mining.miningSelectedToolRequested = selectedToolRequested;
        session.mining.miningToolProtectionEnabled = toolProtectionEnabled;
        session.mining.miningWorkflowEntryId = workflowEntryId;
        session.mining.ultimineTargets.clear();
        session.mining.ultimineTargets.addAll(targets);
        session.mining.ultimineProgressPos = targets.peekFirst();
        session.mining.ultimineTotalTargets = targets.size();
        session.mining.ultimineProcessedTargets = 0;
        session.mining.ultimineProcessedPositions.clear();
        session.mining.ultimineAbsorbedDrops = false;
        session.mining.miningFace = face == null ? Direction.DOWN : face;
        session.mining.miningToolSlot = slot;
        RtsMiningNetworkHelper.sendUltimineProgress(player, 0, targets.size());
        RtsMiningStateMachine.beginRemoteMining(player, session, targets.peekFirst(), face, slot);
    }

    // =========================================================================
    //  Area Mine
    // =========================================================================

    /**
     * Starts an area-mine operation: breaks all breakable blocks within the
     * specified 3D volume bounds, filtered by shape/fill type.
     *
     * <p>Delegates position generation to {@link AreaOperationExecutor} and
     * feeds the results into the ultimine batch processing pipeline.</p>
     */
    public static void areaMine(ServerPlayer player, RtsStorageSession session,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            byte toolSlot, String toolItemId, ItemStack toolPrototype,
            byte shapeType, byte fillType, boolean toolProtectionEnabled) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.ULTIMINE)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        if (RtsProgressionManager.getUltimineLimit(player) <= 0) {
            return;
        }

        // Clamp bounds
        int clampedMinX = minX;
        int clampedMaxX = Math.min(clampedMinX + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxX);
        int clampedMinZ = minZ;
        int clampedMaxZ = Math.min(clampedMinZ + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxZ);
        int clampedMinY = minY;
        int clampedMaxY = Math.min(clampedMinY + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxY);

        RtsMiningStateMachine.stopActiveMining(player, session);
        boolean selectedToolRequested = !player.isCreative() && RtsMiningValidator.isSelectedMiningToolRequested(toolItemId, toolPrototype);
        RtsToolLease toolLease = player.isCreative()
                ? RtsToolLease.empty()
                : RtsToolLeaseManager.borrowMiningTool(player, session, toolItemId, toolPrototype, slot);
        if (selectedToolRequested && toolLease.isEmpty()) {
            return;
        }

        // Use shared shape system
        List<BlockPos> candidatePositions = AreaOperationExecutor.scanAreaMineTargets(
                player.serverLevel(),
                clampedMinX, clampedMaxX,
                clampedMinY, clampedMaxY,
                clampedMinZ, clampedMaxZ,
                player,
                shapeType, fillType);
        Deque<BlockPos> targets = new ArrayDeque<>(candidatePositions);

        if (targets.isEmpty()) {
            RtsToolLeaseManager.returnMiningTool(player, session, toolLease);
            return;
        }

        if (player.isCreative()) {
            RtsMiningStateMachine.stopActiveMining(player, session);
            breakCreativeUltimineTargets(player, session, targets, slot);
            RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
            return;
        }
        int workflowEntryId = startMiningWorkflow(player, RtsWorkflowType.AREA_MINE, targets.size());
        if (workflowEntryId < 0) {
            RtsToolLeaseManager.returnMiningTool(player, session, toolLease);
            return;
        }

        session.mining.miningToolLease = toolLease;
        session.mining.miningSelectedToolRequested = selectedToolRequested;
        session.mining.miningToolProtectionEnabled = toolProtectionEnabled;
        session.mining.miningWorkflowEntryId = workflowEntryId;
        session.mining.ultimineTargets.clear();
        session.mining.ultimineTargets.addAll(targets);
        session.mining.ultimineProgressPos = targets.peekFirst();
        session.mining.ultimineTotalTargets = targets.size();
        session.mining.ultimineProcessedTargets = 0;
        session.mining.ultimineProcessedPositions.clear();
        session.mining.ultimineAbsorbedDrops = false;
        session.mining.miningFace = Direction.DOWN;
        session.mining.miningToolSlot = slot;
        RtsMiningNetworkHelper.sendUltimineProgress(player, 0, targets.size());
        RtsMiningStateMachine.beginRemoteMining(player, session, targets.peekFirst(), null, slot);
    }

    // =========================================================================
    //  Area Destroy
    // =========================================================================

    /**
     * Destroys blocks at the given explicit positions (from Quick Build shape
     * preview).  Creative mode breaks instantly; survival mode feeds targets
     * into the ultimine batch processing pipeline.
     */
    public static void areaDestroy(ServerPlayer player, RtsStorageSession session, List<BlockPos> positions,
            byte toolSlot, String toolItemId, ItemStack toolPrototype, boolean toolProtectionEnabled) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.AREA_DESTROY)) {
            return;
        }
        if (session == null || positions == null || positions.isEmpty()) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        if (player.isCreative()) {
            Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, ItemStack.EMPTY, false, true);
            if (targets.isEmpty()) {
                RtsMiningStateMachine.stopActiveMining(player, session);
                return;
            }
            RtsMiningStateMachine.stopActiveMining(player, session);
            breakCreativeUltimineTargets(player, session, targets, slot);
            RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
            return;
        }

        RtsMiningStateMachine.stopActiveMining(player, session);
        boolean selectedToolRequested = RtsMiningValidator.isSelectedMiningToolRequested(toolItemId, toolPrototype);
        RtsToolLease toolLease = RtsToolLeaseManager.borrowMiningTool(player, session, toolItemId, toolPrototype, slot);
        if (selectedToolRequested && toolLease.isEmpty()) {
            return;
        }
        Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, toolLease.stack(),
                selectedToolRequested, false);
        if (targets.isEmpty()) {
            RtsToolLeaseManager.returnMiningTool(player, session, toolLease);
            return;
        }
        int workflowEntryId = startMiningWorkflow(player, RtsWorkflowType.AREA_DESTROY, targets.size());
        if (workflowEntryId < 0) {
            RtsToolLeaseManager.returnMiningTool(player, session, toolLease);
            return;
        }

        session.mining.miningToolLease = toolLease;
        session.mining.miningSelectedToolRequested = selectedToolRequested;
        session.mining.miningToolProtectionEnabled = toolProtectionEnabled;
        session.mining.miningWorkflowEntryId = workflowEntryId;
        session.mining.ultimineTargets.clear();
        session.mining.ultimineTargets.addAll(targets);
        session.mining.ultimineProgressPos = targets.peekFirst();
        session.mining.ultimineTotalTargets = targets.size();
        session.mining.ultimineProcessedTargets = 0;
        session.mining.ultimineProcessedPositions.clear();
        session.mining.ultimineAbsorbedDrops = false;
        session.mining.miningFace = Direction.DOWN;
        session.mining.miningToolSlot = slot;
        RtsMiningNetworkHelper.sendUltimineProgress(player, 0, targets.size());
        RtsMiningStateMachine.beginRemoteMining(player, session, targets.peekFirst(), null, slot);
    }

    // =========================================================================
    //  Pipeline Entry Points
    // =========================================================================

    /**
     * 从 pipeline 启动连锁挖掘。workflow 和工具租约已由上游 pipe 创建，
     * 这里只负责收集目标并写入状态机需要的 session.mining 字段。
     */
    public static PipelineBatchStartResult startUltimineFromPipeline(ServerPlayer player, RtsStorageSession session,
            BlockPos pos, Direction face, byte toolSlot, RtsToolLease toolLease, boolean selectedToolRequested,
            int requestedLimit, byte mode, boolean toolProtectionEnabled, int workflowEntryId) {
        RtsToolLease lease = toolLease == null ? RtsToolLease.empty() : toolLease;
        if (!RtsProgressionManager.canUse(player, RtsFeature.ULTIMINE) || session == null) {
            return completePipelineWithoutActiveBatch(player, session, lease);
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        int progressionLimit = RtsProgressionManager.getUltimineLimit(player);
        if (progressionLimit <= 0) {
            return completePipelineWithoutActiveBatch(player, session, lease);
        }
        int limit = Math.max(1, Math.min(Math.min(RtsMiningValidator.ULTIMINE_MAX_BLOCKS, progressionLimit), requestedLimit));
        boolean creative = player.isCreative();
        Deque<BlockPos> targets = RtsMiningValidator.collectUltimineTargets(player, pos, slot,
                creative ? ItemStack.EMPTY : lease.stack(),
                creative ? false : selectedToolRequested, limit, creative, mode);
        return beginPipelineBatch(player, session, targets, face, slot, lease,
                selectedToolRequested, toolProtectionEnabled, workflowEntryId);
    }

    /**
     * 从 pipeline 启动区域挖掘。目标扫描仍使用 Forge 现有的
     * AreaOperationExecutor，以保持 1.20.1 已验证的形状/填充语义。
     */
    public static PipelineBatchStartResult areaMineFromPipeline(ServerPlayer player, RtsStorageSession session,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            byte toolSlot, RtsToolLease toolLease, boolean selectedToolRequested,
            byte shapeType, byte fillType, boolean toolProtectionEnabled, int workflowEntryId) {
        RtsToolLease lease = toolLease == null ? RtsToolLease.empty() : toolLease;
        if (!RtsProgressionManager.canUse(player, RtsFeature.ULTIMINE) || session == null) {
            return completePipelineWithoutActiveBatch(player, session, lease);
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        if (RtsProgressionManager.getUltimineLimit(player) <= 0) {
            return completePipelineWithoutActiveBatch(player, session, lease);
        }

        int clampedMinX = minX;
        int clampedMaxX = Math.min(clampedMinX + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxX);
        int clampedMinZ = minZ;
        int clampedMaxZ = Math.min(clampedMinZ + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxZ);
        int clampedMinY = minY;
        int clampedMaxY = Math.min(clampedMinY + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxY);

        List<BlockPos> candidatePositions = AreaOperationExecutor.scanAreaMineTargets(
                player.serverLevel(),
                clampedMinX, clampedMaxX,
                clampedMinY, clampedMaxY,
                clampedMinZ, clampedMaxZ,
                player,
                shapeType, fillType);
        return beginPipelineBatch(player, session, new ArrayDeque<>(candidatePositions), Direction.DOWN, slot,
                lease, selectedToolRequested, toolProtectionEnabled, workflowEntryId);
    }

    /**
     * 从 pipeline 启动区域破坏。这里保留 1.20.1 对 waterlogged 方块和
     * 多方块历史记录的现有修复，只把入口迁入管线。
     */
    public static PipelineBatchStartResult areaDestroyFromPipeline(ServerPlayer player, RtsStorageSession session,
            List<BlockPos> positions, byte toolSlot, RtsToolLease toolLease, boolean selectedToolRequested,
            boolean toolProtectionEnabled, int workflowEntryId) {
        RtsToolLease lease = toolLease == null ? RtsToolLease.empty() : toolLease;
        if (!RtsProgressionManager.canUse(player, RtsFeature.AREA_DESTROY)
                || session == null || positions == null || positions.isEmpty()) {
            return completePipelineWithoutActiveBatch(player, session, lease);
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        boolean creative = player.isCreative();
        Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot,
                creative ? ItemStack.EMPTY : lease.stack(),
                creative ? false : selectedToolRequested, creative);
        return beginPipelineBatch(player, session, targets, Direction.DOWN, slot, lease,
                selectedToolRequested, toolProtectionEnabled, workflowEntryId);
    }

    private static PipelineBatchStartResult beginPipelineBatch(ServerPlayer player, RtsStorageSession session,
            Deque<BlockPos> targets, Direction face, int slot, RtsToolLease toolLease,
            boolean selectedToolRequested, boolean toolProtectionEnabled, int workflowEntryId) {
        if (targets == null || targets.isEmpty()) {
            return completePipelineWithoutActiveBatch(player, session, toolLease);
        }

        int targetCount = targets.size();
        if (player.isCreative()) {
            session.mining.miningFace = face == null ? Direction.DOWN : face;
            breakCreativeUltimineTargets(player, session, targets, slot);
            RtsToolLeaseManager.returnMiningTool(player, session, toolLease);
            RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
            return PipelineBatchStartResult.completedImmediately(targetCount);
        }

        if (workflowEntryId < 0) {
            return completePipelineWithoutActiveBatch(player, session, toolLease);
        }

        session.mining.miningToolLease = toolLease == null ? RtsToolLease.empty() : toolLease;
        session.mining.miningSelectedToolRequested = selectedToolRequested;
        session.mining.miningToolProtectionEnabled = toolProtectionEnabled;
        session.mining.miningWorkflowEntryId = workflowEntryId;
        session.mining.ultimineTargets.clear();
        session.mining.ultimineTargets.addAll(targets);
        session.mining.ultimineProgressPos = targets.peekFirst();
        session.mining.ultimineTotalTargets = targetCount;
        session.mining.ultimineProcessedTargets = 0;
        session.mining.ultimineProcessedPositions.clear();
        session.mining.ultimineAbsorbedDrops = false;
        session.mining.miningFace = face == null ? Direction.DOWN : face;
        session.mining.miningToolSlot = slot;
        RtsMiningNetworkHelper.sendUltimineProgress(player, 0, targetCount);
        RtsMiningStateMachine.beginRemoteMining(player, session, targets.peekFirst(), face, slot);
        return PipelineBatchStartResult.async(targetCount);
    }

    private static PipelineBatchStartResult completePipelineWithoutActiveBatch(ServerPlayer player,
            RtsStorageSession session, RtsToolLease toolLease) {
        if (player != null && session != null && toolLease != null) {
            RtsToolLeaseManager.returnMiningTool(player, session, toolLease);
        }
        return PipelineBatchStartResult.completedImmediately(0);
    }

    public static int queueStartUltimine(ServerPlayer player, RtsStorageSession session,
            BlockPos pos, Direction face, byte toolSlot, RtsToolLease toolLease, boolean selectedToolRequested,
            int requestedLimit, byte mode, boolean toolProtectionEnabled, int workflowEntryId) {
        RtsToolLease lease = toolLease == null ? RtsToolLease.empty() : toolLease;
        if (!RtsProgressionManager.canUse(player, RtsFeature.ULTIMINE) || session == null) {
            RtsToolLeaseManager.returnMiningTool(player, session, lease);
            return 0;
        }

        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        int progressionLimit = RtsProgressionManager.getUltimineLimit(player);
        if (progressionLimit <= 0) {
            RtsToolLeaseManager.returnMiningTool(player, session, lease);
            return 0;
        }
        int limit = Math.max(1, Math.min(Math.min(RtsMiningValidator.ULTIMINE_MAX_BLOCKS, progressionLimit), requestedLimit));
        boolean creative = player.isCreative();
        Deque<BlockPos> targets = RtsMiningValidator.collectUltimineTargets(player, pos, slot,
                creative ? ItemStack.EMPTY : lease.stack(),
                creative ? false : selectedToolRequested, limit, creative, mode);
        return queuePipelineBatch(player, session, targets, face, slot, lease,
                selectedToolRequested, toolProtectionEnabled, workflowEntryId, creative);
    }

    public static int queueAreaMine(ServerPlayer player, RtsStorageSession session,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            byte toolSlot, RtsToolLease toolLease, boolean selectedToolRequested,
            byte shapeType, byte fillType, boolean toolProtectionEnabled, int workflowEntryId) {
        RtsToolLease lease = toolLease == null ? RtsToolLease.empty() : toolLease;
        if (!RtsProgressionManager.canUse(player, RtsFeature.ULTIMINE) || session == null) {
            RtsToolLeaseManager.returnMiningTool(player, session, lease);
            return 0;
        }
        if (RtsProgressionManager.getUltimineLimit(player) <= 0) {
            RtsToolLeaseManager.returnMiningTool(player, session, lease);
            return 0;
        }

        int clampedMinX = minX;
        int clampedMaxX = Math.min(clampedMinX + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxX);
        int clampedMinZ = minZ;
        int clampedMaxZ = Math.min(clampedMinZ + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxZ);
        int clampedMinY = minY;
        int clampedMaxY = Math.min(clampedMinY + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxY);

        List<BlockPos> candidatePositions = AreaOperationExecutor.scanAreaMineTargets(
                player.serverLevel(),
                clampedMinX, clampedMaxX,
                clampedMinY, clampedMaxY,
                clampedMinZ, clampedMaxZ,
                player,
                shapeType, fillType);
        return queuePipelineBatch(player, session, new ArrayDeque<>(candidatePositions), Direction.DOWN,
                RtsMiningValidator.clampHotbarSlot(toolSlot), lease, selectedToolRequested,
                toolProtectionEnabled, workflowEntryId, player.isCreative());
    }

    public static int queueAreaDestroy(ServerPlayer player, RtsStorageSession session, List<BlockPos> positions,
            byte toolSlot, RtsToolLease toolLease, boolean selectedToolRequested,
            boolean toolProtectionEnabled, int workflowEntryId) {
        RtsToolLease lease = toolLease == null ? RtsToolLease.empty() : toolLease;
        if (!RtsProgressionManager.canUse(player, RtsFeature.AREA_DESTROY)
                || session == null || positions == null || positions.isEmpty()) {
            RtsToolLeaseManager.returnMiningTool(player, session, lease);
            return 0;
        }
        boolean creative = player.isCreative();
        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot,
                creative ? ItemStack.EMPTY : lease.stack(),
                creative ? false : selectedToolRequested, creative);
        return queuePipelineBatch(player, session, targets, Direction.DOWN, slot, lease,
                selectedToolRequested, toolProtectionEnabled, workflowEntryId, creative);
    }

    private static int queuePipelineBatch(ServerPlayer player, RtsStorageSession session, Deque<BlockPos> targets,
            Direction face, int slot, RtsToolLease toolLease, boolean selectedToolRequested,
            boolean toolProtectionEnabled, int workflowEntryId, boolean creative) {
        if (targets == null || targets.isEmpty() || workflowEntryId < 0) {
            RtsToolLeaseManager.returnMiningTool(player, session, toolLease);
            return 0;
        }

        int targetCount = targets.size();
        if (creative) {
            Direction previousFace = session.mining.miningFace;
            session.mining.miningFace = face == null ? Direction.DOWN : face;
            breakCreativeUltimineTargets(player, session, targets, slot);
            session.mining.miningFace = previousFace == null ? Direction.DOWN : previousFace;
            RtsToolLeaseManager.returnMiningTool(player, session, toolLease);
            RtsWorkflowEngine.getInstance().from(player, workflowEntryId)
                    .ifPresent(token -> {
                        token.setTotalBlocks(targetCount);
                        token.setCompletedBlocks(targetCount);
                        token.complete();
                    });
            RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
            return targetCount;
        }

        session.mining.ultimineJobQueue.addLast(new RtsMiningStateMachine.MiningJob(
                workflowEntryId,
                targets,
                targetCount,
                face,
                slot,
                toolLease,
                selectedToolRequested,
                toolProtectionEnabled));
        return targetCount;
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
        return RtsMiningTargetQueue.collectExplicitDestroyTargets(
                positions,
                pos -> RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)
                        && RtsClaimProtectionService.canBreakBlock(player, pos, Direction.DOWN),
                pos -> {
            BlockState state = level.getBlockState(pos);
            // FIXED: No longer incorrectly excludes waterlogged blocks
            if (!RtsMiningValidator.isBreakableBlock(state)
                    || !RtsMiningValidator.hasValidDestroySpeed(state, level, pos)) {
                return false;
            }
            if (!creative && RtsMiningStateMachine.computeRemoteDestroyStep(player, state, pos, toolSlot, linkedTool,
                    selectedToolRequested) <= 0.0F) {
                return false;
            }
            return true;
                });
    }

    // =========================================================================
    //  Ultimine Batch Processing
    // =========================================================================

    /**
     * Processes up to {@link RtsMiningValidator#ULTIMINE_BLOCKS_PER_TICK}
     * queued ultimine targets.
     */
    static void processUltimineTargets(ServerPlayer player, RtsStorageSession session) {
        if (session.mining.ultimineTargets.isEmpty()) {
            finishUltimineBatch(player, session);
            return;
        }
        RtsWorkflowToken workflowToken = resolveBatchWorkflow(player, session);
        if (session.mining.miningWorkflowEntryId >= 0 && workflowToken == null) {
            RtsMiningStateMachine.stopActiveMining(player, session);
            return;
        }
        if (workflowToken != null && workflowToken.isPaused()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        int processedThisTick = 0;
        int workflowProgressDelta = 0;
        boolean autoStoreDrops = RtsMiningValidator.canAutoStoreDrops(player, session);
        List<BlockPos> dropsToAbsorb = new ArrayList<>();
        boolean finishAfterThisTick = false;
        while (RtsMiningTargetQueue.canProcessAnotherTargetThisTick(processedThisTick, session.mining.ultimineTargets)) {
            if (RtsMiningValidator.isToolNearBreak(player, session)) {
                markWorkflowProgress(workflowToken, workflowProgressDelta);
                finishUltimineBatch(player, session);
                return;
            }
            BlockPos target = RtsMiningTargetQueue.pollNextTarget(session.mining.ultimineTargets);
            if (target == null) {
                break;
            }
            processedThisTick++;
            workflowProgressDelta++;
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
            if (RtsMiningStateMachine.computeRemoteDestroyStep(player, targetState, target, session.mining.miningToolSlot,
                    session.mining.miningToolLease.stack(), session.mining.miningSelectedToolRequested) <= 0.0F) {
                continue;
            }

            // Capture before state for history (including neighbors for multi-block tracking)
            HistoryBlockRecord preRecord = ServerHistoryManager.captureBlock(player.serverLevel(), target);
            List<HistoryBlockRecord> neighborRecords = captureNeighborRecords(level, target);

            RtsMiningStateMachine.MiningBreakResult result = RtsMiningStateMachine.destroyMinedBlock(
                    player, session, target, session.mining.miningToolSlot);

            if (result.broken() && preRecord != null) {
                session.mining.ultimineProcessedPositions.add(preRecord);
                // Record any collateral multi-block destruction
                recordCollateralBlocks(level, session, neighborRecords, target);
            }
            if (result.broken() && autoStoreDrops) {
                dropsToAbsorb.add(target.immutable());
            }
            if (result.broken() && RtsMiningValidator.isToolNearBreak(player, session)) {
                finishAfterThisTick = true;
                break;
            }
        }

        if (!dropsToAbsorb.isEmpty()
                && RtsDropAbsorber.absorbMinedDropsBatch(player, session, dropsToAbsorb)) {
            RtsPageService.markStorageViewDirty(player, session);
        }
        if (finishAfterThisTick) {
            markWorkflowProgress(workflowToken, workflowProgressDelta);
            finishUltimineBatch(player, session);
            return;
        }

        markWorkflowProgress(workflowToken, workflowProgressDelta);
        RtsMiningNetworkHelper.sendUltimineBatchProgress(player, session);
        if (session.mining.ultimineTargets.isEmpty()) {
            finishUltimineBatch(player, session);
        }
    }

    /**
     * Finalises an ultimine batch: clears progress, returns the borrowed tool,
     * marks the storage page dirty, and resets the mining state.
     */
    static void finishUltimineBatch(ServerPlayer player, RtsStorageSession session) {
        if (!session.mining.ultimineProcessedPositions.isEmpty()) {
            ServerHistoryManager.recordBreakWithRecords(player, new ArrayList<>(session.mining.ultimineProcessedPositions), session.mining.miningFace);
            session.mining.ultimineProcessedPositions.clear();
        }
        RtsMiningNetworkHelper.sendUltimineProgress(player, -1, 0);
        RtsToolLeaseManager.returnMiningTool(player, session, session.mining.miningToolLease);
        RtsPageService.markStorageViewDirty(player, session);
        if (session.mining.ultimineProgressPos != null) {
            RtsMiningNetworkHelper.clearMineProgress(player, session.mining.ultimineProgressPos);
        }
        completeBatchWorkflow(player, session);
        RtsMiningStateMachine.resetMiningState(session);
    }

    /**
     * Instantly breaks all queued ultimine targets for a creative-mode player.
     */
    static void breakCreativeUltimineTargets(ServerPlayer player, RtsStorageSession session, Deque<BlockPos> targets,
            int toolSlot) {
        if (!targets.isEmpty()) {
            List<BlockPos> validTargets = new ArrayList<>();
            for (BlockPos target : targets) {
                if (RtsLinkedStorageResolver.canAccessWorldTarget(player, target)
                        && RtsClaimProtectionService.canBreakBlock(player, target, Direction.DOWN)) {
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
            if (!RtsClaimProtectionService.canBreakBlock(player, target, Direction.DOWN)) {
                continue;
            }
            RtsMiningStateMachine.destroyMinedBlock(player, session, target, toolSlot);
        }
    }

    private static int startMiningWorkflow(ServerPlayer player, RtsWorkflowType type, int totalTargets) {
        return RtsWorkflowEngine.getInstance()
                .start(player, type, RtsWorkflowPriority.NORMAL, totalTargets)
                .map(RtsWorkflowToken::entryId)
                .orElse(-1);
    }

    private static RtsWorkflowToken resolveBatchWorkflow(ServerPlayer player, RtsStorageSession session) {
        if (session.mining.miningWorkflowEntryId < 0) {
            return null;
        }
        return RtsWorkflowEngine.getInstance()
                .from(player, session.mining.miningWorkflowEntryId)
                .orElse(null);
    }

    private static void markWorkflowProgress(RtsWorkflowToken token, int delta) {
        if (token != null && delta > 0) {
            token.updateProgress(delta, null);
        }
    }

    private static void completeBatchWorkflow(ServerPlayer player, RtsStorageSession session) {
        if (session.mining.miningWorkflowEntryId < 0) {
            return;
        }
        RtsWorkflowEngine.getInstance()
                .from(player, session.mining.miningWorkflowEntryId)
                .ifPresent(RtsWorkflowToken::complete);
        session.mining.miningWorkflowEntryId = -1;
    }

    // =========================================================================
    //  Multi-Block Collateral Tracking
    // =========================================================================

    /**
     * Captures the before-break state of all 6 neighbors for multi-block
     * structure tracking.
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
     * and records them as collateral in the session.
     */
    private static void recordCollateralBlocks(ServerLevel level, RtsStorageSession session,
            List<HistoryBlockRecord> neighborRecords, BlockPos brokenPos) {
        for (HistoryBlockRecord nr : neighborRecords) {
            if (nr.pos().equals(brokenPos)) {
                continue;
            }
            // If the neighbor was solid before but is now air, it was collateral-destroyed
            BlockState currentState = level.getBlockState(nr.pos());
            if (currentState.isAir() && !nr.state().isAir()) {
                session.mining.ultimineProcessedPositions.add(nr);
            }
        }
    }
}
