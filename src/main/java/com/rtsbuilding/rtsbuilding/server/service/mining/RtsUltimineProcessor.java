package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.AreaOperationExecutor;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
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
    public static boolean startUltimine(ServerPlayer player, RtsStorageSession session,
            BlockPos pos, Direction face, byte toolSlot, int requestedLimit,
            byte mode, boolean toolProtectionEnabled) {
        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        int progressionLimit = RtsProgressionManager.getUltimineLimit(player);
        if (progressionLimit <= 0) {
            return false;
        }
        int limit = Math.max(1, Math.min(Math.min(RtsMiningValidator.ultimineMaxBlocks(), progressionLimit), requestedLimit));

        if (player.isCreative()) {
            Deque<BlockPos> targets = RtsMiningValidator.collectUltimineTargets(player, pos, slot, ItemStack.EMPTY, false,
                    limit, true, mode);
            if (targets.isEmpty()) {
                return false;
            }
            breakCreativeUltimineTargets(player, session, targets, slot);
            // UiRefresh handled by pipeline
            return false;
        }

        boolean selectedToolRequested = session.mining.miningSelectedToolRequested;
        RtsToolLease toolLease = session.mining.miningToolLease;
        if (toolLease == null) {
            return false;
        }
        Deque<BlockPos> targets = RtsMiningValidator.collectUltimineTargets(player, pos, slot, toolLease.stack(),
                selectedToolRequested, limit, false, mode);
        if (targets.isEmpty()) {
            return false;
        }

        int workflowEntryId = session.mining.workflowEntryId;
        boolean submitted = com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                .submitMiningTargets(player, workflowEntryId, targets,
                        face, slot, selectedToolRequested, toolProtectionEnabled, true);
        if (submitted) session.mining.workflowEntryId = -1;
        return submitted;
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

        // 限定范围
        AreaMineLimitBox limited = limitAreaMineBox(minX, maxX, minY, maxY, minZ, maxZ);
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
        Deque<BlockPos> targets = new ArrayDeque<>(candidatePositions);

        if (targets.isEmpty()) {
            return false;
        }

        if (player.isCreative()) {
            breakCreativeUltimineTargets(player, session, targets, slot);
            return false;
        }

        int workflowEntryId = session.mining.workflowEntryId;
        boolean submitted = com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                .submitMiningTargets(player, workflowEntryId, targets,
                        Direction.DOWN, slot, session.mining.miningSelectedToolRequested,
                        toolProtectionEnabled, true);
        if (submitted) session.mining.workflowEntryId = -1;
        return submitted;
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

        RtsbuildingMod.LOGGER.info("[RtsUltimineProcessor] areaDestroy: {} valid targets out of {} positions for {}",
                targets.size(), positions.size(), player.getGameProfile().getName());
        int workflowEntryId = session.mining.workflowEntryId;
        boolean submitted = com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                .submitMiningTargets(player, workflowEntryId, targets,
                        Direction.DOWN, slot, selectedToolRequested, toolProtectionEnabled, true);
        if (submitted) session.mining.workflowEntryId = -1;
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

        boolean submitted = com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE.submitMiningJob(
                player, session,
                new RtsMiningStateMachine.MiningJob(workflowEntryId, targets, targets.size()));
        RtsbuildingMod.LOGGER.info("[RtsUltimineProcessor] queueAreaDestroy: submitted {} targets for {}",
                targets.size(), player.getGameProfile().getName());
        return submitted ? targets.size() : 0;
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
        int limit = Math.max(1, Math.min(Math.min(RtsMiningValidator.ultimineMaxBlocks(), progressionLimit), requestedLimit));

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

        return com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE.submitMiningJob(
                player, session,
                new RtsMiningStateMachine.MiningJob(workflowEntryId, targets, targets.size()))
                ? targets.size() : 0;
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
        AreaMineLimitBox limitBox = limitAreaMineBox(minX, maxX, minY, maxY, minZ, maxZ);
        int clampedMinX = limitBox.minX();
        int clampedMaxX = limitBox.maxX();
        int clampedMinY = limitBox.minY();
        int clampedMaxY = limitBox.maxY();
        int clampedMinZ = limitBox.minZ();
        int clampedMaxZ = limitBox.maxZ();

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

        return com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE.submitMiningJob(
                player, session,
                new RtsMiningStateMachine.MiningJob(workflowEntryId, targets, targets.size()))
                ? targets.size() : 0;
    }

    static AreaMineLimitBox limitAreaMineBox(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        int safeMinX = Math.min(minX, maxX);
        int safeMaxX = Math.max(minX, maxX);
        int safeMinY = Math.min(minY, maxY);
        int safeMaxY = Math.max(minY, maxY);
        int safeMinZ = Math.min(minZ, maxZ);
        int safeMaxZ = Math.max(minZ, maxZ);

        int width = Math.max(1, Math.min(safeMaxX - safeMinX + 1, RtsMiningValidator.areaMineMaxWidth()));
        int height = Math.max(1, Math.min(safeMaxY - safeMinY + 1, RtsMiningValidator.areaMineMaxHeight()));
        int depth = Math.max(1, Math.min(safeMaxZ - safeMinZ + 1, RtsMiningValidator.areaMineMaxDepth()));
        int maxVolume = Math.max(1, RtsMiningValidator.areaMineMaxVolume());

        while ((long) width * height * depth > maxVolume) {
            if (height >= width && height >= depth && height > 1) {
                height--;
            } else if (width >= depth && width > 1) {
                width--;
            } else if (depth > 1) {
                depth--;
            } else {
                break;
            }
        }

        return new AreaMineLimitBox(
                safeMinX,
                safeMinX + width - 1,
                safeMinY,
                safeMinY + height - 1,
                safeMinZ,
                safeMinZ + depth - 1);
    }

    record AreaMineLimitBox(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
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
        int maxExplicitTargets = Math.min(
                RtsMiningValidator.areaDestroyMaxTargets(),
                RtsMiningValidator.areaMineMaxVolume());
        AreaMineLimitBox explicitLimit = explicitAreaDestroyFitsSoftEnvelopeForCaps(
                positions,
                RtsMiningValidator.areaMineMaxWidth(),
                RtsMiningValidator.areaMineMaxHeight(),
                RtsMiningValidator.areaMineMaxDepth(),
                maxExplicitTargets)
                        ? null
                        : limitExplicitAreaDestroyBox(sortedPositions);
        LinkedHashSet<BlockPos> unique = new LinkedHashSet<>();
        for (BlockPos raw : sortedPositions) {
            if (raw == null || unique.size() >= maxExplicitTargets) {
                continue;
            }
            BlockPos pos = raw.immutable();
            if (explicitLimit != null && !contains(explicitLimit, pos)) {
                continue;
            }
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                continue;
            }
            if (!RtsClaimProtectionService.canBreakBlock(player, pos, Direction.DOWN)) {
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

    static boolean explicitAreaDestroyFitsSoftEnvelopeForCaps(
            List<BlockPos> positions, int maxWidth, int maxHeight, int maxDepth, int maxTargets) {
        if (positions == null || positions.isEmpty()) {
            return true;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int count = 0;
        for (BlockPos pos : positions) {
            if (pos == null) {
                continue;
            }
            count++;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (count == 0) {
            return true;
        }
        return count <= Math.max(1, maxTargets)
                && (maxX - minX + 1) <= Math.max(1, maxWidth) + 1
                && (maxY - minY + 1) <= Math.max(1, maxHeight) + 1
                && (maxZ - minZ + 1) <= Math.max(1, maxDepth) + 1;
    }

    private static AreaMineLimitBox limitExplicitAreaDestroyBox(List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            if (pos == null) {
                continue;
            }
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (minX == Integer.MAX_VALUE) {
            return null;
        }
        return limitAreaMineBox(minX, maxX, minY, maxY, minZ, maxZ);
    }

    private static boolean contains(AreaMineLimitBox box, BlockPos pos) {
        return pos.getX() >= box.minX() && pos.getX() <= box.maxX()
                && pos.getY() >= box.minY() && pos.getY() <= box.maxY()
                && pos.getZ() >= box.minZ() && pos.getZ() <= box.maxZ();
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
            RtsbuildingMod.LOGGER.info("[RtsUltimineProcessor] processUltimineTargets: no remaining targets, finishing batch for {}",
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
            HistoryBlockRecord preRecord = ServerHistoryManager.captureBlock(player.serverLevel(), target);
            List<HistoryBlockRecord> neighborRecords = MultiBlockTracker.captureNeighborRecords(level, target);

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

}
