package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceBatchPayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceOperationTemplate;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowToken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Batch job queuing and tick processing for RTS remote block placement.
 *
 * <p>This helper owns the batch-job lifecycle: queueing placement requests,
 * throttling per-tick block-processing via {@link #tickPlaceBatchJobs}, and
 * the {@link PlaceBatchJob} data holder. It deliberately does not execute
 * individual placement logic, resolve quick-build state plans, play sounds,
 * or extract items ??those responsibilities live in their dedicated helpers.
 */
public final class RtsPlacementBatch {
    private static final int BUILD_BATCH_MAX_BLOCKS_PER_TICK = 64;
    private static final int BUILD_BATCH_MAX_QUEUED_JOBS = 4;

    private RtsPlacementBatch() {
    }

    /**
     * Entry point for single-item remote placement. Delegates to
     * {@link #enqueuePlaceBatch} with a single-position job.
     */
    public static void placeSelected(ServerPlayer player, RtsStorageSession session, BlockPos clickedPos, Direction face,
                                     double hitX, double hitY, double hitZ, byte rotateSteps, boolean forcePlace, boolean skipIfOccupied,
                                     String itemId, ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
                                     double rayDirX, double rayDirY, double rayDirZ, boolean quickBuild) {
        double hitOffsetX = clickedPos == null ? 0.5D : hitX - clickedPos.getX();
        double hitOffsetY = clickedPos == null ? 0.5D : hitY - clickedPos.getY();
        double hitOffsetZ = clickedPos == null ? 0.5D : hitZ - clickedPos.getZ();
        enqueuePlaceBatch(
                player,
                session,
                clickedPos == null ? List.of() : List.of(clickedPos),
                face,
                hitOffsetX,
                hitOffsetY,
                hitOffsetZ,
                rotateSteps,
                forcePlace,
                skipIfOccupied,
                itemId,
                itemPrototype,
                rayOriginX,
                rayOriginY,
                rayOriginZ,
                rayDirX,
                rayDirY,
                rayDirZ,
                quickBuild,
                false,
                true);
    }

    /**
     * Queues a batch of positions for remote placement. Sanitises input,
     * validates progression access, and caps the batch at
     * {@link C2SRtsPlaceBatchPayload#MAX_POSITIONS} positions.
     * <p>
     * Quick-build jobs (shape builds) are limited to
     * {@link #BUILD_BATCH_MAX_QUEUED_JOBS} queued jobs; when the queue is full,
     * new quick-build jobs are rejected. Single-block placements
     * ({@code quickBuild = false}) bypass this limit.
     */
    public static boolean enqueuePlaceBatch(ServerPlayer player, RtsStorageSession session, List<BlockPos> clickedPositions,
            Direction face, double hitOffsetX, double hitOffsetY, double hitOffsetZ, byte rotateSteps,
            boolean forcePlace, boolean skipIfOccupied, String itemId, ItemStack itemPrototype,
            double rayOriginX, double rayOriginY, double rayOriginZ, double rayDirX, double rayDirY,
            double rayDirZ, boolean quickBuild, boolean forceEmptyHand, boolean sendRemoteHint) {
        return enqueuePlaceBatch(player, session, clickedPositions, face, hitOffsetX, hitOffsetY, hitOffsetZ,
                rotateSteps, forcePlace, skipIfOccupied, itemId, itemPrototype,
                rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ,
                quickBuild, forceEmptyHand, sendRemoteHint, -1);
    }

    public static boolean enqueuePlaceBatch(ServerPlayer player, RtsStorageSession session, List<BlockPos> clickedPositions,
            Direction face, double hitOffsetX, double hitOffsetY, double hitOffsetZ, byte rotateSteps,
            boolean forcePlace, boolean skipIfOccupied, String itemId, ItemStack itemPrototype,
            double rayOriginX, double rayOriginY, double rayOriginZ, double rayDirX, double rayDirY,
            double rayDirZ, boolean quickBuild, boolean forceEmptyHand, boolean sendRemoteHint,
            int workflowEntryId) {
        if (!RtsProgressionManager.canUse(
                player, RtsFeature.REMOTE_PLACE)) {
            if (sendRemoteHint && player != null) {
                player.displayClientMessage(
                        Component.translatable("message.rtsbuilding.quick_build.remote_place_locked"), true);
            }
            return false;
        }
        if (session == null || clickedPositions == null || clickedPositions.isEmpty() || face == null) {
            return false;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        List<BlockPos> positions = new ArrayList<>(Math.min(clickedPositions.size(), C2SRtsPlaceBatchPayload.MAX_POSITIONS));
        for (BlockPos pos : clickedPositions) {
            if (pos == null || !RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                continue;
            }
            positions.add(pos.immutable());
            if (positions.size() >= C2SRtsPlaceBatchPayload.MAX_POSITIONS) {
                break;
            }
        }
        if (positions.isEmpty()) {
            return false;
        }
        // Quick-build jobs (shape builds) are limited to BUILD_BATCH_MAX_QUEUED_JOBS;
        // reject when full. Single-block placements bypass this limit.
        if (quickBuild && session.placement.placeBatchJobs.size() >= BUILD_BATCH_MAX_QUEUED_JOBS) {
            return false;
        }
        session.placement.placeBatchJobs.addLast(new PlaceBatchJob(
                positions,
                face,
                RtsPlacementHelper.sanitizeHitOffset(hitOffsetX, face, Direction.Axis.X),
                RtsPlacementHelper.sanitizeHitOffset(hitOffsetY, face, Direction.Axis.Y),
                RtsPlacementHelper.sanitizeHitOffset(hitOffsetZ, face, Direction.Axis.Z),
                rotateSteps,
                forcePlace,
                skipIfOccupied,
                itemId == null ? "" : itemId,
                RtsPlacementExtractor.sanitizePrototype(itemId, itemPrototype),
                rayOriginX,
                rayOriginY,
                rayOriginZ,
                rayDirX,
                rayDirY,
                rayDirZ,
                quickBuild,
                forceEmptyHand,
                sendRemoteHint,
                workflowEntryId));
        return true;
    }

    /**
     * Tick handler that processes up to {@link #BUILD_BATCH_MAX_BLOCKS_PER_TICK}
     * blocks from queued batch jobs. Quick-build jobs use the pre-resolved
     * state plan fast path; all others fall through to the interactive single
     * placement path. Saves and refreshes the session when a full job
     * completes.
     */
    public static void tickPlaceBatchJobs(ServerPlayer player, RtsStorageSession session) {
        tickPlaceBatchJobs(player, session, BUILD_BATCH_MAX_BLOCKS_PER_TICK, Long.MAX_VALUE);
    }

    /** 在数量与纳秒截止时间双预算内推进放置任务。 */
    public static int tickPlaceBatchJobs(ServerPlayer player, RtsStorageSession session,
            int maxBlocks, long deadlineNanos) {
        return tickPlaceBatchJobs(player, session, maxBlocks, deadlineNanos, null);
    }

    /** 仅推进指定任务；供 Task Engine 的 PlacementExecutor 使用。 */
    public static int tickPlaceTask(ServerPlayer player, RtsStorageSession session,
            PlaceBatchJob job, int maxBlocks, long deadlineNanos) {
        if (job == null || session == null || session.placement.placeBatchJobs.peekFirst() != job) {
            return 0;
        }
        return tickPlaceBatchJobs(player, session, maxBlocks, deadlineNanos, job);
    }

    private static int tickPlaceBatchJobs(ServerPlayer player, RtsStorageSession session,
            int maxBlocks, long deadlineNanos, PlaceBatchJob onlyJob) {
        if (player == null || session == null) {
            return 0;
        }
        int initialBudget = Math.max(0, Math.min(BUILD_BATCH_MAX_BLOCKS_PER_TICK, maxBlocks));
        int remaining = initialBudget;
        int pausedJobsSkipped = 0;
        Map<Integer, Integer> placedBeforeTick = new HashMap<>();
        List<PlaceBatchJob> fullyCompletedJobs = new ArrayList<>();
        for (PlaceBatchJob j : session.placement.placeBatchJobs) {
            placedBeforeTick.put(j.workflowEntryId(), j.placedPositions.size());
        }
        while (remaining > 0 && System.nanoTime() < deadlineNanos
                && !session.placement.placeBatchJobs.isEmpty()
                && (onlyJob == null || session.placement.placeBatchJobs.peekFirst() == onlyJob)) {
            PlaceBatchJob job = session.placement.placeBatchJobs.peekFirst();
            boolean hasWorkflowEntry = hasWorkflowEntry(job);
            Optional<RtsWorkflowToken> workflowToken = workflowToken(player, job);
            if (hasWorkflowEntry && workflowToken.isEmpty()) {
                session.placement.placeBatchJobs.removeFirst();
                pausedJobsSkipped = 0;
                continue;
            }
            // Task Engine 路径只读取展示令牌是否存在；暂停状态由 TaskRecord 单向决定。
            if (onlyJob == null && hasWorkflowEntry && workflowToken.get().isPaused()) {
                session.placement.placeBatchJobs.removeFirst();
                session.placement.placeBatchJobs.addLast(job);
                pausedJobsSkipped++;
                if (pausedJobsSkipped >= session.placement.placeBatchJobs.size()) {
                    break;
                }
                continue;
            }
            pausedJobsSkipped = 0;
            while (remaining > 0 && System.nanoTime() < deadlineNanos && job.hasNext()) {
                BlockPos clickedPos = job.next();
                RtsPlacementQuickBuild.StatePlacementPlan statePlan = job.quickBuild()
                        ? job.statePlacementPlan(player) : null;
                boolean keepGoing;
                if (statePlan != null) {
                    // 快速建造路径：记录放置前的状态，用于批撤??
                    BlockPos trackedPos = clickedPos;
                    BlockState beforeState = player.serverLevel().getBlockState(trackedPos);
                    keepGoing = RtsPlacementQuickBuild.placeStateBatchEntry(player, session, clickedPos, statePlan);
                    // 如果方块状态发生了变化（空气→方块），说明放置成功
                    if (keepGoing && (beforeState.isAir() || beforeState.canBeReplaced())
                            && !player.serverLevel().getBlockState(trackedPos).isAir()) {
                        job.placedPositions.add(trackedPos);
                    } else if (keepGoing) {
                        job.skippedWhileProcessing++;
                    }
                } else {
                    Vec3 hitLocation = new Vec3(
                            clickedPos.getX() + job.hitOffsetX(),
                            clickedPos.getY() + job.hitOffsetY(),
                            clickedPos.getZ() + job.hitOffsetZ());
                    // 记录放置前状态，用于检测实际放置位??
                    BlockPos adjPos = clickedPos.relative(job.face());
                    BlockState beforeClicked = player.serverLevel().getBlockState(clickedPos);
                    BlockState beforeAdjacent = player.serverLevel().hasChunkAt(adjPos)
                            ? player.serverLevel().getBlockState(adjPos) : null;
                    keepGoing = RtsPlacementExecutor.placeSelectedInternal(
                            player,
                            session,
                            clickedPos,
                            job.face(),
                            hitLocation.x,
                            hitLocation.y,
                            hitLocation.z,
                            job.rotateSteps(),
                            job.forcePlace(),
                            job.skipIfOccupied(),
                            job.itemId(),
                            job.itemPrototype(),
                            job.rayOriginX(),
                            job.rayOriginY(),
                            job.rayOriginZ(),
                            job.rayDirX(),
                            job.rayDirY(),
                            job.rayDirZ(),
                            job.quickBuild(),
                            job.forceEmptyHand(),
                            false,
                            job.sendRemoteHint());
                    // 检测实际放置位置（可能??clickedPos ??adjacentPos??
                    if (keepGoing) {
                        BlockPos actualPos = RtsPlacementHelper.detectPlacedPos(
                                player.serverLevel(), clickedPos, beforeClicked, adjPos, beforeAdjacent);
                        if (actualPos != null) {
                            job.placedPositions.add(actualPos);
                        } else {
                            job.skippedWhileProcessing++;
                        }
                    }
                }
                remaining--;
                if (!keepGoing) {
                    if (hasWorkflowEntry) {
                        job.unconsumeLast();
                        session.placement.placeBatchJobs.removeFirst();
                        session.placement.addPendingJob(job);
                        workflowToken.ifPresent(token -> token.suspend());
                    } else {
                        // 普通右键空手交互没有工作流槽位；失败或打开菜单后直接收尾，避免 workflow -1 丢弃。
                        session.placement.placeBatchJobs.removeFirst();
                        fullyCompletedJobs.add(job);
                    }
                    break;
                }
            }
            if (!session.placement.placeBatchJobs.isEmpty() && session.placement.placeBatchJobs.peekFirst() == job && !job.hasNext()) {
                session.placement.placeBatchJobs.removeFirst();
                fullyCompletedJobs.add(job);
            }
        }
        for (PlaceBatchJob completedJob : fullyCompletedJobs) {
            int before = placedBeforeTick.getOrDefault(completedJob.workflowEntryId(), 0);
            int delta = completedJob.placedPositions.size() - before;
            if (!completedJob.placedPositions.isEmpty()) {
                ServerHistoryManager.recordPlacement(player, completedJob.placedPositions, completedJob.face());
            }
            if (delta > 0) {
                workflowToken(player, completedJob).ifPresent(token -> token.updateProgress(delta, null));
            }
            if (onlyJob == null) {
                workflowToken(player, completedJob).ifPresent(token -> token.complete());
            }
        }
        boolean storageChanged = !fullyCompletedJobs.isEmpty();
        for (PlaceBatchJob j : session.placement.placeBatchJobs) {
            int before = placedBeforeTick.getOrDefault(j.workflowEntryId(), 0);
            int delta = j.placedPositions.size() - before;
            if (delta > 0) {
                workflowToken(player, j).ifPresent(token -> token.updateProgress(delta, null));
                storageChanged = true;
            }
        }
        if (storageChanged) {
            ServiceOperationTemplate.INSTANCE.markDirtyDeferred(player, session);
            RtsEffectAccumulator.INSTANCE.markStorageViewDirty(player.getUUID(), player.level().dimension());
            RtsEffectAccumulator.INSTANCE.markPersistence(player.getUUID(), player.level().dimension());
        }
        return initialBudget - remaining;
    }

    /**
     * 工作流消失时收拢已经发生的放置副作用，避免直接移除队列后丢失历史与持久化刷新。
     */
    public static void cancelPlaceTask(ServerPlayer player, RtsStorageSession session, PlaceBatchJob job) {
        if (player == null || session == null || job == null) return;
        boolean removed = session.placement.placeBatchJobs.remove(job)
                | session.placement.removePendingJob(job);
        if (!removed) return;
        if (!job.placedPositions.isEmpty()) {
            ServerHistoryManager.recordPlacement(player, job.placedPositions, job.face());
        }
        RtsEffectAccumulator.INSTANCE.markStorageViewDirty(player.getUUID(), player.level().dimension());
        RtsEffectAccumulator.INSTANCE.markWorkflow(player.getUUID(), player.level().dimension());
        RtsEffectAccumulator.INSTANCE.markPersistence(player.getUUID(), player.level().dimension());
    }

    private static boolean hasWorkflowEntry(PlaceBatchJob job) {
        return job.workflowEntryId() >= 0;
    }

    private static Optional<RtsWorkflowToken> workflowToken(ServerPlayer player, PlaceBatchJob job) {
        return hasWorkflowEntry(job)
                ? RtsWorkflowEngine.getInstance().from(player, job.workflowEntryId())
                : Optional.empty();
    }

    public static boolean resumePendingJob(ServerPlayer player, RtsStorageSession session, int workflowEntryId) {
        if (player == null || session == null || workflowEntryId < 0) {
            return false;
        }
        PlaceBatchJob matched = null;
        for (PlaceBatchJob job : session.placement.pendingJobs) {
            if (job.workflowEntryId() == workflowEntryId) {
                matched = job;
                break;
            }
        }
        if (matched == null) {
            return false;
        }
        session.placement.removePendingJob(matched);
        session.placement.placeBatchJobs.addLast(matched);
        RtsWorkflowEngine.getInstance().from(player, workflowEntryId).ifPresent(token -> token.resume());
        RtsSessionService.saveToPlayerNbt(player, session);
        return true;
    }

    public static boolean removePendingJob(RtsStorageSession session, int workflowEntryId) {
        if (session == null || workflowEntryId < 0) {
            return false;
        }
        PlaceBatchJob matched = null;
        for (PlaceBatchJob job : session.placement.pendingJobs) {
            if (job.workflowEntryId() == workflowEntryId) {
                matched = job;
                break;
            }
        }
        return matched != null && session.placement.removePendingJob(matched);
    }

    /**
     * A single batch placement job that holds the shared placement parameters
     * and an ordered list of target positions. Each job is processed by
     * {@link #tickPlaceBatchJobs} at a rate of up to
     * {@link #BUILD_BATCH_MAX_BLOCKS_PER_TICK} blocks per tick.
     */
    public static final class PlaceBatchJob {
        private final List<BlockPos> clickedPositions;
        private final Direction face;
        private final double hitOffsetX;
        private final double hitOffsetY;
        private final double hitOffsetZ;
        private final byte rotateSteps;
        private final boolean forcePlace;
        private final boolean skipIfOccupied;
        private final String itemId;
        private final ItemStack itemPrototype;
        private final double rayOriginX;
        private final double rayOriginY;
        private final double rayOriginZ;
        private final double rayDirX;
        private final double rayDirY;
        private final double rayDirZ;
        private final boolean quickBuild;
        private final boolean forceEmptyHand;
        private final boolean sendRemoteHint;
        private final int workflowEntryId;
        private int index;
        private boolean statePlanResolved;
        private RtsPlacementQuickBuild.StatePlacementPlan statePlan;
        final List<BlockPos> placedPositions = new ArrayList<>();
        /** 已消费游标但未产生放置结果的目标数。 */
        int skippedWhileProcessing;

        private PlaceBatchJob(List<BlockPos> clickedPositions, Direction face, double hitOffsetX, double hitOffsetY,
                double hitOffsetZ, byte rotateSteps, boolean forcePlace, boolean skipIfOccupied, String itemId,
                ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ, double rayDirX,
                double rayDirY, double rayDirZ, boolean quickBuild, boolean forceEmptyHand, boolean sendRemoteHint,
                int workflowEntryId) {
            this.clickedPositions = clickedPositions;
            this.face = face;
            this.hitOffsetX = hitOffsetX;
            this.hitOffsetY = hitOffsetY;
            this.hitOffsetZ = hitOffsetZ;
            this.rotateSteps = rotateSteps;
            this.forcePlace = forcePlace;
            this.skipIfOccupied = skipIfOccupied;
            this.itemId = itemId;
            this.itemPrototype = itemPrototype == null ? ItemStack.EMPTY : itemPrototype.copy();
            this.rayOriginX = rayOriginX;
            this.rayOriginY = rayOriginY;
            this.rayOriginZ = rayOriginZ;
            this.rayDirX = rayDirX;
            this.rayDirY = rayDirY;
            this.rayDirZ = rayDirZ;
            this.quickBuild = quickBuild;
            this.forceEmptyHand = forceEmptyHand;
            this.sendRemoteHint = sendRemoteHint;
            this.workflowEntryId = workflowEntryId;
        }

        private boolean hasNext() {
            return this.index < this.clickedPositions.size();
        }

        public int remainingCount() {
            return this.clickedPositions.size() - this.index;
        }

        public int totalCount() {
            return this.clickedPositions.size();
        }

        /** 供统一任务记录同步持久化游标，避免重新扫描已完成目标。 */
        public int getIndex() {
            return this.index;
        }

        public int successfulCount() {
            return this.placedPositions.size();
        }

        public int failedCount() {
            return this.skippedWhileProcessing;
        }

        private BlockPos next() {
            return this.clickedPositions.get(this.index++);
        }

        public List<BlockPos> remainingPositions() {
            return this.clickedPositions.subList(this.index, this.clickedPositions.size());
        }

        public List<BlockPos> clickedPositions() {
            return List.copyOf(this.clickedPositions);
        }

        void unconsumeLast() {
            if (this.index > 0) {
                this.index--;
            }
        }

        public void skipOne() {
            if (hasNext()) {
                this.index++;
            }
        }

        BlockPos templatePosition() {
            return this.clickedPositions.isEmpty() ? null : this.clickedPositions.get(0);
        }

        BlockHitResult templateHit(BlockPos templatePos) {
            return new BlockHitResult(
                    new Vec3(
                            templatePos.getX() + this.hitOffsetX,
                            templatePos.getY() + this.hitOffsetY,
                            templatePos.getZ() + this.hitOffsetZ),
                    this.face,
                    templatePos,
                    false);
        }

        private RtsPlacementQuickBuild.StatePlacementPlan statePlacementPlan(ServerPlayer player) {
            if (!this.statePlanResolved) {
                this.statePlan = RtsPlacementQuickBuild.resolveStatePlacementPlan(player, this);
                this.statePlanResolved = true;
            }
            return this.statePlan;
        }

        public Direction face() {
            return this.face;
        }

        double hitOffsetX() {
            return this.hitOffsetX;
        }

        double hitOffsetY() {
            return this.hitOffsetY;
        }

        double hitOffsetZ() {
            return this.hitOffsetZ;
        }

        byte rotateSteps() {
            return this.rotateSteps;
        }

        private boolean forcePlace() {
            return this.forcePlace;
        }

        private boolean skipIfOccupied() {
            return this.skipIfOccupied;
        }

        public String itemId() {
            return this.itemId;
        }

        public ItemStack itemPrototype() {
            return this.itemPrototype.copy();
        }

        private double rayOriginX() {
            return this.rayOriginX;
        }

        private double rayOriginY() {
            return this.rayOriginY;
        }

        private double rayOriginZ() {
            return this.rayOriginZ;
        }

        private double rayDirX() {
            return this.rayDirX;
        }

        private double rayDirY() {
            return this.rayDirY;
        }

        private double rayDirZ() {
            return this.rayDirZ;
        }

        boolean quickBuild() {
            return this.quickBuild;
        }

        private boolean forceEmptyHand() {
            return this.forceEmptyHand;
        }

        private boolean sendRemoteHint() {
            return this.sendRemoteHint;
        }

        public int workflowEntryId() {
            return this.workflowEntryId;
        }
    }
}
